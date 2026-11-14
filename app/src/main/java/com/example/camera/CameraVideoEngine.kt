package com.example.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.Range
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

class CameraVideoEngine(private val context: Context) : SensorEventListener {

    private val TAG = "CameraVideoEngine"

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var videoCaptureUseCase: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentPreviewView: PreviewView? = null
    private var gyroProcessor: GyroStabilizerSurfaceProcessor? = null
    private val gyroFilter = GyroStabilizerFilter()

    private var activeHardwareInfo: CameraHardwareInfo =
        CameraHardwareHelper.queryCameraCapabilities(context, isFrontCamera = false)

    // Sensors for Level / Stabilizer
    private val sensorManager = runCatching {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }.getOrNull()
    private val rotationSensor = runCatching {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }.getOrNull()
    private val gyroscopeSensor = runCatching {
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }.getOrNull()

    // Telemetry state
    private val _rollDegrees = MutableStateFlow(0f)
    val rollDegrees: StateFlow<Float> = _rollDegrees.asStateFlow()

    private val _pitchDegrees = MutableStateFlow(0f)
    val pitchDegrees: StateFlow<Float> = _pitchDegrees.asStateFlow()

    private val _stabilizerDamping = MutableStateFlow(0.85f)
    val stabilizerDamping: StateFlow<Float> = _stabilizerDamping.asStateFlow()

    private val _horizonLockAngle = MutableStateFlow(0f)
    val horizonLockAngle: StateFlow<Float> = _horizonLockAngle.asStateFlow()

    private val _shakeCompX = MutableStateFlow(0f)
    val shakeCompX: StateFlow<Float> = _shakeCompX.asStateFlow()

    private val _shakeCompY = MutableStateFlow(0f)
    val shakeCompY: StateFlow<Float> = _shakeCompY.asStateFlow()

    private val _stabilityScore = MutableStateFlow(94)
    val stabilityScore: StateFlow<Int> = _stabilityScore.asStateFlow()

    private val _shakeCancelledDegrees = MutableStateFlow(0f)
    val shakeCancelledDegrees: StateFlow<Float> = _shakeCancelledDegrees.asStateFlow()

    // Zoom state
    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _minZoomRatio = MutableStateFlow(1.0f)
    val minZoomRatio: StateFlow<Float> = _minZoomRatio.asStateFlow()

    private val _maxZoomRatio = MutableStateFlow(4.0f)
    val maxZoomRatio: StateFlow<Float> = _maxZoomRatio.asStateFlow()

    // CameraX VideoCapture & Preview hardware-accelerated stabilization capability & active state
    private val _isHardwareStabilizationSupported = MutableStateFlow(true)
    val isHardwareStabilizationSupported: StateFlow<Boolean> = _isHardwareStabilizationSupported.asStateFlow()

    private val _isPreviewStabilizationSupported = MutableStateFlow(false)
    val isPreviewStabilizationSupported: StateFlow<Boolean> = _isPreviewStabilizationSupported.asStateFlow()

    private val _isHardwareStabilizationActive = MutableStateFlow(false)
    val isHardwareStabilizationActive: StateFlow<Boolean> = _isHardwareStabilizationActive.asStateFlow()

    private val _isGlStabilizerActive = MutableStateFlow(false)
    val isGlStabilizerActive: StateFlow<Boolean> = _isGlStabilizerActive.asStateFlow()

    private val _isGlFilterActive = MutableStateFlow(false)
    val isGlFilterActive: StateFlow<Boolean> = _isGlFilterActive.asStateFlow()

    private val _hasFlashUnit = MutableStateFlow(true)
    val hasFlashUnit: StateFlow<Boolean> = _hasFlashUnit.asStateFlow()

    // Real-time viewfinder telemetry (24x14 zero-allocation grid from PreviewView TextureView)
    private val _lumaHistogram = MutableStateFlow<List<Float>>(List(16) { 0f })
    val lumaHistogram: StateFlow<List<Float>> = _lumaHistogram.asStateFlow()

    private val _zebraCells = MutableStateFlow(BooleanArray(ANALYSIS_COLS * ANALYSIS_ROWS))
    val zebraCells: StateFlow<BooleanArray> = _zebraCells.asStateFlow()

    private val _focusPeakingCells = MutableStateFlow(BooleanArray(ANALYSIS_COLS * ANALYSIS_ROWS))
    val focusPeakingCells: StateFlow<BooleanArray> = _focusPeakingCells.asStateFlow()

    private var analysisBitmap: Bitmap? = null
    private val analysisPixels = IntArray(ANALYSIS_COLS * ANALYSIS_ROWS)
    private val analysisLuma = IntArray(ANALYSIS_COLS * ANALYSIS_ROWS)

    companion object {
        const val ANALYSIS_COLS = 24
        const val ANALYSIS_ROWS = 14
    }

    // Low-pass filter state for ultra-smooth gyro damping
    private var filteredRoll = 0f
    private var filteredPitch = 0f
    private var gyroVelX = 0f
    private var gyroVelY = 0f

    // Audio VU Meter level in dB (-60 dB to 0 dB)
    private val _audioDb = MutableStateFlow(-60f)
    val audioDb: StateFlow<Float> = _audioDb.asStateFlow()

