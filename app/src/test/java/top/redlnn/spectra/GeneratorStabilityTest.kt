package top.redlnn.spectra

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test
import top.redlnn.spectra.capture.*
import top.redlnn.spectra.dsp.*
import top.redlnn.spectra.settings.*

class GeneratorStabilityTest {
    private fun frames(
        window: WindowType,
        hz: Double = 147.0,
        shape: WaveShape = WaveShape.SAW,
    ): List<AnalysisFrame> {
        val settings =
            AnalyzerSettings(
                input = InputSource.GENERATOR,
                fftSize = 8192,
                window = window,
                generatorHz = hz,
                shape = shape,
                generatorAmplitude = .1,
            )
        val dsp = StftProcessor(settings, 48000)
        val ring = PcmRing(dsp.bufferSize)
        val block = FloatArray(dsp.hop)
        val pcm = FloatArray(dsp.bufferSize)
        return SignalInput(settings).use { source ->
            buildList {
                repeat(180) { n ->
                    source.read(block, block.size)
                    ring.add(block, block.size)
                    if (ring.count >= pcm.size) {
                        ring.latest(pcm)
                        add(dsp.process(pcm, (n + 1L) * block.size * 1_000_000_000L / 48000))
                    }
                }
            }
        }
    }

    @Test
    fun generatedFundamentalNeverDropsWhileWindowLeakageChanges() {
        val hamming = frames(WindowType.HAMMING)
        val blackman = frames(WindowType.BLACKMAN)
        val spread = hamming.maxOf { it.peakDb } - hamming.minOf { it.peakDb }
        val backgroundBin = (80.0 / hamming.first().spacingHz).roundToInt()
        val hammingLeak = hamming.map { it.spectrumDb[backgroundBin] }.average()
        val blackmanLeak = blackman.map { it.spectrumDb[backgroundBin] }.average()
        println(
            "147 Hz saw: fundamental variation=$spread dB; 80 Hz leakage Hamming=$hammingLeak dBFS Blackman=$blackmanLeak dBFS"
        )
        assertTrue("Fundamental actually dropped by $spread dB", spread < .15)
        assertTrue(
            "Blackman should suppress the changing sidelobes",
            blackmanLeak < hammingLeak - 12,
        )
    }

    @Test
    fun periodicWaveformIsTriggeredAtAStablePhase() {
        val waveforms = frames(WindowType.HANN, 147.0, WaveShape.SINE).map { it.waveform }
        val firstSampleSpread = waveforms.maxOf { it.first() } - waveforms.minOf { it.first() }
        assertTrue(
            "Oscilloscope start phase jumps: spread=$firstSampleSpread",
            firstSampleSpread < .001,
        )
    }
}
