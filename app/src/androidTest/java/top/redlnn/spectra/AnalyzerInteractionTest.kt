package top.redlnn.spectra

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
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
class AnalyzerInteractionTest {
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
            vm.update(
                AnalyzerSettings(
                    input = InputSource.GENERATOR,
                    themeMode = ThemeMode.DARK,
                    fps = 10,
                )
            )
            vm.selectInput(InputSource.GENERATOR)
        }
        compose.waitUntil(10000) { vm.frame.value?.pitch != null }
    }

    @Test
    fun sliderPreviewsWhilePressedAndCommitsOnlyOnRelease() {
        tone()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForSettingsScreen()
        compose.onNodeWithText("分析", useUnmergedTree = true).performClick()
        val slider = compose.onNodeWithTag("slider-频谱倾斜").performScrollTo()
        val before =
            compose
                .onNodeWithTag("slider-preview-频谱倾斜")
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                .first()
                .text
        slider.performTouchInput {
            down(center)
            moveTo(Offset(width * .8f, centerY))
        }
        compose.waitForIdle()
        val preview =
            compose
                .onNodeWithTag("slider-preview-频谱倾斜")
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                .first()
                .text
        assertEquals(0.0, vm.settings.value.tilt, 0.0)
        assertNotEquals("Preview label never changes while dragging", before, preview)
        slider.performTouchInput { up() }
        compose.waitUntil { vm.settings.value.tilt > 0 }
    }

    @Test
    fun pressedPitchCardCannotPaintOutsideItsRoundedCorners() {
        tone()
        val card = compose.onNodeWithTag("pitch-card")
        val before = card.captureToImage().toPixelMap()[2, 2].toArgb()
        card.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(250)
        val during = card.captureToImage().toPixelMap()[2, 2].toArgb()
        card.performTouchInput { up() }
        assertEquals("Press overlay escaped the card corner", before, during)
    }

    @Test
    fun waveformColorAndWidthChangeRenderedPixels() {
        tone()
        compose.runOnUiThread {
            vm.toggle()
            vm.update(vm.settings.value.copy(view = ViewMode.WAVE, waveThickness = 1f))
        }
        compose.waitForIdle()
        fun countColor(): Int = compose.runOnIdle {
            val view = chart()!!
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val pixels = IntArray(view.width * view.height)
            image.getPixels(pixels, 0, view.width, 0, 0, view.width, view.height)
            image.recycle()
            pixels.count { it == view.colors.wave }
        }
        val green = compose.runOnIdle { chart()!!.colors.wave }
        assertTrue(countColor() > 50)
        compose.runOnUiThread { vm.update(vm.settings.value.copy(waveColor = "Yellow")) }
        compose.waitForIdle()
        assertNotEquals(green, chart()!!.colors.wave)
        val thin = countColor()
        compose.runOnUiThread { vm.update(vm.settings.value.copy(waveThickness = 5f)) }
        compose.waitForIdle()
        assertTrue("Line width option made no visible difference", countColor() > thin * 2)
    }

    @Test
    fun monetStyleIsExpressiveByDefaultAndChangesPalette() {
        tone()
        assertEquals(MonetStyle.EXPRESSIVE, vm.settings.value.monetStyle)
        compose.runOnUiThread { vm.update(vm.settings.value.copy(monet = true)) }
        compose.waitForIdle()
        val before = chart()!!.colors
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForSettingsScreen()
        compose.onNodeWithText("色彩风格").performClick()
        compose.onNodeWithText("鲜明（Vibrant）", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(MonetStyle.VIBRANT, vm.settings.value.monetStyle)
        assertNotEquals(before, chart()!!.colors)
        compose.onNodeWithContentDescription("关闭设置").performClick()
    }

    @Test
    fun generatorPlaybackWritesAudioAndStopsWhenPaused() {
        tone()
        compose.runOnUiThread { vm.update(vm.settings.value.copy(generatorPlayback = false)) }
        compose.waitUntil {
            !vm.state.value.starting && vm.state.value.running && !vm.state.value.playbackActive
        }
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForSettingsScreen()
        compose.onNodeWithText("输入", useUnmergedTree = true).performClick()
        compose.onNodeWithText("播放发生器声音").performScrollTo().performClick()
        compose.onNodeWithContentDescription("关闭设置").performClick()
        compose.waitUntil(10000) {
            vm.state.value.playbackActive && vm.state.value.outputFrames > 4096
        }
        compose.runOnUiThread { vm.toggle() }
        compose.waitUntil { !vm.state.value.running && !vm.state.value.playbackActive }
        val output = vm.state.value.outputFrames
        Thread.sleep(150)
        assertEquals(output, vm.state.value.outputFrames)
    }
}