    // Recording State
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    private var recordingStartTime = 0L
    private var recordingJob: Job? = null

    private var currentRecordingFile: File? = null
    private var currentRecordingTitle: String = ""
    private var onClipFinalizedCallback: ((VideoClip) -> Unit)? = null
    private var onClipErrorCallback: ((String) -> Unit)? = null

    // Video config
    var fpsMode: FpsMode = FpsMode.FPS_120
    var resolutionMode: ResolutionMode = ResolutionMode.FHD_1080P
    var stabilizationMode: StabilizationMode = StabilizationMode.HYBRID
    var gyroStabilizerStrength: GyroStabilizerStrength = GyroStabilizerStrength.ACTION
    var isAntiBlurShutterEnabled: Boolean = true
    var isHardwareVideoStabilizationEnabled: Boolean = true
    var isHorizonLockEnabled: Boolean = true
    var exposureMode: ExposureMode = ExposureMode.AUTO
    var manualIso: Int = 400
    var manualShutterSpeedIndex: Int = 3 // 1/120s
    var manualEvCompensation: Float = 0f
    var focusMode: FocusMode = FocusMode.CONTINUOUS
    var manualFocusDistance: Float = 0.5f
    var whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO
    var isAeLocked: Boolean = false
    var isAfLocked: Boolean = false
    var currentColorFilter: ColorFilterType = ColorFilterType.TEAL_AND_ORANGE
    var isFlashTorchOn: Boolean = false
    var isFrontCamera: Boolean = false

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val shutterSpeeds = listOf(
        "1/24s" to 1_000_000_000L / 24,
        "1/30s" to 1_000_000_000L / 30,
        "1/60s" to 1_000_000_000L / 60,
        "1/120s" to 1_000_000_000L / 120,
        "1/240s" to 1_000_000_000L / 240,
        "1/500s" to 1_000_000_000L / 500,
        "1/1000s" to 1_000_000_000L / 1000,
        "1/2000s" to 1_000_000_000L / 2000,
        "1/4000s" to 1_000_000_000L / 4000
    )

