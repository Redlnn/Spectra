package top.redlnn.spectra

import org.junit.Assert.*
import org.junit.Test
import top.redlnn.spectra.dsp.StftProcessor
import top.redlnn.spectra.export.*
import top.redlnn.spectra.settings.AnalyzerSettings

class ExportQueueTest {
    private fun snapshot(format: ExportFormat = ExportFormat.CSV): ExportSnapshot {
        val settings = AnalyzerSettings()
        val processor = StftProcessor(settings, 48000)
        val frame = processor.process(FloatArray(processor.bufferSize), 1_000_000_000L)
        return ExportSnapshot(format, frame, settings, null, null, "now", "")
    }

    @Test
    fun rejectsAnotherRequestWhilePickerOrWriterOwnsSnapshot() {
        val queue = ExportQueue()
        val first = snapshot()
        assertTrue(queue.enqueue(first))
        assertFalse(queue.enqueue(snapshot()))
        assertSame(first, queue.take(ExportFormat.CSV))
        assertTrue(queue.busy)
        assertFalse(queue.enqueue(snapshot()))
        queue.complete(first)
        assertFalse(queue.busy)
        assertTrue(queue.enqueue(snapshot()))
    }

    @Test
    fun cancellationDoesNotClearAnActiveWrite() {
        val queue = ExportQueue()
        val first = snapshot()
        queue.enqueue(first)
        queue.take(ExportFormat.CSV)
        queue.cancelPending()
        assertTrue(queue.busy)
        queue.complete(first)
        assertFalse(queue.busy)
    }

    @Test
    fun mismatchedPickerResultCannotConsumePendingSnapshot() {
        val queue = ExportQueue()
        val first = snapshot()
        queue.enqueue(first)
        assertNull(queue.take(ExportFormat.PNG))
        queue.cancelPending(ExportFormat.PNG)
        assertTrue(queue.busy)
        assertSame(first, queue.take(ExportFormat.CSV))
        queue.complete(first)
    }
}
