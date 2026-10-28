package top.redlnn.spectra.presentation

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

internal fun ComponentActivity.enableTransparentSystemBars() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Icon contrast is managed by SpectraTheme; do not add the three-button navigation scrim.
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
    }
}
