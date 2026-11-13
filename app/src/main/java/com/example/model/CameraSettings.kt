package com.example.model

import androidx.compose.ui.graphics.ColorMatrix

enum class FpsMode(val fps: Int, val label: String, val isHighSpeed: Boolean, val slowMoMultiplier: String) {
    FPS_30(30, "30 FPS", false, "1x Normal"),
    FPS_60(60, "60 FPS", false, "2x Smooth"),
    FPS_120(120, "120 FPS", true, "4x Slow-Mo"),
    FPS_240(240, "240 FPS", true, "8x Ultra Slow-Mo")
}

enum class ResolutionMode(val width: Int, val height: Int, val label: String, val shortName: String) {
    HD_720P(1280, 720, "HD 720p (1280x720)", "720p HD"),
    FHD_1080P(1920, 1080, "Full HD 1080p (1920x1080)", "1080p FHD"),
    UHD_4K(3840, 2160, "4K UHD (3840x2160)", "4K UHD")
}

enum class StabilizationMode(val label: String, val description: String, val iconName: String) {
    OFF("Off", "No stabilization", "Off"),
    OIS("OIS", "Optical Image Stabilization (Lens shift)", "Optical"),
    EIS("EIS", "Electronic Image Stabilization (Sensor crop)", "Electronic"),
    HYBRID("Hybrid Pro", "OIS + EIS Dual Gyro Stabilization", "Dual Pro"),
    SUPER_EXTREME("Super Extreme", "Preview + VideoCapture + OIS/EIS + Horizon Lock", "Gimbal Ultra")
}

enum class ExposureMode(val label: String) {
    AUTO("Auto Exposure"),
    MANUAL("Manual Pro")
}

enum class FocusMode(val label: String) {
    CONTINUOUS("AF-C (Continuous)"),
    MANUAL("Manual (MF)"),
    MACRO("Macro"),
    INFINITY("Infinity")
}

enum class WhiteBalanceMode(val label: String, val kelvin: Int) {
    AUTO("Auto AWB", 0),
    DAYLIGHT("Daylight (5500K)", 5500),
    CLOUDY("Cloudy (6500K)", 6500),
    SHADE("Shade (7500K)", 7500),
    TUNGSTEN("Tungsten (3200K)", 3200),
    FLUORESCENT("Fluorescent (4000K)", 4000),
    CUSTOM("Custom Kelvin", 5000)
}

enum class GridType(val label: String) {
    NONE("None"),
    RULE_OF_THIRDS("Rule of Thirds 3x3"),
    GOLDEN_RATIO("Golden Ratio"),
    CROSSHAIR("Center Crosshair"),
    CINEMASCOPE("CinemaScope 2.39:1")
}

enum class ColorFilterType(val displayName: String, val description: String, val badge: String) {
    NORMAL("Normal", "Natural true-to-life colors", "Standard"),
    TEAL_AND_ORANGE("Teal & Orange", "Hollywood blockbuster cinema look", "Cinema"),
    NOIR("Noir Monocrome", "High-contrast vintage black & white", "B&W"),
    CYBERPUNK("Cyberpunk", "Futuristic neon magenta & cyan tones", "Neon"),
    VINTAGE_70S("Vintage 70s", "Warm nostalgic Kodak film tone", "Film"),
    GOLDEN_HOUR("Golden Hour", "Rich warm sunset glow and amber highlights", "Warm"),
    EMERALD_INDIE("Emerald Indie", "Moody teal-green shadows and indie aesthetics", "Indie"),
    FLAT_LOG("Flat / Log", "Low contrast dynamic range curve for color grading", "Log");

    fun getColorMatrix(): ColorMatrix {
        return when (this) {
            NORMAL -> ColorMatrix()
            TEAL_AND_ORANGE -> ColorMatrix(
                floatArrayOf(
                    1.25f, -0.05f, -0.1f, 0f, 15f,
                    -0.05f, 1.05f, -0.05f, 0f, -5f,
                    -0.15f, 0.05f, 1.35f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            NOIR -> ColorMatrix(
                floatArrayOf(
                    0.299f * 1.3f, 0.587f * 1.3f, 0.114f * 1.3f, 0f, -10f,
                    0.299f * 1.3f, 0.587f * 1.3f, 0.114f * 1.3f, 0f, -10f,
                    0.299f * 1.3f, 0.587f * 1.3f, 0.114f * 1.3f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            CYBERPUNK -> ColorMatrix(
                floatArrayOf(
                    1.4f, 0.0f, 0.2f, 0f, 25f,
                    -0.1f, 0.85f, 0.35f, 0f, -10f,
                    0.3f, 0.0f, 1.5f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            VINTAGE_70S -> ColorMatrix(
                floatArrayOf(
                    1.2f, 0.1f, -0.1f, 0f, 10f,
                    0.05f, 1.05f, -0.05f, 0f, 5f,
                    -0.1f, -0.1f, 0.85f, 0f, -15f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            GOLDEN_HOUR -> ColorMatrix(
                floatArrayOf(
                    1.35f, 0.0f, -0.15f, 0f, 25f,
                    0.1f, 1.15f, -0.1f, 0f, 10f,
                    -0.2f, -0.1f, 0.75f, 0f, -25f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            EMERALD_INDIE -> ColorMatrix(
                floatArrayOf(
                    0.9f, 0.0f, -0.1f, 0f, -10f,
                    0.05f, 1.25f, 0.1f, 0f, 15f,
                    -0.05f, 0.1f, 1.15f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            FLAT_LOG -> ColorMatrix(
                floatArrayOf(
                    0.7f, 0.15f, 0.15f, 0f, 40f,
                    0.15f, 0.7f, 0.15f, 0f, 40f,
                    0.15f, 0.15f, 0.7f, 0f, 40f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
    }
}

data class VideoClip(
    val id: String,
    val filePath: String,
    val title: String,
    val timestamp: Long,
    val durationSeconds: Long,
    val fps: Int,
    val resolution: String,
    val filterName: String,
    val stabilization: String,
    val iso: Int,
    val shutterSpeedText: String,
    val isSlowMotion: Boolean,
    val fileSizeFormatted: String
)
