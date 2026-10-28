package top.redlnn.spectra.presentation.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.redlnn.spectra.AnalyzerViewModel
import top.redlnn.spectra.presentation.PreferencePage
import top.redlnn.spectra.settings.InputSource

@Composable
fun SettingsScreen(
    vm: AnalyzerViewModel,
    onClose: () -> Unit,
    onInput: (InputSource) -> Unit,
    onAbout: () -> Unit,
    onNoiseGuide: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val state by vm.state.collectAsState()
    val tabs = listOf("外观", "分析", "图表", "输入", "工具")
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(LocalSettingsMiuix provides settings.settingsMiuix) {
        PreferencePage("设置", settings.settingsMiuix, onClose) { padding ->
            val direction = LocalLayoutDirection.current
            val viewportPadding =
                PaddingValues(
                    start = padding.calculateStartPadding(direction),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(direction),
                )
            Column(Modifier.fillMaxSize().padding(viewportPadding)) {
                Row(
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tabs.forEachIndexed { index, title ->
                        FilterChip(
                            pager.currentPage == index,
                            { scope.launch { pager.animateScrollToPage(index) } },
                            label = { Text(title) },
                        )
                    }
                }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("settings-pager"),
                ) { page ->
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            // Keep the last control safe without clipping the scroll viewport.
                            .padding(bottom = 28.dp + padding.calculateBottomPadding()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        when (page) {
                            0 -> AppearanceSettings(settings, vm::update)
                            1 ->
                                AnalysisSettings(
                                    settings,
                                    state.actualRate,
                                    vm::update,
                                    vm::resetPeaks,
                                )
                            2 -> ChartSettings(settings, vm::update, vm::resetHistory)
                            3 -> InputSettings(settings, state, vm::update, onInput)
                            4 -> ToolSettings(settings, state, vm, onAbout, onNoiseGuide)
                        }
                    }
                }
            }
        }
    }
}
