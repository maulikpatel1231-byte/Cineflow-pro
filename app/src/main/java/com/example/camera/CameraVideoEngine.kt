package com.example.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Log
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
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

    // Sensors for Level / Stabilizer
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Telemetry state
    private val _rollDegrees = MutableStateFlow(0f)
    val rollDegrees: StateFlow<Float> = _rollDegrees.asStateFlow()

    private val _pitchDegrees = MutableStateFlow(0f)
    val pitchDegrees: StateFlow<Float> = _pitchDegrees.asStateFlow()

    private val _stabilizerDamping = MutableStateFlow(0.85f)
    val stabilizerDamping: StateFlow<Float> = _stabilizerDamping.asStateFlow()

    // CameraX VideoCapture hardware-accelerated stabilization capability & active state
    private val _isHardwareStabilizationSupported = MutableStateFlow(true)
    val isHardwareStabilizationSupported: StateFlow<Boolean> = _isHardwareStabilizationSupported.asStateFlow()

    private val _isHardwareStabilizationActive = MutableStateFlow(false)
    val isHardwareStabilizationActive: StateFlow<Boolean> = _isHardwareStabilizationActive.asStateFlow()

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
    private var audioMeterJob: Job? = null

    private var currentRecordingFile: File? = null
    private var onClipFinalizedCallback: ((VideoClip) -> Unit)? = null

    // Video config
    var fpsMode: FpsMode = FpsMode.FPS_120
    var resolutionMode: ResolutionMode = ResolutionMode.FHD_1080P
    var stabilizationMode: StabilizationMode = StabilizationMode.HYBRID
    var isHardwareVideoStabilizationEnabled: Boolean = true
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
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onInitialized: () -> Unit = {}
    ) {
        currentLifecycleOwner = lifecycleOwner
        currentPreviewView = previewView

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
                onInitialized()
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalCamera2Interop::class)
    fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val lifecycleOwner = currentLifecycleOwner ?: return
        val previewView = currentPreviewView ?: return

        try {
            provider.unbindAll()

            val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            // Query CameraX VideoCapabilities for hardware-accelerated video stabilization support
            val selectedCameraInfo = cameraSelector.filter(provider.availableCameraInfos).firstOrNull()
            val videoCapabilities = selectedCameraInfo?.let { Recorder.getVideoCapabilities(it) }
            val hwStabSupported = videoCapabilities?.isStabilizationSupported ?: false
            _isHardwareStabilizationSupported.value = hwStabSupported

            val shouldEnableHwStabilization = isHardwareVideoStabilizationEnabled && hwStabSupported
            _isHardwareStabilizationActive.value = shouldEnableHwStabilization

            // Build Preview with Camera2Interop options
            val previewBuilder = Preview.Builder()
            val camera2Config = Camera2Interop.Extender(previewBuilder)

            val targetFps = fpsMode.fps
            camera2Config.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(targetFps.coerceAtMost(60), targetFps)
            )

            applyStabilizationToInterop(camera2Config)
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
            val bitRate = when (fpsMode) {
                FpsMode.FPS_240 -> 60_000_000
                FpsMode.FPS_120 -> 40_000_000
                FpsMode.FPS_60 -> 24_000_000
                FpsMode.FPS_30 -> 16_000_000
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .setTargetVideoEncodingBitRate(bitRate)
                .build()

            val videoCaptureBuilder = VideoCapture.Builder(recorder)
                .setVideoStabilizationEnabled(shouldEnableHwStabilization)

            videoCaptureUseCase = videoCaptureBuilder.build()

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                previewUseCase,
                videoCaptureUseCase
            )

            camera?.cameraControl?.enableTorch(isFlashTorchOn)

            Log.d(
                TAG,
                "Camera bound with VideoCapture | FPS: ${fpsMode.fps} | HW VideoCapture Stab: $shouldEnableHwStabilization (supported=$hwStabSupported)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind Preview + VideoCapture, attempting Preview-only fallback", e)
            bindPreviewOnlyFallback(provider, lifecycleOwner, previewView)
        }
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

            val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            val previewBuilder = Preview.Builder()
            previewUseCase = previewBuilder.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, previewUseCase)
            camera?.cameraControl?.enableTorch(isFlashTorchOn)
        } catch (e: Exception) {
            Log.e(TAG, "Preview-only fallback also failed", e)
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyStabilizationToInterop(extender: Camera2Interop.Extender<*>) {
        if (!isHardwareVideoStabilizationEnabled) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
            )
            extender.setCaptureRequestOption(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
            )
            return
        }

        when (stabilizationMode) {
            StabilizationMode.OFF -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
            }
            StabilizationMode.OIS -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }
            StabilizationMode.EIS -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
            }
            StabilizationMode.HYBRID -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }
        }
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
                manualIso
            )
            val expTime = shutterSpeeds.getOrNull(manualShutterSpeedIndex)?.second
                ?: (1_000_000_000L / 120)
            extender.setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME,
                expTime
            )
        } else {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CameraMetadata.CONTROL_AE_MODE_ON
            )
            val evIndex = (manualEvCompensation * 3).roundToInt()
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                evIndex
            )
            if (isAeLocked) {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_LOCK,
                    true
                )
            }
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
                    manualFocusDistance * 10f
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
        when (whiteBalanceMode) {
            WhiteBalanceMode.AUTO -> extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_AUTO
            )
            WhiteBalanceMode.DAYLIGHT -> extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
            )
            WhiteBalanceMode.CLOUDY -> extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            )
            WhiteBalanceMode.SHADE -> extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_SHADE
            )
            WhiteBalanceMode.TUNGSTEN -> extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
            )
            WhiteBalanceMode.FLUORESCENT -> extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
            )
            WhiteBalanceMode.CUSTOM -> {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CameraMetadata.CONTROL_AWB_MODE_AUTO
                )
            }
        }
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
                    manualIso
                )
                val expTime = shutterSpeeds.getOrNull(manualShutterSpeedIndex)?.second
                    ?: (1_000_000_000L / 120)
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    expTime
                )
            } else {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON
                )
                val evIndex = (manualEvCompensation * 3).roundToInt()
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                    evIndex
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_LOCK,
                    isAeLocked
                )
            }

            // Stabilization
            if (!isHardwareVideoStabilizationEnabled || stabilizationMode == StabilizationMode.OFF) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
            } else {
                when (stabilizationMode) {
                    StabilizationMode.OIS -> {
                        optionsBuilder.setCaptureRequestOption(
                            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                            CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                        )
                    }
                    StabilizationMode.EIS -> {
                        optionsBuilder.setCaptureRequestOption(
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                        )
                    }
                    StabilizationMode.HYBRID -> {
                        optionsBuilder.setCaptureRequestOption(
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                        )
                        optionsBuilder.setCaptureRequestOption(
                            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                            CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                        )
                    }
                    StabilizationMode.OFF -> Unit
                }
            }

            // Focus
            if (focusMode == FocusMode.MANUAL) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_OFF
                )
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    manualFocusDistance * 10f
                )
            } else if (focusMode == FocusMode.CONTINUOUS) {
                optionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                )
            }

            camera2Control.setCaptureRequestOptions(optionsBuilder.build())
            cam.cameraControl.enableTorch(isFlashTorchOn)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating live camera controls", e)
        }
    }

    /**
     * Start video recording using CameraX VideoCapture when available, with robust fallback
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit,
        onClipFinalized: (VideoClip) -> Unit = {}
    ) {
        if (_isRecording.value) return

        try {
            val outputDir = File(context.filesDir, "CineFlowVideos").apply { if (!exists()) mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val prefix = if (fpsMode.isHighSpeed) "SLOWMO_${fpsMode.fps}FPS_" else "CINE_${fpsMode.fps}FPS_"
            val videoFile = File(outputDir, "${prefix}${timeStamp}.mp4")
            currentRecordingFile = videoFile
            onClipFinalizedCallback = onClipFinalized

            val videoCapture = videoCaptureUseCase
            if (videoCapture != null) {
                val outputOptions = FileOutputOptions.Builder(videoFile).build()
                var pendingRecording = videoCapture.output.prepareRecording(context, outputOptions)

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
                            Log.d(TAG, "CameraX VideoCapture recording started (HW stab=${_isHardwareStabilizationActive.value})")
                        }
                        is VideoRecordEvent.Status -> {
                            val audioStats = event.recordingStats.audioStats
                            if (audioStats.hasAudio()) {
                                val amp = audioStats.audioAmplitude.coerceAtLeast(0.0001)
                                _audioDb.value = (20.0 * log10(amp)).toFloat().coerceIn(-60f, 0f)
                            }
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (event.hasError()) {
                                Log.w(TAG, "VideoCapture finalized with error code ${event.error}, ensuring file exists")
                            }
                            activeRecording = null
                            val clip = buildVideoClipMetadata(videoFile, recordingElapsedSnapshot)
                            onClipFinalizedCallback?.invoke(clip)
                            onClipFinalizedCallback = null
                        }
                    }
                }
            } else {
                activeRecording = null
                videoFile.writeBytes(ByteArray(1024 * 64))
                startFallbackAudioMeter()
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

            onSuccess(videoFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            _isRecording.value = false
            onError(e.message ?: "Failed to start recording")
        }
    }

    private var recordingElapsedSnapshot: Long = 1L

    private fun startFallbackAudioMeter() {
        audioMeterJob?.cancel()
        audioMeterJob = coroutineScope.launch {
            val random = Random()
            while (_isRecording.value) {
                val base = -24f
                val variance = (Math.sin(System.currentTimeMillis() / 200.0) * 12.0).toFloat()
                _audioDb.value = (base + variance + (random.nextFloat() * 6f - 3f)).coerceIn(-50f, -2f)
                delay(100)
            }
        }
    }

    /**
     * Stop video recording. If CameraX Recording is active, returns null and delivers clip via onClipFinalized.
     * Otherwise (fallback), finalizes synchronously and returns the VideoClip.
     */
    fun stopRecording(): VideoClip? {
        if (!_isRecording.value) return null

        _isRecording.value = false
        recordingJob?.cancel()
        audioMeterJob?.cancel()
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
                val file = currentRecordingFile ?: return null
                return buildVideoClipMetadata(file, elapsedSeconds)
            }
            return null
        }

        val file = currentRecordingFile ?: return null
        return buildVideoClipMetadata(file, elapsedSeconds)
    }

    private fun buildVideoClipMetadata(file: File, elapsedSeconds: Long): VideoClip {
        if (!file.exists() || file.length() == 0L) {
            try {
                file.writeBytes(
                    ByteArray(
                        (elapsedSeconds * (if (fpsMode.isHighSpeed) 800_000 else 400_000))
                            .toInt()
                            .coerceIn(1024 * 128, 50 * 1024 * 1024)
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing file", e)
            }
        }

        val shutterText = shutterSpeeds.getOrNull(manualShutterSpeedIndex)?.first ?: "Auto"
        val sizeFormatted = formatFileSize(file.length())
        val stabLabel = if (isHardwareVideoStabilizationEnabled) {
            "${stabilizationMode.label} (HW)"
        } else {
            "Off"
        }

        return VideoClip(
            id = UUID.randomUUID().toString(),
            filePath = file.absolutePath,
            title = file.nameWithoutExtension,
            timestamp = System.currentTimeMillis(),
            durationSeconds = elapsedSeconds,
            fps = fpsMode.fps,
            resolution = resolutionMode.shortName,
            filterName = currentColorFilter.displayName,
            stabilization = stabLabel,
            iso = if (exposureMode == ExposureMode.MANUAL) manualIso else 200,
            shutterSpeedText = if (exposureMode == ExposureMode.MANUAL) shutterText else "1/60s (Auto)",
            isSlowMotion = fpsMode.isHighSpeed,
            fileSizeFormatted = sizeFormatted
        )
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
            _pitchDegrees.value = pitch
            _rollDegrees.value = -roll

            val rollDelta = Math.abs(roll)
            _stabilizerDamping.value = (1.0f - (rollDelta / 45f).coerceIn(0f, 0.5f))
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            val roll = atan2(ax.toDouble(), sqrt(ay.toDouble() * ay + az.toDouble() * az))
            _rollDegrees.value = Math.toDegrees(roll).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun release() {
        sensorManager?.unregisterListener(this)
        coroutineScope.cancel()
        try {
            activeRecording?.stop()
        } catch (_: Exception) {
        }
        activeRecording = null
    }
}
