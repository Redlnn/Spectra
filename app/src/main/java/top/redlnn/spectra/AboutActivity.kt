package top.redlnn.spectra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import top.redlnn.spectra.presentation.AboutPage
import top.redlnn.spectra.presentation.SpectraTheme
import top.redlnn.spectra.presentation.enableTransparentSystemBars

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentSystemBars()
        val vm = (application as SpectraApplication).analyzer
        setContent {
            val settings by vm.settings.collectAsState()
            SpectraTheme(settings) { AboutPage(settings.monet, ::finish) }
        }
    }
}
