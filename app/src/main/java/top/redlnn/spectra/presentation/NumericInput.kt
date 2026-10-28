package top.redlnn.spectra.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*
import top.yukonga.miuix.kmp.window.WindowDialog

data class NumericInputSpec(val minimum: Double, val maximum: Double, val decimals: Int) {
    fun parse(text: String): Double? {
        val number = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!number.isFinite() || number < minimum - 1e-7 || number > maximum + 1e-7) return null
        val scale = 10.0.pow(decimals)
        if (abs(number * scale - round(number * scale)) > 1e-6) return null
        return number.coerceIn(minimum, maximum)
    }

    fun format(value: Double): String =
        BigDecimal.valueOf(value)
            .setScale(decimals, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
}

@Composable
internal fun NumericValueDialog(
    title: String,
    initial: Double,
    spec: NumericInputSpec,
    rangeLabel: String,
    miuix: Boolean,
    onClose: () -> Unit,
    onApply: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(spec.format(initial)) }
    val value = spec.parse(text)
    val focus = remember { FocusRequester() }
    val apply = {
        value?.let { onApply(it) }
        Unit
    }
    val field: @Composable () -> Unit = {
        OutlinedTextField(
            text,
            { text = it },
            label = { Text(title) },
            singleLine = true,
            isError = value == null,
            supportingText = {
                Text(
                    "范围：$rangeLabel；${if(spec.decimals == 0) "请输入整数" else "最多 ${spec.decimals} 位小数"}"
                )
            },
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (value != null) apply() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
    if (miuix)
        WindowDialog(show = true, title = title, onDismissRequest = onClose) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                field()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("取消") }
                    TextButton(enabled = value != null, onClick = apply) { Text("应用") }
                }
            }
        }
    else
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(title) },
            text = field,
            confirmButton = { TextButton(enabled = value != null, onClick = apply) { Text("应用") } },
            dismissButton = { TextButton(onClick = onClose) { Text("取消") } },
        )
}
