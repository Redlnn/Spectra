package top.redlnn.spectra.dsp

import kotlin.math.*
import top.redlnn.spectra.settings.WindowType

/** Radix-2 FFT. Scratch buffers, windows and twiddles belong to one processor. */
class Fft(private val size: Int, windowType: WindowType) {
    private val real = DoubleArray(size)
    private val imaginary = DoubleArray(size)
    private val cosines = DoubleArray(size / 2) { cos(-2 * PI * it / size) }
    private val sines = DoubleArray(size / 2) { sin(-2 * PI * it / size) }
    private val window =
        DoubleArray(size) { n ->
            val angle = 2 * PI * n / (size - 1)
            when (windowType) {
                WindowType.HANN -> .5 - .5 * cos(angle)
                WindowType.HAMMING -> .54 - .46 * cos(angle)
                WindowType.BLACKMAN -> .42 - .5 * cos(angle) + .08 * cos(2 * angle)
                WindowType.NONE -> 1.0
            }
        }
    private val coherentGain = window.sum()

    init {
        require(size >= 2 && size and (size - 1) == 0)
    }

    fun amplitudes(input: FloatArray, offset: Int = 0): FloatArray {
        for (i in 0 until size) {
            real[i] = input[offset + i] * window[i]
            imaginary[i] = 0.0
        }
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val t = real[i]
                real[i] = real[j]
                real[j] = t
            }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            for (start in 0 until size step length) for (k in 0 until half) {
                val a = start + k
                val b = a + half
                val c = cosines[k * step]
                val s = sines[k * step]
                val r = real[b] * c - imaginary[b] * s
                val im = real[b] * s + imaginary[b] * c
                real[b] = real[a] - r
                imaginary[b] = imaginary[a] - im
                real[a] += r
                imaginary[a] += im
            }
            length *= 2
        }
        return FloatArray(size / 2 + 1) { k ->
            (hypot(real[k], imaginary[k]) * (if (k == 0 || k == size / 2) 1 else 2) / coherentGain)
                .toFloat()
        }
    }
}
