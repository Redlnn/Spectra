package top.redlnn.spectra.presentation.settings

import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import top.redlnn.spectra.settings.*

@Composable
internal fun ChartSettings(
    s: AnalyzerSettings,
    update: (AnalyzerSettings) -> Unit,
    resetHistory: () -> Unit,
) {
    SettingsSection("频率轴与纹理") {
        SettingToggle("对数刻度", s.logAxis, "关闭后使用线性刻度；图表和光标共享坐标") { update(s.copy(logAxis = it)) }
        SettingChoice("频率重采样", s.mapping, Mapping.entries, label = { it.label }) {
            update(s.copy(mapping = it))
        }
        if (s.mapping == Mapping.BAND_POWER)
            SettingInfo("频带功率平均的定义", "按像素覆盖范围对 bin 功率加权平均；窄于一个 bin 时作功率插值。它不是 PSD，也不代表带内总功率。")
        SettingToggle("双线性纹理过滤", s.bilinear, "仅改变绘图，不提高 FFT 分辨率") { update(s.copy(bilinear = it)) }
    }
    SettingsSection("声谱图", "最新数据在底部 · 时间单位为秒") {
        SettingSlider(
            "历史长度",
            { "${it.roundToInt()} s" },
            s.duration.toFloat(),
            4f..15f,
            10,
            numberDecimals = 0,
        ) {
            update(s.copy(duration = it.roundToInt()))
        }
        SettingSlider(
            "色阶下限",
            { "${it.roundToInt()} dBFS" },
            s.floorDb,
            -150f..-60f,
            17,
            numberDecimals = 0,
        ) {
            update(s.copy(floorDb = it.roundToInt().toFloat()))
        }
        SettingSlider(
            "色阶上限",
            { "${it.roundToInt()} dBFS" },
            s.ceilingDb,
            -50f..0f,
            9,
            numberDecimals = 0,
        ) {
            update(s.copy(ceilingDb = it.roundToInt().toFloat()))
        }
        SettingToggle("应用频谱倾斜", s.applyTilt, "修改倾斜度或本开关会清空历史") { update(s.copy(applyTilt = it)) }
        SettingAction("清空历史", "立即清除", onClick = resetHistory)
    }
    SettingsSection("时域波形") {
        SettingToggle("自动量程", s.waveAutoScale, "放大弱信号以便观察，纵轴保留真实 FS 幅度；不改变分析与导出数据") {
            update(s.copy(waveAutoScale = it))
        }
        SettingChoice(
            "波形配色",
            s.waveColor,
            listOf("Green", "Yellow", "Invert"),
            label = ::waveColorLabel,
            enabled = !s.monet,
            summary = if (s.monet) "跟随 Monet" else waveColorLabel(s.waveColor),
        ) {
            update(s.copy(waveColor = it))
        }
        SettingSlider("线宽", { "${"%.1f".format(it)} dp" }, s.waveThickness, 1f..5f, 7) {
            update(s.copy(waveThickness = it))
        }
    }
}
