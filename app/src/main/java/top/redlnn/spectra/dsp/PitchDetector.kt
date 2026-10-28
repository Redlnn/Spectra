package top.redlnn.spectra.dsp

import kotlin.math.*

/** Radix-2 FFT. Scratch buffers, windows and twiddles belong to one processor. */
class PitchDetector(private val denoise: Boolean = true, private val threshold: Double = .25) {
    private val samples = DoubleArray(1024)
    private val differences = DoubleArray(514)
    private var filterRate = 0
    private var filter: PitchFilter? = null
    var status = PitchStatus.WAITING
        private set

    fun detect(pcm: FloatArray, sampleRate: Int): Pitch? {
        status = PitchStatus.UNSTABLE
        val stride = max(1, sampleRate / 12000)
        val rate = sampleRate.toDouble() / stride
        val count = min(samples.size, pcm.size / stride)
        if (count < rate / 50 * 2) {
            status = PitchStatus.WAITING
            return null
        }
        val start = pcm.size - count * stride
        if (denoise) {
            if (filterRate != sampleRate) {
                filter = PitchFilter(sampleRate)
                filterRate = sampleRate
            }
            val f = filter!!
            f.reset()
            var sum = 0.0
            for (index in max(0, start - sampleRate / 20) until pcm.size) {
                val value = f.process(pcm[index])
                if (index >= start) {
                    sum += value
                    if ((index - start + 1) % stride == 0) {
                        samples[(index - start) / stride] = sum / stride
                        sum = 0.0
                    }
                }
            }
        } else
            for (i in 0 until count) {
                var sum = 0.0
                for (k in 0 until stride) sum += pcm[start + i * stride + k]
                samples[i] = sum / stride
            }
        var mean = 0.0
        for (i in 0 until count) mean += samples[i]
        mean /= count
        var energy = 0.0
        for (i in 0 until count) {
            samples[i] -= mean
            energy += samples[i] * samples[i]
        }
        if (energy / count < 1e-8) {
            status = if (energy / count < 1e-10) PitchStatus.SILENCE else PitchStatus.TOO_QUIET
            return null
        }
        val maxLag = min(differences.lastIndex - 1, min((rate / 50).toInt(), count / 2 - 1))
        val minLag = max(2, (rate / 2000).toInt())
        val span = count - maxLag - 1
        var cumulative = 0.0
        differences[0] = 1.0
        // The extra lag distinguishes a genuine boundary minimum from a curve
        // that is still decreasing beyond the allowed fundamental range.
        for (tau in 1..maxLag + 1) {
            var sum = 0.0
            for (i in 0 until span) {
                val delta = samples[i] - samples[i + tau]
                sum += delta * delta
            }
            cumulative += sum
            differences[tau] = if (cumulative > 0) sum * tau / cumulative else 1.0
        }
        var lag = minLag
        while (lag <= maxLag) {
            if (differences[lag] < threshold) {
                while (lag + 1 <= maxLag && differences[lag + 1] < differences[lag]) lag++
                val a = differences[lag - 1]
                val b = differences[lag]
                val c = differences[lag + 1]
                if (b <= a && b <= c) {
                    val delta =
                        if (abs(a - 2 * b + c) > 1e-12)
                            (.5 * (a - c) / (a - 2 * b + c)).coerceIn(-.5, .5)
                        else 0.0
                    val hz = rate / (lag + delta)
                    if (hz.isFinite() && hz in 50.0..2000.0) {
                        status = PitchStatus.DETECTED
                        return Pitch(hz, 1 - b)
                    }
                }
            }
            lag++
        }
        return null
    }
}
