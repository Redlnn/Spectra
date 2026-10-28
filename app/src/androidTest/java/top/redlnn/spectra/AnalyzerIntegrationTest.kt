package top.redlnn.spectra

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.settings.*

@RunWith(AndroidJUnit4::class)
class AnalyzerIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm
        get() = compose.activity.analyzer

    private fun defaults() {
        compose.waitUntil(15000) { !vm.state.value.starting }
        val oldTimestamp = vm.frame.value?.timestampNs
        compose.runOnUiThread {
            vm.update(AnalyzerSettings(input = InputSource.GENERATOR, fps = 10))
            vm.selectInput(InputSource.GENERATOR)
        }
        compose.waitUntil(15000) {
            vm.frame.value != null &&
                vm.frame.value?.timestampNs != oldTimestamp &&
                vm.state.value.running
        }
    }

    @Test
    fun removedFilePreferencesMigrateWithoutDiscardingOtherSettings() {
        val old =
            AnalyzerSettings(
                    input = InputSource.GENERATOR,
                    fftSize = 8192,
                    tilt = 3.0,
                    settingsMiuix = false,
                )
                .toJson()
                .replace("GENERATOR", "DEMO")
                .dropLast(1) + ",\"audible\":true}"
        val migrated = AnalyzerSettings.fromJson(old)
        assertEquals(InputSource.MICROPHONE, migrated.input)
        assertEquals(8192, migrated.fftSize)
        assertEquals(3.0, migrated.tilt, 0.0)
        assertFalse(migrated.settingsMiuix)
        assertFalse(migrated.toJson().contains("audible"))
        assertEquals(
            listOf(InputSource.MICROPHONE, InputSource.GENERATOR),
            InputSource.entries.toList(),
        )
    }

    @Test
    fun pauseFreezesFramesResumeAndBackgroundRebuildHistory() {
        defaults()
        compose.onNodeWithContentDescription("暂停分析").performClick()
        compose.waitUntil { !vm.state.value.running }
        val count = vm.state.value.frames
        val timestamp = vm.frame.value!!.timestampNs
        Thread.sleep(500)
        assertEquals(count, vm.state.value.frames)
        assertEquals(timestamp, vm.frame.value!!.timestampNs)
        compose.onNodeWithContentDescription("开始分析").performClick()
        compose.waitUntil(10000) {
            vm.state.value.running &&
                vm.frame.value != null &&
                vm.frame.value?.timestampNs != timestamp
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        Thread.sleep(200)
        assertFalse(vm.state.value.running)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10000) { vm.state.value.running && vm.frame.value != null }
        val rows = vm.history.snapshot()
        assertTrue(rows.last().timestampNs - rows.first().timestampNs < 2_000_000_000L)
    }

    @Test
    fun settingsSwitchBetweenRealMiuixAndExpressiveComponentsAndChangeFft() {
        defaults()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForSettingsScreen()
        compose.onNodeWithText("界面样式").performClick()
        compose.onNodeWithText("Material 3 Expressive", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertFalse(vm.settings.value.settingsMiuix) }
        compose.onNodeWithText("分析", useUnmergedTree = true).performClick()
        compose.onNodeWithText("FFT 点数").performClick()
        compose.onNodeWithText("16384", useUnmergedTree = true).performClick()
        assertEquals(16384, vm.settings.value.fftSize)
        compose.onNodeWithText("外观", useUnmergedTree = true).performClick()
        compose.onNodeWithText("界面样式").performClick()
        compose.onNodeWithText("Miuix", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(vm.settings.value.settingsMiuix) }
        compose.onNodeWithContentDescription("关闭设置").performClick()
        compose.waitUntil(15000) { vm.frame.value?.fftSize == 16384 }
    }

    @Test
    fun preferencesRoundTripAndThemeCannotChangeNumericalFrame() {
        val settings =
            AnalyzerSettings(
                fftSize = 8192,
                window = WindowType.BLACKMAN,
                tilt = 3.5,
                spectrumPalette = SpectrumPalette.NIGHT,
                colorMap = "Aurora",
                settingsMiuix = false,
                input = InputSource.GENERATOR,
                generatorHz = 125.0,
                generatorAmplitude = .1,
            )
        assertEquals(settings, AnalyzerSettings.fromJson(settings.toJson()))
        defaults()
        compose.onNodeWithContentDescription("暂停分析").performClick()
        compose.waitUntil { !vm.state.value.running }
        val before = vm.frame.value!!
        compose.runOnUiThread {
            vm.update(
                vm.settings.value.copy(
                    spectrumPalette = SpectrumPalette.NIGHT,
                    colorMap = "Aurora",
                    settingsMiuix = false,
                )
            )
        }
        assertSame(before, vm.frame.value)
        compose.runOnUiThread { vm.update(vm.settings.value.copy(tilt = 3.0)) }
        assertNull(vm.frame.value)
        assertTrue(vm.history.snapshot().isEmpty())
    }

    @Test
    fun generatorReportsNyquistClampAndFinds440Hz() {
        compose.runOnUiThread {
            vm.update(
                AnalyzerSettings(input = InputSource.GENERATOR, generatorHz = 440.0, fftSize = 8192)
            )
            vm.selectInput(InputSource.GENERATOR)
        }
        compose.waitUntil(15000) { vm.frame.value?.pitch != null }
        assertEquals(440.0, vm.frame.value!!.pitch!!.hz, 2.0)
        compose.runOnUiThread {
            vm.update(vm.settings.value.copy(sampleRate = 32000, generatorHz = 20000.0))
        }
        compose.waitUntil(15000) { vm.state.value.message.contains("Nyquist") }
        assertEquals(32000, vm.state.value.actualRate)
        assertTrue(vm.state.value.message.contains("14400"))
    }
}
