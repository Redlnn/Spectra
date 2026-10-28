package top.redlnn.spectra

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.dsp.StftProcessor
import top.redlnn.spectra.render.AnalyzerChartView
import top.redlnn.spectra.settings.*

@RunWith(AndroidJUnit4::class)
class WaveVisibilityTest {
    @Test
    fun peakMarkerHasIndependentColorAndCanBeDisabled() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.runOnMainSync {
            val settings = AnalyzerSettings(fftSize = 8192, view = ViewMode.SPECTRUM)
            val dsp = StftProcessor(settings, 48000)
            val view =
                AnalyzerChartView(instrument.targetContext).apply {
                    this.settings = settings
                    frame =
                        dsp.process(
                            FloatArray(dsp.bufferSize) {
                                (.1 * sin(2 * PI * 440 * it / 48000)).toFloat()
                            },
                            1_000_000_000L,
                        )
                    layout(0, 0, 1080, 1200)
                }
            fun peakPixels(): Int {
                val image = Bitmap.createBitmap(1080, 1200, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
                var count = 0
                for (y in 0 until image.height) for (x in 0 until image.width) if (
                    image.getPixel(x, y) == view.colors.peak
                )
                    count++
                image.recycle()
                return count
            }
            assertTrue("Automatic peak line absent", peakPixels() > 500)
            view.settings = settings.copy(showPeakMarker = false)
            assertTrue("Automatic peak line did not disappear", peakPixels() == 0)
        }
    }

    @Test
    fun weakSignalRemainsVisibleWithTrueAmplitudeScale() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.runOnMainSync {
            val settings = AnalyzerSettings(fftSize = 8192, view = ViewMode.WAVE)
            val dsp = StftProcessor(settings, 48000)
            val pcm =
                FloatArray(dsp.bufferSize) { (.001 * sin(2 * PI * 440 * it / 48000)).toFloat() }
            val view =
                AnalyzerChartView(instrument.targetContext).apply {
                    this.settings = settings
                    frame = dsp.process(pcm, 1_000_000_000L)
                    layout(0, 0, 1080, 1200)
                }
            val image = Bitmap.createBitmap(1080, 1200, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            var low = image.height
            var high = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                if (image.getPixel(x, y) == view.colors.wave) {
                    low = min(low, y)
                    high = max(high, y)
                }
            }
            image.recycle()
            assertTrue("Weak waveform only spans ${high-low}px of a 1200px view", high - low > 360)
        }
    }
}
