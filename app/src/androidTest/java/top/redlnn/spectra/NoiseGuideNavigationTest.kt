package top.redlnn.spectra

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.settings.ThemeMode

@RunWith(AndroidJUnit4::class)
class NoiseGuideNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        instrument.waitForIdleSync()
        Thread.sleep(500) // Wait for the cross-activity transition before visual inspection.
        val bitmap = instrument.uiAutomation.takeScreenshot()
        File(instrument.targetContext.getExternalFilesDir(null), "noise-guide-$name.png")
            .outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun checkGuide(miuix: Boolean, theme: ThemeMode) {
        val vm = (compose.activity.application as SpectraApplication).analyzer
        compose.waitUntil(10000) { !vm.state.value.starting }
        compose.runOnUiThread {
            vm.update(vm.settings.value.copy(settingsMiuix = miuix, themeMode = theme))
        }
        compose.onNodeWithText("工具", useUnmergedTree = true).performClick()
        compose.onNodeWithText("关于").performScrollTo()
        val guide = compose.onNodeWithText("噪音寻源指南").assertIsDisplayed()
        val about = compose.onNodeWithText("关于").assertIsDisplayed()
        assertTrue(
            guide.fetchSemanticsNode().boundsInRoot.bottom <=
                about.fetchSemanticsNode().boundsInRoot.top
        )
        screenshot(if (miuix) "settings-miuix" else "settings-material")
        guide.performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("noise-guide-page").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("从声音特征找到排查方向").assertIsDisplayed()
        screenshot(if (miuix) "dark" else "light")

        for (section in listOf("peaks", "patterns", "limits", "steps", "reading")) {
            compose.onNodeWithTag("guide-tab-$section").performScrollTo().performClick()
            compose.onNodeWithTag("guide-section-$section").assertIsDisplayed()
        }
        compose.onNodeWithTag("guide-tab-peaks").performScrollTo().performClick()
        screenshot(if (miuix) "frequencies-dark" else "frequencies-light")
        compose.onNodeWithTag("guide-tab-sources").performScrollTo().performClick()
        compose.onNodeWithTag("guide-section-sources").assertIsDisplayed()

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
            val expected =
                listOf(
                    "https://www.bilibili.com/opus/1106431601837867015",
                    "https://www.bilibili.com/opus/1191454233225855011",
                    "https://www.bilibili.com/opus/1106760871183384580",
                )
            expected.indices.forEach { index ->
                compose
                    .onNodeWithTag("noise-guide-list")
                    .performScrollToNode(hasTestTag("guide-source-$index"))
                compose.onNodeWithTag("guide-source-$index").performClick()
            }
            assertEquals(expected, opened)
        } finally {
            instrument.removeMonitor(monitor)
        }
        screenshot(if (miuix) "sources-dark" else "sources-light")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForSettingsScreen()
        compose.onNodeWithText("关于").performScrollTo().assertIsDisplayed()
    }

    @Test fun guideNavigationInMiuixDarkTheme() = checkGuide(true, ThemeMode.DARK)

    @Test fun guideNavigationInMaterialLightTheme() = checkGuide(false, ThemeMode.LIGHT)
}
