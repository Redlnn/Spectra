package top.redlnn.spectra.dsp

import kotlin.math.*

/** Only the pitch branch is filtered. Spectrum, exported PCM and RMS remain untouched. */
internal class PitchFilter(rate: Int) {
    private class Biquad(rate: Int, hz: Double, q: Double, highPass: Boolean) {
        private val omega = 2 * PI * hz / rate
        private val alpha = sin(omega) / (2 * q)
        private val a0 = 1 + alpha
        private val b0 = (if (highPass) 1 + cos(omega) else 1 - cos(omega)) / 2 / a0
        private val b1 = (if (highPass) -(1 + cos(omega)) else 1 - cos(omega)) / a0
        private val b2 = b0
        private val a1 = -2 * cos(omega) / a0
        private val a2 = (1 - alpha) / a0
        private var z1 = 0.0
        private var z2 = 0.0

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    private val high1 = Biquad(rate, 40.0, .5411961, true)
    private val high2 = Biquad(rate, 40.0, 1.3065630, true)
    private val low1 = Biquad(rate, 2400.0, .5411961, false)
    private val low2 = Biquad(rate, 2400.0, 1.3065630, false)

    fun reset() {
        high1.reset()
        high2.reset()
        low1.reset()
        low2.reset()
    }

    fun process(value: Float) =
        low2.process(low1.process(high2.process(high1.process(value.toDouble()))))
}
