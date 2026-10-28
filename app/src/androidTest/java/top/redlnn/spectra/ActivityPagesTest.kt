package top.redlnn.spectra

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.settings.*

@RunWith(AndroidJUnit4::class)
class ActivityPagesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm
        get() = compose.activity.analyzer

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.waitForIdleSync()
        Thread.sleep(
            500
        ) // Let the system's cross-activity transition finish before reading the screen.
        val bitmap = instrument.uiAutomation.takeScreenshot()
        File(instrument.targetContext.getExternalFilesDir(null), "v140-$name.png")
            .outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun tone() {
        compose.waitUntil(15000) { !vm.state.value.starting }
        compose.runOnUiThread {
            vm.update(AnalyzerSettings(input = InputSource.GENERATOR, fps = 10))
            vm.selectInput(InputSource.GENERATOR)
        }
        compose.waitUntil(10000) { vm.frame.value?.pitch != null && vm.state.value.running }
    }

    private fun resumed(): Class<*>? {
        var result: Class<*>? = null
        compose.runOnUiThread {
            result =
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .firstOrNull()
                    ?.javaClass
        }
        return result
    }

    @Test
    fun settingsAndAboutUseSeparateActivitiesAndOpenSourceLinks() {
        tone()
        compose.runOnUiThread { vm.toggle() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForSettingsScreen()
        compose.waitUntil(5000) { resumed() == SettingsActivity::class.java }
        screenshot("settings-miuix")
        assertFalse(vm.state.value.running)
        compose.onNodeWithText("分析", useUnmergedTree = true).performClick()
        compose.onNodeWithText("FFT 点数").performScrollTo().performClick()
        compose.onNodeWithText("16384", useUnmergedTree = true).performClick()
        assertEquals(16384, vm.settings.value.fftSize)
        compose.onNodeWithText("工具", useUnmergedTree = true).performClick()
        compose.onNodeWithText("关于声镜").performScrollTo().performClick()
        compose.waitUntil(5000) { resumed() == AboutActivity::class.java }
        compose.onNodeWithText("关于").assertDoesNotExist()
        compose
            .onNodeWithText("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .assertExists()
        screenshot("about-dark")
        compose.runOnUiThread { vm.update(vm.settings.value.copy(themeMode = ThemeMode.LIGHT)) }
        screenshot("about-light")
        compose.runOnUiThread {
            val activity =
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<AboutActivity>()
                    .single()
            val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertTrue(bars.isAppearanceLightStatusBars)
            assertTrue(bars.isAppearanceLightNavigationBars)
            vm.update(vm.settings.value.copy(themeMode = ThemeMode.DARK, monet = true))
        }
        screenshot("about-monet")
        compose.runOnUiThread { vm.update(vm.settings.value.copy(monet = false)) }
        compose.onNodeWithText("开放源代码许可").performScrollTo().assertExists()
        val instrument = InstrumentationRegistry.getInstrumentation()
        val opened = mutableListOf<String>()
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action != Intent.ACTION_VIEW) return null
                    opened += intent.dataString!!
                    return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
                }
            }
        instrument.addMonitor(monitor)
        try {
            compose.onNodeWithTag("source-link-Spectra").performScrollTo().performClick()
            assertEquals("https://github.com/Redlnn/Spectra", opened.last())
            compose.onNodeWithTag("source-link-Miuix").performScrollTo().performClick()
            assertEquals("https://github.com/compose-miuix-ui/miuix", opened.last())
            compose.onNodeWithTag("project-license").performScrollTo().performClick()
            assertEquals("https://www.gnu.org/licenses/agpl-3.0.html", opened.last())
            assertEquals(3, opened.size)
            compose.onNodeWithText("Apache License", substring = true).assertDoesNotExist()
        } finally {
            instrument.removeMonitor(monitor)
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(5000) { resumed() == SettingsActivity::class.java }
        compose.onNodeWithContentDescription("关闭设置").performClick()
        compose.waitUntil(5000) { resumed() == MainActivity::class.java }
        assertEquals(16384, vm.settings.value.fftSize)
    }

    @Test
    fun levelCardTogglesOffsetAndAllWaveModesAreAccessible() {
        tone()
        compose.onNodeWithText("采集中").assertExists()
        compose.runOnUiThread { vm.toggle() }
        compose.waitForIdle()
        screenshot("main")
        val level = vm.frame.value!!.rmsDb
        compose.onNodeWithText("峰值频率").assertExists()
        compose.onNodeWithText("频率分辨率").assertDoesNotExist()
        compose.onNodeWithTag("input-level-card").performClick()
        compose.onNodeWithText("dB · +100").assertExists()
        assertTrue(vm.settings.value.levelPositiveDb)
        assertEquals(level, vm.frame.value!!.rmsDb, 0f)
        compose.onNodeWithTag("input-level-card").performClick()
        compose.onNodeWithText("dBFS").assertExists()
        compose.onNodeWithText("W", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(ViewMode.WAVE, vm.settings.value.view)
        compose.onNodeWithText("W + 2D", useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(ViewMode.WAVE_SPECTROGRAM, vm.settings.value.view)
        compose.onNodeWithText("1D", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("reset-spectrum-peaks").performClick()
        assertTrue(vm.frame.value!!.peaksDb.all { it == -180f })
        compose.runOnUiThread {
            vm.update(vm.settings.value.copy(view = ViewMode.WAVE, generatorAmplitude = .001))
            vm.toggle()
        }
        compose.waitUntil(10000) { vm.frame.value?.rmsDb?.let { it < -60 } == true }
        compose.runOnUiThread { vm.toggle() }
        screenshot("wave-auto")
    }

    @Test
    fun expressiveSettingsStillNavigateAndEditOnTheirOwnActivity() {
        tone()
        compose.runOnUiThread {
            vm.toggle()
            vm.update(vm.settings.value.copy(settingsMiuix = false))
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForSettingsScreen()
        compose.waitUntil(5000) { resumed() == SettingsActivity::class.java }
        screenshot("settings-expressive")
        compose.onNodeWithText("图表", useUnmergedTree = true).performClick()
        compose.onNodeWithText("自动量程").performScrollTo().performClick()
        assertFalse(vm.settings.value.waveAutoScale)
        compose.onNodeWithContentDescription("关闭设置").performClick()
        compose.waitUntil(5000) { resumed() == MainActivity::class.java }
        assertFalse(vm.settings.value.waveAutoScale)
    }
}
