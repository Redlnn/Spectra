package top.redlnn.spectra.render

import java.util.ArrayDeque
import kotlin.math.*
import top.redlnn.spectra.dsp.*
import top.redlnn.spectra.settings.Mapping

data class HistoryRow(val timestampNs: Long, val values: FloatArray)

class SpectrogramHistory {
    private val rows = ArrayDeque<HistoryRow>()
    val width = 512
    var maxFrequency = 24000.0
        private set

    var version = 0L
        private set

    private var bucketStartNs = Long.MIN_VALUE

    @Synchronized
    fun clear() {
        rows.clear()
        bucketStartNs = Long.MIN_VALUE
        version++
    }

    @Synchronized
    fun append(frame: AnalysisFrame, mapping: Mapping) {
        // At most 120 rows/s. Preserve short transients by taking per-frequency
        // maxima within each display bucket, stamped at the latest contributing frame.
        maxFrequency = frame.sampleRate / 2.0
        val values =
            FloatArray(width) { x ->
                val hz = 10.0 * (maxFrequency / 10).pow(x.toDouble() / (width - 1))
                if (mapping == Mapping.BAND_POWER) {
                    val left =
                        10.0 * (maxFrequency / 10).pow((x - .5) / (width - 1)) / frame.spacingHz
                    val right =
                        10.0 * (maxFrequency / 10).pow((x + .5) / (width - 1)) / frame.spacingHz
                    Levels.pixelPower(frame.heatDb, left, right)
                } else Levels.sample(frame.heatDb, hz / frame.spacingHz, mapping)
            }
        if (rows.isNotEmpty() && frame.timestampNs - bucketStartNs < 8_333_333L) {
            val previous = rows.removeLast()
            for (x in values.indices) values[x] = max(values[x], previous.values[x])
        } else bucketStartNs = frame.timestampNs
        rows.addLast(HistoryRow(frame.timestampNs, values))
        while (
            rows.isNotEmpty() && frame.timestampNs - rows.first.timestampNs > 15_000_000_000L
        ) rows.removeFirst()
        version++
    }

    @Synchronized fun snapshot(): List<HistoryRow> = rows.toList()

    fun value(row: HistoryRow, frequency: Double): Float {
        val index =
            ((ln(frequency.coerceAtLeast(10.0) / 10) / ln(maxFrequency / 10)) * (width - 1))
                .roundToInt()
                .coerceIn(0, width - 1)
        return row.values[index]
    }
}
