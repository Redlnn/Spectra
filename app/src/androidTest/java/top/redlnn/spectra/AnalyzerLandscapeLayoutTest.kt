package top.redlnn.spectra

import android.app.AlarmManager
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Date
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.render.AnalyzerChartView
import top.redlnn.spectra.settings.*

@RunWith(AndroidJUnit4::class)
class AnalyzerLandscapeLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val vm
        get() = compose.activity.analyzer

    private fun chart(view: View = compose.activity.window.decorView): AnalyzerChartView? {
        if (view is AnalyzerChartView) return view
        if (view is ViewGroup)
            for (i in 0 until view.childCount) chart(view.getChildAt(i))?.let {
                return it
            }
        return null
    }

    private fun rotate(orientation: Int) {
        compose.runOnUiThread { compose.activity.requestedOrientation = orientation }
        compose.waitUntil(10000) {
            val config = compose.activity.resources.configuration
            if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                config.screenWidthDp > config.screenHeightDp
            else config.screenWidthDp < config.screenHeightDp
        }
        compose.waitForIdle()
    }

    private fun statusBarsVisible() = compose.runOnIdle {
        ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.isVisible(
            WindowInsetsCompat.Type.statusBars()
        )
    }

    private fun assertEqualFullscreenMargins() {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val chart = compose.onNodeWithTag("analyzer-chart").fetchSemanticsNode().boundsInRoot
        val insets = compose.runOnIdle {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
        }
        val density = compose.activity.resources.displayMetrics.density
        val margins =
            listOf(
                chart.left - root.left - insets.left,
                chart.top - root.top - insets.top,
                root.right - chart.right - insets.right,
                root.bottom - chart.bottom - insets.bottom,
            )
        margins.forEachIndexed { index, margin ->
            assertEquals("Fullscreen margin $index: $margins", 12f, margin / density, .5f)
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.waitForIdleSync()
        val bitmap = instrument.uiAutomation.takeScreenshot()
        File(instrument.targetContext.getExternalFilesDir(null), "layout-$name.png")
            .outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .executeShellCommand(command)
            )
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun landscapeDeviceStatusTracksBatteryAndTimeZoneChanges() {
        val orientation = compose.activity.requestedOrientation
        val originalZone = TimeZone.getDefault().id
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val alarm = compose.activity.getSystemService(AlarmManager::class.java)
        automation.adoptShellPermissionIdentity("android.permission.SET_TIME_ZONE")
        try {
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            shell("dumpsys battery unplug")
            shell("dumpsys battery set level 42")
            shell("dumpsys battery set status 2")
            compose.waitUntil(5000) {
                compose.onAllNodesWithText("42%").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("正在充电").assertIsDisplayed()
            val status = compose.onNodeWithTag("landscape-device-status").getUnclippedBoundsInRoot()
            val selector = compose.onNodeWithTag("input-selector").getUnclippedBoundsInRoot()
            val chart = compose.onNodeWithTag("analyzer-chart").getUnclippedBoundsInRoot()
            assertTrue(status.top >= selector.bottom && status.bottom <= chart.top)
            assertTrue(status.left > selector.right / 2)
            val before =
                compose
                    .onNodeWithTag("landscape-time")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.Text]
                    .single()
                    .text
            alarm.setTimeZone(
                if (TimeZone.getDefault().rawOffset == 9 * 3600000) "Etc/UTC" else "Asia/Tokyo"
            )
            compose.waitUntil(5000) {
                val displayed =
                    compose
                        .onNodeWithTag("landscape-time")
                        .fetchSemanticsNode()
                        .config[SemanticsProperties.Text]
                        .single()
                        .text
                displayed != before &&
                    displayed == DateFormat.getTimeFormat(compose.activity).format(Date())
            }
            screenshot("device-status")
            shell("dumpsys battery set level 7")
            shell("dumpsys battery set status 3")
            compose.waitUntil(5000) {
                compose.onAllNodesWithText("7%").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("正在充电").assertDoesNotExist()
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            compose.onNodeWithTag("landscape-device-status").assertDoesNotExist()
        } finally {
            shell("dumpsys battery reset")
            alarm.setTimeZone(originalZone)
            automation.dropShellPermissionIdentity()
            compose.runOnUiThread { compose.activity.requestedOrientation = orientation }
        }
    }

    @Test
    fun landscapeMovesPitchAndActionsWhilePortraitAndChartStateStayIntact() {
        compose.waitUntil(15000) { !vm.state.value.starting }
        val saved = vm.settings.value
        val orientation = compose.activity.requestedOrientation
        try {
            compose.runOnUiThread {
                if (vm.state.value.running) vm.toggle()
                vm.update(
                    AnalyzerSettings(
                        input = InputSource.GENERATOR,
                        generatorPlayback = false,
                        startOn = false,
                        fps = 10,
                        colorMap = "Aurora",
                    )
                )
                vm.selectInput(InputSource.GENERATOR)
            }
            compose.waitUntil(10000) { vm.state.value.running && vm.frame.value?.pitch != null }
            compose.runOnUiThread { vm.toggle() }
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            compose.onNodeWithTag("pitch-card").assertIsDisplayed()
            compose.onNodeWithTag("chart-footer").assertIsDisplayed()
            compose.onNodeWithText("SPECTRA").assertIsDisplayed()
            compose.onNodeWithTag("landscape-chart-actions").assertDoesNotExist()
            compose.onNodeWithTag("landscape-device-status").assertDoesNotExist()
            assertTrue(statusBarsVisible())
            val portraitChart = compose.onNodeWithTag("analyzer-chart").getUnclippedBoundsInRoot()
            val nativeChart = compose.runOnIdle { chart()!! }
            assertFalse(nativeChart.horizontal)
            val frame = nativeChart.frame
            screenshot("portrait")
            compose.onNodeWithContentDescription("图表全屏").performClick()
            assertEqualFullscreenMargins()
            screenshot("portrait-fullscreen")
            compose.onNodeWithContentDescription("退出图表全屏").performClick()

            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            compose.waitUntil(5000) { !statusBarsVisible() }
            compose.onNodeWithText("声镜 · Spectra").assertIsDisplayed()
            compose.onNodeWithText("SPECTRA").assertDoesNotExist()
            compose.onNodeWithTag("pitch-card").assertDoesNotExist()
            compose.onNodeWithTag("chart-footer").assertDoesNotExist()
            compose.onNodeWithTag("landscape-device-status").assertIsDisplayed()
            compose.onNodeWithText("重置峰值").assertDoesNotExist()
            val input = compose.onNodeWithTag("input-selector").getUnclippedBoundsInRoot()
            val pitch = compose.onNodeWithTag("landscape-pitch", useUnmergedTree = true)
            pitch.assertTextEquals(frame!!.pitch!!.note(vm.settings.value.flats))
            val pitchBounds = pitch.getUnclippedBoundsInRoot()
            assertTrue(pitchBounds.left > (input.left + input.right) / 2)
            assertTrue(pitchBounds.top >= input.top && pitchBounds.bottom <= input.bottom)
            val landscapeChart = compose.onNodeWithTag("analyzer-chart").getUnclippedBoundsInRoot()
            val actions =
                compose.onNodeWithTag("landscape-chart-actions").getUnclippedBoundsInRoot()
            compose.runOnIdle { assertTrue(nativeChart.horizontal) }
            assertTrue(actions.left >= landscapeChart.right)
            assertEquals(landscapeChart.top.value, actions.top.value, .5f)
            assertEquals(landscapeChart.bottom.value, actions.bottom.value, .5f)
            for (tag in listOf("reset-spectrum-peaks", "reset-viewport")) {
                compose.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction()
            }
            screenshot("landscape")

            compose.onNodeWithContentDescription("图表全屏").performClick()
            compose.onNodeWithTag("landscape-chart-actions").assertDoesNotExist()
            compose.onNodeWithTag("landscape-device-status").assertDoesNotExist()
            assertEqualFullscreenMargins()
            screenshot("landscape-fullscreen")
            val fullscreen = compose.onNodeWithTag("analyzer-chart").getUnclippedBoundsInRoot()
            assertTrue(
                fullscreen.right - fullscreen.left > landscapeChart.right - landscapeChart.left
            )
            assertTrue(
                fullscreen.bottom - fullscreen.top > landscapeChart.bottom - landscapeChart.top
            )
            compose.runOnIdle {
                assertSame(nativeChart, chart())
                assertSame(frame, chart()!!.frame)
            }
            compose.onNodeWithContentDescription("退出图表全屏").performClick()
            compose.onNodeWithTag("reset-viewport").performClick()
            compose.runOnUiThread {
                vm.update(vm.settings.value.copy(pitch = false, view = ViewMode.WAVE))
            }
            compose.onNodeWithTag("landscape-pitch").assertDoesNotExist()
            compose.onNodeWithTag("reset-spectrum-peaks").assertDoesNotExist()
            compose.onNodeWithTag("reset-viewport").assertIsDisplayed()
            compose.runOnUiThread {
                vm.update(
                    vm.settings.value.copy(pitch = true, view = ViewMode.SPECTRUM_SPECTROGRAM)
                )
            }
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            compose.waitUntil(5000) { statusBarsVisible() }
            compose.onNodeWithTag("pitch-card").assertIsDisplayed()
            compose.onNodeWithTag("chart-footer").assertIsDisplayed()
            assertEquals(
                portraitChart,
                compose.onNodeWithTag("analyzer-chart").getUnclippedBoundsInRoot(),
            )
            compose.runOnIdle { assertSame(nativeChart, chart()) }
        } finally {
            compose.runOnUiThread {
                if (vm.state.value.running) vm.toggle()
                vm.update(saved)
                compose.activity.requestedOrientation = orientation
            }
        }
    }
}
