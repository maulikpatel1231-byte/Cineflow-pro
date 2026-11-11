package com.example

import com.example.model.ColorFilterType
import com.example.model.FpsMode
import com.example.model.ResolutionMode
import com.example.model.StabilizationMode
import org.junit.Assert.*
import org.junit.Test

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
    }

    @Test
    fun testColorFilters() {
        assertNotNull(ColorFilterType.TEAL_AND_ORANGE.getColorMatrix())
        assertNotNull(ColorFilterType.NOIR.getColorMatrix())
        assertNotNull(ColorFilterType.CYBERPUNK.getColorMatrix())
        assertNotNull(ColorFilterType.GOLDEN_HOUR.getColorMatrix())
    }
}
