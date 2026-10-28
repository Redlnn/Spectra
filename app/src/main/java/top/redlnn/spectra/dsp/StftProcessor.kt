package top.redlnn.spectra.dsp

import kotlin.math.*
import top.redlnn.spectra.settings.AnalyzerSettings
import top.redlnn.spectra.settings.Engine
import top.redlnn.spectra.settings.InputSource

/** Radix-2 FFT. Scratch buffers, windows and twiddles belong to one processor. */
class StftProcessor(val settings: AnalyzerSettings, val sampleRate: Int) {
    val size = settings.analysisSize
    val hop = max(1, (size * (1 - settings.overlap)).roundToInt())
    val bufferSize =
        max(size, max(8192, sampleRate / 12)).let { Integer.highestOneBit(it - 1) shl 1 }
    private val fft = Fft(size, settings.window)
    private val multi =
        if (settings.engine == Engine.MULTI)
            listOf(4096, 2048, 1024).associateWith { Fft(it, settings.window) }
        else emptyMap()
    private val averages = Array(settings.average) { DoubleArray(size / 2 + 1) }
    private val sum = DoubleArray(size / 2 + 1)
    private val peaks = FloatArray(size / 2 + 1) { -180f }
    private val pitchDetector =
        PitchDetector(settings.pitchDenoise, settings.pitchSensitivity.threshold)
    private var averageIndex = 0
    private var averageCount = 0
    private var lastPitchNs = Long.MIN_VALUE
    private var pitch: Pitch? = null

    fun resetPeaks() {
        peaks.fill(-180f)
    }

    fun process(pcm: FloatArray, timestampEndNs: Long): AnalysisFrame {
        val offset = pcm.size - size
        val amplitude = fft.amplitudes(pcm, offset)
        if (multi.isNotEmpty()) {
            val spectra = multi.mapValues { (n, f) -> f.amplitudes(pcm, offset + (size - n) / 2) }
            for (k in 1 until amplitude.size) {
                val hz = k * sampleRate.toDouble() / size
                // Continuous log-frequency crossfades around 250, 2000 and 8000 Hz.
                var power = amplitude[k].toDouble().pow(2)
                for ((boundary, n) in listOf(250.0 to 4096, 2000.0 to 2048, 8000.0 to 1024)) {
                    val t = ((log2(hz / boundary) + .125) / .25).coerceIn(0.0, 1.0)
                    if (t > 0) {
                        val bins = spectra.getValue(n)
                        val index = hz * n / sampleRate
                        val lo = index.toInt().coerceIn(0, bins.lastIndex)
                        val hi = (lo + 1).coerceAtMost(bins.lastIndex)
                        val p =
                            bins[lo].toDouble().pow(2) * (1 - (index - lo)) +
                                bins[hi].toDouble().pow(2) * (index - lo)
                        power = power * (1 - t) + p * t
                    }
                }
                amplitude[k] = sqrt(max(0.0, power)).toFloat()
            }
        }
        val old = averages[averageIndex]
        averageCount = min(settings.average, averageCount + 1)
        val spectrum = FloatArray(amplitude.size)
        val heat = FloatArray(amplitude.size)
        for (k in amplitude.indices) {
            val power = amplitude[k].toDouble().pow(2)
            sum[k] += power - old[k]
            old[k] = power
            val hz = k * sampleRate.toDouble() / size
            val base =
                Levels.powerDb(sum[k] / averageCount) + Levels.weighting(hz, settings.weighting)
            val tilt = Levels.tilt(hz, settings.tilt)
            spectrum[k] = (base + tilt).toFloat()
            heat[k] = (base + if (settings.applyTilt) tilt else 0.0).toFloat()
            if (settings.peakHold) peaks[k] = max(peaks[k], spectrum[k])
        }
        averageIndex = (averageIndex + 1) % settings.average
        if (lastPitchNs == Long.MIN_VALUE || timestampEndNs - lastPitchNs >= 80_000_000L) {
            pitch = pitchDetector.detect(pcm, sampleRate)
            lastPitchNs = timestampEndNs
        }
        var energy = 0.0
        var clipped = false
        for (i in offset until pcm.size) {
            val v = pcm[i]
            energy += v * v
            if (abs(v) >= .999f) clipped = true
        }
        val waveCount = min(size, 2048)
        val triggerHz =
            if (settings.input == InputSource.GENERATOR)
                settings.generatorHz.coerceAtMost(sampleRate * .45)
            else pitch?.hz
        val waveform = WaveformTrigger.extract(pcm, waveCount, sampleRate, triggerHz)
        return AnalysisFrame(
            timestampEndNs - (size * 500_000_000L / sampleRate),
            sampleRate,
            size,
            amplitude,
            spectrum,
            heat,
            peaks.copyOf(),
            waveform,
            pitch,
            Levels.db(sqrt(energy / size)),
            clipped,
            (1 until spectrum.size).maxBy { spectrum[it] },
            settings.processingKey().toString(),
            pitchDetector.status,
        )
    }
}
