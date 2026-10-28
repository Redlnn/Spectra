@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.redlnn.spectra.presentation.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.redlnn.spectra.presentation.NumericInputSpec
import top.redlnn.spectra.presentation.NumericValueDialog
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiCard
import top.yukonga.miuix.kmp.basic.Slider as MiSlider
import top.yukonga.miuix.kmp.basic.Switch as MiSwitch
import top.yukonga.miuix.kmp.basic.Text as MiText
import top.yukonga.miuix.kmp.window.WindowDialog

internal val LocalSettingsMiuix = staticCompositionLocalOf { true }

@Composable
internal fun SettingsSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            title,
            Modifier.padding(start = 4.dp, top = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        subtitle?.let {
            Text(
                it,
                Modifier.padding(start = 4.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (LocalSettingsMiuix.current)
            MiCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp), content = content)
        else
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                content = content,
            )
    }
}

@Composable
internal fun SettingAction(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    if (LocalSettingsMiuix.current)
        BasicComponent(
            modifier = Modifier.semantics { if (!enabled) disabled() },
            title = title,
            summary = summary,
            enabled = enabled,
            onClick = if (enabled) onClick else null,
            endActions = {
                Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    else
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary) },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
            modifier =
                Modifier.alpha(if (enabled) 1f else .5f)
                    .clickable(enabled = enabled, onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
}

/** The displayed option and committed value come from one typed list. */
@Composable
internal fun <T> SettingChoice(
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String = { it.toString() },
    enabled: Boolean = true,
    summary: String = label(value),
    onSelected: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingAction(title, summary, enabled) { open = true }
    if (!open) return
    val content: @Composable () -> Unit = {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            options.forEachIndexed { index, option ->
                Row(
                    Modifier.fillMaxWidth()
                        .testTag("picker-option-$title-$index")
                        .clickable {
                            onSelected(option)
                            open = false
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (LocalSettingsMiuix.current) MiText(label(option), Modifier.weight(1f))
                    else Text(label(option), Modifier.weight(1f))
                    if (option == value)
                        Icon(Icons.Rounded.Check, "已选", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (LocalSettingsMiuix.current)
        WindowDialog(
            show = true,
            title = title,
            onDismissRequest = { open = false },
            content = content,
        )
    else
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = content,
            confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
}

@Composable
internal fun SettingToggle(
    title: String,
    checked: Boolean,
    summary: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    if (LocalSettingsMiuix.current)
        BasicComponent(
            title = title,
            summary = summary,
            enabled = enabled,
            onClick = { if (enabled) onChange(!checked) },
            endActions = { MiSwitch(checked, onChange, enabled = enabled) },
        )
    else
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = summary?.let { { Text(it, fontSize = 12.sp) } },
            trailingContent = { Switch(checked, onChange, enabled = enabled) },
            modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
}

@Composable
internal fun SettingInfo(title: String, summary: String) {
    if (LocalSettingsMiuix.current) BasicComponent(title = title, summary = summary)
    else
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary, fontSize = 12.sp) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
}

@Composable
internal fun SettingSlider(
    title: String,
    label: (Float) -> String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    numberDecimals: Int = 1,
    valueToNumber: (Float) -> Double = { it.toDouble() },
    numberToValue: (Double) -> Float = { it.toFloat() },
    onChange: (Float) -> Unit,
) {
    var pending by remember(value) { mutableFloatStateOf(value) }
    var inputOpen by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { inputOpen = true },
                modifier =
                    Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                        .testTag("slider-preview-$title"),
                shape = RoundedCornerShape(50),
                colors =
                    ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .1f)
                    ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(label(pending), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (LocalSettingsMiuix.current)
            MiSlider(
                pending,
                { pending = it },
                valueRange = range,
                steps = steps,
                onValueChangeFinished = { onChange(pending) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("slider-$title"),
            )
        else
            Slider(
                pending,
                { pending = it },
                valueRange = range,
                steps = steps,
                onValueChangeFinished = { onChange(pending) },
                modifier = Modifier.testTag("slider-$title"),
            )
    }
    if (inputOpen)
        NumericValueDialog(
            title,
            valueToNumber(pending),
            NumericInputSpec(
                valueToNumber(range.start),
                valueToNumber(range.endInclusive),
                numberDecimals,
            ),
            "${label(range.start)}～${label(range.endInclusive)}",
            LocalSettingsMiuix.current,
            { inputOpen = false },
        ) { number ->
            pending = numberToValue(number)
            onChange(pending)
            inputOpen = false
        }
}
