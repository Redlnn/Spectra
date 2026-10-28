package top.redlnn.spectra.render

import kotlin.math.*

data class SpectrumPeak(val hz: Double, val db: Float) {
    companion object {
        fun find(
            values: FloatArray,
            spacingHz: Double,
            minimum: Double,
            maximum: Double,
        ): SpectrumPeak? {
            val first = ceil(minimum / spacingHz).toInt().coerceAtLeast(1)
            val last = floor(maximum / spacingHz).toInt().coerceAtMost(values.lastIndex)
            if (first > last) return null
            val index = (first..last).maxByOrNull { values[it] } ?: return null
            val db = values[index]
            if (!db.isFinite() || db <= -150f) return null
            var offset = 0.0
            if (index > first && index < last) {
                val a = values[index - 1].toDouble()
                val b = db.toDouble()
                val c = values[index + 1].toDouble()
                val denominator = a - 2 * b + c
                if (denominator.isFinite() && denominator < -1e-9)
                    offset = (.5 * (a - c) / denominator).coerceIn(-.5, .5)
            }
            return SpectrumPeak(((index + offset) * spacingHz).coerceIn(minimum, maximum), db)
        }
    }
}
