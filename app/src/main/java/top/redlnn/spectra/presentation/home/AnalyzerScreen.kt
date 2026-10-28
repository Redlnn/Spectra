@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.redlnn.spectra.presentation.home

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.*
import top.redlnn.spectra.AnalyzerViewModel
import top.redlnn.spectra.SettingsActivity
import top.redlnn.spectra.export.ExportFormat
import top.redlnn.spectra.presentation.GeneratorDialog
import top.redlnn.spectra.presentation.LocalGraphColors
import top.redlnn.spectra.presentation.SpectraLogo
import top.redlnn.spectra.presentation.SpectraTheme
import top.redlnn.spectra.render.AnalyzerChartView
import top.redlnn.spectra.settings.*

private val Accent: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Base: Color
    @Composable get() = MaterialTheme.colorScheme.background
private val SurfaceColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer

@Composable
internal fun AnalyzerScreen(
    vm: AnalyzerViewModel,
    onInput: (InputSource) -> Unit,
    onExport: (ExportFormat) -> Unit,
    onChart: (AnalyzerChartView) -> Unit,
) {
    val s by vm.settings.collectAsState()
    SpectraTheme(s) { AnalyzerContent(vm, s, onInput, onExport, onChart) }
}

@Composable
private fun AnalyzerContent(
    vm: AnalyzerViewModel,
    s: AnalyzerSettings,
    onInput: (InputSource) -> Unit,
    onExport: (ExportFormat) -> Unit,
    onChart: (AnalyzerChartView) -> Unit,
) {
    val state by vm.state.collectAsState()
    val frame by vm.frame.collectAsState()
    val historyRevision by vm.historyRevision.collectAsState()
    val context = LocalContext.current
    var inputOpen by remember { mutableStateOf(false) }
    var exportsOpen by remember { mutableStateOf(false) }
    var chart by remember { mutableStateOf<AnalyzerChartView?>(null) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var generatorOpen by remember { mutableStateOf(false) }
    BackHandler(fullscreen) { fullscreen = false }
    val windowSize = LocalWindowInfo.current.containerSize
    val compact = with(LocalDensity.current) { windowSize.height.toDp() } < 480.dp
    val wideLandscape =
        with(LocalDensity.current) {
            windowSize.width > windowSize.height && windowSize.width.toDp() >= 600.dp
        }
    val compactLandscape = wideLandscape && compact
    val window = LocalActivity.current?.window
    DisposableEffect(window, compactLandscape) {
        if (window == null || !compactLandscape) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val statusBars = WindowInsetsCompat.Type.statusBars()
        val wasVisible =
            ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(statusBars) ?: true
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(statusBars)
        onDispose {
            controller.systemBarsBehavior = previousBehavior
            if (wasVisible) controller.show(statusBars)
        }
    }
    val graphColors = LocalGraphColors.current
    Surface(Modifier.fillMaxSize(), color = Base) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .padding(
                        if (fullscreen) PaddingValues(12.dp)
                        else PaddingValues(horizontal = if (compact) 12.dp else 16.dp)
                    )
            ) {
                if (!fullscreen) {
                    Row(
                        Modifier.fillMaxWidth().height(if (compact) 48.dp else 64.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpectraLogo(
                            Modifier.size(if (compact) 32.dp else 38.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            Text(
                                if (wideLandscape) "声镜 · Spectra"
                                else
                                    "声镜" +
                                        if (compact && s.pitch)
                                            " · ${frame?.pitch?.note(s.flats) ?: "—"}"
                                        else "",
                                fontSize = if (compact) 20.sp else 24.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = if (wideLandscape) 0.sp else 2.sp,
                                maxLines = if (wideLandscape) 1 else Int.MAX_VALUE,
                                overflow =
                                    if (wideLandscape) TextOverflow.Ellipsis else TextOverflow.Clip,
                                style =
                                    androidx.compose.ui.text.TextStyle(
                                        platformStyle =
                                            androidx.compose.ui.text.PlatformTextStyle(
                                                includeFontPadding = false
                                            )
                                    ),
                            )
                            if (!compact && !wideLandscape)
                                Text(
                                    "SPECTRA",
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    color = Muted,
                                    letterSpacing = 2.sp,
                                    style =
                                        androidx.compose.ui.text.TextStyle(
                                            platformStyle =
                                                androidx.compose.ui.text.PlatformTextStyle(
                                                    includeFontPadding = false
                                                )
                                        ),
                                )
                        }
                        IconButton(onClick = vm::toggle, enabled = !state.starting) {
                            Icon(
                                if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (state.running) "暂停分析" else "开始分析",
                                tint = Accent,
                            )
                        }
                        Box {
                            IconButton(onClick = { exportsOpen = true }) {
                                Icon(Icons.Rounded.IosShare, "导出")
                            }
                            DropdownMenu(exportsOpen, { exportsOpen = false }) {
                                ExportFormat.entries.forEach { format ->
                                    DropdownMenuItem(
                                        text = { Text(format.label) },
                                        onClick = {
                                            exportsOpen = false
                                            onExport(format)
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            }
                        ) {
                            Icon(Icons.Rounded.Tune, "设置")
                        }
                    }
                    Surface(
                        onClick = { inputOpen = true },
                        color = SurfaceColor,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().testTag("input-selector"),
                    ) {
                        Row(
                            Modifier.padding(
                                horizontal = 14.dp,
                                vertical = if (compact) 8.dp else 12.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                when (s.input) {
                                    InputSource.MICROPHONE -> Icons.Rounded.Mic
                                    InputSource.GENERATOR -> Icons.Rounded.Waves
                                },
                                null,
                                tint = Accent,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.input.label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (!compact)
                                    Text(
                                        when (s.input) {
                                            InputSource.GENERATOR ->
                                                "${s.shape.label} · ${s.generatorHz.coerceAtMost(state.actualRate * .45).toInt()} Hz · ${if(state.playbackActive) "播放中" else "已静音"}"
                                            InputSource.MICROPHONE -> "${state.actualRate} Hz"
                                        },
                                        color = Muted,
                                        fontSize = 11.sp,
                                    )
                            }
                            if (compactLandscape && s.pitch)
                                Text(
                                    frame?.pitch?.note(s.flats) ?: "—",
                                    modifier =
                                        Modifier.padding(end = 16.dp).testTag("landscape-pitch"),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Accent,
                                    maxLines = 1,
                                )
                            if (compactLandscape && frame?.clipping == true)
                                Text(
                                    "削波",
                                    modifier = Modifier.padding(end = 8.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 10.sp,
                                )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(6.dp)
                                        .background(
                                            if (state.running) Accent else Muted,
                                            CircleShape,
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (state.starting) "启动中"
                                    else if (state.running) "采集中" else "冻结",
                                    color = if (state.running) Accent else Muted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Icon(
                                Icons.Rounded.ExpandMore,
                                null,
                                tint = Muted,
                                modifier = Modifier.padding(start = 8.dp).size(18.dp),
                            )
                        }
                    }
                    if (!compact)
                        AnalyzerMetrics(
                            s,
                            frame,
                            { vm.update(s.copy(levelPositiveDb = !s.levelPositiveDb)) },
                        ) {
                            generatorOpen = true
                        }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f)
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = if (compact) 0.dp else 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(
                                    ViewMode.SPECTRUM_SPECTROGRAM,
                                    ViewMode.WAVE_SPECTRUM,
                                    ViewMode.WAVE_SPECTROGRAM,
                                    ViewMode.WAVE,
                                    ViewMode.SPECTRUM,
                                    ViewMode.SPECTROGRAM,
                                )
                                .forEach { view ->
                                    FilterChip(
                                        s.view == view,
                                        { vm.update(s.copy(view = view)) },
                                        label = {
                                            Text(
                                                when (view) {
                                                    ViewMode.SPECTRUM_SPECTROGRAM -> "1D + 2D"
                                                    ViewMode.WAVE_SPECTRUM -> "W + 1D"
                                                    ViewMode.WAVE_SPECTROGRAM -> "W + 2D"
                                                    ViewMode.WAVE -> "W"
                                                    ViewMode.SPECTRUM -> "1D"
                                                    else -> "2D"
                                                },
                                                fontSize = 11.sp,
                                            )
                                        },
                                    )
                                }
                        }
                        if (compactLandscape)
                            AnalyzerDeviceStatus(Modifier.padding(start = 12.dp, end = 8.dp))
                    }
                }
                Row(
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = if (wideLandscape && !fullscreen) 12.dp else 0.dp)
                        .testTag("chart-workspace")
                ) {
                    AndroidView(
                        factory = { context ->
                            AnalyzerChartView(context).also {
                                chart = it
                                onChart(it)
                                it.history = vm.history
                                it.frameSource = vm::chartFrame
                                it.onRenderRate = vm::reportRenderRate
                                it.onSplitChanged = { ratio ->
                                    vm.update(vm.settings.value.copy(split = ratio))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("analyzer-chart"),
                        update = { view ->
                            val changed = view.settings != s || view.colors != graphColors
                            view.settings = s
                            view.colors = graphColors
                            view.frame = frame
                            view.history = vm.history
                            view.running = state.running
                            view.tag = historyRevision
                            if (!state.running || changed) view.invalidate()
                        },
                    )
                    if (compactLandscape && !fullscreen)
                        Column(
                            Modifier.width(48.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .testTag("landscape-chart-actions"),
                            verticalArrangement =
                                Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (1 in s.view.panels)
                                IconButton(
                                    onClick = vm::resetPeaks,
                                    modifier = Modifier.testTag("reset-spectrum-peaks"),
                                ) {
                                    Icon(Icons.Rounded.Refresh, "重置峰值", tint = Accent)
                                }
                            IconButton(
                                onClick = {
                                    chart?.resetViewport()
                                    Toast.makeText(context, "已重置图表缩放与光标", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("reset-viewport"),
                            ) {
                                Icon(Icons.Rounded.RestartAlt, "重置缩放", tint = Accent)
                            }
                            IconButton(onClick = { fullscreen = true }) {
                                Icon(Icons.Rounded.Fullscreen, "图表全屏", tint = Accent)
                            }
                        }
                }
                if (!fullscreen && !compactLandscape)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("chart-footer"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${state.actualRate / 1000.0}k · ${s.analysisSize} · ${s.window.label} · ${state.renderFps.toInt()} FPS",
                            fontSize = 9.sp,
                            color = Muted,
                            modifier = Modifier.weight(1f),
                        )
                        if (frame?.clipping == true)
                            Text("削波", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                        if (1 in s.view.panels)
                            ChartAction(
                                "重置峰值",
                                Icons.Rounded.Refresh,
                                Modifier.testTag("reset-spectrum-peaks"),
                                vm::resetPeaks,
                            )
                        ChartAction(
                            "重置缩放",
                            Icons.Rounded.RestartAlt,
                            Modifier.testTag("reset-viewport"),
                        ) {
                            chart?.resetViewport()
                            Toast.makeText(context, "已重置图表缩放与光标", Toast.LENGTH_SHORT).show()
                        }
                        IconButton(onClick = { fullscreen = true }) {
                            Icon(
                                Icons.Rounded.Fullscreen,
                                "图表全屏",
                                modifier = Modifier.size(24.dp),
                                tint = Accent,
                            )
                        }
                    }
                if (state.error && !fullscreen)
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            state.message,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onInput(InputSource.GENERATOR) }) { Text("使用测试信号") }
                    }
            }
            if (fullscreen)
                FilledIconButton(
                    onClick = { fullscreen = false },
                    modifier =
                        Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(12.dp),
                ) {
                    Icon(Icons.Rounded.FullscreenExit, "退出图表全屏")
                }
        }
    }
    if (generatorOpen)
        GeneratorDialog(s, { generatorOpen = false }) { next ->
            vm.update(next)
            generatorOpen = false
        }
    if (inputOpen)
        AlertDialog(
            onDismissRequest = { inputOpen = false },
            title = { Text("选择音频输入") },
            text = {
                Column {
                    InputSource.entries.forEach { source ->
                        ListItem(
                            headlineContent = { Text(source.label) },
                            supportingContent = {
                                Text(
                                    when (source) {
                                        InputSource.MICROPHONE -> "实时采集 · 需要录音权限"
                                        InputSource.GENERATOR -> "正弦 / 三角 / 方波 / 锯齿"
                                    }
                                )
                            },
                            trailingContent = {
                                if (s.input == source)
                                    Icon(Icons.Rounded.Check, "已选", tint = Accent)
                            },
                            modifier =
                                Modifier.clickable {
                                    inputOpen = false
                                    onInput(source)
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { inputOpen = false }) { Text("取消") } },
        )
}

@Composable
private fun ChartAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Accent),
    ) {
        Icon(icon, label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp)
    }
}
