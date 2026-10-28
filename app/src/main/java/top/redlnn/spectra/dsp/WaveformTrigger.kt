package top.redlnn.spectra.dsp

import kotlin.math.*

/** Rising-edge trigger for the displayed trace. Raw PCM and the STFT remain untouched. */
object WaveformTrigger {
    fun extract(pcm: FloatArray, count: Int, sampleRate: Int, frequency: Double?): FloatArray {
        val length = min(count, pcm.size)
        if (frequency == null || !frequency.isFinite() || frequency <= 0)
            return pcm.copyOfRange(pcm.size - length, pcm.size)
        val latest = pcm.size - length - 2
        val search = ceil(sampleRate / frequency * 2).toInt().coerceAtLeast(4)
        for (i in latest downTo max(0, latest - search)) {
            if (pcm[i] <= 0f && pcm[i + 1] > 0f) {
                val fraction = -pcm[i] / (pcm[i + 1] - pcm[i])
                return FloatArray(length) { j ->
                    pcm[i + j] + fraction * (pcm[i + j + 1] - pcm[i + j])
                }
            }
        }
        return pcm.copyOfRange(pcm.size - length, pcm.size)
    }
}
