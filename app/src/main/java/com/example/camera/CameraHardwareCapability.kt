package com.example.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size
import com.example.model.FpsMode
import com.example.model.ResolutionMode

data class CameraHardwareInfo(
    val cameraId: String,
    val facing: String,
    val hardwareLevel: String,
    val supportsHighSpeed120: Boolean,
    val supportsHighSpeed240: Boolean,
    val supportsOis: Boolean,
    val supportsEis: Boolean,
    val minIso: Int,
    val maxIso: Int,
    val minExposureTimeNs: Long,
    val maxExposureTimeNs: Long,
    val minEv: Int,
    val maxEv: Int,
    val evStep: Float,
    val maxFocusDistance: Float,
    val highSpeedSizes: List<Size>,
    val supportedHighSpeedFpsRanges: List<Range<Int>>
)

object CameraHardwareHelper {

    fun queryCameraCapabilities(context: Context): CameraHardwareInfo {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return createFallbackInfo()

        try {
            val cameraIds = cameraManager.cameraIdList
            if (cameraIds.isEmpty()) return createFallbackInfo()

            // Find back camera first, otherwise first available
            var selectedId = cameraIds[0]
            for (id in cameraIds) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    selectedId = id
                    break
                }
            }

            val chars = cameraManager.getCameraCharacteristics(selectedId)
            val facingVal = chars.get(CameraCharacteristics.LENS_FACING)
            val facingStr = when (facingVal) {
                CameraCharacteristics.LENS_FACING_BACK -> "Back (Main Cinema Sensor)"
                CameraCharacteristics.LENS_FACING_FRONT -> "Front (Selfie Sensor)"
                else -> "External / Multi-Camera"
            }

            val hwLevelVal = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            val hwLevelStr = when (hwLevelVal) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full (Pro Manual Controls)"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3 (Raw & High Speed Pro)"
                else -> "External"
            }

            val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val hasHighSpeedCapability = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO
            )

            val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val highSpeedSizes = streamMap?.highSpeedVideoSizes?.toList() ?: emptyList()
            val highSpeedRanges = mutableListOf<Range<Int>>()
            var has120 = false
            var has240 = false

            for (size in highSpeedSizes) {
                val ranges = streamMap?.getHighSpeedVideoFpsRangesFor(size) ?: emptyArray()
                for (range in ranges) {
                    if (!highSpeedRanges.contains(range)) {
                        highSpeedRanges.add(range)
                    }
                    if (range.upper >= 120) has120 = true
                    if (range.upper >= 240) has240 = true
                }
            }

            // Check standard FPS ranges as well
            val targetFpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
            for (range in targetFpsRanges) {
                if (range.upper >= 120) has120 = true
                if (range.upper >= 240) has240 = true
            }

            // Check OIS and EIS
            val opticalStabilizationModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
            val supportsOis = opticalStabilizationModes.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

            val videoStabilizationModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
            val supportsEis = videoStabilizationModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)

            val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: Range(50, 3200)
            val expTimeRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: Range(100_000L, 1_000_000_000L)
            val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(-6, 6)
            val evStepRational = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
            val evStep = evStepRational?.toFloat() ?: 0.33f
            val minFocusDistance = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 10.0f

            return CameraHardwareInfo(
                cameraId = selectedId,
                facing = facingStr,
                hardwareLevel = hwLevelStr,
                supportsHighSpeed120 = has120 || hasHighSpeedCapability,
                supportsHighSpeed240 = has240,
                supportsOis = supportsOis,
                supportsEis = supportsEis,
                minIso = isoRange.lower,
                maxIso = isoRange.upper,
                minExposureTimeNs = expTimeRange.lower,
                maxExposureTimeNs = expTimeRange.upper,
                minEv = evRange.lower,
                maxEv = evRange.upper,
                evStep = evStep,
                maxFocusDistance = minFocusDistance,
                highSpeedSizes = highSpeedSizes,
                supportedHighSpeedFpsRanges = highSpeedRanges
            )
        } catch (e: Exception) {
            return createFallbackInfo()
        }
    }

    private fun createFallbackInfo(): CameraHardwareInfo {
        return CameraHardwareInfo(
            cameraId = "0",
            facing = "Back Camera",
            hardwareLevel = "Full (Pro Simulation & Hardware Mode)",
            supportsHighSpeed120 = true,
            supportsHighSpeed240 = true,
            supportsOis = true,
            supportsEis = true,
            minIso = 50,
            maxIso = 6400,
            minExposureTimeNs = 100_000L,
            maxExposureTimeNs = 1_000_000_000L,
            minEv = -6,
            maxEv = 6,
            evStep = 0.33f,
            maxFocusDistance = 10.0f,
            highSpeedSizes = listOf(Size(1280, 720), Size(1920, 1080)),
            supportedHighSpeedFpsRanges = listOf(Range(120, 120), Range(240, 240))
        )
    }
}
