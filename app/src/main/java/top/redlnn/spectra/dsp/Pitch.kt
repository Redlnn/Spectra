package top.redlnn.spectra.dsp

import kotlin.math.*

/** Radix-2 FFT. Scratch buffers, windows and twiddles belong to one processor. */
data class Pitch(val hz: Double, val confidence: Double) {
    val isValid
        get() = hz.isFinite() && hz in 50.0..2000.0 && confidence.isFinite()

    val midi
        get() = 69 + 12 * log2(hz / 440)

    val cents
        get() = if (isValid) 100 * (midi - midi.roundToInt()) else 0.0

    fun note(flats: Boolean): String {
        if (!isValid) return "—"
        val names =
            if (flats) arrayOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
            else arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
        val m = midi.roundToInt()
        return names[(m % 12 + 12) % 12] + (m / 12 - 1)
    }
}

/** YIN difference + cumulative mean normalization; independent of spectrum transforms. */
enum class PitchStatus(val hint: String) {
    WAITING("等待信号"),
    SILENCE("未检测到声音"),
    TOO_QUIET("信号较弱"),
    UNSTABLE("未检测到稳定单音"),
    DETECTED(""),
}
