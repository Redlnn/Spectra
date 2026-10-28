package top.redlnn.spectra.export

import android.graphics.Bitmap
import top.redlnn.spectra.dsp.AnalysisFrame
import top.redlnn.spectra.settings.AnalyzerSettings

internal data class ExportSnapshot(
    val format: ExportFormat,
    val frame: AnalysisFrame,
    val settings: AnalyzerSettings,
    val audio: Pair<Int, FloatArray>?,
    val image: Bitmap?,
    val time: String,
    val viewport: String,
) : AutoCloseable {
    override fun close() {
        if (image != null && !image.isRecycled) image.recycle()
    }
}

/** Main-thread ownership: a pending picker or an active writer owns exactly one snapshot. */
internal class ExportQueue {
    private var pending: ExportSnapshot? = null
    private var active: ExportSnapshot? = null
    val busy: Boolean
        get() = pending != null || active != null

    fun enqueue(snapshot: ExportSnapshot): Boolean {
        if (busy) {
            snapshot.close()
            return false
        }
        pending = snapshot
        return true
    }

    fun take(format: ExportFormat): ExportSnapshot? {
        val snapshot = pending?.takeIf { it.format == format } ?: return null
        pending = null
        active = snapshot
        return snapshot
    }

    fun cancelPending(format: ExportFormat? = null) {
        val snapshot = pending ?: return
        if (format != null && snapshot.format != format) return
        pending = null
        snapshot.close()
    }

    fun complete(snapshot: ExportSnapshot) {
        if (active === snapshot) active = null
        snapshot.close()
    }
}
