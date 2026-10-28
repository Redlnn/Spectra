package top.redlnn.spectra.presentation.settings

import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import top.redlnn.spectra.AnalyzerState
import top.redlnn.spectra.AnalyzerViewModel
import top.redlnn.spectra.settings.*

@Composable
internal fun ToolSettings(
    s: AnalyzerSettings,
    state: AnalyzerState,
    vm: AnalyzerViewModel,
    onAbout: () -> Unit,
) {
    val defaults = AnalyzerSettings()
    SettingsSection("设置与性能") {
        SettingAction(
            "恢复默认设置",
            "FFT ${defaults.fftSize} · ${(defaults.overlap*100).roundToInt()}% 重叠 · 深色",
        ) {
            vm.update(defaults)
        }
        SettingChoice("界面刷新率", s.fps, RefreshRates, label = { "$it FPS" }) {
            vm.update(s.copy(fps = it))
        }
        SettingInfo("刷新与新数据", "图表、输入电平和音高读数使用同一刷新档位。新频谱速度由采样率 ÷ hop 决定；高绘制 FPS 不代表每帧都有新的 FFT。")
        SettingInfo(
            "测量状态",
            "目标 ${s.fps} FPS · 实测 ${"%.1f".format(state.renderFps)} FPS\n分析数据 ${"%.1f".format(state.analysisHz)} 帧/秒 · 受 FFT 与重叠率影响\n${state.frames} 帧 · 最近 DSP ${"%.2f".format(state.processingMs)} ms\n${if(state.running) "采集中" else "已暂停"} · ${state.actualRate} Hz",
        )
    }
    SettingsSection("使用与隐私") {
        SettingInfo("图表操作", "点击或拖动查看光标；双指缩放频率与幅度；双击重置；拖动中间分隔条改变比例。暂停后可检查声谱图历史。")
        SettingInfo(
            "导出",
            "主页右上角可导出 PNG、CSV、最近 15 秒 WAV 或原始 PCM。CSV 附带完整配置、时间戳和幅值定义；WAV 为单声道 16-bit PCM。",
        )
        SettingInfo("幅值参考", "点击主页输入电平可在 dBFS 与 dB（+100）之间切换。dB 为数字满刻度读数加 100 的显示偏移，不是校准声压级 dB SPL。")
        SettingAction("关于声镜", "版本、应用信息与开放源代码许可", onClick = onAbout)
    }
}
