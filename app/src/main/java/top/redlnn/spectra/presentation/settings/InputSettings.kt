package top.redlnn.spectra.presentation.settings

import androidx.compose.runtime.Composable
import kotlin.math.*
import top.redlnn.spectra.AnalyzerState
import top.redlnn.spectra.settings.*

@Composable
internal fun InputSettings(
    s: AnalyzerSettings,
    state: AnalyzerState,
    update: (AnalyzerSettings) -> Unit,
    onInput: (InputSource) -> Unit,
) {
    SettingsSection("音频输入") {
        SettingChoice(
            "输入来源",
            s.input,
            InputSource.entries,
            label = { it.label },
            onSelected = onInput,
        )
        SettingChoice(
            "请求采样率",
            s.sampleRate,
            SettingsChoices.sampleRates,
            label = { "$it Hz" },
        ) {
            update(s.copy(sampleRate = it))
        }
        SettingInfo("实际输入", "${state.actualRate} Hz\n${state.message}")
        SettingToggle("启动时开始分析", s.startOn) { update(s.copy(startOn = it)) }
    }
    SettingsSection("信号发生器") {
        SettingToggle("播放发生器声音", s.generatorPlayback, "仅在测试信号输入时播放；暂停或离开前台即停止") {
            update(s.copy(generatorPlayback = it))
        }
        SettingChoice("波形", s.shape, WaveShape.entries, label = { it.label }) {
            update(s.copy(shape = it))
        }
        SettingChoice(
            "频率预设",
            s.generatorHz,
            SettingsChoices.generatorFrequencies,
            label = { "${it.toInt()} Hz" },
            summary = "${"%.1f".format(s.generatorHz)} Hz",
        ) {
            update(s.copy(generatorHz = it))
        }
        SettingSlider(
            "连续频率",
            { "${10.0.pow(it.toDouble()).roundToInt()} Hz" },
            log10(s.generatorHz).toFloat(),
            log10(20f)..log10(20000f),
            numberDecimals = 0,
            valueToNumber = { 10.0.pow(it.toDouble()).roundToInt().toDouble() },
            numberToValue = { log10(it).toFloat() },
        ) {
            update(s.copy(generatorHz = 10.0.pow(it.toDouble()).roundToInt().toDouble()))
        }
        SettingSlider(
            "信号幅度",
            { "${"%.1f".format(it)} dBFS" },
            (20 * log10(s.generatorAmplitude)).toFloat(),
            -60f..-1f,
        ) {
            update(s.copy(generatorAmplitude = 10.0.pow(it / 20.0)))
        }
        SettingInfo("采样限制", "高频方波和锯齿使用 PolyBLEP 减少混叠；频率限制为实际采样率的 45%。")
    }
}
