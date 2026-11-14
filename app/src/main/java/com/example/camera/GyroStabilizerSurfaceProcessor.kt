package com.example.camera

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import com.example.model.ColorFilterType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class GyroStabilizerEffect(
    executor: Executor,
    surfaceProcessor: SurfaceProcessor,
    errorListener: Consumer<Throwable>
) : CameraEffect(
    PREVIEW or VIDEO_CAPTURE,
    executor,
    surfaceProcessor,
    errorListener
)

/**
 * Real-time OpenGL ES 2.0 CameraX SurfaceProcessor that stabilizes AND color-grades BOTH
 * the live Preview and the recorded VideoCapture MP4 frames using 3-axis gyroscope data
 * and active cinema LUT matrices.
 */
class GyroStabilizerSurfaceProcessor(
    private val filter: GyroStabilizerFilter,
    private val onShakeTelemetry: (Float) -> Unit = {},
    private val onFatalGlError: (Throwable) -> Unit = {}
) : SurfaceProcessor, SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "GyroStabilizerGL"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private const val VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvpMatrix * aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            uniform mat3 uColorMatrix;
            uniform vec3 uColorOffset;
            void main() {
                vec4 texColor = texture2D(sTexture, vTexCoord);
                vec3 graded = clamp(uColorMatrix * texColor.rgb + uColorOffset, 0.0, 1.0);
                gl_FragColor = vec4(graded, texColor.a);
            }
        """

        private val QUAD_COORDS = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
        )

        private val TEX_COORDS = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f
        )
    }

    private data class OutputTarget(
        val surface: Surface,
        val eglSurface: EGLSurface,
        val size: Size
    )

    private val glThread = HandlerThread("CineFlowGlStabilizer", Process.THREAD_PRIORITY_DISPLAY).apply {
        start()
    }
    private val glHandler = Handler(glThread.looper)
    private val threadStopped = AtomicBoolean(false)

    val glExecutor: Executor = Executor { command ->
        if (!threadStopped.get() && glThread.isAlive) {
            glHandler.post(command)
        }
    }

    @Volatile
    var sensorOrientationDegrees: Int = 90

    @Volatile
    var isFrontCamera: Boolean = false

    @Volatile
    var colorFilter: ColorFilterType = ColorFilterType.NORMAL

    private val released = AtomicBoolean(false)
    private val fatalErrorReported = AtomicBoolean(false)
    private var glInitialized = false
    private var glFailed = false

    private var eglDisplay: EGLDisplay? = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext? = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var tempPbufferSurface: EGLSurface? = EGL14.EGL_NO_SURFACE

    private var programId = 0
    private var aPositionLoc = -1
    private var aTexCoordLoc = -1
    private var uMvpMatrixLoc = -1
    private var uTexMatrixLoc = -1
    private var uColorMatrixLoc = -1
    private var uColorOffsetLoc = -1
    private var sTextureLoc = -1
    private var oesTextureId = 0

    private val colorMat3 = FloatArray(9)
    private val colorOffset3 = FloatArray(3)

    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(QUAD_COORDS); position(0) }

    private val texCoordBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(TEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(TEX_COORDS); position(0) }

    private val rawTexMatrix = FloatArray(16)
    private val outputTexMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var inputSurfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private var activeInputCount = 0

    private val outputTargets = LinkedHashMap<SurfaceOutput, OutputTarget>()

    private fun reportFatalGlError(t: Throwable) {
        if (fatalErrorReported.compareAndSet(false, true)) {
            onFatalGlError(t)
        }
    }

    override fun onInputSurface(request: SurfaceRequest) {
        if (released.get() || glFailed) {
            request.willNotProvideSurface()
            return
        }
        try {
            ensureGlInitialized()
            val pbuffer = tempPbufferSurface
            val display = eglDisplay
            val context = eglContext
            if (pbuffer != null && pbuffer != EGL14.EGL_NO_SURFACE &&
                display != null && display != EGL14.EGL_NO_DISPLAY &&
                context != null && context != EGL14.EGL_NO_CONTEXT
            ) {
                EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
            }
            // Detach & release any previous SurfaceTexture BEFORE binding a new one to oesTextureId
            inputSurfaceTexture?.let { prevSt ->
                try {
                    prevSt.setOnFrameAvailableListener(null)
                    prevSt.release()
                } catch (_: Throwable) {
                }
            }
            inputSurface?.let { prevS ->
                try {
                    prevS.release()
                } catch (_: Throwable) {
                }
            }
            inputSurfaceTexture = null
            inputSurface = null

            val surfaceTexture = SurfaceTexture(oesTextureId).apply {
                setDefaultBufferSize(request.resolution.width.coerceAtLeast(1), request.resolution.height.coerceAtLeast(1))
                setOnFrameAvailableListener(this@GyroStabilizerSurfaceProcessor, glHandler)
            }
            val surface = Surface(surfaceTexture)
            inputSurfaceTexture = surfaceTexture
            inputSurface = surface
            activeInputCount++

            request.provideSurface(surface, glExecutor) {
                activeInputCount = (activeInputCount - 1).coerceAtLeast(0)
                try {
                    surfaceTexture.setOnFrameAvailableListener(null)
                    surfaceTexture.release()
                } catch (_: Throwable) {
                }
                try {
                    surface.release()
                } catch (_: Throwable) {
                }
                if (inputSurface === surface) {
                    inputSurfaceTexture = null
                    inputSurface = null
                }
                checkReadyToReleaseEgl()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to provide GL input surface", t)
            try {
                request.willNotProvideSurface()
            } catch (_: Throwable) {
            }
            reportFatalGlError(t)
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (released.get() || glFailed) {
            try {
                surfaceOutput.close()
            } catch (_: Throwable) {
            }
            return
        }
        var eglSurface: EGLSurface? = EGL14.EGL_NO_SURFACE
        try {
            ensureGlInitialized()
            val surface = surfaceOutput.getSurface(glExecutor) { event ->
                if (event.eventCode == SurfaceOutput.Event.EVENT_REQUEST_CLOSE) {
                    removeOutputSurface(surfaceOutput)
                }
            }
            if (!surface.isValid) {
                surfaceOutput.close()
                return
            }
            val display = eglDisplay
            if (display == null || display == EGL14.EGL_NO_DISPLAY) {
                throw IllegalStateException("EGL display not initialized")
            }
            val windowAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(
                display,
                eglConfig,
                surface,
                windowAttribs,
                0
            )
            if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
                throw IllegalStateException("eglCreateWindowSurface failed: ${EGL14.eglGetError()}")
            }
            outputTargets[surfaceOutput] = OutputTarget(
                surface = surface,
                eglSurface = eglSurface,
                size = surfaceOutput.size
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to attach GL output surface", t)
            val display = eglDisplay
            if (display != null && display != EGL14.EGL_NO_DISPLAY && eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
                try {
                    EGL14.eglDestroySurface(display, eglSurface)
                } catch (_: Throwable) {
                }
            }
            try {
                surfaceOutput.close()
            } catch (_: Throwable) {
            }
            reportFatalGlError(t)
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        if (released.get() || !glInitialized || glFailed) return
        if (inputSurfaceTexture !== surfaceTexture) return
        try {
            val activeSurfaceForUpdate = if (tempPbufferSurface != null && tempPbufferSurface != EGL14.EGL_NO_SURFACE) {
                tempPbufferSurface
            } else {
                outputTargets.values.firstOrNull { it.surface.isValid }?.eglSurface ?: EGL14.EGL_NO_SURFACE
            }
            val display = eglDisplay
            val context = eglContext
            if (display == null || display == EGL14.EGL_NO_DISPLAY ||
                context == null || context == EGL14.EGL_NO_CONTEXT ||
                activeSurfaceForUpdate == null || activeSurfaceForUpdate == EGL14.EGL_NO_SURFACE
            ) return
            if (!EGL14.eglMakeCurrent(display, activeSurfaceForUpdate, activeSurfaceForUpdate, context)) {
                return
            }
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(rawTexMatrix)
            val timestampNs = surfaceTexture.timestamp

            if (outputTargets.isEmpty()) return

            val m = colorFilter.getColorMatrix().values
            // Convert row-major 4x5 Compose ColorMatrix into column-major mat3 + normalized vec3 offset
            colorMat3[0] = m[0]; colorMat3[1] = m[5]; colorMat3[2] = m[10]
            colorMat3[3] = m[1]; colorMat3[4] = m[6]; colorMat3[5] = m[11]
            colorMat3[6] = m[2]; colorMat3[7] = m[7]; colorMat3[8] = m[12]
            colorOffset3[0] = m[4] / 255f
            colorOffset3[1] = m[9] / 255f
            colorOffset3[2] = m[14] / 255f

            var latestShakeDeg = 0f
            val targetsSnapshot = outputTargets.entries.toList()
            for ((surfaceOutput, target) in targetsSnapshot) {
                if (!target.surface.isValid || released.get()) continue
                try {
                    surfaceOutput.updateTransformMatrix(outputTexMatrix, rawTexMatrix)

                    val w = target.size.width.coerceAtLeast(1)
                    val h = target.size.height.coerceAtLeast(1)
                    val aspect = w.toFloat() / h.toFloat()

                    val stab = filter.sampleTransformForSurface(
                        surfaceWidth = w,
                        surfaceHeight = h,
                        surfaceTexMatrix = outputTexMatrix,
                        sensorOrientationDegrees = sensorOrientationDegrees,
                        isFrontCamera = isFrontCamera
                    )
                    latestShakeDeg = stab.shakeCancelledDegrees
                    GyroStabilizerFilter.buildMvpMatrix(mvpMatrix, stab, aspect)

                    if (!EGL14.eglMakeCurrent(display, target.eglSurface, target.eglSurface, context)) {
                        continue
                    }

                    GLES20.glViewport(0, 0, w, h)
                    GLES20.glUseProgram(programId)

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
                    GLES20.glUniform1i(sTextureLoc, 0)

                    GLES20.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
                    GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, outputTexMatrix, 0)
                    GLES20.glUniformMatrix3fv(uColorMatrixLoc, 1, false, colorMat3, 0)
                    GLES20.glUniform3fv(uColorOffsetLoc, 1, colorOffset3, 0)

                    GLES20.glEnableVertexAttribArray(aPositionLoc)
                    GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

                    GLES20.glEnableVertexAttribArray(aTexCoordLoc)
                    GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                    GLES20.glDisableVertexAttribArray(aPositionLoc)
                    GLES20.glDisableVertexAttribArray(aTexCoordLoc)

                    EGLExt.eglPresentationTimeANDROID(display, target.eglSurface, timestampNs)
                    EGL14.eglSwapBuffers(display, target.eglSurface)
                } catch (perTargetErr: Throwable) {
                    Log.w(TAG, "Skipped GL render for closed target", perTargetErr)
                }
            }
            onShakeTelemetry(latestShakeDeg)
        } catch (t: Throwable) {
            Log.e(TAG, "Error rendering stabilized GL frame", t)
        }
    }

    fun release() {
        if (released.compareAndSet(false, true)) {
            if (!threadStopped.get() && glThread.isAlive) {
                glHandler.post {
                    val iter = outputTargets.entries.iterator()
                    while (iter.hasNext()) {
                        val entry = iter.next()
                        val display = eglDisplay
                        if (display != null && display != EGL14.EGL_NO_DISPLAY && entry.value.eglSurface != EGL14.EGL_NO_SURFACE) {
                            try {
                                EGL14.eglDestroySurface(display, entry.value.eglSurface)
                            } catch (_: Throwable) {
                            }
                        }
                        try {
                            entry.key.close()
                        } catch (_: Throwable) {
                        }
                        iter.remove()
                    }
                    try {
                        inputSurfaceTexture?.setOnFrameAvailableListener(null)
                        inputSurfaceTexture?.release()
                    } catch (_: Throwable) {
                    }
                    try {
                        inputSurface?.release()
                    } catch (_: Throwable) {
                    }
                    inputSurfaceTexture = null
                    inputSurface = null
                    releaseEglResources()
                    // Allow CameraX async release callbacks ~200ms to drain on glExecutor before stopping looper
                    glHandler.postDelayed({
                        if (threadStopped.compareAndSet(false, true)) {
                            glThread.quitSafely()
                        }
                    }, 200L)
                }
            }
        }
    }

    private fun removeOutputSurface(surfaceOutput: SurfaceOutput) {
        val target = outputTargets.remove(surfaceOutput)
        val display = eglDisplay
        if (target != null && display != null && display != EGL14.EGL_NO_DISPLAY && target.eglSurface != EGL14.EGL_NO_SURFACE) {
            val pbuffer = tempPbufferSurface
            val context = eglContext
            if (pbuffer != null && pbuffer != EGL14.EGL_NO_SURFACE && context != null && context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
            } else {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            }
            try {
                EGL14.eglDestroySurface(display, target.eglSurface)
            } catch (_: Throwable) {
            }
        }
        try {
            surfaceOutput.close()
        } catch (_: Throwable) {
        }
        checkReadyToReleaseEgl()
    }

    private fun checkReadyToReleaseEgl() {
        if (released.get() && activeInputCount == 0 && outputTargets.isEmpty()) {
            releaseEglResources()
            if (threadStopped.compareAndSet(false, true)) {
                glThread.quitSafely()
            }
        }
    }

    private fun ensureGlInitialized() {
        if (glInitialized) return
        if (glFailed) throw IllegalStateException("GL stabilizer initialization previously failed")

        try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) {
                throw IllegalStateException("Unable to get EGL14 display")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                throw IllegalStateException("Unable to initialize EGL14")
            }
            eglDisplay = display

            val config = chooseEglConfig(display, withRecordable = true, withPbufferBit = true)
                ?: chooseEglConfig(display, withRecordable = false, withPbufferBit = true)
                ?: chooseEglConfig(display, withRecordable = false, withPbufferBit = false)
                ?: throw IllegalStateException("No matching EGL14 GLES2 config found")
            eglConfig = config

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (context == null || context == EGL14.EGL_NO_CONTEXT) {
                throw IllegalStateException("Unable to create EGL14 context: ${EGL14.eglGetError()}")
            }
            eglContext = context

            val pbufferAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            val pbuffer = EGL14.eglCreatePbufferSurface(display, config, pbufferAttribs, 0)
            if (pbuffer == null || pbuffer == EGL14.EGL_NO_SURFACE) {
                throw IllegalStateException("Unable to create temp EGL pbuffer surface: ${EGL14.eglGetError()}")
            }
            tempPbufferSurface = pbuffer

            if (!EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)) {
                throw IllegalStateException("eglMakeCurrent failed during init: ${EGL14.eglGetError()}")
            }

            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            programId = GLES20.glCreateProgram().also { prog ->
                GLES20.glAttachShader(prog, vertexShader)
                GLES20.glAttachShader(prog, fragmentShader)
                GLES20.glLinkProgram(prog)
                val linkStatus = IntArray(1)
                GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
                if (linkStatus[0] == 0) {
                    val info = GLES20.glGetProgramInfoLog(prog)
                    GLES20.glDeleteShader(vertexShader)
                    GLES20.glDeleteShader(fragmentShader)
                    GLES20.glDeleteProgram(prog)
                    throw IllegalStateException("GL program link failed: $info")
                }
                GLES20.glDeleteShader(vertexShader)
                GLES20.glDeleteShader(fragmentShader)
            }

            aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
            aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
            uMvpMatrixLoc = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
            uTexMatrixLoc = GLES20.glGetUniformLocation(programId, "uTexMatrix")
            uColorMatrixLoc = GLES20.glGetUniformLocation(programId, "uColorMatrix")
            uColorOffsetLoc = GLES20.glGetUniformLocation(programId, "uColorOffset")
            sTextureLoc = GLES20.glGetUniformLocation(programId, "sTexture")

            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            oesTextureId = texIds[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            glInitialized = true
            Log.d(TAG, "EGL14 + GLES20 3-Axis Gyro Stabilizer initialized")
        } catch (t: Throwable) {
            glFailed = true
            releaseEglResourcesInternal()
            throw t
        }
    }

    private fun chooseEglConfig(
        display: EGLDisplay,
        withRecordable: Boolean,
        withPbufferBit: Boolean
    ): EGLConfig? {
        val surfaceType = if (withPbufferBit) {
            EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT
        } else {
            EGL14.EGL_WINDOW_BIT
        }
        val attribs = if (withRecordable) {
            intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_SURFACE_TYPE, surfaceType,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
        } else {
            intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_SURFACE_TYPE, surfaceType,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
        }
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, numConfigs, 0)) {
            return null
        }
        return if (numConfigs[0] > 0) configs[0] else null
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Shader compile failed: $info")
        }
        return shader
    }

    private fun releaseEglResources() {
        if (!glInitialized) return
        releaseEglResourcesInternal()
        glInitialized = false
    }

    private fun releaseEglResourcesInternal() {
        val display = eglDisplay
        if (display != null && display != EGL14.EGL_NO_DISPLAY) {
            try {
                EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
            } catch (_: Throwable) {
            }
            if (oesTextureId != 0) {
                try {
                    GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
                } catch (_: Throwable) {
                }
                oesTextureId = 0
            }
            if (programId != 0) {
                try {
                    GLES20.glDeleteProgram(programId)
                } catch (_: Throwable) {
                }
                programId = 0
            }
            val pbuffer = tempPbufferSurface
            if (pbuffer != null && pbuffer != EGL14.EGL_NO_SURFACE) {
                try {
                    EGL14.eglDestroySurface(display, pbuffer)
                } catch (_: Throwable) {
                }
                tempPbufferSurface = EGL14.EGL_NO_SURFACE
            }
            val context = eglContext
            if (context != null && context != EGL14.EGL_NO_CONTEXT) {
                try {
                    EGL14.eglDestroyContext(display, context)
                } catch (_: Throwable) {
                }
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            try {
                EGL14.eglTerminate(display)
            } catch (_: Throwable) {
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}
