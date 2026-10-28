package top.redlnn.spectra.render

import kotlin.math.*

/** Display-only, timestamp-based auto range; repeated redraws do not pump the gain. */
class WaveformScale {
    private var previousTime = Long.MIN_VALUE
    private var range = 1f

    fun gain(samples: FloatArray, timestampNs: Long): Float {
        if (timestampNs == previousTime) return 1f / range
        val peak = samples.maxOfOrNull { abs(it) } ?: 0f
        val target =
            if (peak < 1e-7f) 1f
            else {
                val desired = max(1e-6, peak * 1.2)
                val decade = 10.0.pow(floor(log10(desired)))
                (listOf(1.0, 2.0, 5.0, 10.0).first { it * decade >= desired } * decade).toFloat()
            }
        range =
            if (previousTime == Long.MIN_VALUE || timestampNs <= previousTime || target >= range)
                target
            else {
                val seconds = (timestampNs - previousTime) / 1e9
                max(target, (range * exp(-seconds / .4)).toFloat())
            }
        previousTime = timestampNs
        return 1f / range
    }
}
