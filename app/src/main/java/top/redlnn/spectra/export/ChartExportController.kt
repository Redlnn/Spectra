package top.redlnn.spectra.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.redlnn.spectra.dsp.AnalysisFrame
import top.redlnn.spectra.render.AnalyzerChartView
import top.redlnn.spectra.settings.AnalyzerSettings

/** Owns SAF launchers and snapshot lifetime, independently of the home screen's layout. */
internal class ChartExportController(private val activity: ComponentActivity) :
    DefaultLifecycleObserver {
    private val queue = ExportQueue()
    private val launchers =
        ExportFormat.entries.associateWith { format ->
            activity.registerForActivityResult(
                ActivityResultContracts.CreateDocument(format.mimeType)
            ) { uri ->
                onDocument(format, uri)
            }
        }

    init {
        activity.lifecycle.addObserver(this)
    }

    fun launch(
        format: ExportFormat,
        chart: AnalyzerChartView?,
        settings: AnalyzerSettings,
        pcm: () -> Pair<Int, FloatArray>?,
    ) {
        if (queue.busy) {
            notify("请等待当前导出完成")
            return
        }
        val frame = chart?.frame
        if (frame == null || frame.processingKey != settings.processingKey().toString()) {
            notify("请等待分析画面更新")
            return
        }
        val visibleSettings = chart.settings
        val now = Instant.now()
        val snapshot =
            ExportSnapshot(
                format,
                frame,
                visibleSettings,
                if (format == ExportFormat.WAV || format == ExportFormat.PCM) pcm() else null,
                if (format == ExportFormat.PNG) capture(chart, frame, visibleSettings, now)
                else null,
                now.toString(),
                chart.viewportMetadata(),
            )
        if (!queue.enqueue(snapshot)) return
        val prefix = if (format == ExportFormat.PCM) "${frame.sampleRate}Hz_mono_s16le_" else ""
        try {
            launchers
                .getValue(format)
                .launch("Spectra_${prefix}${now.toEpochMilli()}.${format.extension}")
        } catch (error: Exception) {
            queue.cancelPending(format)
            notify("无法打开导出位置：${error.message}")
        }
    }

    private fun onDocument(format: ExportFormat, uri: Uri?) {
        if (uri == null) {
            queue.cancelPending(format)
            return
        }
        val snapshot = queue.take(format) ?: return
        activity.lifecycleScope.launch {
            val result =
                try {
                    withContext(Dispatchers.IO) { write(uri, snapshot) }
                    Result.success(Unit)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                } finally {
                    queue.complete(snapshot)
                }
            notify(
                if (result.isSuccess) "已导出 ${format.extension.uppercase()}"
                else "导出失败：${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG,
            )
        }
    }

    private fun write(uri: Uri, snapshot: ExportSnapshot) {
        activity.contentResolver.openOutputStream(uri)?.use { output ->
            when (snapshot.format) {
                ExportFormat.PNG ->
                    check(snapshot.image?.compress(Bitmap.CompressFormat.PNG, 100, output) == true)
                ExportFormat.CSV ->
                    ExportWriter.csv(
                        output,
                        snapshot.frame,
                        snapshot.settings.toJson(),
                        snapshot.time,
                        snapshot.viewport,
                    )
                ExportFormat.WAV,
                ExportFormat.PCM -> {
                    val (rate, samples) = snapshot.audio ?: error("暂无 PCM 缓存")
                    ExportWriter.audio(output, rate, samples, snapshot.format == ExportFormat.WAV)
                }
            }
        } ?: error("无法打开所选文件")
    }

    private fun capture(
        view: AnalyzerChartView,
        frame: AnalysisFrame,
        settings: AnalyzerSettings,
        now: Instant,
    ): Bitmap =
        Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height + 170, Bitmap.Config.ARGB_8888)
            .also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.rgb(12, 16, 24))
                canvas.save()
                canvas.translate(0f, 70f)
                view.draw(canvas)
                canvas.restore()
                val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        textSize = 26f
                    }
                canvas.drawText("Spectra · $now", 18f, 42f, paint)
                paint.textSize = 21f
                paint.color = android.graphics.Color.LTGRAY
                canvas.drawText(
                    "fs=${frame.sampleRate} Hz  N=${frame.fftSize}  ${settings.window}  Δf=${"%.3f".format(frame.spacingHz)} Hz",
                    18f,
                    (view.height + 105).toFloat(),
                    paint,
                )
                canvas.drawText(
                    "${if(settings.logAxis) "Log" else "Linear"}  ${settings.weighting}  Tilt=${settings.tilt}  ${settings.floorDb}..${settings.ceilingDb} dBFS  ${settings.duration}s",
                    18f,
                    (view.height + 140).toFloat(),
                    paint,
                )
            }

    private fun notify(message: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(activity, message, length).show()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        queue.cancelPending()
    }
}
