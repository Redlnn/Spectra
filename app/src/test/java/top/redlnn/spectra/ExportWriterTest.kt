package top.redlnn.spectra

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import top.redlnn.spectra.dsp.*
import top.redlnn.spectra.export.ExportWriter
import top.redlnn.spectra.settings.*

class ExportWriterTest {
    @Test
    fun wavHeaderAndPcmAreCorrectAndDoNotOverflowFullScale() {
        val output = ByteArrayOutputStream()
        ExportWriter.audio(output, 44100, floatArrayOf(-1f, -.5f, 0f, .5f, 1f), true)
        val data = output.toByteArray()
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(data, 0, 4))
        assertEquals(46, b.getInt(4))
        assertEquals("WAVE", String(data, 8, 4))
        assertEquals(1, b.getShort(20).toInt())
        assertEquals(1, b.getShort(22).toInt())
        assertEquals(44100, b.getInt(24))
        assertEquals(88200, b.getInt(28))
        assertEquals(16, b.getShort(34).toInt())
        assertEquals(10, b.getInt(40))
        assertEquals(-32768, b.getShort(44).toInt())
        assertEquals(-16384, b.getShort(46).toInt())
        assertEquals(0, b.getShort(48).toInt())
        assertEquals(16384, b.getShort(50).toInt())
        assertEquals(32767, b.getShort(52).toInt())
    }

    @Test
    fun rawPcmHasNoHeaderAndLargeChunksPreserveSampleCount() {
        val output = ByteArrayOutputStream()
        val samples = FloatArray(10003) { .25f }
        ExportWriter.audio(output, 48000, samples, false)
        assertEquals(samples.size * 2, output.size())
        val b = ByteBuffer.wrap(output.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        while (b.remaining() > 0) assertEquals(8192, b.short.toInt())
    }

    @Test
    fun csvIncludesNativeRateAndMetadataAndIsIndependentOfDeviceLocale() {
        val locale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val processor = StftProcessor(AnalyzerSettings(fftSize = 1024), 44100)
            val frame = processor.process(FloatArray(processor.bufferSize), 1_000_000_000)
            val output = ByteArrayOutputStream()
            ExportWriter.csv(
                output,
                frame,
                "{\"window\":\"HANN\"}",
                "2026-10-12T15:00:00Z",
                "f_min_hz=62.5; f_max_hz=8000; visible_history_s=3",
            )
            val text = output.toString("UTF-8")
            assertTrue(text.contains("fs_hz=44100; N=1024"))
            assertTrue(text.contains("amplitude_squared is not PSD"))
            assertTrue(
                text.contains("# viewport: f_min_hz=62.5; f_max_hz=8000; visible_history_s=3")
            )
            val lines = text.lines().filter { it.isNotEmpty() && !it.startsWith("#") }
            assertEquals(514, lines.size)
            assertEquals(5, lines[2].split(',').size)
            assertEquals(43.066406, lines[2].split(',')[0].toDouble(), .000001)
        } finally {
            Locale.setDefault(locale)
        }
    }
}
