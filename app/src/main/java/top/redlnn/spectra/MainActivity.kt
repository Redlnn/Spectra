package top.redlnn.spectra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import top.redlnn.spectra.export.ChartExportController
import top.redlnn.spectra.presentation.enableTransparentSystemBars
import top.redlnn.spectra.presentation.home.AnalyzerScreen
import top.redlnn.spectra.render.AnalyzerChartView
import top.redlnn.spectra.settings.InputSource

class MainActivity : ComponentActivity() {
    val analyzer: AnalyzerViewModel
        get() = (application as SpectraApplication).analyzer

    private var chart: AnalyzerChartView? = null
    private val exports = ChartExportController(this)
    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) analyzer.selectInput(InputSource.MICROPHONE)
            else analyzer.permissionDenied()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentSystemBars()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AnalyzerScreen(
                analyzer,
                ::selectInput,
                { format ->
                    exports.launch(format, chart, analyzer.settings.value, analyzer::pcmSnapshot)
                },
            ) {
                chart = it
            }
        }
        lifecycleScope.launch {
            analyzer.notices.collect {
                Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
            }
        }
        lifecycleScope.launch {
            analyzer.state
                .map { it.needsMicrophonePermission }
                .distinctUntilChanged()
                .collect { needed ->
                    if (needed) permission.launch(Manifest.permission.RECORD_AUDIO)
                }
        }
    }

    override fun onStart() {
        super.onStart()
        analyzer.foreground(true)
    }

    override fun onStop() {
        analyzer.foreground(false)
        super.onStop()
    }

    private fun selectInput(source: InputSource) {
        if (
            source == InputSource.MICROPHONE &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
        )
            permission.launch(Manifest.permission.RECORD_AUDIO)
        else analyzer.selectInput(source)
    }
}
