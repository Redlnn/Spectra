package top.redlnn.spectra

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.settings.*

@RunWith(AndroidJUnit4::class)
class AnalyzerRefreshTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun graphRefreshIsIndependentOfLongFftHop() {
        val vm = compose.activity.analyzer
        compose.waitUntil(15000) { !vm.state.value.starting }
        compose.runOnUiThread {
            vm.update(
                AnalyzerSettings(
                    input = InputSource.GENERATOR,
                    fftSize = 32768,
                    overlap = .5,
                    fps = 60,
                )
            )
            vm.selectInput(InputSource.GENERATOR)
        }
        compose.waitUntil(15000) { vm.frame.value?.fftSize == 32768 && vm.state.value.running }
        val draws = AtomicLong()
        val listener = android.view.ViewTreeObserver.OnDrawListener { draws.incrementAndGet() }
        compose.runOnUiThread {
            compose.activity.window.decorView.viewTreeObserver.addOnDrawListener(listener)
        }
        Thread.sleep(500)
        val start = draws.get()
        val time = System.nanoTime()
        Thread.sleep(2000)
        val fps = (draws.get() - start) * 1e9 / (System.nanoTime() - time)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendStatus(
                0,
                android.os.Bundle().apply {
                    putString(
                        "stream",
                        "60 FPS target: draw callbacks=$fps; measured chart=${vm.state.value.renderFps}; analysis=${vm.state.value.analysisHz} Hz\n",
                    )
                },
            )
        assertTrue("60 FPS target, actual draw callbacks=$fps with 32768/.5 STFT", fps >= 50)
        compose.runOnUiThread { vm.update(vm.settings.value.copy(fps = 30)) }
        compose.waitForIdle()
        Thread.sleep(500)
        val start30 = draws.get()
        val time30 = System.nanoTime()
        Thread.sleep(2000)
        val fps30 = (draws.get() - start30) * 1e9 / (System.nanoTime() - time30)
        fun find(view: android.view.View): top.redlnn.spectra.render.AnalyzerChartView? {
            if (view is top.redlnn.spectra.render.AnalyzerChartView) return view
            if (view is android.view.ViewGroup)
                for (i in 0 until view.childCount) find(view.getChildAt(i))?.let {
                    return it
                }
            return null
        }
        val nativeTarget = compose.runOnIdle {
            find(compose.activity.window.decorView)!!.settings.fps
        }
        compose.runOnUiThread {
            compose.activity.window.decorView.viewTreeObserver.removeOnDrawListener(listener)
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendStatus(
                0,
                android.os.Bundle().apply {
                    putString(
                        "stream",
                        "30 FPS target: draw callbacks=$fps30; measured chart=${vm.state.value.renderFps}; native target=$nativeTarget; stored target=${vm.settings.value.fps}\n",
                    )
                },
            )
        assertEquals(30, nativeTarget)
        assertTrue("30 FPS target, actual draw callbacks=$fps30", fps30 in 27.0..37.0)
    }
}
