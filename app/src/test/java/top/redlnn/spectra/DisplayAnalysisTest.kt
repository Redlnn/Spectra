package top.redlnn.spectra

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test
import top.redlnn.spectra.render.*

class DisplayAnalysisTest {
    @Test
    fun autoRangeRevealsWeakSignalsWithoutChangingSamples() {
        val pcm = FloatArray(1024) { (.001 * sin(2 * PI * it / 50)).toFloat() }
        val original = pcm.clone()
        val gain = WaveformScale().gain(pcm, 1_000_000L)
        assertTrue(gain >= 400f)
        assertArrayEquals(original, pcm, 0f)
    }

    @Test
    fun repeatedDrawsDoNotChangeAutoGainAndSilenceStaysFinite() {
        val scale = WaveformScale()
        val quiet = FloatArray(20) { .001f }
        val initial = scale.gain(quiet, 1_000_000L)
        repeat(60) { assertEquals(initial, scale.gain(quiet, 1_000_000L), 0f) }
        assertEquals(1f, scale.gain(FloatArray(20), 2_000_000L), 0f)
    }

    @Test
    fun highestPeakTracksOnlyVisibleFrequenciesAndRejectsSilence() {
        val values =
            FloatArray(100) { -120f }
                .apply {
                    this[5] = -20f
                    this[30] = -40f
                }
        assertEquals(50.0, SpectrumPeak.find(values, 10.0, 20.0, 900.0)!!.hz, 1.0)
        assertEquals(300.0, SpectrumPeak.find(values, 10.0, 200.0, 900.0)!!.hz, 1.0)
        assertNull(SpectrumPeak.find(FloatArray(100) { -180f }, 10.0, 20.0, 900.0))
        assertNull(SpectrumPeak.find(values, 10.0, 991.0, 999.0))
    }
}
