package top.redlnn.spectra

import org.junit.Assert.*
import org.junit.Test
import top.redlnn.spectra.render.FramePacer

class FramePacerTest {
    @Test
    fun normalDisplayJitterDoesNotHalveThirtyOrSixtyFps() {
        for (target in listOf(10, 20, 30, 40, 50, 60)) {
            val pacer = FramePacer()
            var rendered = 0
            for (i in 0 until 600) {
                val now =
                    (i * 1_000_000_000.0 / 59.94).toLong() + if (i % 3 == 0) 200_000 else -100_000
                if (pacer.due(now, target)) rendered++
            }
            assertTrue(
                "target=$target rendered=$rendered",
                rendered in target * 10 - 2..target * 10 + 2,
            )
        }
    }
}
