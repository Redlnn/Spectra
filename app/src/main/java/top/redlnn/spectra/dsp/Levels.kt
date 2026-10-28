package top.redlnn.spectra.dsp

import kotlin.math.*
import top.redlnn.spectra.settings.Mapping
import top.redlnn.spectra.settings.Weighting

/** Radix-2 FFT. Scratch buffers, windows and twiddles belong to one processor. */
object Levels {
    fun db(amplitude: Double) = (20 * log10(max(amplitude, 1e-9))).toFloat()

    fun powerDb(power: Double) = (10 * log10(max(power, 1e-18))).toFloat()

    fun tilt(frequency: Double, amount: Double) =
        if (frequency > 0) amount * log2(frequency / 1000) else 0.0

    fun weighting(f: Double, type: Weighting): Double {
        if (type == Weighting.FLAT) return 0.0
        if (f <= 0) return -180.0
        val f2 = f * f
        val high = 12194.0.pow(2)
        val low = 20.6.pow(2)
        return when (type) {
            Weighting.A ->
                20 *
                    log10(
                        high * f2 * f2 /
                            ((f2 + low) *
                                sqrt((f2 + 107.7.pow(2)) * (f2 + 737.9.pow(2))) *
                                (f2 + high))
                    ) + 2.0
            Weighting.C -> 20 * log10(high * f2 / ((f2 + low) * (f2 + high))) + .06
            Weighting.FLAT -> 0.0
        }
    }

    fun sample(values: FloatArray, bin: Double, mapping: Mapping): Float {
        if (values.size < 2) return -180f
        // Below the first positive bin we show that bin, without inventing spectral detail.
        val k = bin.coerceIn(1.0, values.lastIndex.toDouble())
        if (mapping == Mapping.NEAREST) return values[k.roundToInt()]
        val lo = k.toInt()
        val hi = (lo + 1).coerceAtMost(values.lastIndex)
        val fraction = k - lo
        return powerDb(
            10.0.pow(values[lo] / 10.0) * (1 - fraction) + 10.0.pow(values[hi] / 10.0) * fraction
        )
    }

    fun band(
        values: FloatArray,
        left: Double,
        right: Double,
        useMax: Boolean,
        fillEmpty: Boolean = true,
    ): Float {
        val first = max(1, ceil(left).toInt())
        val last = min(values.lastIndex, floor(right).toInt())
        if (last < first)
            return if (fillEmpty) sample(values, (left + right) / 2, Mapping.NEAREST) else -180f
        if (useMax) return (first..last).maxOf { values[it] }
        var p = 0.0
        for (i in first..last) p += 10.0.pow(values[i] / 10.0)
        return powerDb(p / (last - first + 1))
    }

    /** Display aggregation is a weighted bin-power mean, not PSD or total band energy. */
    fun pixelPower(values: FloatArray, left: Double, right: Double): Float {
        if (right - left < 1) return sample(values, (left + right) / 2, Mapping.LINEAR_POWER)
        val first = max(1, floor(left - .5).toInt())
        val last = min(values.lastIndex, ceil(right + .5).toInt())
        var power = 0.0
        var weight = 0.0
        for (k in first..last) {
            val overlap = (min(right, k + .5) - max(left, k - .5)).coerceAtLeast(0.0)
            power += 10.0.pow(values[k] / 10.0) * overlap
            weight += overlap
        }
        return if (weight > 0) powerDb(power / weight)
        else sample(values, (left + right) / 2, Mapping.LINEAR_POWER)
    }
}
