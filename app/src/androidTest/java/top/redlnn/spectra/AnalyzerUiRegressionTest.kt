package top.redlnn.spectra

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.dsp.AnalysisFrame
import top.redlnn.spectra.render.AnalyzerChartView
import top.redlnn.spectra.settings.*

@RunWith(AndroidJUnit4::class)
class AnalyzerUiRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm
        get() = compose.activity.analyzer

    private fun findChart(view: View = compose.activity.window.decorView): AnalyzerChartView? {
        if (view is AnalyzerChartView) return view
        if (view is ViewGroup)
            for (i in 0 until view.childCount) findChart(view.getChildAt(i))?.let {
                return it
            }
        return null
    }

    private fun frozenTone(): AnalysisFrame {
        compose.waitUntil(15000) { !vm.state.value.starting }
        val desired = AnalyzerSettings(input = InputSource.GENERATOR, fftSize = 8192, fps = 10)
        val previous = vm.frame.value
        compose.runOnUiThread {
            vm.update(desired)
            vm.selectInput(InputSource.GENERATOR)
        }
        compose.waitUntil(10000) {
            vm.frame.value?.let {
                it !== previous &&
                    it.pitch != null &&
                    it.processingKey == desired.processingKey().toString()
            } == true && vm.state.value.running && !vm.state.value.starting
        }
        val frozen = compose.runOnIdle {
            val frame = vm.frame.value!!
            vm.toggle()
            frame
        }
        compose.waitUntil { !vm.state.value.running && !vm.state.value.starting }
        compose.waitForIdle()
        return frozen
    }

    @Test
    fun accidentalNotesKeepFrequencyAndCentsInsideCard() {
        frozenTone()
        for (hz in listOf(416.27, 466.16, 554.37, 1108.73)) {
            compose.runOnUiThread {
                vm.update(vm.settings.value.copy(generatorHz = hz))
                vm.toggle()
            }
            compose.waitUntil(10000) {
                vm.frame.value?.let {
                    it.pitch != null &&
                        it.processingKey == vm.settings.value.processingKey().toString()
                } == true
            }
            compose.runOnUiThread { vm.toggle() }
            compose.waitForIdle()
            val card = compose.onNodeWithTag("pitch-card").getUnclippedBoundsInRoot()
            val details =
                compose
                    .onNodeWithTag("pitch-details", useUnmergedTree = true)
                    .getUnclippedBoundsInRoot()
            assertTrue("hz=$hz details=$details card=$card", details.bottom <= card.bottom)
            assertTrue(details.left >= card.left && details.right <= card.right)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("pitch-details", useUnmergedTree = true).performSemanticsAction(
                SemanticsActions.GetTextLayoutResult
            ) {
                it(layouts)
            }
            val layout = layouts.single()
            assertFalse(
                "hz=$hz details=$details card=$card size=${layout.size} lineBottom=${layout.getLineBottom(layout.lineCount - 1)}",
                layout.didOverflowHeight,
            )
            // Text measures to integer pixels; Compose may flag a fractional-pixel width overflow.
            for (line in 0 until layout.lineCount) assertTrue(
                layout.getLineRight(line) <= layout.size.width + 1f
            )
        }
    }

    @Test
    fun fullscreenExpandsCurrentChartAndExitRestoresIt() {
        frozenTone()
        val chart = compose.onNodeWithTag("analyzer-chart")
        val before = chart.getUnclippedBoundsInRoot()
        val nativeBefore = compose.runOnIdle { findChart()!! }
        val frameBefore = nativeBefore.frame
        compose.onNodeWithContentDescription("图表全屏").performClick()
        compose.waitForIdle()
        val expanded = chart.getUnclippedBoundsInRoot()
        assertTrue(
            "before=$before expanded=$expanded",
            expanded.bottom - expanded.top >
                before.bottom - before.top + androidx.compose.ui.unit.Dp(100f),
        )
        compose.onNodeWithContentDescription("设置").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(
                ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.isVisible(
                    WindowInsetsCompat.Type.statusBars()
                )
            )
            assertSame(nativeBefore, findChart())
            assertSame(frameBefore, findChart()!!.frame)
        }
        compose.onNodeWithContentDescription("退出图表全屏").performClick()
        compose.waitForIdle()
        assertEquals(before, chart.getUnclippedBoundsInRoot())
        for (mode in
            listOf(
                ViewMode.WAVE,
                ViewMode.SPECTRUM,
                ViewMode.SPECTROGRAM,
                ViewMode.WAVE_SPECTRUM,
            )) {
            compose.runOnUiThread { vm.update(vm.settings.value.copy(view = mode)) }
            compose.onNodeWithContentDescription("图表全屏").performClick()
            compose.runOnIdle { assertEquals(mode, findChart()!!.settings.view) }
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithContentDescription("图表全屏").assertExists()
            compose.runOnIdle { assertSame(nativeBefore, findChart()) }
        }
    }

    @Test
    fun obsoleteSourceAndCopyAreAbsent() {
        frozenTone()
        compose.onNodeWithText("音高 · YIN").assertDoesNotExist()
        compose.onNodeWithText("离线").assertDoesNotExist()
        compose.onNodeWithText("信号发生器").performClick()
        compose.onNodeWithText("麦克风").assertExists()
        compose.onNodeWithText("成都 · 文件输入").assertDoesNotExist()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForSettingsScreen()
        compose.onNodeWithText("输入", useUnmergedTree = true).performClick()
        compose.onNodeWithText("文件输入说明").assertDoesNotExist()
        compose.onNodeWithText("播放文件 / 信号发生器").assertDoesNotExist()
        compose.onNodeWithContentDescription("关闭设置").performClick()
    }
}
