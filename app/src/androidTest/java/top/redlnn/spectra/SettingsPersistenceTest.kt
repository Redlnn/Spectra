package top.redlnn.spectra

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.settings.*

@RunWith(AndroidJUnit4::class)
class SettingsPersistenceTest {
    @Test
    fun invalidNumbersCannotCreateAnInvalidProcessor() {
        val json =
            JSONObject(AnalyzerSettings().toJson())
                .put("fftSize", 0)
                .put("sampleRate", -1)
                .put("average", 0)
                .put("overlap", 1.0)
                .put("generatorHz", "NaN")
                .put("waveThickness", "Infinity")
                .put("floorDb", 900)
                .put("ceilingDb", -900)
                .put("duration", 0)
        val restored = AnalyzerSettings.fromJson(json.toString())
        assertEquals(8192, restored.fftSize)
        assertEquals(48000, restored.sampleRate)
        assertEquals(1, restored.average)
        assertEquals(.75, restored.overlap, 0.0)
        assertEquals(440.0, restored.generatorHz, 0.0)
        assertTrue(restored.floorDb < restored.ceilingDb)
        val processor = top.redlnn.spectra.dsp.StftProcessor(restored, restored.sampleRate)
        assertTrue(
            processor.process(FloatArray(processor.bufferSize), 1_000_000_000L).amplitudes.all {
                it.isFinite()
            }
        )
    }

    @Test
    fun allPersistedFieldsRoundTrip() {
        val settings =
            AnalyzerSettings(
                input = InputSource.GENERATOR,
                sampleRate = 96000,
                fftSize = 16384,
                window = WindowType.BLACKMAN,
                overlap = .875,
                average = 8,
                weighting = Weighting.C,
                tilt = -3.5,
                peakHold = false,
                octaveBands = 12,
                octaveMax = false,
                logAxis = false,
                engine = Engine.ENHANCED,
                mapping = Mapping.BAND_POWER,
                view = ViewMode.WAVE_SPECTRUM,
                split = .65f,
                themeMode = ThemeMode.LIGHT,
                monet = true,
                monetStyle = MonetStyle.VIBRANT,
                spectrumPalette = SpectrumPalette.NIGHT,
                settingsMiuix = false,
                colorMap = "Aurora",
                floorDb = -135f,
                ceilingDb = -20f,
                duration = 12,
                applyTilt = false,
                bilinear = false,
                pitch = false,
                flats = false,
                startOn = false,
                waveColor = "Yellow",
                waveThickness = 4f,
                waveAutoScale = false,
                showPeakMarker = false,
                levelPositiveDb = true,
                positiveDb = true,
                shape = WaveShape.SAW,
                generatorHz = 125.0,
                generatorAmplitude = .1,
                generatorPlayback = false,
                fps = 50,
                pitchDenoise = false,
                pitchSensitivity = PitchSensitivity.STRICT,
            )
        assertEquals(settings, AnalyzerSettings.fromJson(settings.toJson()))
    }

    @Test
    fun unknownEnumOnlyResetsThatField() {
        val saved =
            AnalyzerSettings(
                fftSize = 16384,
                sampleRate = 96000,
                tilt = 3.5,
                generatorPlayback = false,
            )
        val json = JSONObject(saved.toJson()).put("themeMode", "FUTURE_THEME")
        val restored = AnalyzerSettings.fromJson(json.toString())
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertEquals(saved.fftSize, restored.fftSize)
        assertEquals(saved.sampleRate, restored.sampleRate)
        assertEquals(saved.tilt, restored.tilt, 0.0)
        assertFalse(restored.generatorPlayback)
    }
}
