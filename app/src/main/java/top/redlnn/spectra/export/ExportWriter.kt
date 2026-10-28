package top.redlnn.spectra.export

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.*
import top.redlnn.spectra.dsp.AnalysisFrame

object ExportWriter {
    fun audio(output: OutputStream, rate: Int, samples: FloatArray, wav: Boolean) {
        require(rate > 0)
        val size = samples.size * 2
        if (wav) {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header
                .put("RIFF".toByteArray())
                .putInt(size + 36)
                .put("WAVEfmt ".toByteArray())
                .putInt(16)
                .putShort(1)
                .putShort(1)
                .putInt(rate)
                .putInt(rate * 2)
                .putShort(2)
                .putShort(16)
                .put("data".toByteArray())
                .putInt(size)
            output.write(header.array())
        }
        val chunk = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            val value = if (sample.isFinite()) sample.coerceIn(-1f, .999969f) else 0f
            chunk.putShort((value * 32768).roundToInt().toShort())
            if (!chunk.hasRemaining()) {
                output.write(chunk.array())
                chunk.clear()
            }
        }
        if (chunk.position() > 0) output.write(chunk.array(), 0, chunk.position())
    }

    fun csv(
        output: OutputStream,
        frame: AnalysisFrame,
        settingsJson: String,
        capturedAt: String,
        viewport: String = "",
    ) {
        output.writer(Charsets.UTF_8).use { writer ->
            writer.appendLine(
                "# Spectra 1.0; captured=$capturedAt; center_timestamp_ns=${frame.timestampNs}; fs_hz=${frame.sampleRate}; N=${frame.fftSize}"
            )
            writer.appendLine("# $settingsJson")
            if (viewport.isNotEmpty()) writer.appendLine("# viewport: $viewport")
            writer.appendLine(
                "# amplitude=single-sided coherent-gain-corrected sine peak; unit=dBFS; average=linear power; amplitude_squared is not PSD; octave display is derived from these bins"
            )
            writer.appendLine(
                "frequency_hz,raw_amplitude_fs,raw_amplitude_squared_fs2,display_dbfs,peak_hold_dbfs"
            )
            for (k in frame.amplitudes.indices) writer.appendLine(
                String.format(
                    Locale.US,
                    "%.6f,%.9g,%.9g,%.6f,%.6f",
                    k * frame.spacingHz,
                    frame.amplitudes[k],
                    frame.amplitudes[k].toDouble().pow(2),
                    frame.spectrumDb[k],
                    frame.peaksDb[k],
                )
            )
        }
    }
}