    init {
        registerSensorSafely(rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        // Android 12+ (targetSdk >= 31) throws SecurityException for SENSOR_DELAY_FASTEST (>200Hz)
        // without HIGH_SAMPLING_RATE_SENSORS; 6,000us (~166Hz) is smooth and safe on all devices.
        registerSensorSafely(gyroscopeSensor, 6_000)
    }

    private fun registerSensorSafely(sensor: Sensor?, samplingPeriodUs: Int) {
        if (sensor == null || sensorManager == null) return
        try {
            sensorManager.registerListener(this, sensor, samplingPeriodUs)
        } catch (_: SecurityException) {
            try {
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onInitialized: () -> Unit = {}
    ) {
        currentLifecycleOwner = lifecycleOwner
        currentPreviewView = previewView

        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    if (currentPreviewView !== previewView ||
                        lifecycleOwner.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.DESTROYED
                    ) {
                        return@addListener
                    }
                    cameraProvider = cameraProviderFuture.get()
                    bindCameraUseCases()
                    onInitialized()
                } catch (t: Throwable) {
                    Log.e(TAG, "Use case binding failed", t)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to obtain ProcessCameraProvider", t)
        }
    }

    fun unbindPreview(previewView: PreviewView? = null) {
        if (previewView != null && currentPreviewView !== previewView) return
        try {
            if (!_isRecording.value) {
                cameraProvider?.unbindAll()
                gyroProcessor?.release()
                gyroProcessor = null
                _isGlStabilizerActive.value = false
                _isGlFilterActive.value = false
                camera = null
            }
        } catch (_: Throwable) {
        }
        currentPreviewView = null
        currentLifecycleOwner = null
    }

    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalCamera2Interop::class)
    fun bindCameraUseCases(allowGlEffect: Boolean = true) {
        val provider = cameraProvider ?: return
        val lifecycleOwner = currentLifecycleOwner ?: return
        val previewView = currentPreviewView ?: return
        if (lifecycleOwner.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.DESTROYED) {
            return
        }

        try {
            provider.unbindAll()
            gyroProcessor?.release()
            gyroProcessor = null
            _isGlStabilizerActive.value = false
            _isGlFilterActive.value = false

            activeHardwareInfo = CameraHardwareHelper.queryCameraCapabilities(context, isFrontCamera)

            val preferredSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            val availableInfos = provider.availableCameraInfos
            val selectedCameraInfo = preferredSelector.filter(availableInfos).firstOrNull()
                ?: availableInfos.firstOrNull()
            val cameraSelector = if (preferredSelector.filter(availableInfos).isNotEmpty()) {
                preferredSelector
            } else {
                selectedCameraInfo?.cameraSelector ?: preferredSelector
            }
            val videoCapabilities = selectedCameraInfo?.let { Recorder.getVideoCapabilities(it) }
            val hwStabSupported = videoCapabilities?.isStabilizationSupported ?: false
            _isHardwareStabilizationSupported.value = hwStabSupported

            val previewCapabilities = selectedCameraInfo?.let { Preview.getPreviewCapabilities(it) }
            val previewStabSupported = previewCapabilities?.isStabilizationSupported ?: false
            _isPreviewStabilizationSupported.value = previewStabSupported

            val shouldEnableHwStabilization = isHardwareVideoStabilizationEnabled && hwStabSupported
            val shouldEnablePreviewStabilization =
                isHardwareVideoStabilizationEnabled &&
                    stabilizationMode == StabilizationMode.SUPER_EXTREME &&
                    previewStabSupported
            _isHardwareStabilizationActive.value = shouldEnableHwStabilization

            // Build Preview with CameraX Preview Stabilization + hardware-safe Camera2Interop options
            val previewBuilder = Preview.Builder()
            if (shouldEnablePreviewStabilization) {
                previewBuilder.setPreviewStabilizationEnabled(true)
            }
            val camera2Config = Camera2Interop.Extender(previewBuilder)

            val bestFpsRange = CameraHardwareHelper.chooseBestFpsRange(
                availableRanges = activeHardwareInfo.availableAeFpsRanges,
                targetFps = fpsMode.fps,
                preferFastShutterFloor = isAntiBlurShutterEnabled && gyroStabilizerStrength != GyroStabilizerStrength.OFF
            )
            if (bestFpsRange != null) {
                camera2Config.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    bestFpsRange
                )
            }
            camera2Config.setCaptureRequestOption(
                CaptureRequest.CONTROL_CAPTURE_INTENT,
                CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_RECORD
            )

            applyStabilizationToInterop(camera2Config, previewStabSupported)
            applyExposureToInterop(camera2Config)
            applyFocusToInterop(camera2Config)
            applyWhiteBalanceToInterop(camera2Config)

            previewUseCase = previewBuilder.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Build CameraX Recorder & VideoCapture use case with hardware-accelerated stabilization
            val preferredQuality = when (resolutionMode) {
                ResolutionMode.UHD_4K -> Quality.UHD
                ResolutionMode.FHD_1080P -> Quality.FHD
                ResolutionMode.HD_720P -> Quality.HD
            }
            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(preferredQuality, Quality.FHD, Quality.HD, Quality.SD),
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
            val baseBitRate = when (resolutionMode) {
                ResolutionMode.UHD_4K -> 36_000_000
                ResolutionMode.FHD_1080P -> if (fpsMode.fps >= 60) 24_000_000 else 16_000_000
                ResolutionMode.HD_720P -> if (fpsMode.fps >= 60) 14_000_000 else 10_000_000
            }
            val bitRate = if (stabilizationMode == StabilizationMode.SUPER_EXTREME ||
                gyroStabilizerStrength == GyroStabilizerStrength.LOCKDOWN
            ) {
                (baseBitRate * 1.15).toInt()
            } else {
                baseBitRate
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .setTargetVideoEncodingBitRate(bitRate)
                .build()

            val videoCaptureBuilder = VideoCapture.Builder(recorder)
                .setVideoStabilizationEnabled(shouldEnableHwStabilization)

            videoCaptureUseCase = videoCaptureBuilder.build()

            val needsGyroWarp = stabilizationMode != StabilizationMode.OFF &&
                gyroStabilizerStrength != GyroStabilizerStrength.OFF
            val needsColorLut = currentColorFilter != ColorFilterType.NORMAL
            val shouldAttachGlEffect = allowGlEffect && (needsGyroWarp || needsColorLut)

            if (shouldAttachGlEffect) {
                val sensorOrient = selectedCameraInfo?.sensorRotationDegrees
                    ?: if (isFrontCamera) 270 else 90
                syncGyroFilterConfig(shouldEnableHwStabilization)
                gyroFilter.reset()

                val processor = GyroStabilizerSurfaceProcessor(
                    filter = gyroFilter,
                    onShakeTelemetry = { deg -> _shakeCancelledDegrees.value = deg },
                    onFatalGlError = { err ->
                        Log.w(TAG, "GL Processor runtime error, falling back cleanly", err)
                        coroutineScope.launch {
                            if ((_isGlStabilizerActive.value || _isGlFilterActive.value) && !_isRecording.value) {
                                bindCameraUseCases(allowGlEffect = false)
                            }
                        }
                    }
                ).apply {
                    sensorOrientationDegrees = sensorOrient
                    isFrontCamera = this@CameraVideoEngine.isFrontCamera
                    colorFilter = currentColorFilter
                }
                gyroProcessor = processor

                val effect = GyroStabilizerEffect(
                    executor = processor.glExecutor,
                    surfaceProcessor = processor
                ) { throwable ->
                    Log.w(TAG, "CameraEffect runtime error, falling back to non-GL pipeline", throwable)
                    coroutineScope.launch {
                        if ((_isGlStabilizerActive.value || _isGlFilterActive.value) && !_isRecording.value) {
                            bindCameraUseCases(allowGlEffect = false)
                        }
                    }
                }

                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(previewUseCase!!)
                    .addUseCase(videoCaptureUseCase!!)
                    .addEffect(effect)
                    .build()

                try {
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        useCaseGroup
                    )
                    _isGlStabilizerActive.value = needsGyroWarp
                    _isGlFilterActive.value = true
                } catch (glBindError: Throwable) {
                    Log.w(TAG, "UseCaseGroup GL CameraEffect unsupported on stream, binding direct", glBindError)
                    processor.release()
                    gyroProcessor = null
                    _isGlStabilizerActive.value = false
                    _isGlFilterActive.value = false
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        previewUseCase,
                        videoCaptureUseCase
                    )
                }
            } else {
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    videoCaptureUseCase
                )
            }

