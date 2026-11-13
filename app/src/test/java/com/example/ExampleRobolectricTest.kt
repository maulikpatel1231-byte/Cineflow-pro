package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

    viewModel.toggleHardwareStabilization(false)
    assertFalse(viewModel.isHardwareStabilizationEnabled.value)
    assertFalse(viewModel.engine.isHardwareVideoStabilizationEnabled)
    assertEquals(StabilizationMode.OFF, viewModel.stabilizationMode.value)

    viewModel.toggleHardwareStabilization(true)
    assertTrue(viewModel.isHardwareStabilizationEnabled.value)
    assertTrue(viewModel.engine.isHardwareVideoStabilizationEnabled)
    assertEquals(StabilizationMode.HYBRID, viewModel.stabilizationMode.value)
  }

  @Test
  fun `super extreme stability and horizon lock toggles update state`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = CameraViewModel(app)

    assertEquals(StabilizationMode.HYBRID, viewModel.stabilizationMode.value)
    viewModel.toggleSuperExtremeStability()
    assertEquals(StabilizationMode.SUPER_EXTREME, viewModel.stabilizationMode.value)
    assertEquals(StabilizationMode.SUPER_EXTREME, viewModel.engine.stabilizationMode)
    assertTrue(viewModel.isHardwareStabilizationEnabled.value)

    assertTrue(viewModel.isHorizonLockEnabled.value)
    viewModel.toggleHorizonLock(false)
    assertFalse(viewModel.isHorizonLockEnabled.value)
    assertFalse(viewModel.engine.isHorizonLockEnabled)

    viewModel.toggleSuperExtremeStability()
    assertEquals(StabilizationMode.HYBRID, viewModel.stabilizationMode.value)
  }
}
