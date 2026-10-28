package top.redlnn.spectra

import org.junit.Assert.*
import org.junit.Test
import top.redlnn.spectra.settings.*

class DefaultSettingsTest {
    @Test
    fun defaultsUsePlainFftAndDarkTheme() {
        val settings = AnalyzerSettings()
        assertEquals(Engine.CLASSIC, settings.engine)
        assertEquals(0, settings.octaveBands)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(8192, settings.fftSize)
        assertEquals(.75, settings.overlap, 0.0)
        assertTrue(settings.showPeakMarker)
        assertTrue(settings.waveAutoScale)
        assertTrue(settings.generatorPlayback)
    }
}