            syncCameraCapabilitiesFromBoundCamera()

            Log.d(
                TAG,
                "Camera bound | FPS: ${fpsMode.fps} ($bestFpsRange) | Mode: ${stabilizationMode.name} | GL Gyro EIS: ${_isGlStabilizerActive.value} (${gyroStabilizerStrength.shortBadge}) | GL LUT: ${_isGlFilterActive.value} | HW Stab: $shouldEnableHwStabilization"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to bind Preview + VideoCapture, attempting Preview-only fallback", t)
            bindPreviewOnlyFallback(provider, lifecycleOwner, previewView)
        }
    }

    private fun syncCameraCapabilitiesFromBoundCamera() {
        val cam = camera ?: return
        try {
            val flashAvailable = cam.cameraInfo.hasFlashUnit()
            _hasFlashUnit.value = flashAvailable
            if (!flashAvailable) {
                isFlashTorchOn = false
            }
            cam.cameraControl.enableTorch(isFlashTorchOn && flashAvailable)
            val zState = cam.cameraInfo.zoomState.value
            if (zState != null) {
                _minZoomRatio.value = zState.minZoomRatio
                _maxZoomRatio.value = zState.maxZoomRatio
                val clamped = _zoomRatio.value.coerceIn(zState.minZoomRatio, zState.maxZoomRatio)
                _zoomRatio.value = clamped
                if (clamped != 1.0f) {
                    cam.cameraControl.setZoomRatio(clamped)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error syncing camera capabilities", t)
        }
    }

    /**
     * Zero-allocation 24x14 preview frame sampler that drives genuine live Histogram,
     * Zebra overexposure clipping, and Focus Peaking contrast edges without consuming
     * an extra CameraX ImageAnalysis hardware stream.
     */
    fun analyzePreviewFrame(
        computeHistogram: Boolean,
        computeZebra: Boolean,
        computeFocusPeaking: Boolean
    ) {
        if (!computeHistogram && !computeZebra && !computeFocusPeaking) return
        val previewView = currentPreviewView ?: return
        if (!previewView.isAttachedToWindow || previewView.width <= 0 || previewView.height <= 0) return
        val textureView = previewView.getChildAt(0) as? TextureView ?: return
        if (!textureView.isAvailable) return

        try {
            val existing = analysisBitmap
            val bmp = if (existing != null && !existing.isRecycled) {
                existing
            } else {
                Bitmap.createBitmap(
                    ANALYSIS_COLS,
                    ANALYSIS_ROWS,
                    Bitmap.Config.ARGB_8888
                ).also { analysisBitmap = it }
            }

            textureView.getBitmap(bmp)
            bmp.getPixels(analysisPixels, 0, ANALYSIS_COLS, 0, 0, ANALYSIS_COLS, ANALYSIS_ROWS)

            val bins = if (computeHistogram) IntArray(16) else null
            val zebra = if (computeZebra) BooleanArray(ANALYSIS_COLS * ANALYSIS_ROWS) else null

            for (i in analysisPixels.indices) {
                val px = analysisPixels[i]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                // Rec.709 fast integer luminance 0..255
                val luma = (54 * r + 183 * g + 19 * b) shr 8
                analysisLuma[i] = luma
                if (bins != null) {
                    val bin = (luma * 15) / 255
                    bins[bin]++
                }
                if (zebra != null) {
                    zebra[i] = luma >= 225
                }
            }

            if (bins != null) {
                val maxBin = bins.maxOrNull()?.coerceAtLeast(1) ?: 1
                _lumaHistogram.value = bins.map { it.toFloat() / maxBin.toFloat() }
            }
            if (zebra != null) {
                _zebraCells.value = zebra
            }
            if (computeFocusPeaking) {
                val peaking = BooleanArray(ANALYSIS_COLS * ANALYSIS_ROWS)
                for (y in 1 until ANALYSIS_ROWS - 1) {
                    for (x in 1 until ANALYSIS_COLS - 1) {
                        val idx = y * ANALYSIS_COLS + x
                        val c = analysisLuma[idx]
                        val grad = kotlin.math.abs(analysisLuma[idx - 1] - c) +
                            kotlin.math.abs(analysisLuma[idx + 1] - c) +
                            kotlin.math.abs(analysisLuma[idx - ANALYSIS_COLS] - c) +
                            kotlin.math.abs(analysisLuma[idx + ANALYSIS_COLS] - c)
                        peaking[idx] = grad >= 85
                    }
                }
                _focusPeakingCells.value = peaking
            }
        } catch (_: Exception) {
        }
    }

    fun setLiveColorFilter(filter: ColorFilterType) {
        val prev = currentColorFilter
        currentColorFilter = filter
        val processor = gyroProcessor
        if (processor != null) {
            processor.colorFilter = filter
        } else if (filter != ColorFilterType.NORMAL && prev == ColorFilterType.NORMAL && !_isRecording.value) {
            // Attach GL processor so LUT bakes into recorded video too
            bindCameraUseCases()
        }
    }

    fun tapToFocus(x: Float, y: Float) {
        val cam = camera ?: return
        val previewView = currentPreviewView ?: return
        try {
            val point = previewView.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
            Log.w(TAG, "Tap-to-focus failed", e)
        }
    }

    fun setZoomRatio(ratio: Float) {
        val cam = camera
        val minZ = _minZoomRatio.value
        val maxZ = _maxZoomRatio.value.coerceAtLeast(minZ)
        val clamped = ratio.coerceIn(minZ, maxZ)
        _zoomRatio.value = clamped
        cam?.cameraControl?.setZoomRatio(clamped)
    }

    fun zoomBy(scaleFactor: Float) {
        if (scaleFactor <= 0f) return
        setZoomRatio(_zoomRatio.value * scaleFactor)
    }

    fun syncGyroFilterConfig(hwStabActive: Boolean = _isHardwareStabilizationActive.value) {
        gyroFilter.strength = if (stabilizationMode == StabilizationMode.OFF) {
            GyroStabilizerStrength.OFF
        } else {
            gyroStabilizerStrength
        }
        gyroFilter.isHorizonLockEnabled = isHorizonLockEnabled &&
            (stabilizationMode == StabilizationMode.SUPER_EXTREME ||
                gyroStabilizerStrength == GyroStabilizerStrength.ACTION ||
                gyroStabilizerStrength == GyroStabilizerStrength.LOCKDOWN)
        val hasHalEis = hwStabActive &&
            stabilizationMode != StabilizationMode.OIS &&
            stabilizationMode != StabilizationMode.OFF
        gyroFilter.gainMultiplier = if (hasHalEis) 0.70f else 1.05f
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindPreviewOnlyFallback(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        try {
            provider.unbindAll()
            videoCaptureUseCase = null
            _isHardwareStabilizationActive.value = false
            _isGlStabilizerActive.value = false
            _isGlFilterActive.value = false

            val preferredSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            val availableInfos = provider.availableCameraInfos
            val cameraSelector = if (preferredSelector.filter(availableInfos).isNotEmpty()) {
                preferredSelector
            } else {
                availableInfos.firstOrNull()?.cameraSelector ?: preferredSelector
            }
            val previewBuilder = Preview.Builder()
            previewUseCase = previewBuilder.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, previewUseCase)
            syncCameraCapabilitiesFromBoundCamera()
        } catch (e: Exception) {
            Log.e(TAG, "Preview-only fallback also failed", e)
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyStabilizationToInterop(
        extender: Camera2Interop.Extender<*>,
        previewStabSupported: Boolean = _isPreviewStabilizationSupported.value
    ) {
        val supportsOis = activeHardwareInfo.supportsOis
        val supportsEis = activeHardwareInfo.supportsEis

        if (!isHardwareVideoStabilizationEnabled || stabilizationMode == StabilizationMode.OFF) {
            if (supportsEis) {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
            }
            if (supportsOis) {
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
            }
            return
        }

        when (stabilizationMode) {
            StabilizationMode.OFF -> Unit
            StabilizationMode.OIS -> {
                if (supportsEis) {
                    extender.setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                    )
                }
                if (supportsOis) {
                    extender.setCaptureRequestOption(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                    )
                }
            }
            StabilizationMode.EIS -> {
                if (supportsEis) {
                    extender.setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                    )
                }
                if (supportsOis) {
                    extender.setCaptureRequestOption(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                    )
                }
            }
            StabilizationMode.HYBRID -> {
                if (supportsEis) {
                    extender.setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                    )
                }
                if (supportsOis) {
                    extender.setCaptureRequestOption(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                    )
                }
            }
            StabilizationMode.SUPER_EXTREME -> {
                // If previewBuilder.setPreviewStabilizationEnabled(true) was applied, let CameraX manage stabilization mode
                if (!previewStabSupported) {
                    if (supportsEis) {
                        extender.setCaptureRequestOption(
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                        )
                    }
                    if (supportsOis) {
                        extender.setCaptureRequestOption(
                            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                            CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                        )
                    }
                }
            }
        }
    }

    private fun clampedIso(): Int =
        manualIso.coerceIn(activeHardwareInfo.minIso, activeHardwareInfo.maxIso.coerceAtLeast(activeHardwareInfo.minIso))

    private fun clampedExposureTimeNs(): Long {
        val raw = shutterSpeeds.getOrNull(manualShutterSpeedIndex)?.second ?: (1_000_000_000L / 120)
        return raw.coerceIn(
            activeHardwareInfo.minExposureTimeNs,
            activeHardwareInfo.maxExposureTimeNs.coerceAtLeast(activeHardwareInfo.minExposureTimeNs)
        )
    }

    private fun clampedEvIndex(): Int {
        val step = activeHardwareInfo.evStep.coerceAtLeast(0.05f)
        return (manualEvCompensation / step).roundToInt()
            .coerceIn(activeHardwareInfo.minEv, activeHardwareInfo.maxEv.coerceAtLeast(activeHardwareInfo.minEv))
    }

    private fun manualFocusDiopters(): Float {
        val maxDiopters = activeHardwareInfo.maxFocusDistance.coerceAtLeast(0f)
        // UI slider: 0.0 = Near (Macro -> maxDiopters), 1.0 = Infinity (0.0 diopters)
        return ((1f - manualFocusDistance.coerceIn(0f, 1f)) * maxDiopters).coerceIn(0f, maxDiopters)
    }

    private fun camera2AwbMode(): Int = when (whiteBalanceMode) {
        WhiteBalanceMode.AUTO -> CameraMetadata.CONTROL_AWB_MODE_AUTO
        WhiteBalanceMode.DAYLIGHT -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
        WhiteBalanceMode.CLOUDY -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
        WhiteBalanceMode.SHADE -> CameraMetadata.CONTROL_AWB_MODE_SHADE
        WhiteBalanceMode.TUNGSTEN -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
        WhiteBalanceMode.FLUORESCENT -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
        WhiteBalanceMode.CUSTOM -> CameraMetadata.CONTROL_AWB_MODE_WARM_FLUORESCENT
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyExposureToInterop(extender: Camera2Interop.Extender<*>) {
        if (exposureMode == ExposureMode.MANUAL) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CameraMetadata.CONTROL_AE_MODE_OFF
            )
            extender.setCaptureRequestOption(
                CaptureRequest.SENSOR_SENSITIVITY,
                clampedIso()
            )
            extender.setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                clampedExposureTimeNs()
            )
        } else {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CameraMetadata.CONTROL_AE_MODE_ON
            )
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                clampedEvIndex()
            )
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_LOCK,
                isAeLocked
            )
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyFocusToInterop(extender: Camera2Interop.Extender<*>) {
        when (focusMode) {
            FocusMode.CONTINUOUS -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                )
            }
            FocusMode.MANUAL -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_OFF
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    manualFocusDiopters()
                )
            }
            FocusMode.MACRO -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_MACRO
                )
            }
            FocusMode.INFINITY -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_OFF
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    0.0f
                )
            }
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyWhiteBalanceToInterop(extender: Camera2Interop.Extender<*>) {
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_MODE,
            camera2AwbMode()
        )
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun updateLiveControls() {
        val cam = camera ?: return
        try {
            val camera2Control = Camera2CameraControl.from(cam.cameraControl)
            val optionsBuilder = CaptureRequestOptions.Builder()

            if (exposureMode == ExposureMode.MANUAL) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_OFF
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    clampedIso()
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    clampedExposureTimeNs()
                )
            } else {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                    clampedEvIndex()
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_LOCK,
                    isAeLocked
                )
            }

            // Stabilization (only apply Camera2 modes reported as supported by this lens)
            val supportsOis = activeHardwareInfo.supportsOis
            val supportsEis = activeHardwareInfo.supportsEis
            if (!isHardwareVideoStabilizationEnabled || stabilizationMode == StabilizationMode.OFF) {
                if (supportsEis) {
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                    )
                }
                if (supportsOis) {
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                        CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                    )
                }
            } else {
                when (stabilizationMode) {
                    StabilizationMode.OIS -> {
                        if (supportsEis) {
                            optionsBuilder.setCaptureRequestOption(
                                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                            )
                        }
                        if (supportsOis) {
                            optionsBuilder.setCaptureRequestOption(
                                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                            )
                        }
                    }
                    StabilizationMode.EIS -> {
                        if (supportsEis) {
                            optionsBuilder.setCaptureRequestOption(
                                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                            )
                        }
                        if (supportsOis) {
                            optionsBuilder.setCaptureRequestOption(
                                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                            )
                        }
                    }
                    StabilizationMode.HYBRID -> {
                        if (supportsEis) {
                            optionsBuilder.setCaptureRequestOption(
                                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                            )
                        }
                        if (supportsOis) {
                            optionsBuilder.setCaptureRequestOption(
                                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                            )
                        }
                    }
                    StabilizationMode.SUPER_EXTREME -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && _isPreviewStabilizationSupported.value) {
                            optionsBuilder.setCaptureRequestOption(
                                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                            )
                        } else {
                            if (supportsEis) {
                                optionsBuilder.setCaptureRequestOption(
                                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                                )
                            }
                            if (supportsOis) {
                                optionsBuilder.setCaptureRequestOption(
                                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                                )
                            }
                        }
                    }
                    StabilizationMode.OFF -> Unit
                }
            }

            // Focus
            when (focusMode) {
                FocusMode.MANUAL -> {
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_OFF
                    )
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.LENS_FOCUS_DISTANCE,
                        manualFocusDiopters()
                    )
                }
                FocusMode.CONTINUOUS -> {
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    )
                }
                FocusMode.MACRO -> {
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_MACRO
                    )
                }
                FocusMode.INFINITY -> {
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_OFF
                    )
                    optionsBuilder.setCaptureRequestOption(
                        CaptureRequest.LENS_FOCUS_DISTANCE,
                        0.0f
                    )
                }
            }

            // White Balance live update without restarting session
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                camera2AwbMode()
            )

            camera2Control.setCaptureRequestOptions(optionsBuilder.build())
            cam.cameraControl.enableTorch(isFlashTorchOn && _hasFlashUnit.value)
        } catch (t: Throwable) {
            Log.e(TAG, "Error updating live camera controls", t)
        }
    }

    /**
     * Start video recording into device MediaStore Movies/CineFlowPro (API 29+) or app Movies directory (API 24-28).
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        onClipFinalized: (VideoClip) -> Unit = {}
    ) {
        if (_isRecording.value) return

        val videoCapture = videoCaptureUseCase
        if (videoCapture == null) {
            onError("Camera video recorder unavailable on this device")
            return
        }

        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val prefix = if (fpsMode.isHighSpeed) "SLOWMO_${fpsMode.fps}FPS_" else "CINE_${fpsMode.fps}FPS_"
            val displayName = "${prefix}${timeStamp}"
            currentRecordingTitle = displayName
            onClipFinalizedCallback = onClipFinalized
            onClipErrorCallback = onError

            var pendingRecording = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                currentRecordingFile = null
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.mp4")
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/CineFlowPro")
                }
                val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                    context.contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ).setContentValues(contentValues).build()
                videoCapture.output.prepareRecording(context, mediaStoreOutput)
            } else {
                val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?.let { File(it, "CineFlowPro") }
                    ?: File(context.filesDir, "CineFlowVideos")
                if (!outputDir.exists()) outputDir.mkdirs()
                val videoFile = File(outputDir, "$displayName.mp4")
                currentRecordingFile = videoFile
                val fileOutput = FileOutputOptions.Builder(videoFile).build()
                videoCapture.output.prepareRecording(context, fileOutput)
            }

            val hasAudioPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasAudioPerm) {
                pendingRecording = pendingRecording.withAudioEnabled()
            }

            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "CameraX VideoCapture started (GL=${_isGlStabilizerActive.value}, HW=${_isHardwareStabilizationActive.value})")
                    }
                    is VideoRecordEvent.Status -> {
                        val audioStats = event.recordingStats.audioStats
                        if (audioStats.hasAudio()) {
                            val amp = audioStats.audioAmplitude.coerceAtLeast(0.0001)
                            _audioDb.value = (20.0 * log10(amp)).toFloat().coerceIn(-60f, 0f)
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        activeRecording = null
                        _isRecording.value = false
                        recordingJob?.cancel()
                        _audioDb.value = -60f

                        val uri = event.outputResults.outputUri
                        val nonFatalLimitError = event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
                            event.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED

                        if (event.hasError() && !nonFatalLimitError) {
                            Log.e(TAG, "VideoCapture failed with error=${event.error}", event.cause)
                            if (uri != Uri.EMPTY) {
                                try {
                                    context.contentResolver.delete(uri, null, null)
                                } catch (_: Exception) {
                                }
                            }
                            currentRecordingFile?.let { f ->
                                if (f.exists() && f.length() == 0L) f.delete()
                            }
                            onClipErrorCallback?.invoke("Recording failed (code ${event.error})")
                        } else {
                            val clip = buildVideoClipMetadata(uri, currentRecordingFile, currentRecordingTitle, recordingElapsedSnapshot)
                            if (clip != null) {
                                onClipFinalizedCallback?.invoke(clip)
                            } else {
                                onClipErrorCallback?.invoke("Recorded video file was empty")
                            }
                        }
                        onClipFinalizedCallback = null
                        onClipErrorCallback = null
                    }
                }
            }

            _isRecording.value = true
            recordingStartTime = SystemClock.elapsedRealtime()
            recordingElapsedSnapshot = 1L

            recordingJob?.cancel()
            recordingJob = coroutineScope.launch {
                while (_isRecording.value) {
                    val elapsed = (SystemClock.elapsedRealtime() - recordingStartTime) / 1000L
                    _recordingDurationSeconds.value = elapsed
                    recordingElapsedSnapshot = elapsed.coerceAtLeast(1L)
                    delay(500)
                }
            }

            onSuccess(displayName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _isRecording.value = false
            onError(e.message ?: "Failed to start recording")
        }
    }

    private var recordingElapsedSnapshot: Long = 1L

    fun stopRecording() {
        if (!_isRecording.value) return

        _isRecording.value = false
        recordingJob?.cancel()
        _audioDb.value = -60f

        val elapsedSeconds = _recordingDurationSeconds.value.coerceAtLeast(1L)
        recordingElapsedSnapshot = elapsedSeconds
        _recordingDurationSeconds.value = 0L

        val recording = activeRecording
        if (recording != null) {
            try {
                recording.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Exception stopping CameraX Recording", e)
                activeRecording = null
            }
        }
    }

    private fun buildVideoClipMetadata(
        outputUri: Uri,
        fallbackFile: File?,
        title: String,
        elapsedSeconds: Long
    ): VideoClip? {
        val (pathOrUri, byteSize) = if (outputUri != Uri.EMPTY && outputUri.scheme == "content") {
            val size = queryUriSizeBytes(outputUri)
            if (size <= 0L) return null
            outputUri.toString() to size
        } else {
            val file = fallbackFile ?: outputUri.path?.let { File(it) }
            if (file == null || !file.exists() || file.length() <= 0L) {
                return null
            }
            file.absolutePath to file.length()
        }

        val shutterText = shutterSpeeds.getOrNull(manualShutterSpeedIndex)?.first ?: "Auto"
        val sizeFormatted = formatFileSize(byteSize)
        val stabLabel = buildString {
            append(stabilizationMode.label)
            if (_isGlStabilizerActive.value && gyroStabilizerStrength != GyroStabilizerStrength.OFF) {
                append(" + GL Gyro ${gyroStabilizerStrength.shortBadge}")
            }
            if (_isHardwareStabilizationActive.value) {
                append(" (HW)")
            }
        }

        return VideoClip(
            id = UUID.randomUUID().toString(),
            filePath = pathOrUri,
            title = title.ifBlank { "CineFlow_${System.currentTimeMillis()}" },
            timestamp = System.currentTimeMillis(),
            durationSeconds = elapsedSeconds,
            fps = fpsMode.fps,
            resolution = resolutionMode.shortName,
            filterName = currentColorFilter.displayName,
            stabilization = stabLabel,
            iso = if (exposureMode == ExposureMode.MANUAL) clampedIso() else 200,
            shutterSpeedText = if (exposureMode == ExposureMode.MANUAL) shutterText else "Auto",
            isSlowMotion = fpsMode.isHighSpeed,
            fileSizeFormatted = sizeFormatted
        )
    }

    private fun queryUriSizeBytes(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
                }
            }
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values.size < 3) return
        try {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val rawPitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                val rawRoll = -Math.toDegrees(orientation[2].toDouble()).toFloat()

                gyroFilter.onRotationRollSample(rawRoll)

                val alpha = if (stabilizationMode == StabilizationMode.SUPER_EXTREME) 0.14f else 0.25f
                filteredPitch = filteredPitch * (1f - alpha) + rawPitch * alpha
                filteredRoll = filteredRoll * (1f - alpha) + rawRoll * alpha

                _pitchDegrees.value = filteredPitch
                _rollDegrees.value = filteredRoll

                val rollDelta = Math.abs(filteredRoll)
                val isExtreme = stabilizationMode == StabilizationMode.SUPER_EXTREME && isHardwareVideoStabilizationEnabled
                _horizonLockAngle.value = if (isExtreme && isHorizonLockEnabled) {
                    (-filteredRoll).coerceIn(-18f, 18f)
                } else {
                    0f
                }

                val baseEfficiency = when {
                    stabilizationMode == StabilizationMode.OFF -> 0.40f
                    _isGlStabilizerActive.value && gyroStabilizerStrength == GyroStabilizerStrength.LOCKDOWN -> 0.99f
                    _isGlStabilizerActive.value && gyroStabilizerStrength == GyroStabilizerStrength.ACTION -> 0.96f
                    stabilizationMode == StabilizationMode.SUPER_EXTREME -> 0.98f
                    stabilizationMode == StabilizationMode.HYBRID -> 0.93f
                    else -> 0.85f
                }
                val motionPenalty = (rollDelta / 60f).coerceIn(0f, 0.15f)
                val eff = (baseEfficiency - (if (isExtreme || _isGlStabilizerActive.value) motionPenalty * 0.15f else motionPenalty)).coerceIn(0.35f, 0.99f)
                _stabilizerDamping.value = eff
                _stabilityScore.value = (eff * 100).roundToInt()
            } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]

                gyroFilter.onGyroSample(gx, gy, gz, event.timestamp)

                gyroVelX = gyroVelX * 0.72f + gy * 0.28f
                gyroVelY = gyroVelY * 0.72f + gx * 0.28f

                if (stabilizationMode == StabilizationMode.SUPER_EXTREME && isHardwareVideoStabilizationEnabled) {
                    _shakeCompX.value = (-gyroVelX * 14f).coerceIn(-18f, 18f)
                    _shakeCompY.value = (-gyroVelY * 14f).coerceIn(-18f, 18f)
                } else {
                    _shakeCompX.value = 0f
                    _shakeCompY.value = 0f
                }
            } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val roll = Math.toDegrees(atan2(ax.toDouble(), sqrt(ay.toDouble() * ay + az.toDouble() * az))).toFloat()
                gyroFilter.onRotationRollSample(roll)
                filteredRoll = filteredRoll * 0.85f + roll * 0.15f
                _rollDegrees.value = filteredRoll
                val isExtreme = stabilizationMode == StabilizationMode.SUPER_EXTREME && isHardwareVideoStabilizationEnabled
                _horizonLockAngle.value = if (isExtreme && isHorizonLockEnabled) {
                    (-filteredRoll).coerceIn(-18f, 18f)
                } else {
                    0f
                }
            }
        } catch (_: Throwable) {
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun release() {
        sensorManager?.unregisterListener(this)
        coroutineScope.cancel()
        gyroProcessor?.release()
        gyroProcessor = null
        analysisBitmap?.recycle()
        analysisBitmap = null
        try {
            activeRecording?.stop()
        } catch (_: Exception) {
        }
        activeRecording = null
    }
}
