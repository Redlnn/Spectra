package top.redlnn.spectra.capture

import kotlin.math.*
import top.redlnn.spectra.settings.AnalyzerSettings
import top.redlnn.spectra.settings.WaveShape

class SignalInput(private val settings: AnalyzerSettings) : AudioInput {
    override val sampleRate = settings.sampleRate
    private val effectiveHz = settings.generatorHz.coerceAtMost(sampleRate * .45)
    override val description
        get() =
            "${settings.shape.label} · ${effectiveHz.toInt()} Hz · ${LevelsText.amplitude(settings.generatorAmplitude)} dBFS" +
                if (effectiveHz != settings.generatorHz)
                    " · 请求 ${settings.generatorHz.toInt()} Hz 已受 Nyquist 限制"
                else ""

    override val paced = true
    private var phase = 0.0

    private fun blep(t: Double, dt: Double): Double =
        when {
            t < dt -> {
                val x = t / dt
                x + x - x * x - 1
            }
            t > 1 - dt -> {
                val x = (t - 1) / dt
                x * x + x + x + 1
            }
            else -> 0.0
        }

    private fun blamp(t: Double, dt: Double): Double =
        when {
            t < dt -> (1 - t / dt).pow(3) / 3
            t > 1 - dt -> ((t - 1) / dt + 1).pow(3) / 3
            else -> 0.0
        }

    override fun read(destination: FloatArray, count: Int): Int {
        val step = settings.generatorHz.coerceAtMost(sampleRate * .45) / sampleRate
        for (i in 0 until count) {
            val square =
                (if (phase < .5) 1.0 else -1.0) + blep(phase, step) - blep((phase + .5) % 1, step)
            val value =
                when (settings.shape) {
                    WaveShape.SINE -> sin(2 * PI * phase)
                    WaveShape.SQUARE ->
                        (if (phase < .5) 1.0 else -1.0) + blep(phase, step) -
                            blep((phase + .5) % 1, step)
                    WaveShape.SAW -> 2 * phase - 1 - blep(phase, step)
                    WaveShape.TRIANGLE ->
                        4 * abs(phase - .5) - 1 - 4 * step * blamp(phase, step) +
                            4 * step * blamp((phase + .5) % 1, step)
                }
            destination[i] = (value * settings.generatorAmplitude).toFloat().coerceIn(-1f, 1f)
            phase = (phase + step) % 1
        }
        return count
    }

    override fun close() {}
}

private object LevelsText {
    fun amplitude(a: Double) = "%.1f".format(20 * log10(max(a, 1e-9)))
}
