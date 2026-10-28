package top.redlnn.spectra.settings

internal object SettingsChoices {
    val sampleRates = listOf(32000, 44100, 48000, 96000, 192000)
    val fftSizes = listOf(1024, 2048, 4096, 8192, 16384, 32768)
    val overlaps = listOf(.5, .75, .875)
    val averages = listOf(1, 2, 4, 8)
    val octaveBands = listOf(0, 24, 12, 6, 3, 1)
    val generatorFrequencies =
        listOf(20.0, 50.0, 100.0, 125.0, 250.0, 440.0, 1000.0, 2000.0, 8000.0, 16000.0, 20000.0)
}

enum class InputSource(val label: String) {
    MICROPHONE("麦克风"),
    GENERATOR("测试信号"),
}

enum class WindowType(val label: String) {
    HANN("汉宁窗"),
    HAMMING("海明窗"),
    BLACKMAN("布莱克曼窗"),
    NONE("矩形窗"),
}

enum class Engine(val label: String) {
    CLASSIC("FFT"),
    ENHANCED("增强固定 FFT"),
    MULTI("多分辨率 · 实验"),
}

val RefreshRates = listOf(10, 20, 30, 40, 50, 60)

enum class ViewMode(val label: String, val panels: List<Int>) {
    SPECTRUM_SPECTROGRAM("频谱 + 声谱图", listOf(1, 2)),
    WAVE_SPECTRUM("波形 + 频谱", listOf(0, 1)),
    WAVE_SPECTROGRAM("波形 + 声谱图", listOf(0, 2)),
    WAVE("波形", listOf(0)),
    SPECTRUM("频谱", listOf(1)),
    SPECTROGRAM("声谱图", listOf(2)),
}

enum class Weighting(val label: String) {
    FLAT("平直"),
    A("A 加权"),
    C("C 加权"),
}

enum class Mapping(val label: String) {
    NEAREST("最近频点"),
    LINEAR_POWER("线性功率插值"),
    BAND_POWER("频带功率平均"),
}

enum class WaveShape(val label: String) {
    SINE("正弦波"),
    TRIANGLE("三角波"),
    SQUARE("方波"),
    SAW("锯齿波"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

enum class SpectrumPalette(val label: String) {
    CLASSIC("蓝绿渐变"),
    NIGHT("红黄渐变"),
    GREEN("绿色"),
    YELLOW("黄色"),
}

enum class MonetStyle(val label: String) {
    TONAL_SPOT("色调点（TonalSpot）"),
    NEUTRAL("中性（Neutral）"),
    VIBRANT("鲜明（Vibrant）"),
    EXPRESSIVE("表现力（Expressive）"),
    RAINBOW("彩虹（Rainbow）"),
    FRUIT_SALAD("水果沙拉（FruitSalad）"),
    MONOCHROME("单色（Monochrome）"),
    FIDELITY("保真（Fidelity）"),
}

enum class PitchSensitivity(val label: String, val threshold: Double) {
    STRICT("严格", .15),
    BALANCED("均衡", .25),
    SENSITIVE("灵敏", .35),
}

fun waveColorLabel(value: String) =
    when (value) {
        "Yellow" -> "黄色"
        "Invert" -> "跟随主题"
        else -> "绿色"
    }

fun spectrogramColorLabel(value: String) =
    when (value) {
        "Aurora" -> "极光"
        "Gray" -> "灰度"
        else -> "彩虹"
    }
