package top.redlnn.spectra

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.render.AnalyzerChartView
import top.redlnn.spectra.settings.*

@RunWith(AndroidJUnit4::class)
class AnalyzerRequestedChangesTest {
    @Test
    fun publicationRateWithSingleSpectrum() {
        tone()
        compose.runOnUiThread {
            vm.update(
                vm.settings.value.copy(
                    fftSize = 1024,
                    view = ViewMode.SPECTRUM,
                    generatorPlayback = false,
                    fps = 10,
                )
            )
        }
        compose.waitUntil(10000) { vm.frame.value?.fftSize == 1024 && vm.state.value.running }
        val count = AtomicLong()
        val observer = compose.runOnIdle {
            compose.activity.lifecycleScope.launch {
                vm.frame.collect { if (it != null) count.incrementAndGet() }
            }
        }
        try {
            for (rate in listOf(10, 40, 60)) {
                compose.runOnUiThread { vm.update(vm.settings.value.copy(fps = rate)) }
                Thread.sleep(200)
                val before = count.get()
                val start = System.nanoTime()
                Thread.sleep(1200)
                val measured = (count.get() - before) * 1e9 / (System.nanoTime() - start)
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .sendStatus(
                        0,
                        android.os.Bundle().apply {
                            putString(
                                "stream",
                                "single-spectrum target=$rate publication=$measured Hz; analysis=${vm.state.value.analysisHz} Hz; render=${vm.state.value.renderFps} FPS\n",
                            )
                        },
                    )
                assertTrue(
                    "$rate FPS published $measured frames/s",
                    measured >= rate * .8 && measured <= rate + 3,
                )
            }
        } finally {
            compose.runOnUiThread { vm.toggle() }
            observer.cancel()
        }
    }

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
        val desired =
            AnalyzerSettings(
                input = InputSource.GENERATOR,
                fftSize = 8192,
                view = ViewMode.WAVE_SPECTROGRAM,
            )
        compose.runOnUiThread {
            vm.update(desired)
            vm.selectInput(InputSource.GENERATOR)
        }
        compose.waitUntil(10000) {
            vm.frame.value?.processingKey == desired.processingKey().toString() &&
                vm.frame.value?.pitch != null &&
                vm.state.value.running
        }
    }

    @Test
    fun waveformTapDoesNotCreateOrResetSpectrogramCursor() {
        tone()
        compose.runOnUiThread { vm.toggle() }
        compose.waitForIdle()
        compose.runOnIdle {
            val view = chart()!!
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.recycle()
        }
        fun tap(fractionY: Float, double: Boolean = false) = compose.runOnIdle {
            val view = chart()!!
            val x = view.width * .6f
            val y = view.height * fractionY
            val time = SystemClock.uptimeMillis() + 1000
            repeat(if (double) 2 else 1) { n ->
                val start = time + n * 100
                MotionEvent.obtain(start, start, MotionEvent.ACTION_DOWN, x, y, 0).also {
                    view.onTouchEvent(it)
                    it.recycle()
                }
                MotionEvent.obtain(start, start + 40, MotionEvent.ACTION_UP, x, y, 0).also {
                    view.onTouchEvent(it)
                    it.recycle()
                }
            }
        }
        val before = compose.runOnIdle { chart()!!.viewportMetadata() }
        tap(.25f)
        val after = compose.runOnIdle { chart()!!.viewportMetadata() }
        assertEquals("Waveform tap created a frequency/history cursor", before, after)
        tap(.75f)
        val cursor = compose.runOnIdle { chart()!!.viewportMetadata() }
        assertTrue(cursor.contains("cursor_hz=") && !cursor.contains("cursor_hz=none"))
        tap(.25f, true)
        assertEquals(
            "Waveform double tap reset another panel",
            cursor,
            compose.runOnIdle { chart()!!.viewportMetadata() },
        )
    }

    @Test
    fun previewNumberOpensDirectEntryAndValidatesBounds() {
        tone()
        compose.runOnUiThread { vm.toggle() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForSettingsScreen()
        compose.onNodeWithText("分析", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("slider-preview-频谱倾斜").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("9")
        compose.onNodeWithText("应用").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("1.3")
        compose.onNodeWithText("应用").performClick()
        compose.waitUntil { kotlin.math.abs(vm.settings.value.tilt - 1.3) < .001 }
    }

    @Test
    fun displayFramePublicationFollowsRequestedRefreshRate() {
        tone()
        compose.runOnUiThread { vm.update(vm.settings.value.copy(fftSize = 1024, fps = 10)) }
        compose.waitUntil(10000) { vm.frame.value?.fftSize == 1024 }
        val frames = AtomicLong()
        val observer = compose.runOnIdle {
            compose.activity.lifecycleScope.launch {
                vm.frame.collect { if (it != null) frames.incrementAndGet() }
            }
        }
        for (rate in listOf(10, 40, 60)) {
            // Measure the real-time publisher without waiting for a continuously updating Compose
            // tree to become idle.
            compose.runOnUiThread { vm.update(vm.settings.value.copy(fps = rate)) }
            Thread.sleep(200)
            val start = frames.get()
            val time = System.nanoTime()
            Thread.sleep(1200)
            val published = (frames.get() - start) * 1e9 / (System.nanoTime() - time)
            assertTrue(
                "$rate FPS published $published frames/s",
                published >= rate * .8 && published <= rate + 3,
            )
            compose.runOnUiThread { assertSame(vm.frame.value, vm.chartFrame()) }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .sendStatus(
                    0,
                    android.os.Bundle().apply {
                        putString("stream", "target=$rate readout publication=$published Hz\n")
                    },
                )
        }
        compose.runOnUiThread { vm.toggle() }
        observer.cancel()
    }

    @Test
    fun refreshPickerContainsAllSixRates() {
        tone()
        compose.runOnUiThread { vm.toggle() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForSettingsScreen()
        compose.onNodeWithText("工具", useUnmergedTree = true).performClick()
        compose.onNodeWithText("界面刷新率").performScrollTo().performClick()
        RefreshRates.forEachIndexed { index, rate ->
            compose.onNodeWithTag("picker-option-界面刷新率-$index").assertTextContains("$rate FPS")
        }
        compose.onNodeWithTag("picker-option-界面刷新率-3").performClick()
        compose.runOnUiThread { assertEquals(40, vm.settings.value.fps) }
        compose.onNodeWithContentDescription("关闭设置").performClick()
    }

    @Test
    fun twoGradientPalettesUseTheSameCurveGeometry() {
        tone()
        compose.runOnUiThread {
            vm.toggle()
            vm.update(
                vm.settings.value.copy(
                    view = ViewMode.SPECTRUM,
                    monet = true,
                    spectrumPalette = SpectrumPalette.CLASSIC,
                )
            )
        }
        compose.waitForIdle()
        fun pixels(): IntArray = compose.runOnIdle {
            val view = chart()!!
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            IntArray(view.width * view.height).also {
                bitmap.getPixels(it, 0, view.width, 0, 0, view.width, view.height)
                bitmap.recycle()
            }
        }
        val blueGreen = pixels()
        compose.runOnUiThread {
            vm.update(vm.settings.value.copy(spectrumPalette = SpectrumPalette.NIGHT))
        }
        compose.waitForIdle()
        assertArrayEquals(
            "Blue-green still draws bars instead of the red-yellow curve geometry",
            blueGreen,
            pixels(),
        )
    }
}
