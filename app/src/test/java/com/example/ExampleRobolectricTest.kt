package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.GyroStabilizerStrength
import com.example.model.StabilizationMode
import com.example.viewmodel.CameraViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("CineFlow Pro", appName)
  }

  @Test
  fun `toggle hardware video stabilization updates viewModel and engine state`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = CameraViewModel(app)

    assertTrue(viewModel.isHardwareStabilizationEnabled.value)
    assertTrue(viewModel.engine.isHardwareVideoStabilizationEnabled)
    assertEquals(StabilizationMode.HYBRID, viewModel.stabilizationMode.value)
    assertEquals(GyroStabilizerStrength.ACTION, viewModel.gyroStabilizerStrength.value)

    viewModel.toggleHardwareStabilization(false)
    assertFalse(viewModel.isHardwareStabilizationEnabled.value)
    assertFalse(viewModel.engine.isHardwareVideoStabilizationEnabled)
    assertEquals(StabilizationMode.OFF, viewModel.stabilizationMode.value)
    assertEquals(GyroStabilizerStrength.OFF, viewModel.gyroStabilizerStrength.value)

    viewModel.toggleHardwareStabilization(true)
    assertTrue(viewModel.isHardwareStabilizationEnabled.value)
    assertTrue(viewModel.engine.isHardwareVideoStabilizationEnabled)
    assertEquals(StabilizationMode.HYBRID, viewModel.stabilizationMode.value)
    assertEquals(GyroStabilizerStrength.ACTION, viewModel.gyroStabilizerStrength.value)
  }

  @Test
  fun `super extreme stability and horizon lock toggles update state`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = CameraViewModel(app)

    assertEquals(StabilizationMode.HYBRID, viewModel.stabilizationMode.value)
    viewModel.toggleSuperExtremeStability()
    assertEquals(StabilizationMode.SUPER_EXTREME, viewModel.stabilizationMode.value)
    assertEquals(StabilizationMode.SUPER_EXTREME, viewModel.engine.stabilizationMode)
    assertEquals(GyroStabilizerStrength.LOCKDOWN, viewModel.gyroStabilizerStrength.value)
    assertTrue(viewModel.isHardwareStabilizationEnabled.value)

    assertTrue(viewModel.isHorizonLockEnabled.value)
    viewModel.toggleHorizonLock(false)
    assertFalse(viewModel.isHorizonLockEnabled.value)
    assertFalse(viewModel.engine.isHorizonLockEnabled)

    viewModel.toggleSuperExtremeStability()
    assertEquals(StabilizationMode.HYBRID, viewModel.stabilizationMode.value)
    assertEquals(GyroStabilizerStrength.ACTION, viewModel.gyroStabilizerStrength.value)
  }

  @Test
  fun `gyro stabilizer strength and anti blur shutter controls update engine`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = CameraViewModel(app)

    viewModel.setGyroStabilizerStrength(GyroStabilizerStrength.LOCKDOWN)
    assertEquals(GyroStabilizerStrength.LOCKDOWN, viewModel.gyroStabilizerStrength.value)
    assertEquals(GyroStabilizerStrength.LOCKDOWN, viewModel.engine.gyroStabilizerStrength)

    assertTrue(viewModel.isAntiBlurShutterEnabled.value)
    viewModel.toggleAntiBlurShutter(false)
    assertFalse(viewModel.isAntiBlurShutterEnabled.value)
    assertFalse(viewModel.engine.isAntiBlurShutterEnabled)
  }

  @Test
  fun `chooseBestFpsRange selects supported range and prefers fast shutter floor`() {
    val ranges = listOf(
      android.util.Range(15, 30),
      android.util.Range(30, 30),
      android.util.Range(24, 60),
      android.util.Range(60, 60)
    )

    // 120 FPS requested on 60 FPS max sensor -> safely picks [60, 60] instead of crashing
    val capped = com.example.camera.CameraHardwareHelper.chooseBestFpsRange(
      availableRanges = ranges,
      targetFps = 120,
      preferFastShutterFloor = true
    )
    assertEquals(android.util.Range(60, 60), capped)

    // 30 FPS with anti-blur fast shutter floor prefers [30, 30] over [15, 30]
    val steady30 = com.example.camera.CameraHardwareHelper.chooseBestFpsRange(
      availableRanges = ranges,
      targetFps = 30,
      preferFastShutterFloor = true
    )
    assertEquals(android.util.Range(30, 30), steady30)

    // Empty ranges returns null gracefully
    assertEquals(
      null,
      com.example.camera.CameraHardwareHelper.chooseBestFpsRange(emptyList(), 60, false)
    )
  }

  @Test
  fun `repository starts empty without fake sample clips and persists valid real files`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("cineflow_clips_pref", Context.MODE_PRIVATE).edit().clear().commit()

    val repo = com.example.data.VideoClipRepository(app)
    assertTrue("Repository should not seed unplayable dummy sample clips", repo.clips.value.isEmpty())

    val realClipFile = java.io.File(app.filesDir, "test_real_clip.mp4").apply {
      writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70))
    }
    val clip = com.example.model.VideoClip(
      id = "clip-1",
      filePath = realClipFile.absolutePath,
      title = "RealClip",
      timestamp = 123456L,
      durationSeconds = 5L,
      fps = 60,
      resolution = "1080P",
      filterName = "Original",
      stabilization = "Hybrid Pro",
      iso = 200,
      shutterSpeedText = "1/120s",
      isSlowMotion = false,
      fileSizeFormatted = "8 B"
    )
    repo.addClip(clip)
    assertEquals(1, repo.clips.value.size)

    // Reloading repository preserves valid clip
    val reloadedRepo = com.example.data.VideoClipRepository(app)
    assertEquals(1, reloadedRepo.clips.value.size)
    assertEquals("clip-1", reloadedRepo.clips.value.first().id)

    reloadedRepo.deleteClip("clip-1")
    assertTrue(reloadedRepo.clips.value.isEmpty())
    assertFalse("Deleted clip file should be removed from disk", realClipFile.exists())
  }

  @Test
  fun `viewModel clamps zoom, ISO, EV and updates live color filter and white balance`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = CameraViewModel(app)

    // Zoom clamping within [minZoomRatio, maxZoomRatio]
    viewModel.setZoomRatio(999f)
    assertEquals(viewModel.maxZoomRatio.value, viewModel.zoomRatio.value, 0.001f)
    viewModel.setZoomRatio(0.01f)
    assertEquals(viewModel.minZoomRatio.value, viewModel.zoomRatio.value, 0.001f)
    viewModel.setZoomRatio(2.0f)
    assertEquals(2.0f, viewModel.zoomRatio.value, 0.001f)

    // White balance & color filter update engine cleanly
    viewModel.setWhiteBalance(com.example.model.WhiteBalanceMode.DAYLIGHT)
    assertEquals(com.example.model.WhiteBalanceMode.DAYLIGHT, viewModel.engine.whiteBalanceMode)

    viewModel.setColorFilter(com.example.model.ColorFilterType.NOIR)
    assertEquals(com.example.model.ColorFilterType.NOIR, viewModel.engine.currentColorFilter)

    // Manual ISO clamps to hardware info bounds
    val hw = viewModel.hardwareInfo.value
    viewModel.setManualIso(999999)
    assertEquals(hw.maxIso, viewModel.manualIso.value)
    viewModel.setManualIso(1)
    assertEquals(hw.minIso, viewModel.manualIso.value)
  }

  @Test
  fun `mainActivity launches and renders without crashing`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    org.junit.Assert.assertNotNull(activity)
    assertFalse(activity.isFinishing)
    controller.pause().stop().destroy()
  }
}
