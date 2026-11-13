package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.CameraHardwareHelper
import com.example.camera.CameraHardwareInfo
import com.example.camera.CameraVideoEngine
import com.example.data.VideoClipRepository
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ActiveModal {
    object None : ActiveModal()
    object HardwareCapabilities : ActiveModal()
    object FpsResolutionSelector : ActiveModal()
    object FilterSelector : ActiveModal()
    object ManualExposurePanel : ActiveModal()
    object StabilizerSelector : ActiveModal()
    object ProSettingsDialog : ActiveModal()
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    val engine = CameraVideoEngine(application)
    val repository = VideoClipRepository(application)

    private val _hardwareInfo = MutableStateFlow(CameraHardwareHelper.queryCameraCapabilities(application))
    val hardwareInfo: StateFlow<CameraHardwareInfo> = _hardwareInfo.asStateFlow()

    // Camera Settings State
    private val _fpsMode = MutableStateFlow(FpsMode.FPS_120)
    val fpsMode: StateFlow<FpsMode> = _fpsMode.asStateFlow()

    private val _resolutionMode = MutableStateFlow(ResolutionMode.FHD_1080P)
    val resolutionMode: StateFlow<ResolutionMode> = _resolutionMode.asStateFlow()

    private val _stabilizationMode = MutableStateFlow(StabilizationMode.HYBRID)
    val stabilizationMode: StateFlow<StabilizationMode> = _stabilizationMode.asStateFlow()

    private val _gyroStabilizerStrength = MutableStateFlow(GyroStabilizerStrength.ACTION)
    val gyroStabilizerStrength: StateFlow<GyroStabilizerStrength> = _gyroStabilizerStrength.asStateFlow()

    private val _isAntiBlurShutterEnabled = MutableStateFlow(true)
    val isAntiBlurShutterEnabled: StateFlow<Boolean> = _isAntiBlurShutterEnabled.asStateFlow()

    private val _isHardwareStabilizationEnabled = MutableStateFlow(true)
    val isHardwareStabilizationEnabled: StateFlow<Boolean> = _isHardwareStabilizationEnabled.asStateFlow()

    private val _isHorizonLockEnabled = MutableStateFlow(true)
    val isHorizonLockEnabled: StateFlow<Boolean> = _isHorizonLockEnabled.asStateFlow()

    val isHardwareStabilizationSupported: StateFlow<Boolean> = engine.isHardwareStabilizationSupported
    val isPreviewStabilizationSupported: StateFlow<Boolean> = engine.isPreviewStabilizationSupported
    val isHardwareStabilizationActive: StateFlow<Boolean> = engine.isHardwareStabilizationActive
    val isGlStabilizerActive: StateFlow<Boolean> = engine.isGlStabilizerActive
    val isGlFilterActive: StateFlow<Boolean> = engine.isGlFilterActive
    val hasFlashUnit: StateFlow<Boolean> = engine.hasFlashUnit
    val shakeCancelledDegrees: StateFlow<Float> = engine.shakeCancelledDegrees
    val zoomRatio: StateFlow<Float> = engine.zoomRatio
    val minZoomRatio: StateFlow<Float> = engine.minZoomRatio
    val maxZoomRatio: StateFlow<Float> = engine.maxZoomRatio
    val lumaHistogram: StateFlow<List<Float>> = engine.lumaHistogram
    val zebraCells: StateFlow<BooleanArray> = engine.zebraCells
    val focusPeakingCells: StateFlow<BooleanArray> = engine.focusPeakingCells

    private val _exposureMode = MutableStateFlow(ExposureMode.AUTO)
    val exposureMode: StateFlow<ExposureMode> = _exposureMode.asStateFlow()

    private val _manualIso = MutableStateFlow(400)
    val manualIso: StateFlow<Int> = _manualIso.asStateFlow()

    private val _manualShutterIndex = MutableStateFlow(3) // 1/120s
    val manualShutterIndex: StateFlow<Int> = _manualShutterIndex.asStateFlow()

    private val _manualEv = MutableStateFlow(0f)
    val manualEv: StateFlow<Float> = _manualEv.asStateFlow()

    private val _focusMode = MutableStateFlow(FocusMode.CONTINUOUS)
    val focusMode: StateFlow<FocusMode> = _focusMode.asStateFlow()

    private val _manualFocusDistance = MutableStateFlow(0.5f)
    val manualFocusDistance: StateFlow<Float> = _manualFocusDistance.asStateFlow()

    private val _whiteBalanceMode = MutableStateFlow(WhiteBalanceMode.AUTO)
    val whiteBalanceMode: StateFlow<WhiteBalanceMode> = _whiteBalanceMode.asStateFlow()

    private val _colorFilter = MutableStateFlow(ColorFilterType.TEAL_AND_ORANGE)
    val colorFilter: StateFlow<ColorFilterType> = _colorFilter.asStateFlow()

    private val _gridType = MutableStateFlow(GridType.RULE_OF_THIRDS)
    val gridType: StateFlow<GridType> = _gridType.asStateFlow()

    private val _isLevelEnabled = MutableStateFlow(true)
    val isLevelEnabled: StateFlow<Boolean> = _isLevelEnabled.asStateFlow()

    private val _isHistogramEnabled = MutableStateFlow(true)
    val isHistogramEnabled: StateFlow<Boolean> = _isHistogramEnabled.asStateFlow()

    private val _isFocusPeakingEnabled = MutableStateFlow(false)
    val isFocusPeakingEnabled: StateFlow<Boolean> = _isFocusPeakingEnabled.asStateFlow()

    private val _isZebraEnabled = MutableStateFlow(false)
    val isZebraEnabled: StateFlow<Boolean> = _isZebraEnabled.asStateFlow()

    private val _isAeLocked = MutableStateFlow(false)
    val isAeLocked: StateFlow<Boolean> = _isAeLocked.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    // UI Dialogs
    private val _activeModal = MutableStateFlow<ActiveModal>(ActiveModal.None)
    val activeModal: StateFlow<ActiveModal> = _activeModal.asStateFlow()

    // Navigation (viewfinder vs gallery)
    private val _currentScreen = MutableStateFlow("camera") // "camera" or "gallery"
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _selectedClip = MutableStateFlow<VideoClip?>(null)
    val selectedClip: StateFlow<VideoClip?> = _selectedClip.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val isRecording = engine.isRecording
    val recordingDuration = engine.recordingDurationSeconds
    val audioDb = engine.audioDb
    val rollDegrees = engine.rollDegrees
    val pitchDegrees = engine.pitchDegrees
    val stabilizerDamping = engine.stabilizerDamping
    val horizonLockAngle = engine.horizonLockAngle
    val shakeCompX = engine.shakeCompX
    val shakeCompY = engine.shakeCompY
    val stabilityScore = engine.stabilityScore

    val clips = repository.clips

    fun navigateTo(screen: String) {
        if (screen != "camera" && isRecording.value) {
            engine.stopRecording()
        }
        _activeModal.value = ActiveModal.None
        _currentScreen.value = screen
    }

    fun openModal(modal: ActiveModal) {
        _activeModal.value = modal
    }

    fun closeModal() {
        _activeModal.value = ActiveModal.None
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun setFpsMode(mode: FpsMode) {
        if (isRecording.value) {
            showToast("Stop recording to change frame rate")
            return
        }
        _fpsMode.value = mode
        engine.fpsMode = mode
        engine.bindCameraUseCases()
        val maxHwFps = (_hardwareInfo.value.availableAeFpsRanges.maxOfOrNull { it.upper } ?: 30)
            .coerceAtLeast(if (_hardwareInfo.value.supportsHighSpeed240) 240 else if (_hardwareInfo.value.supportsHighSpeed120) 120 else 30)
        if (mode.fps > maxHwFps) {
            showToast("${mode.label} selected (sensor caps AE at ${maxHwFps} FPS)")
        } else {
            showToast("${mode.label} selected (${mode.slowMoMultiplier})")
        }
    }

    fun setResolutionMode(mode: ResolutionMode) {
        if (isRecording.value) {
            showToast("Stop recording to change resolution")
            return
        }
        _resolutionMode.value = mode
        engine.resolutionMode = mode
        engine.bindCameraUseCases()
        showToast("Resolution: ${mode.shortName}")
    }

    fun setStabilizationMode(mode: StabilizationMode) {
        if (isRecording.value) {
            showToast("Stop recording to change stabilization mode")
            return
        }
        val previousMode = _stabilizationMode.value
        val previousStrength = _gyroStabilizerStrength.value
        _stabilizationMode.value = mode
        engine.stabilizationMode = mode

        val nextStrength = when (mode) {
            StabilizationMode.OFF -> GyroStabilizerStrength.OFF
            StabilizationMode.OIS -> GyroStabilizerStrength.OFF
            StabilizationMode.EIS -> GyroStabilizerStrength.SMOOTH
            StabilizationMode.HYBRID -> GyroStabilizerStrength.ACTION
            StabilizationMode.SUPER_EXTREME -> GyroStabilizerStrength.LOCKDOWN
        }
        _gyroStabilizerStrength.value = nextStrength
        engine.gyroStabilizerStrength = nextStrength

        val shouldEnableHw = mode != StabilizationMode.OFF
        val glAttachmentChanged = (previousStrength == GyroStabilizerStrength.OFF) != (nextStrength == GyroStabilizerStrength.OFF)
        val needsRebind =
            _isHardwareStabilizationEnabled.value != shouldEnableHw ||
                previousMode == StabilizationMode.SUPER_EXTREME ||
                mode == StabilizationMode.SUPER_EXTREME ||
                glAttachmentChanged
        _isHardwareStabilizationEnabled.value = shouldEnableHw
        engine.isHardwareVideoStabilizationEnabled = shouldEnableHw
        engine.syncGyroFilterConfig(shouldEnableHw)
        if (needsRebind) {
            engine.bindCameraUseCases()
        } else {
            engine.updateLiveControls()
        }
        val toastText = if (mode == StabilizationMode.SUPER_EXTREME) {
            "Super Extreme 3-Axis Gimbal Lockdown ON"
        } else {
            "Stabilizer: ${mode.label} (${nextStrength.shortBadge} Gyro)"
        }
        showToast(toastText)
    }

    fun setGyroStabilizerStrength(strength: GyroStabilizerStrength) {
        val wasOff = _gyroStabilizerStrength.value == GyroStabilizerStrength.OFF
        val nowOff = strength == GyroStabilizerStrength.OFF
        if ((wasOff != nowOff) && isRecording.value) {
            showToast("Stop recording to toggle GL Gyro EIS on/off")
            return
        }
        _gyroStabilizerStrength.value = strength
        engine.gyroStabilizerStrength = strength
        if (!nowOff && _stabilizationMode.value == StabilizationMode.OFF) {
            _stabilizationMode.value = StabilizationMode.HYBRID
            engine.stabilizationMode = StabilizationMode.HYBRID
            _isHardwareStabilizationEnabled.value = true
            engine.isHardwareVideoStabilizationEnabled = true
        }
        engine.syncGyroFilterConfig()
        if (wasOff != nowOff) {
            engine.bindCameraUseCases()
        }
        showToast("Recorded Gyro EIS: ${strength.label}")
    }

    fun toggleAntiBlurShutter(enabled: Boolean = !_isAntiBlurShutterEnabled.value) {
        if (isRecording.value) {
            showToast("Stop recording to adjust anti-blur shutter floor")
            return
        }
        _isAntiBlurShutterEnabled.value = enabled
        engine.isAntiBlurShutterEnabled = enabled
        engine.bindCameraUseCases()
        showToast(if (enabled) "Anti-Motion-Blur Fast Shutter: ON" else "Anti-Motion-Blur Shutter: OFF")
    }

    fun toggleSuperExtremeStability() {
        val nextMode = if (_stabilizationMode.value == StabilizationMode.SUPER_EXTREME) {
            StabilizationMode.HYBRID
        } else {
            StabilizationMode.SUPER_EXTREME
        }
        setStabilizationMode(nextMode)
    }

    fun toggleHorizonLock(enabled: Boolean = !_isHorizonLockEnabled.value) {
        _isHorizonLockEnabled.value = enabled
        engine.isHorizonLockEnabled = enabled
        engine.syncGyroFilterConfig()
        showToast(if (enabled) "Active Horizon Lock: ON" else "Active Horizon Lock: OFF")
    }

    fun toggleHardwareStabilization(enabled: Boolean = !_isHardwareStabilizationEnabled.value) {
        if (isRecording.value) {
            showToast("Stop recording to toggle hardware stabilization")
            return
        }
        _isHardwareStabilizationEnabled.value = enabled
        engine.isHardwareVideoStabilizationEnabled = enabled
        if (enabled && _stabilizationMode.value == StabilizationMode.OFF) {
            _stabilizationMode.value = StabilizationMode.HYBRID
            engine.stabilizationMode = StabilizationMode.HYBRID
            _gyroStabilizerStrength.value = GyroStabilizerStrength.ACTION
            engine.gyroStabilizerStrength = GyroStabilizerStrength.ACTION
        } else if (!enabled && _stabilizationMode.value != StabilizationMode.OFF) {
            _stabilizationMode.value = StabilizationMode.OFF
            engine.stabilizationMode = StabilizationMode.OFF
            _gyroStabilizerStrength.value = GyroStabilizerStrength.OFF
            engine.gyroStabilizerStrength = GyroStabilizerStrength.OFF
        }
        engine.syncGyroFilterConfig()
        engine.bindCameraUseCases()
        val hwSupported = isHardwareStabilizationSupported.value
        val msg = when {
            !enabled -> "Video Stabilization: OFF"
            hwSupported -> "Hardware + GL Gyro Stabilization: ON"
            else -> "GL Gyro Stabilization: ON (3-Axis Recorded EIS)"
        }
        showToast(msg)
    }

    fun setExposureMode(mode: ExposureMode) {
        _exposureMode.value = mode
        engine.exposureMode = mode
        engine.updateLiveControls()
    }

    fun setManualIso(iso: Int) {
        val hw = _hardwareInfo.value
        val clamped = iso.coerceIn(hw.minIso, hw.maxIso.coerceAtLeast(hw.minIso))
        _manualIso.value = clamped
        engine.manualIso = clamped
        engine.updateLiveControls()
    }

    fun setShutterIndex(index: Int) {
        val clampedIdx = index.coerceIn(0, engine.shutterSpeeds.lastIndex)
        _manualShutterIndex.value = clampedIdx
        engine.manualShutterSpeedIndex = clampedIdx
        engine.updateLiveControls()
    }

    fun setManualEv(ev: Float) {
        val hw = _hardwareInfo.value
        val minEvFloat = (hw.minEv * hw.evStep).coerceAtMost(-0.5f)
        val maxEvFloat = (hw.maxEv * hw.evStep).coerceAtLeast(0.5f)
        val clamped = ev.coerceIn(minEvFloat, maxEvFloat)
        _manualEv.value = clamped
        engine.manualEvCompensation = clamped
        engine.updateLiveControls()
    }

    fun setFocusMode(mode: FocusMode) {
        if (mode == FocusMode.MANUAL && _hardwareInfo.value.maxFocusDistance <= 0f) {
            showToast("Fixed-focus lens: manual diopter distance unsupported")
        }
        _focusMode.value = mode
        engine.focusMode = mode
        engine.updateLiveControls()
    }

    fun setManualFocusDistance(distance: Float) {
        val clamped = distance.coerceIn(0f, 1f)
        _manualFocusDistance.value = clamped
        engine.manualFocusDistance = clamped
        engine.updateLiveControls()
    }

    fun setWhiteBalance(wb: WhiteBalanceMode) {
        _whiteBalanceMode.value = wb
        engine.whiteBalanceMode = wb
        engine.updateLiveControls()
    }

    fun setColorFilter(filter: ColorFilterType) {
        _colorFilter.value = filter
        engine.setLiveColorFilter(filter)
        if (isRecording.value && !isGlFilterActive.value && filter != ColorFilterType.NORMAL) {
            showToast("LUT preview only during active recording")
        }
    }

    fun setGridType(grid: GridType) {
        _gridType.value = grid
    }

    fun toggleLevelOverlay() {
        _isLevelEnabled.value = !_isLevelEnabled.value
    }

    fun toggleHistogram() {
        _isHistogramEnabled.value = !_isHistogramEnabled.value
    }

    fun toggleFocusPeaking() {
        _isFocusPeakingEnabled.value = !_isFocusPeakingEnabled.value
    }

    fun toggleZebraStripes() {
        _isZebraEnabled.value = !_isZebraEnabled.value
    }

    fun toggleAeLock() {
        _isAeLocked.value = !_isAeLocked.value
        engine.isAeLocked = _isAeLocked.value
        engine.updateLiveControls()
    }

    fun toggleTorch() {
        if (!hasFlashUnit.value) {
            _isTorchOn.value = false
            engine.isFlashTorchOn = false
            showToast("Torch unavailable on this camera lens")
            return
        }
        _isTorchOn.value = !_isTorchOn.value
        engine.isFlashTorchOn = _isTorchOn.value
        engine.updateLiveControls()
    }

    fun switchCamera() {
        if (isRecording.value) {
            showToast("Stop recording to switch camera")
            return
        }
        _isFrontCamera.value = !_isFrontCamera.value
        engine.isFrontCamera = _isFrontCamera.value
        _isTorchOn.value = false
        engine.isFlashTorchOn = false
        _hardwareInfo.value = CameraHardwareHelper.queryCameraCapabilities(
            getApplication(),
            _isFrontCamera.value
        )
        engine.bindCameraUseCases()
    }

    fun tapToFocus(x: Float, y: Float) {
        engine.tapToFocus(x, y)
    }

    fun setZoomRatio(ratio: Float) {
        engine.setZoomRatio(ratio)
    }

    fun zoomBy(scaleFactor: Float) {
        engine.zoomBy(scaleFactor)
    }

    fun sampleViewfinderTelemetry() {
        engine.analyzePreviewFrame(
            computeHistogram = _isHistogramEnabled.value,
            computeZebra = _isZebraEnabled.value,
            computeFocusPeaking = _isFocusPeakingEnabled.value
        )
    }

    fun toggleRecording() {
        if (isRecording.value) {
            engine.stopRecording()
            showToast("Finalizing recording...")
        } else {
            engine.startRecording(
                onSuccess = { _ ->
                    val stabTag = if (_isHardwareStabilizationEnabled.value) "HW Stab ON" else "Stab OFF"
                    showToast("Recording @ ${_fpsMode.value.fps} FPS [$stabTag]")
                },
                onError = { err ->
                    showToast("Recording error: $err")
                },
                onClipFinalized = { clip ->
                    repository.addClip(clip)
                    showToast("Saved ${clip.durationSeconds}s @ ${clip.fps} FPS")
                }
            )
        }
    }

    fun selectClipForPlayback(clip: VideoClip) {
        _selectedClip.value = clip
        // If slow motion, default playback to slow speed!
        _playbackSpeed.value = if (clip.fps >= 240) 0.125f else if (clip.fps >= 120) 0.25f else 1.0f
    }

    fun clearSelectedClip() {
        _selectedClip.value = null
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    fun deleteClip(id: String) {
        repository.deleteClip(id)
        if (_selectedClip.value?.id == id) {
            _selectedClip.value = null
        }
        showToast("Clip deleted")
    }

    override fun onCleared() {
        super.onCleared()
        engine.release()
    }
}
