package top.redlnn.spectra

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test
import top.redlnn.spectra.noise.*

class NoiseProcessorTest {
    private fun analyze(
        rate: Int = 48000,
        block: Int = 1024,
        signal: (Double) -> Float,
    ): NoiseReference {
        val processor = NoiseProcessor(rate)
        var latest = NoiseReference()
        var offset = 0
        while (offset < rate * 11) {
            val count = min(block, rate * 11 - offset)
            processor.accept(FloatArray(count) { signal((offset + it).toDouble() / rate) })?.let {
                latest = it
            }
            offset += count
        }
        return latest
    }

    @Test
    fun steadyMotorToneIsOnlyACandidate() {
        val result = analyze { (.1 * sin(2 * PI * 100 * it)).toFloat() }
        assertTrue(result.observation.contains("100 Hz"))
        assertTrue(result.sources.contains("电机"))
        assertFalse(result.sources.contains("楼上"))
    }

    @Test
    fun readSizeDoesNotChangeEvidence() {
        val signal: (Double) -> Float = { (.1 * sin(2 * PI * 214 * it)).toFloat() }
        assertEquals(analyze(block = 317, signal = signal), analyze(block = 2048, signal = signal))
    }

    @Test
    fun actualSampleRateIsUsed() {
        val result = analyze(rate = 44100) { (.1 * sin(2 * PI * 214 * it)).toFloat() }
        assertTrue(result.observation.contains("214 Hz"))
    }

    @Test
    fun silenceDoesNotInventSources() {
        assertEquals("尚未检测到可用特征", analyze { 0f }.observation)
    }

    @Test
    fun clippingSuppressesSourceGuess() {
        assertEquals(
            "输入过载，暂无法判断",
            analyze { if (sin(2 * PI * 100 * it) > 0) 1f else -1f }.observation,
        )
    }

    @Test
    fun missingFundamentalDoesNotProveTwoMotors() {
        val result = analyze {
            (.1 * sin(2 * PI * 100 * it) + .1 * sin(2 * PI * 150 * it)).toFloat()
        }
        assertFalse(result.sources.contains("双电机"))
        assertFalse(result.sources.contains("洗衣机"))
    }

    @Test
    fun driftIsNotPresentedAsStableFrequency() {
        val result = analyze { (.1 * sin(2 * PI * (100 * it + 4 * it * it))).toFloat() }
        assertEquals("主要频率随时间变化", result.observation)
    }

    @Test
    fun freshProcessorDoesNotInheritEvidence() {
        val processor = NoiseProcessor(48000)
        val result = processor.accept(FloatArray(48000))!!
        assertTrue(result.seconds <= 1)
        assertFalse(result.sources.contains("电机"))
    }
}
