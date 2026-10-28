package top.redlnn.spectra.presentation.settings

import android.os.Build
import androidx.compose.runtime.Composable
import top.redlnn.spectra.settings.*

@Composable
internal fun AppearanceSettings(s: AnalyzerSettings, update: (AnalyzerSettings) -> Unit) {
    SettingsSection("界面") {
        SettingChoice(
            "界面样式",
            s.settingsMiuix,
            listOf(true, false),
            label = { if (it) "Miuix" else "Material 3 Expressive" },
        ) {
            update(s.copy(settingsMiuix = it))
        }
    }
    SettingsSection("颜色与主题") {
        SettingChoice("主题模式", s.themeMode, ThemeMode.entries, label = { it.label }) {
            update(s.copy(themeMode = it))
        }
        SettingToggle(
            "Monet 取色",
            s.monet,
            if (Build.VERSION.SDK_INT >= 31) "可用时使用系统动态取色"
            else "系统动态取色需要 Android 12 或更高版本",
            enabled = Build.VERSION.SDK_INT >= 31,
        ) {
            update(s.copy(monet = it))
        }
        SettingChoice(
            "色彩风格",
            s.monetStyle,
            MonetStyle.entries,
            label = { it.label },
            enabled = s.monet,
        ) {
            update(s.copy(monetStyle = it))
        }
        SettingChoice(
            "频谱配色",
            s.spectrumPalette,
            SpectrumPalette.entries,
            label = { it.label },
            enabled = !s.monet,
            summary = if (s.monet) "跟随 Monet" else s.spectrumPalette.label,
        ) {
            update(s.copy(spectrumPalette = it))
        }
        SettingChoice(
            "声谱图配色",
            s.colorMap,
            listOf("Jet", "Aurora", "Gray"),
            label = ::spectrogramColorLabel,
        ) {
            update(s.copy(colorMap = it))
        }
    }
    SettingsSection("视图与音高") {
        SettingChoice("显示视图", s.view, ViewMode.entries, label = { it.label }) {
            update(s.copy(view = it))
        }
        SettingToggle("显示音高仪", s.pitch) { update(s.copy(pitch = it)) }
        SettingToggle("音高降噪", s.pitchDenoise, "仅过滤音高检测中的低频轰鸣和高频干扰，不改变频谱与导出音频") {
            update(s.copy(pitchDenoise = it))
        }
        SettingChoice("音高灵敏度", s.pitchSensitivity, PitchSensitivity.entries, label = { it.label }) {
            update(s.copy(pitchSensitivity = it))
        }
        SettingInfo(
            "音高识别",
            "音高估计基频，峰值频率表示频谱最强分量；谐波较强时两者可相差八度。不同估计方法、检测窗口与整数取整会产生小幅差异，440 与 441 Hz 约相差 4 音分。",
        )
        SettingToggle("音名使用降号 ♭", s.flats, "关闭后使用升号 ♯") { update(s.copy(flats = it)) }
    }
}
