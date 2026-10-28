package top.redlnn.spectra.presentation.settings

import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import top.redlnn.spectra.settings.*

@Composable
internal fun AnalysisSettings(
    s: AnalyzerSettings,
    actualRate: Int,
    update: (AnalyzerSettings) -> Unit,
    resetPeaks: () -> Unit,
) {
    SettingsSection("FFT 与时间窗", "参数改变会重建分析和历史") {
        SettingChoice("分析引擎", s.engine, Engine.entries, label = { it.label }) { engine ->
            update(
                s.copy(
                    engine = engine,
                    fftSize = if (engine == Engine.ENHANCED) 8192 else s.fftSize,
                    overlap = if (engine == Engine.ENHANCED) .875 else s.overlap,
                )
            )
        }
        SettingChoice(
            "FFT 点数",
            s.fftSize,
            SettingsChoices.fftSizes,
            summary =
                "${s.fftSize}${if(s.analysisSize != s.fftSize) " · 实际 ${s.analysisSize}" else ""}",
        ) {
            update(s.copy(fftSize = it))
        }
        SettingChoice("窗函数", s.window, WindowType.entries, label = { it.label }) {
            update(s.copy(window = it))
        }
        if (
            s.input == InputSource.GENERATOR &&
                s.window in listOf(WindowType.HAMMING, WindowType.NONE)
        )
            SettingInfo("稳定音与窗口", "非整周期截窗会让旁瓣随相位周期变化，基频并未断流。汉宁窗或布莱克曼窗可降低泄漏。")
        SettingChoice("STFT 重叠率", s.overlap, SettingsChoices.overlaps, label = { "${it*100}%" }) {
            update(s.copy(overlap = it))
        }
        SettingInfo(
            "实际分辨率",
            "Δf = ${"%.3f".format(actualRate.toDouble()/s.analysisSize)} Hz\nTw = ${"%.2f".format(s.analysisSize*1000.0/actualRate)} ms · H = ${(s.analysisSize*(1-s.overlap)).toInt()} samples",
        )
        if (s.engine == Engine.MULTI)
            SettingInfo(
                "实验模式",
                "8192 / 4096 / 2048 / 1024 点窗中心对齐；250 Hz、2 kHz、8 kHz 交界混合。运算超时自动降级。",
            )
    }
    SettingsSection("频谱处理", "单边正弦幅值 dBFS · N Ave 为功率平均") {
        SettingChoice("频谱平均 N Ave", s.average, SettingsChoices.averages, label = { "$it 帧" }) {
            update(s.copy(average = it))
        }
        SettingChoice("频率加权", s.weighting, Weighting.entries, label = { it.label }) {
            update(s.copy(weighting = it))
        }
        SettingSlider("频谱倾斜", { "${"%.1f".format(it)} dB/oct" }, s.tilt.toFloat(), -6f..6f, 23) {
            update(s.copy(tilt = (it * 10).roundToInt() / 10.0))
        }
        SettingToggle("峰值保持", s.peakHold) { update(s.copy(peakHold = it)) }
        SettingToggle("最高峰标记", s.showPeakMarker, "以独立颜色标记当前可见频段的最高峰，显示频率与强度") {
            update(s.copy(showPeakMarker = it))
        }
        SettingChoice(
            "倍频程分析",
            s.octaveBands,
            SettingsChoices.octaveBands,
            label = { if (it == 0) "FFT" else "1/$it oct" },
        ) {
            update(s.copy(octaveBands = it))
        }
        SettingToggle("倍频程取最大值", s.octaveMax, "关闭为均方根功率平均；中心以 1 kHz 为基准。无 FFT bin 的频带留空。") {
            update(s.copy(octaveMax = it))
        }
        SettingAction("重置峰值保持", "立即清除", onClick = resetPeaks)
    }
}
