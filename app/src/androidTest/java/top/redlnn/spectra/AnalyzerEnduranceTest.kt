package top.redlnn.spectra

import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.settings.*

/** Opt-in real wall-clock soak: adb instrument -e soakMinutes 30 -e class ... */
@RunWith(AndroidJUnit4::class)
class AnalyzerEnduranceTest {
    @Test
    fun thirtyMinuteSignalAnalysis() {
        val minutes =
            InstrumentationRegistry.getArguments().getString("soakMinutes")?.toIntOrNull() ?: 30
        assumeTrue("Use -e soakMinutes 30 to run endurance validation", minutes > 0)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: AnalyzerViewModel
            val draws = AtomicLong()
            scenario.onActivity { activity ->
                vm = activity.analyzer
                activity.window.decorView.viewTreeObserver.addOnDrawListener {
                    draws.incrementAndGet()
                }
            }
            val startup = SystemClock.elapsedRealtime()
            while (
                vm.state.value.starting && SystemClock.elapsedRealtime() - startup < 15000
            ) Thread.sleep(100)
            scenario.onActivity {
                vm.update(AnalyzerSettings(input = InputSource.GENERATOR))
                vm.selectInput(InputSource.GENERATOR)
            }
            while (vm.frame.value == null && SystemClock.elapsedRealtime() - startup < 30000) Thread
                .sleep(100)
            assertNotNull(vm.frame.value)
            val start = SystemClock.elapsedRealtime()
            var previousTimestamp = vm.frame.value!!.timestampNs
            val report = File(instrumentation.targetContext.filesDir, "endurance.csv")
            report.writeText("elapsed_s,fft_frames,draws,total_pss_kb,dsp_ms,history_rows\n")
            var previousDraws = draws.get()
            var lastReport = start
            while (SystemClock.elapsedRealtime() - start < minutes * 60_000L) {
                Thread.sleep(1000)
                assertTrue("input stopped: ${vm.state.value.message}", vm.state.value.running)
                val frame = vm.frame.value ?: error("frame lost during continuous input")
                assertTrue("PCM timestamp stalled", frame.timestampNs > previousTimestamp)
                assertTrue("Invalid spectral values", frame.spectrumDb.all { it.isFinite() })
                previousTimestamp = frame.timestampNs
                val now = SystemClock.elapsedRealtime()
                if (now - lastReport >= 60_000 || now - start >= minutes * 60_000L) {
                    val memory = Debug.MemoryInfo()
                    Debug.getMemoryInfo(memory)
                    val currentDraws = draws.get()
                    val elapsed = (now - start) / 1000.0
                    val rows = vm.history.snapshot()
                    assertTrue("history is unbounded", rows.size <= 1801)
                    assertTrue("no UI draws", currentDraws > previousDraws)
                    report.appendText(
                        "$elapsed,${vm.state.value.frames},$currentDraws,${memory.totalPss},${vm.state.value.processingMs},${rows.size}\n"
                    )
                    instrumentation.sendStatus(
                        0,
                        Bundle().apply {
                            putString(
                                "stream",
                                "Soak ${elapsed.toInt()}s: ${rows.size} rows, ${memory.totalPss} KB PSS, draws=${currentDraws - previousDraws}\n",
                            )
                        },
                    )
                    previousDraws = currentDraws
                    lastReport = now
                }
            }
            assertTrue(report.length() > 100)
        }
    }
}
