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

    private val _isHardwareStabilizationEnabled = MutableStateFlow(true)
    val isHardwareStabilizationEnabled: StateFlow<Boolean> = _isHardwareStabilizationEnabled.asStateFlow()

    private val _isHorizonLockEnabled = MutableStateFlow(true)
    val isHorizonLockEnabled: StateFlow<Boolean> = _isHorizonLockEnabled.asStateFlow()

    val isHardwareStabilizationSupported: StateFlow<Boolean> = engine.isHardwareStabilizationSupported
    val isPreviewStabilizationSupported: StateFlow<Boolean> = engine.isPreviewStabilizationSupported
    val isHardwareStabilizationActive: StateFlow<Boolean> = engine.isHardwareStabilizationActive

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
        _fpsMode.value = mode
        engine.fpsMode = mode
        engine.bindCameraUseCases()
        showToast("${mode.label} selected (${mode.slowMoMultiplier})")
    }

    fun setResolutionMode(mode: ResolutionMode) {
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
        _stabilizationMode.value = mode
        engine.stabilizationMode = mode
        val shouldEnableHw = mode != StabilizationMode.OFF
        val needsRebind =
            _isHardwareStabilizationEnabled.value != shouldEnableHw ||
                previousMode == StabilizationMode.SUPER_EXTREME ||
                mode == StabilizationMode.SUPER_EXTREME
        _isHardwareStabilizationEnabled.value = shouldEnableHw
        engine.isHardwareVideoStabilizationEnabled = shouldEnableHw
        if (needsRebind) {
            engine.bindCameraUseCases()
        } else {
            engine.updateLiveControls()
        }
        val toastText = if (mode == StabilizationMode.SUPER_EXTREME) {
            "Super Extreme Gimbal Stability ON"
        } else {
            "Stabilizer: ${mode.label}"
        }
        showToast(toastText)
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
        showToast(if (enabled) "Active Horizon Lock: ON (±18°)" else "Active Horizon Lock: OFF")
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
        } else if (!enabled && _stabilizationMode.value != StabilizationMode.OFF) {
            _stabilizationMode.value = StabilizationMode.OFF
            engine.stabilizationMode = StabilizationMode.OFF
        }
        engine.bindCameraUseCases()
        val hwSupported = isHardwareStabilizationSupported.value
        val msg = when {
            !enabled -> "Hardware Video Stabilization: OFF"
            hwSupported -> "Hardware Video Stabilization: ON (CameraX VideoCapture)"
            else -> "Stabilization: ON (CameraX HW unsupported on lens, using software/OIS fallback)"
        }
        showToast(msg)
    }

    fun setExposureMode(mode: ExposureMode) {
        _exposureMode.value = mode
        engine.exposureMode = mode
        engine.updateLiveControls()
    }

    fun setManualIso(iso: Int) {
        _manualIso.value = iso
        engine.manualIso = iso
        engine.updateLiveControls()
    }

    fun setShutterIndex(index: Int) {
        _manualShutterIndex.value = index
        engine.manualShutterSpeedIndex = index
        engine.updateLiveControls()
    }

    fun setManualEv(ev: Float) {
        _manualEv.value = ev
        engine.manualEvCompensation = ev
        engine.updateLiveControls()
    }

    fun setFocusMode(mode: FocusMode) {
        _focusMode.value = mode
        engine.focusMode = mode
        engine.updateLiveControls()
    }

    fun setManualFocusDistance(distance: Float) {
        _manualFocusDistance.value = distance
        engine.manualFocusDistance = distance
        engine.updateLiveControls()
    }

    fun setWhiteBalance(wb: WhiteBalanceMode) {
        _whiteBalanceMode.value = wb
        engine.whiteBalanceMode = wb
        engine.bindCameraUseCases()
    }

    fun setColorFilter(filter: ColorFilterType) {
        _colorFilter.value = filter
        engine.currentColorFilter = filter
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
        _isTorchOn.value = !_isTorchOn.value
        engine.isFlashTorchOn = _isTorchOn.value
        engine.updateLiveControls()
    }

    fun switchCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
        engine.isFrontCamera = _isFrontCamera.value
        engine.bindCameraUseCases()
    }

    fun toggleRecording() {
        if (isRecording.value) {
            val clip = engine.stopRecording()
            if (clip != null) {
                repository.addClip(clip)
                showToast("Recorded ${clip.durationSeconds}s @ ${clip.fps} FPS")
            }
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
