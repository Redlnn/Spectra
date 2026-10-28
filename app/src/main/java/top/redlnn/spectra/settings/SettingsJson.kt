package top.redlnn.spectra.settings

import org.json.JSONObject

/** Explicit, stable JSON schema shared by saved settings, profiles and CSV exports. */
internal object SettingsJson {
    fun encode(settings: AnalyzerSettings): String =
        JSONObject()
            .apply {
                put("input", settings.input.name)
                put("sampleRate", settings.sampleRate)
                put("fftSize", settings.fftSize)
                put("window", settings.window.name)
                put("overlap", settings.overlap)
                put("average", settings.average)
                put("weighting", settings.weighting.name)
                put("tilt", settings.tilt)
                put("peakHold", settings.peakHold)
                put("octaveBands", settings.octaveBands)
                put("octaveMax", settings.octaveMax)
                put("logAxis", settings.logAxis)
                put("engine", settings.engine.name)
                put("mapping", settings.mapping.name)
                put("view", settings.view.name)
                put("split", settings.split)
                put("themeMode", settings.themeMode.name)
                put("monet", settings.monet)
                put("monetStyle", settings.monetStyle.name)
                put("spectrumPalette", settings.spectrumPalette.name)
                put("settingsMiuix", settings.settingsMiuix)
                put("colorMap", settings.colorMap)
                put("floorDb", settings.floorDb)
                put("ceilingDb", settings.ceilingDb)
                put("duration", settings.duration)
                put("applyTilt", settings.applyTilt)
                put("bilinear", settings.bilinear)
                put("pitch", settings.pitch)
                put("flats", settings.flats)
                put("startOn", settings.startOn)
                put("waveColor", settings.waveColor)
                put("waveThickness", settings.waveThickness)
                put("waveAutoScale", settings.waveAutoScale)
                put("showPeakMarker", settings.showPeakMarker)
                put("levelPositiveDb", settings.levelPositiveDb)
                put("shape", settings.shape.name)
                put("generatorHz", settings.generatorHz)
                put("generatorAmplitude", settings.generatorAmplitude)
                put("generatorPlayback", settings.generatorPlayback)
                put("fps", settings.fps)
                put("pitchDenoise", settings.pitchDenoise)
                put("pitchSensitivity", settings.pitchSensitivity.name)
            }
            .toString()

    fun decode(text: String): AnalyzerSettings {
        val j = JSONObject(text)
        val d = AnalyzerSettings()
        fun i(k: String, v: Int) = j.optInt(k, v)
        fun f(k: String, v: Float) =
            j.optDouble(k, v.toDouble()).toFloat().takeIf { it.isFinite() } ?: v
        fun b(k: String, v: Boolean) = j.optBoolean(k, v)
        fun s(k: String, v: String) = j.optString(k, v)
        fun x(k: String, v: Double) = j.optDouble(k, v).takeIf { it.isFinite() } ?: v
        fun choice(k: String, v: Int, options: List<Int>) = i(k, v).takeIf { it in options } ?: v
        return d.copy(
            input =
                InputSource.entries.firstOrNull { it.name == s("input", d.input.name) }
                    ?: InputSource.MICROPHONE,
            sampleRate = choice("sampleRate", d.sampleRate, SettingsChoices.sampleRates),
            fftSize = choice("fftSize", d.fftSize, SettingsChoices.fftSizes),
            window = readEnum(j, "window", d.window),
            overlap =
                x("overlap", d.overlap).takeIf { it in SettingsChoices.overlaps } ?: d.overlap,
            average = choice("average", d.average, SettingsChoices.averages),
            weighting = readEnum(j, "weighting", d.weighting),
            tilt = x("tilt", d.tilt).coerceIn(-6.0, 6.0),
            peakHold = b("peakHold", d.peakHold),
            octaveBands = choice("octaveBands", d.octaveBands, SettingsChoices.octaveBands),
            octaveMax = b("octaveMax", d.octaveMax),
            logAxis = b("logAxis", d.logAxis),
            engine = readEnum(j, "engine", d.engine),
            mapping = readEnum(j, "mapping", d.mapping),
            view = readEnum(j, "view", d.view),
            split = f("split", d.split).coerceIn(.2f, .8f),
            themeMode =
                if (j.has("themeMode")) readEnum(j, "themeMode", d.themeMode)
                else if (b("whiteBackground", false)) ThemeMode.LIGHT
                else if (j.has("whiteBackground") || b("settingsDark", false)) ThemeMode.DARK
                else d.themeMode,
            monet = b("monet", d.monet),
            monetStyle = readEnum(j, "monetStyle", d.monetStyle),
            settingsMiuix = b("settingsMiuix", d.settingsMiuix),
            spectrumPalette =
                if (j.has("spectrumPalette")) readEnum(j, "spectrumPalette", d.spectrumPalette)
                else if (b("night", false)) SpectrumPalette.NIGHT
                else
                    when (s("spectrumColor", "")) {
                        "Green" -> SpectrumPalette.GREEN
                        "Yellow" -> SpectrumPalette.YELLOW
                        else -> SpectrumPalette.CLASSIC
                    },
            colorMap =
                s("colorMap", d.colorMap).takeIf { it in listOf("Jet", "Aurora", "Gray") }
                    ?: d.colorMap,
            floorDb = f("floorDb", d.floorDb).coerceIn(-150f, -60f),
            ceilingDb = f("ceilingDb", d.ceilingDb).coerceIn(-50f, 0f),
            duration = i("duration", d.duration).coerceIn(4, 15),
            applyTilt = b("applyTilt", d.applyTilt),
            bilinear = b("bilinear", d.bilinear),
            pitch = b("pitch", d.pitch),
            flats = b("flats", d.flats),
            startOn = b("startOn", d.startOn),
            waveColor =
                s("waveColor", d.waveColor).takeIf { it in listOf("Green", "Yellow", "Invert") }
                    ?: d.waveColor,
            waveThickness = f("waveThickness", d.waveThickness).coerceIn(1f, 5f),
            waveAutoScale = b("waveAutoScale", d.waveAutoScale),
            showPeakMarker = b("showPeakMarker", d.showPeakMarker),
            levelPositiveDb = b("levelPositiveDb", d.levelPositiveDb),
            shape = readEnum(j, "shape", d.shape),
            generatorHz = x("generatorHz", d.generatorHz).coerceIn(20.0, 20000.0),
            generatorAmplitude = x("generatorAmplitude", d.generatorAmplitude).coerceIn(.001, 1.0),
            generatorPlayback = b("generatorPlayback", d.generatorPlayback),
            fps =
                i("fps", d.fps).coerceIn(10, 60).let { rate ->
                    RefreshRates.minBy { kotlin.math.abs(it - rate) }
                },
            pitchDenoise = b("pitchDenoise", d.pitchDenoise),
            pitchSensitivity = readEnum(j, "pitchSensitivity", d.pitchSensitivity),
        )
    }

    private inline fun <reified T : Enum<T>> readEnum(
        json: JSONObject,
        key: String,
        fallback: T,
    ): T = enumValues<T>().firstOrNull { it.name == json.optString(key, fallback.name) } ?: fallback
}
