package top.redlnn.spectra

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.settings.*

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm
        get() = compose.activity.analyzer

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.waitForIdleSync()
        Thread.sleep(500)
        val bitmap = instrument.uiAutomation.takeScreenshot()
        File(instrument.targetContext.getExternalFilesDir(null), "v150-$name.png")
            .outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun checkStyle(miuix: Boolean) {
        compose.waitUntil(15000) { !vm.state.value.starting }
        compose.runOnUiThread {
            vm.update(
                AnalyzerSettings(
                    input = InputSource.GENERATOR,
                    generatorPlayback = false,
                    settingsMiuix = miuix,
                    fps = 10,
                )
            )
            vm.selectInput(InputSource.GENERATOR)
        }
        compose.waitUntil(10000) { vm.frame.value != null && vm.state.value.running }
        compose.runOnUiThread { vm.toggle() }
        compose.waitForIdle()
        compose.onNodeWithText("对数刻度").assertDoesNotExist()
        val peaks = compose.onNodeWithTag("reset-spectrum-peaks")
        val viewport = compose.onNodeWithTag("reset-viewport")
        peaks.assertHasClickAction()
        viewport.assertHasClickAction()
        assertEquals(
            peaks.fetchSemanticsNode().boundsInRoot.height,
            viewport.fetchSemanticsNode().boundsInRoot.height,
            .5f,
        )
        compose.onNodeWithContentDescription("重置峰值").assertExists()
        compose.onNodeWithContentDescription("重置缩放").assertExists()
        if (miuix) screenshot("main")
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForSettingsScreen()
        compose.onNodeWithTag("settings-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("FFT 点数").assertIsDisplayed()
        compose.onNodeWithTag("settings-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("对数刻度").assertIsDisplayed().performClick()
        assertFalse(vm.settings.value.logAxis)
        screenshot(if (miuix) "charts-miuix" else "charts-expressive")
        compose.onNodeWithTag("settings-pager").performTouchInput { swipeRight() }
        compose.onNodeWithText("FFT 点数").assertIsDisplayed()
        compose.onNodeWithText("输入", useUnmergedTree = true).performClick()
        compose.onNodeWithText("音频输入").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭设置").performClick()
        compose.onNodeWithText("对数刻度").assertDoesNotExist()
        assertFalse(vm.settings.value.logAxis)
        assertTrue(AnalyzerSettings().generatorPlayback)
        assertFalse(
            AnalyzerSettings.fromJson(AnalyzerSettings(generatorPlayback = false).toJson())
                .generatorPlayback
        )
    }

    @Test fun miuixTabsSwipeAndLogScaleLivesOnlyInSettings() = checkStyle(true)

    @Test fun expressiveTabsSwipeAndLogScaleLivesOnlyInSettings() = checkStyle(false)
}
