package top.redlnn.spectra.settings

data class AnalyzerSettings(
    val input: InputSource = InputSource.MICROPHONE,
    val sampleRate: Int = 48000,
    val fftSize: Int = 8192,
    val window: WindowType = WindowType.HANN,
    val overlap: Double = .75,
    val average: Int = 1,
    val weighting: Weighting = Weighting.FLAT,
    val tilt: Double = 0.0,
    val peakHold: Boolean = true,
    val octaveBands: Int = 0,
    val octaveMax: Boolean = true,
    val logAxis: Boolean = true,
    val engine: Engine = Engine.CLASSIC,
    val mapping: Mapping = Mapping.LINEAR_POWER,
    val view: ViewMode = ViewMode.SPECTRUM_SPECTROGRAM,
    val split: Float = .48f,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val monet: Boolean = false,
    val monetStyle: MonetStyle = MonetStyle.EXPRESSIVE,
    val spectrumPalette: SpectrumPalette = SpectrumPalette.CLASSIC,
    val settingsMiuix: Boolean = true,
    val colorMap: String = "Jet",
    val floorDb: Float = -120f,
    val ceilingDb: Float = 0f,
    val duration: Int = 6,
    val applyTilt: Boolean = true,
    val bilinear: Boolean = true,
    val pitch: Boolean = true,
    val flats: Boolean = true,
    val startOn: Boolean = true,
    val waveColor: String = "Green",
    val waveThickness: Float = 1.5f,
    val waveAutoScale: Boolean = true,
    val showPeakMarker: Boolean = true,
    val levelPositiveDb: Boolean = false,
    val shape: WaveShape = WaveShape.SINE,
    val generatorHz: Double = 440.0,
    val generatorAmplitude: Double = .25,
    val generatorPlayback: Boolean = true,
    val fps: Int = 30,
    val pitchDenoise: Boolean = true,
    val pitchSensitivity: PitchSensitivity = PitchSensitivity.BALANCED,
) {
    val night: Boolean
        get() = spectrumPalette == SpectrumPalette.NIGHT

    val analysisSize: Int
        get() =
            when (engine) {
                Engine.CLASSIC -> fftSize
                Engine.ENHANCED -> fftSize.coerceAtLeast(4096)
                Engine.MULTI -> 8192
            }

    fun processingKey() =
        listOf(
            input,
            sampleRate,
            analysisSize,
            window,
            overlap,
            average,
            weighting,
            tilt,
            peakHold,
            applyTilt,
            engine,
            pitchDenoise,
            pitchSensitivity,
            if (input == InputSource.GENERATOR)
                listOf(shape, generatorHz, generatorAmplitude, generatorPlayback)
            else emptyList<Any>(),
        )

    fun toJson(): String = SettingsJson.encode(this)

    companion object {
        fun fromJson(text: String): AnalyzerSettings = SettingsJson.decode(text)
    }
}
