package top.redlnn.spectra

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.redlnn.spectra.settings.AnalyzerSettings
import top.redlnn.spectra.settings.ThemeMode

@RunWith(AndroidJUnit4::class)
class SettingsInsetsTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()

    private fun checkNavigationArea(miuix: Boolean) {
        val vm = (compose.activity.application as SpectraApplication).analyzer
        compose.runOnUiThread {
            vm.update(AnalyzerSettings(settingsMiuix = miuix, themeMode = ThemeMode.DARK))
        }
        compose.waitForSettingsScreen()
        compose.waitForIdle()
        val pager = compose.onNodeWithTag("settings-pager").fetchSemanticsNode().boundsInWindow
        var windowHeight = 0
        var navigationHeight = 0
        compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            windowHeight = decor.height
            navigationHeight =
                ViewCompat.getRootWindowInsets(decor)!!.getInsets(
                        WindowInsetsCompat.Type.navigationBars()
                    )
                    .bottom
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "settings-insets-$miuix.png")
            .outputStream()
            .use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        assertTrue("The test requires a visible navigation area", navigationHeight > 0)
        assertEquals(
            "The scrolling viewport stops above the navigation area: " +
                "window=$windowHeight, navigation=$navigationHeight, viewport=$pager",
            windowHeight.toFloat(),
            pager.bottom,
            1f,
        )
        compose.onNodeWithText("工具", useUnmergedTree = true).performClick()
        compose.onNodeWithText("关于").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings-pager").performTouchInput { swipeUp() }
        val lastAction = compose.onNodeWithText("关于").fetchSemanticsNode().boundsInWindow
        assertTrue(
            "The last action must remain above the navigation area",
            lastAction.bottom <= windowHeight - navigationHeight,
        )
    }

    @Test fun miuixSettingsDrawThroughNavigationArea() = checkNavigationArea(true)

    @Test fun expressiveSettingsDrawThroughNavigationArea() = checkNavigationArea(false)
}
