package top.redlnn.spectra.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.*
import top.redlnn.spectra.settings.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneratorDialog(
    settings: AnalyzerSettings,
    onClose: () -> Unit,
    onApply: (AnalyzerSettings) -> Unit,
) {
    var shape by remember { mutableStateOf(settings.shape) }
    var frequency by remember { mutableStateOf(settings.generatorHz.toString()) }
    var db by remember { mutableFloatStateOf((20 * log10(settings.generatorAmplitude)).toFloat()) }
    var dbInputOpen by remember { mutableStateOf(false) }
    val hz = frequency.toDoubleOrNull()
    val valid = hz != null && hz.isFinite() && hz in 20.0..20000.0
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("信号发生器") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WaveShape.entries.forEach { wave ->
                        FilterChip(shape == wave, { shape = wave }, label = { Text(wave.label) })
                    }
                }
                OutlinedTextField(
                    frequency,
                    { frequency = it },
                    label = { Text("频率（Hz）") },
                    singleLine = true,
                    isError = !valid,
                    supportingText = { Text("20～20000 Hz；最高实际频率为采样率的 45%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { dbInputOpen = true }) {
                    Text("信号幅度：${"%.1f".format(db)} dBFS")
                }
                Slider(db, { db = it }, valueRange = -60f..-1f)
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onApply(
                        settings.copy(
                            shape = shape,
                            generatorHz = hz!!,
                            generatorAmplitude = 10.0.pow(db / 20.0),
                        )
                    )
                },
            ) {
                Text("应用")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } },
    )
    if (dbInputOpen)
        NumericValueDialog(
            "信号幅度",
            db.toDouble(),
            NumericInputSpec(-60.0, -1.0, 1),
            "-60～-1 dBFS",
            false,
            { dbInputOpen = false },
        ) {
            db = it.toFloat()
            dbInputOpen = false
        }
}
