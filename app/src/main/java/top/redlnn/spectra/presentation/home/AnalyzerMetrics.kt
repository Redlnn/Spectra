@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.redlnn.spectra.presentation.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import top.redlnn.spectra.dsp.AnalysisFrame
import top.redlnn.spectra.dsp.Pitch
import top.redlnn.spectra.dsp.PitchStatus
import top.redlnn.spectra.render.SpectrumPeak
import top.redlnn.spectra.settings.*

@Composable
private fun Metric(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (onClick != null)
                    Modifier.clickable(onClickLabel = "切换 dBFS 与 dB 加100", onClick = onClick)
                else Modifier
            )
            .padding(12.dp, 8.dp)
    ) {
        Text(
            title,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f).heightIn(min = 3.dp))
        Text(
            unit,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("metric-unit-$title"),
        )
    }
}

@Composable
internal fun AnalyzerMetrics(
    s: AnalyzerSettings,
    frame: AnalysisFrame?,
    onLevel: () -> Unit,
    onGenerator: () -> Unit,
) {
    val peak =
        remember(frame) {
            frame?.let {
                SpectrumPeak.find(it.spectrumDb, it.spacingHz, 31.25, it.sampleRate / 2.0)
            }
        }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        val separatePitch = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.25f
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Metric(
                    "输入电平",
                    frame?.let { "%.1f".format(it.rmsDb + if (s.levelPositiveDb) 100 else 0) }
                        ?: "—",
                    if (s.levelPositiveDb) "dB · +100" else "dBFS",
                    Modifier.weight(1f).fillMaxHeight().testTag("input-level-card"),
                    onLevel,
                )
                Metric(
                    "峰值频率",
                    peak?.let { "%.0f".format(it.hz) } ?: "—",
                    peak?.let { "Hz · ${"%.1f".format(it.db)} dBFS" } ?: "Hz",
                    Modifier.weight(1f).fillMaxHeight(),
                )
                if (s.pitch && !separatePitch)
                    PitchCard(
                        frame?.pitch,
                        s.flats,
                        frame?.pitchStatus,
                        Modifier.weight(1f).fillMaxHeight(),
                        if (s.input == InputSource.GENERATOR) onGenerator else null,
                    )
            }
            if (s.pitch && separatePitch)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    PitchCard(
                        frame?.pitch,
                        s.flats,
                        frame?.pitchStatus,
                        Modifier.fillMaxWidth().fillMaxHeight(),
                        if (s.input == InputSource.GENERATOR) onGenerator else null,
                    )
                }
        }
    }
}

@Composable
private fun PitchCard(
    pitch: Pitch?,
    flats: Boolean,
    status: PitchStatus?,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error
    Column(
        modifier
            .testTag("pitch-card")
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (onClick != null) Modifier.clickable(onClickLabel = "编辑测试信号", onClick = onClick)
                else Modifier
            )
            .padding(12.dp, 8.dp)
    ) {
        Text(
            "音高",
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            pitch?.note(flats) ?: "—",
            fontSize = 22.sp,
            lineHeight = 27.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f).heightIn(min = 2.dp))
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawLine(
                muted.copy(alpha = .3f),
                androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                strokeWidth = 2f,
            )
            drawLine(
                muted,
                androidx.compose.ui.geometry.Offset(size.width / 2, 0f),
                androidx.compose.ui.geometry.Offset(size.width / 2, size.height),
                strokeWidth = 2f,
            )
            if (pitch != null)
                drawCircle(
                    if (abs(pitch.cents) < 10) accent else error,
                    radius = 4f,
                    center =
                        androidx.compose.ui.geometry.Offset(
                            ((pitch.cents + 50) / 100).toFloat().coerceIn(0f, 1f) * size.width,
                            size.height / 2,
                        ),
                )
        }
        Text(
            if (pitch != null) "${"%.0f".format(pitch.hz)} Hz · ${"%+.0f".format(pitch.cents)} ¢"
            else (status ?: PitchStatus.WAITING).hint,
            modifier = Modifier.testTag("pitch-details"),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
