package top.redlnn.spectra.presentation.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.redlnn.spectra.noise.NoiseReference

@Composable
internal fun NoiseReferenceCard(
    reference: NoiseReference,
    running: Boolean,
    onRestart: () -> Unit,
    onGuide: () -> Unit,
) {
    val advice =
        if (!running && reference.seconds == 0) "点击首页播放按钮开始采集，保持手机位置不变。" else reference.advice
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("noise-reference-card"),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (!running) "已暂停 · 本段 ${reference.seconds} 秒"
                    else "正在观察 · 本段 ${reference.seconds} 秒",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onRestart,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("noise-restart"),
                ) {
                    Text("重新开始")
                }
                TextButton(
                    onClick = onGuide,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("noise-guide-link"),
                ) {
                    Text("寻源指南")
                }
            }
            Text(
                reference.observation,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                reference.sources,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "下一步建议",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(advice, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
