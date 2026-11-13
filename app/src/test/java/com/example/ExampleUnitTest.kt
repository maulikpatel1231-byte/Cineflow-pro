package com.example

import com.example.camera.GyroStabilizerFilter
import com.example.model.ColorFilterType
import com.example.model.FpsMode
import com.example.model.GyroStabilizerStrength
import com.example.model.ResolutionMode
import com.example.model.StabilizationMode
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ExampleUnitTest {
    @Test
    fun testFpsModesHighSpeed() {
        assertTrue(FpsMode.FPS_120.isHighSpeed)
        assertTrue(FpsMode.FPS_240.isHighSpeed)
        assertFalse(FpsMode.FPS_30.isHighSpeed)
        assertFalse(FpsMode.FPS_60.isHighSpeed)
        assertEquals(120, FpsMode.FPS_120.fps)
        assertEquals(240, FpsMode.FPS_240.fps)
    }

    @Test
    fun testResolutionModes() {
        assertEquals(1280, ResolutionMode.HD_720P.width)
        assertEquals(720, ResolutionMode.HD_720P.height)
        assertEquals(1920, ResolutionMode.FHD_1080P.width)
        assertEquals(1080, ResolutionMode.FHD_1080P.height)
    }

    @Test
    fun testStabilizationModes() {
        assertEquals("Off", StabilizationMode.OFF.label)
        assertEquals("OIS", StabilizationMode.OIS.label)
        assertEquals("EIS", StabilizationMode.EIS.label)
        assertEquals("Hybrid Pro", StabilizationMode.HYBRID.label)
        assertEquals("Super Extreme", StabilizationMode.SUPER_EXTREME.label)
    }

    @Test
    fun testColorFilters() {
        assertNotNull(ColorFilterType.TEAL_AND_ORANGE.getColorMatrix())
        assertNotNull(ColorFilterType.NOIR.getColorMatrix())
        assertNotNull(ColorFilterType.CYBERPUNK.getColorMatrix())
        assertNotNull(ColorFilterType.GOLDEN_HOUR.getColorMatrix())
    }

    @Test
    fun testGyroClampNeverLeavesBlackBordersAcrossAspectRatios() {
        val aspects = listOf(9f / 16f, 3f / 4f, 1f, 16f / 9f)
        val overscans = listOf(1.12f, 1.22f, 1.32f)
        for (aspect in aspects) {
            for (overscan in overscans) {
                val clamped = GyroStabilizerFilter.clampToSafeBounds(
                    rawTxAspect = 2.5f,
                    rawTyAspect = -2.5f,
                    rawRollDeg = 35f,
                    overscanScale = overscan,
                    aspectRatio = aspect,
                    maxRollDeg = 12.5f
                )
                val rad = Math.toRadians(clamped.rollDegrees.toDouble())
                val c = cos(rad).toFloat()
                val s = sin(rad).toFloat()
                // Verify all 4 viewport corners (±aspect, ±1) lie strictly inside rotated scaled source [-aspect, aspect] x [-1, 1]
                val corners = listOf(
                    -aspect to -1f,
                    aspect to -1f,
                    -aspect to 1f,
                    aspect to 1f
                )
                for ((cx, cy) in corners) {
                    val dx = cx - clamped.txAspect
                    val dy = cy - clamped.tyAspect
                    val ux = (dx * c + dy * s) / clamped.overscanScale
                    val uy = (-dx * s + dy * c) / clamped.overscanScale
                    assertTrue(
                        "Corner ($cx,$cy) escaped X bound ($ux > $aspect) for aspect=$aspect overscan=$overscan",
                        abs(ux) <= aspect + 1e-3f
                    )
                    assertTrue(
                        "Corner ($cx,$cy) escaped Y bound ($uy > 1) for aspect=$aspect overscan=$overscan",
                        abs(uy) <= 1f + 1e-3f
                    )
                }
            }
        }
    }

    @Test
    fun testGyroFilterCancelsShakeAndFollowsSlowPan() {
        val filter = GyroStabilizerFilter().apply {
            strength = GyroStabilizerStrength.LOCKDOWN
            isHorizonLockEnabled = false
            gainMultiplier = 1.0f
        }
        var t = 1_000_000_000L
        filter.onGyroSample(0f, 0f, 0f, t)
        // Sudden 10ms jolt of 2.0 rad/s yaw
        t += 10_000_000L
        filter.onGyroSample(0f, 2.0f, 0f, t)

        // Identity 4x4 texture matrix on 16:9 landscape surface
        val texMat = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
        val immediate = filter.sampleTransformForSurface(
            surfaceWidth = 1920,
            surfaceHeight = 1080,
            surfaceTexMatrix = texMat,
            sensorOrientationDegrees = 0,
            isFrontCamera = false
        )
        assertTrue("Immediate counter-shift should be non-zero", abs(immediate.txAspect) > 0.01f)

        // Hold steady for 3 seconds (zero gyro) -> high-pass pan follower decays smoothly back toward center
        repeat(60) {
            t += 50_000_000L
            filter.onGyroSample(0f, 0f, 0f, t)
        }
        val settled = filter.sampleTransformForSurface(
            surfaceWidth = 1920,
            surfaceHeight = 1080,
            surfaceTexMatrix = texMat,
            sensorOrientationDegrees = 0,
            isFrontCamera = false
        )
        assertTrue(
            "Filter should smoothly recenter after sustained stillness (${settled.txAspect} vs ${immediate.txAspect})",
            abs(settled.txAspect) < abs(immediate.txAspect) * 0.25f
        )
    }
}
