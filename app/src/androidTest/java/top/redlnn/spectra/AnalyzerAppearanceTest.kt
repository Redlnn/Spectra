package top.redlnn.spectra

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.render.AnalyzerChartView
import top.redlnn.spectra.settings.*

@RunWith(AndroidJUnit4::class)
class AnalyzerAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm
        get() = compose.activity.analyzer

    private fun chart(view: View = compose.activity.window.decorView): AnalyzerChartView? {
        if (view is AnalyzerChartView) return view
        if (view is ViewGroup)
            for (i in 0 until view.childCount) chart(view.getChildAt(i))?.let {
                return it
            }
        return null
    }

    private fun tone() {
        compose.waitUntil(15000) { !vm.state.value.starting }
        compose.runOnUiThread {
            vm.update(AnalyzerSettings(input = InputSource.GENERATOR, fftSize = 8192, fps = 10))
            vm.selectInput(InputSource.GENERATOR)
        }
        compose.waitUntil(10000) { vm.frame.value?.pitch != null }
    }

    @Test
    fun pitchCardEditsGeneratorAndRejectsInvalidFrequency() {
        tone()
        compose.onNodeWithTag("pitch-card").performClick()
        compose.onNodeWithText("锯齿波").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("21000")
        compose.onNodeWithText("应用").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("125")
        compose.onNodeWithText("应用").performClick()
        compose.waitUntil(10000) {
            vm.settings.value.shape == WaveShape.SAW &&
                vm.frame.value?.pitch?.hz?.let { kotlin.math.abs(it - 125) < 2 } == true
        }
        assertEquals(125.0, vm.settings.value.generatorHz, 0.0)
    }

    @Test
    fun globalThemeMonetAndIndependentHeatPalette() {
        tone()
        compose.runOnUiThread {
            vm.toggle()
            vm.update(vm.settings.value.copy(themeMode = ThemeMode.DARK))
        }
        compose.waitForIdle()
        assertTrue(chart()!!.colors.dark)
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForSettingsScreen()
        compose.onNodeWithText("主题模式").performClick()
        compose.onNodeWithText("浅色", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertFalse(chart()!!.colors.dark)
        compose.onNodeWithText("Monet 取色").performClick()
        compose.waitForIdle()
        assertTrue(vm.settings.value.monet)
        val before = chart()!!.colors
        assertEquals(before.live, before.wave)
        compose.onNodeWithText("频谱配色").onParent().assertIsNotEnabled()
        compose.onNodeWithText("声谱图配色").performClick()
        compose.onNodeWithText("极光", useUnmergedTree = true).performClick()
        assertEquals("Aurora", vm.settings.value.colorMap)
        compose.runOnUiThread {
            vm.update(
                vm.settings.value.copy(
                    spectrumPalette = SpectrumPalette.YELLOW,
                    waveColor = "Yellow",
                )
            )
        }
        compose.waitForIdle()
        assertEquals(before, chart()!!.colors)
        compose.onNodeWithContentDescription("关闭设置").performClick()
    }

    @Test
    fun compactCardsAlignFooterTextAndAutomaticLayoutUsesWidth() {
        tone()
        val pitch = compose.onNodeWithTag("pitch-card").getUnclippedBoundsInRoot()
        val detail =
            compose
                .onNodeWithTag("pitch-details", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        assertTrue("card=$pitch", pitch.bottom - pitch.top <= androidx.compose.ui.unit.Dp(90f))
        for (label in listOf("输入电平", "峰值频率")) {
            val unit =
                compose
                    .onNodeWithTag("metric-unit-$label", useUnmergedTree = true)
                    .getUnclippedBoundsInRoot()
            assertEquals(detail.bottom.value, unit.bottom.value, 1f)
        }
        compose.runOnIdle {
            val native = AnalyzerChartView(compose.activity)
            val scale = compose.activity.resources.displayMetrics.density
            native.layout(0, 0, (360 * scale).toInt(), (600 * scale).toInt())
            assertFalse(native.horizontal)
            native.layout(0, 0, (800 * scale).toInt(), (400 * scale).toInt())
            assertTrue(native.horizontal)
        }
    }
}
