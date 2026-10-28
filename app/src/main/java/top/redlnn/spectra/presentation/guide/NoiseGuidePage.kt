package top.redlnn.spectra.presentation.guide

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.redlnn.spectra.presentation.PreferencePage

@Composable
internal fun NoiseGuidePage(miuix: Boolean, onBack: () -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val sections = NoiseGuideContent.sections
    val offsets = remember {
        buildMap {
            var offset = 1 // Introduction precedes the section headers.
            sections.forEach { section ->
                put(section.id, offset)
                offset += 1 + section.entries.size
            }
            put("sources", offset)
        }
    }
    PreferencePage("噪音寻源指南", miuix, onBack) { padding ->
        val direction = LocalLayoutDirection.current
        Box(
            Modifier.fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(direction),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(direction),
                )
                .testTag("noise-guide-page"),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (sections.map { it.id to it.label } + ("sources" to "资料来源")).forEach {
                        (id, label) ->
                        AssistChip(
                            onClick = { scope.launch { state.scrollToItem(offsets.getValue(id)) } },
                            label = { Text(label) },
                            modifier = Modifier.testTag("guide-tab-$id"),
                        )
                    }
                }
                LazyColumn(
                    state = state,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("noise-guide-list"),
                    contentPadding =
                        PaddingValues(
                            start = 20.dp,
                            top = 12.dp,
                            end = 20.dp,
                            bottom = 28.dp + padding.calculateBottomPadding(),
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "introduction", contentType = "introduction") {
                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                        ) {
                            SelectionContainer {
                                Column(Modifier.padding(20.dp)) {
                                    Text(
                                        "从声音特征找到排查方向",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "本指南据三篇寻源笔记整理，可离线阅读。典型频率是经验线索，不是设备指纹。请结合频谱形状、时间规律与启停对照判断。",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                    }
                    sections.forEach { section ->
                        item(key = section.id, contentType = "heading") {
                            GuideHeading(section.title, section.introduction, section.id)
                        }
                        itemsIndexed(
                            section.entries,
                            key = { index, _ -> "${section.id}-$index" },
                            contentType = { _, _ -> "entry" },
                        ) { _, entry ->
                            GuideEntryCard(entry)
                        }
                    }
                    item(key = "sources", contentType = "heading") {
                        GuideHeading(
                            "06 · 资料来源",
                            "依据三篇网页笔记整理，作者为「朱林-声学」。这里保留原文经验与案例，并对容易过度推断的结论作了限定。原文链接需联网打开。",
                            "sources",
                        )
                    }
                    itemsIndexed(
                        NoiseGuideContent.sources,
                        key = { index, _ -> "source-$index" },
                        contentType = { _, _ -> "source" },
                    ) { index, source ->
                        OutlinedCard(
                            onClick = {
                                runCatching { uriHandler.openUri(source.url) }
                                    .onFailure {
                                        Toast.makeText(
                                                context,
                                                "无法打开链接，请检查是否已安装浏览器",
                                                Toast.LENGTH_SHORT,
                                            )
                                            .show()
                                    }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("guide-source-$index"),
                        ) {
                            Row(
                                Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(source.title, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        source.detail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "打开原文",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideHeading(title: String, introduction: String, id: String) {
    Column(Modifier.padding(top = 16.dp, bottom = 4.dp).testTag("guide-section-$id")) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            introduction,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GuideEntryCard(entry: GuideEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        SelectionContainer {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                entry.frequency?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(entry.detail, style = MaterialTheme.typography.bodyLarge)
                entry.candidates?.let { GuideDetail("候选声源", it) }
                entry.check?.let { GuideDetail("如何核对", it) }
            }
        }
    }
}

@Composable
private fun GuideDetail(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
