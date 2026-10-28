package top.redlnn.spectra.dsp

/** Radix-2 FFT. Scratch buffers, windows and twiddles belong to one processor. */
data class AnalysisFrame(
    val timestampNs: Long,
    val sampleRate: Int,
    val fftSize: Int,
    val amplitudes: FloatArray,
    val spectrumDb: FloatArray,
    val heatDb: FloatArray,
    val peaksDb: FloatArray,
    val waveform: FloatArray,
    val pitch: Pitch?,
    val rmsDb: Float,
    val clipping: Boolean,
    val peakBin: Int,
    val processingKey: String,
    val pitchStatus: PitchStatus =
        if (pitch != null) PitchStatus.DETECTED else PitchStatus.UNSTABLE,
) {
    val spacingHz
        get() = sampleRate.toDouble() / fftSize

    val peakHz
        get() = peakBin * spacingHz

    val peakDb
        get() = spectrumDb[peakBin]
}
