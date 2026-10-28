package top.redlnn.spectra.noise

import kotlin.math.*
import top.redlnn.spectra.capture.PcmRing
import top.redlnn.spectra.dsp.Fft
import top.redlnn.spectra.settings.WindowType

data class NoiseReference(
    val seconds: Int = 0,
    val observation: String = "正在等待声音",
    val sources: String = "积累稳定特征后提供来源参考",
    val advice: String = "保持手机位置不变，在目标声音明显时观察。",
)

/** Owns a fixed, unweighted analysis window; never consumes the display spectrum. */
class NoiseProcessor(private val rate: Int) {
    private val size = 32768
    private val hop = 8192
    private val ring = PcmRing(size)
    private val window = FloatArray(size)
    private val fft = Fft(size, WindowType.HANN)
    private var samples = 0L
    private var pending = 0
    private var lastPublished = 0L

    private data class Feature(
        val hz: Double,
        val tonal: Boolean,
        val low: Double,
        val mid: Double,
        val clipped: Boolean,
        val silent: Boolean,
    )

    private val history = ArrayDeque<Feature>()

    fun accept(pcm: FloatArray): NoiseReference? {
        var result: NoiseReference? = null
        // Sample-count scheduling makes observations independent of AudioRecord read sizes.
        for (sample in pcm) {
            single[0] = sample
            ring.add(single, 1)
            samples++
            pending++
            if (ring.count == size && pending >= hop) {
                pending = 0
                ring.latest(window)
                history.addLast(features())
                while (history.size > max(1, rate * 60 / hop)) history.removeFirst()
                if (samples - lastPublished >= rate / 2) {
                    lastPublished = samples
                    result = summarize()
                }
            }
        }
        return result
    }

    private val single = FloatArray(1)

    private fun features(): Feature {
        val amp = fft.amplitudes(window)
        val spacing = rate.toDouble() / size
        val first = ceil(20 / spacing).toInt().coerceAtLeast(1)
        val last = floor(min(10000.0, rate * .45) / spacing).toInt()
        val peak = (first..last).maxBy { amp[it] }
        var total = 0.0
        var low = 0.0
        var mid = 0.0
        var peakPower = 0.0
        for (i in first..last) {
            val power = amp[i].toDouble().pow(2)
            total += power
            if (i * spacing < 100) low += power
            if (i * spacing in 80.0..500.0) mid += power
            if (abs(i - peak) <= 2) peakPower += power
        }
        val a = ln(amp[peak - 1].toDouble().coerceAtLeast(1e-12))
        val b = ln(amp[peak].toDouble().coerceAtLeast(1e-12))
        val c = ln(amp[peak + 1].toDouble().coerceAtLeast(1e-12))
        val denominator = a - 2 * b + c
        val offset =
            if (denominator < -1e-9) (.5 * (a - c) / denominator).coerceIn(-.5, .5) else 0.0
        return Feature(
            (peak + offset) * spacing,
            peakPower / total.coerceAtLeast(1e-20) > .35,
            low / total.coerceAtLeast(1e-20),
            mid / total.coerceAtLeast(1e-20),
            window.count { abs(it) >= .999f } > size / 100,
            window.all { abs(it) < 1e-6f },
        )
    }

    private fun summarize(): NoiseReference {
        val seconds = (samples / rate).toInt()
        val recent = history.takeLast(max(1, rate * 8 / hop))
        if (recent.count { it.clipped } > recent.size / 4)
            return NoiseReference(seconds, "输入过载，暂无法判断", "削波会产生额外频率成分", "将手机远离强声源，待输入恢复后继续观察。")
        if (recent.count { it.silent } > recent.size * .8)
            return NoiseReference(seconds, "尚未检测到可用特征", "这不代表环境中没有噪声", "检查麦克风输入，等目标声音出现后继续观察。")
        if (seconds < 8)
            return NoiseReference(seconds, "正在积累声音特征", "持续观察后给出参考", "保持手机不动，避免说话或触碰麦克风。")
        val tones = recent.filter { it.tonal && !it.silent && !it.clipped }
        if (tones.size >= recent.size * .7) {
            val sorted = tones.map { it.hz }.sorted()
            val hz = sorted[sorted.size / 2]
            val spread =
                sorted[(sorted.size * .9).toInt().coerceAtMost(sorted.lastIndex)] -
                    sorted[(sorted.size * .1).toInt()]
            val tolerance = max(rate.toDouble() / size * 2, hz * .025)
            val label = "约 ${hz.roundToInt()} Hz"
            if (spread > tolerance * 2)
                return NoiseReference(
                    seconds,
                    "主要频率随时间变化",
                    "变速设备、柔性部件或多个声音交替出现",
                    "观察变化是否与设备负荷、用水或可听节奏同步。",
                )
            if (hz < 35)
                return NoiseReference(
                    seconds,
                    "$label 低频成分",
                    "低频设备振动；手机响应可能影响结果",
                    "对照风机等设备运行时段，并用其他测量设备复核低频。",
                )
            val mains = abs(hz - (hz / 50).roundToInt() * 50) <= tolerance && hz < 350
            if (mains)
                return NoiseReference(
                    seconds,
                    "$label 持续窄带声",
                    "风扇、泵等电机或电气设备",
                    "保持手机位置不变，逐一观察可控制设备启停时声音是否消失。",
                )
            if (hz in 150.0..500.0)
                return NoiseReference(
                    seconds,
                    "$label 持续窄带声",
                    "阀门、管路或稳定运行的机械设备",
                    "先留意声音是否随用水出现，再排查附近持续运行的设备。",
                )
            if (hz > 1000)
                return NoiseReference(
                    seconds,
                    "$label 持续高频声",
                    "电器啸叫或其他稳定高频声源",
                    "逐一对照附近电器的开关，观察这一声音是否同步变化。",
                )
            return NoiseReference(
                seconds,
                "$label 持续窄带声",
                "压缩机、风机或其他旋转设备",
                "对照空调、冰箱和泵的运行时段；单个频率不能区分具体设备。",
            )
        }
        if (recent.map { it.low }.average() > .6)
            return NoiseReference(
                seconds,
                "低频宽带能量较明显",
                "管道、气流或其他低频振动",
                "留意用水、通风和设备启停时的变化，避免把碰手机产生的声音计入。",
            )
        if (recent.map { it.mid }.average() > .65)
            return NoiseReference(seconds, "中低频宽带能量较明显", "气流、风道或机械背景声", "比较通风设备开关前后的变化，并保持手机位置不变。")
        return NoiseReference(
            seconds,
            "声音较复杂，暂无法区分",
            "多个声音或间歇成分可能叠加",
            "在目标声音更清晰时继续观察，也可参考寻源指南逐项排查。",
        )
    }
}
