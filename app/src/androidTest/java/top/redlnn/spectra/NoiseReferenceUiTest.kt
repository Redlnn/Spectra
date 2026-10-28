package top.redlnn.spectra

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import top.redlnn.spectra.settings.*

class NoiseReferenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(800)
        val instrument = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrument.uiAutomation.takeScreenshot()
        File(instrument.targetContext.getExternalFilesDir(null), "noise-reference-$name.png")
            .outputStream()
            .use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        bitmap.recycle()
    }

    @Test
    fun modeSwitchAdviceGuideAndLandscape() {
        val vm = compose.activity.analyzer
        compose.waitUntil(15000) { !vm.state.value.starting }
        val original = vm.settings.value
        try {
            compose.runOnUiThread {
                vm.update(original.copy(input = InputSource.GENERATOR, generatorPlayback = false))
                vm.selectInput(InputSource.GENERATOR)
            }
            compose.waitUntil(10000) { vm.state.value.running }
            compose.onNodeWithTag("input-selector").performClick()
            compose.onNodeWithTag("noise-reference-switch").assertIsNotEnabled().assertIsOff()
            val generatorBounds =
                compose.onNodeWithTag("input-option-GENERATOR").fetchSemanticsNode().boundsInRoot
            val micBounds =
                compose.onNodeWithTag("input-option-MICROPHONE").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue(generatorBounds.top < micBounds.top)
            compose.onNodeWithTag("input-option-GENERATOR").performClick()
            compose.onNodeWithText("选择音频输入").assertDoesNotExist()
            compose.onNodeWithTag("input-selector").performClick()
            compose.onNodeWithTag("input-option-MICROPHONE").performClick()
            compose.onNodeWithText("选择音频输入").assertIsDisplayed()
            capture("input-dialog")
            compose.onNodeWithTag("noise-reference-switch").assertIsEnabled().performClick()
            compose.onNodeWithText("选择音频输入").assertDoesNotExist()
            compose.onNodeWithTag("input-level-card").assertDoesNotExist()
            compose.onNodeWithText("下一步建议").assertIsDisplayed()
            compose.onNodeWithText("查看依据").assertDoesNotExist()
            compose.onNodeWithText("做一次对照").assertDoesNotExist()
            compose.onNodeWithText("结束观察").assertDoesNotExist()
            capture("portrait")
            compose.onNodeWithTag("noise-guide-link").performClick()
            compose.waitUntil(5000) {
                runCatching {
                        compose
                            .onAllNodesWithTag("noise-guide-page")
                            .fetchSemanticsNodes()
                            .isNotEmpty()
                    }
                    .getOrDefault(false)
            }
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithTag("noise-reference-card").assertExists()
            compose.waitUntil(15000) { vm.noiseReference.value.seconds >= 2 }
            capture("microphone")
            compose.onNodeWithTag("noise-restart").performClick()
            org.junit.Assert.assertEquals(0, vm.noiseReference.value.seconds)
            compose.waitUntil(15000) { vm.noiseReference.value.seconds >= 2 }
            compose.runOnUiThread { vm.toggle() }
            val frozen = vm.noiseReference.value
            compose.waitForIdle()
            org.junit.Assert.assertEquals(frozen, vm.noiseReference.value)
            compose.runOnUiThread {
                compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            compose.waitForIdle()
            compose.onNodeWithTag("noise-reference-card").assertDoesNotExist()
            compose.onNodeWithTag("noise-reference-summary").assertIsDisplayed().performClick()
            compose.onNodeWithTag("landscape-pitch").assertDoesNotExist()
            compose.onNodeWithTag("noise-reference-dialog").assertExists()
            compose.onNodeWithText("下一步建议").assertIsDisplayed()
            compose.onNodeWithTag("noise-restart").performClick()
            compose.waitUntil(15000) {
                vm.state.value.running && vm.noiseReference.value.seconds >= 1
            }
            capture("details")
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithTag("analyzer-chart").assertIsDisplayed()
            capture("landscape")
            compose.onNodeWithTag("input-selector").performClick()
            compose.onNodeWithTag("noise-reference-switch").performClick()
            compose.onNodeWithText("选择音频输入").assertDoesNotExist()
            compose.onNodeWithTag("noise-reference-summary").assertDoesNotExist()
            compose.onNodeWithTag("noise-reference-card").assertDoesNotExist()
        } finally {
            compose.runOnUiThread {
                vm.setNoiseMode(false)
                vm.update(original)
                compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}
