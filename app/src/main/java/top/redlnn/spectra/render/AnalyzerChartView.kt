package top.redlnn.spectra.render

import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*
import top.redlnn.spectra.dsp.*
import top.redlnn.spectra.settings.*

/** Vsync-paced Canvas drawing, reusing the most recently published frame between new results. */
class AnalyzerChartView(context: Context) : View(context) {
    var settings = AnalyzerSettings()
    var frame: AnalysisFrame? = null
    var history: SpectrogramHistory? = null
    var onSplitChanged: ((Float) -> Unit)? = null
    var colors = GraphColors()
    val horizontal: Boolean
        get() = width / density >= 600f

    var frameSource: (() -> AnalysisFrame?)? = null
    var onRenderRate: ((Double) -> Unit)? = null
    var running = false
        set(value) {
            if (field != value) {
                field = value
                updateAnimation()
            }
        }

    private val pacer = FramePacer()
    private var scheduled = false
    private var drawCount = 0
    private var rateStart = 0L
    private val clock = Choreographer.getInstance()
    private val tick =
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running || !isAttachedToWindow) {
                    scheduled = false
                    return
                }
                if (pacer.due(frameTimeNanos, settings.fps)) invalidate()
                clock.postFrameCallback(this)
            }
        }

    private fun updateAnimation() {
        if (running && isAttachedToWindow && !scheduled) {
            scheduled = true
            rateStart = 0
            drawCount = 0
            clock.postFrameCallback(tick)
        }
        if (!running && scheduled) {
            clock.removeFrameCallback(tick)
            scheduled = false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        clock.removeFrameCallback(tick)
        scheduled = false
        super.onDetachedFromWindow()
    }

    private val density = resources.displayMetrics.density
    private val fontScale = resources.displayMetrics.scaledDensity
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val plots = mutableListOf<Pair<Int, RectF>>()
    private val panelAreas = mutableListOf<Pair<Int, RectF>>()
    private var zoomMin = Double.NaN
    private var zoomMax = Double.NaN
    private var dbFloor = -120f
    private var waveGain = 1f
    private val waveScale = WaveformScale()
    private var displayedWaveGain = 1f
    private var waveZoom = 1f
    private var historyZoom = 1f
    private var cursorFrequency: Double? = null
    private var cursorAge = 0.0
    private var cursorPanel = 1
    private var movingDivider = false
    private var downX = 0f
    private var downY = 0f
    private var scaling = false
    private val bitmap = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(512 * 256)
    private var cacheKey = ""
    private val scaleDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    scaling = true
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val selected =
                        plots.firstOrNull { it.second.contains(detector.focusX, detector.focusY) }
                            ?: return false
                    val rect = selected.second
                    val bounds = frequencyBounds()
                    val p =
                        ((detector.focusX - rect.left) / rect.width()).coerceIn(0f, 1f).toDouble()
                    val scaleX =
                        if (detector.previousSpanX > 10)
                            detector.currentSpanX / detector.previousSpanX
                        else 1f
                    if (selected.first == 0) waveZoom = (waveZoom * scaleX).coerceIn(1f, 16f)
                    else if (abs(scaleX - 1) > .002) {
                        if (settings.logAxis) {
                            val center = ln(bounds.first) + p * ln(bounds.second / bounds.first)
                            val span =
                                (ln(bounds.second / bounds.first) / scaleX).coerceIn(
                                    .15,
                                    ln(nyquist() / minimum()),
                                )
                            var lo = center - p * span
                            var hi = lo + span
                            if (lo < ln(minimum())) {
                                hi += ln(minimum()) - lo
                                lo = ln(minimum())
                            }
                            if (hi > ln(nyquist())) {
                                lo -= hi - ln(nyquist())
                                hi = ln(nyquist())
                            }
                            zoomMin = exp(lo)
                            zoomMax = exp(hi)
                        } else {
                            val center = bounds.first + p * (bounds.second - bounds.first)
                            val span =
                                ((bounds.second - bounds.first) / scaleX).coerceIn(
                                    50.0,
                                    nyquist() - minimum(),
                                )
                            zoomMin = (center - p * span).coerceIn(minimum(), nyquist() - span)
                            zoomMax = zoomMin + span
                        }
                    }
                    val scaleY =
                        if (detector.previousSpanY > 10)
                            detector.currentSpanY / detector.previousSpanY
                        else 1f
                    when (selected.first) {
                        0 -> waveGain = (waveGain * scaleY).coerceIn(.25f, 16f)
                        1 -> dbFloor = (dbFloor / scaleY).coerceIn(-180f, -20f)
                        2 -> historyZoom = (historyZoom * scaleY).coerceIn(.25f, 16f)
                    }
                    invalidate()
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    scaling = false
                }
            },
        )

    init {
        isClickable = true
        contentDescription = "实时分析图，双指缩放频率和幅度，点击查看频率、幅度或历史读数，双击重置缩放"
    }

    fun resetViewport() {
        zoomMin = Double.NaN
        zoomMax = Double.NaN
        dbFloor = -120f
        waveGain = 1f
        waveZoom = 1f
        historyZoom = 1f
        cursorFrequency = null
        invalidate()
    }

    private fun visibleHistorySeconds() =
        (settings.duration.toDouble() / historyZoom).coerceIn(.5, 15.0)

    fun viewportMetadata(): String {
        val (lo, hi) = frequencyBounds()
        return String.format(
            java.util.Locale.US,
            "f_min_hz=%.8f; f_max_hz=%.8f; spectrum_floor_dbfs=%.3f; wave_gain=%.3f; wave_zoom=%.3f; visible_history_s=%.3f; horizontal=%b; split=%.4f; width_px=%d; height_px=%d; density=%.4f; cursor_hz=%s; cursor_age_s=%.4f",
            lo,
            hi,
            dbFloor,
            displayedWaveGain,
            waveZoom,
            visibleHistorySeconds(),
            horizontal,
            settings.split,
            width,
            height,
            density,
            cursorFrequency?.toString() ?: "none",
            cursorAge,
        )
    }

    private fun dp(value: Float) = value * density

    private fun minimum() = 31.25

    private fun nyquist() = (frame?.sampleRate ?: settings.sampleRate) / 2.0

    private fun frequencyBounds(): Pair<Double, Double> {
        if (zoomMin.isNaN()) return minimum() to nyquist()
        val lo = zoomMin.coerceIn(minimum(), nyquist() - 1)
        return lo to zoomMax.coerceIn(lo + 1, nyquist())
    }

    private fun frequency(x: Float, rect: RectF): Double {
        val (lo, hi) = frequencyBounds()
        val ratio = ((x - rect.left) / rect.width()).coerceIn(0f, 1f).toDouble()
        return if (settings.logAxis) lo * (hi / lo).pow(ratio) else lo + (hi - lo) * ratio
    }

    private fun frequencyX(f: Double, rect: RectF): Float {
        val (lo, hi) = frequencyBounds()
        val ratio =
            if (settings.logAxis) ln(f.coerceAtLeast(lo) / lo) / ln(hi / lo)
            else (f - lo) / (hi - lo)
        return rect.left + rect.width() * ratio.toFloat()
    }

    private val ink
        get() = colors.ink

    private val dim
        get() = colors.dim

    private val grid
        get() = colors.grid

    private val accent
        get() = colors.live

    private fun text(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        color: Int = ink,
        size: Float = 10f,
        align: Paint.Align = Paint.Align.LEFT,
        bold: Boolean = false,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size * fontScale
        paint.textAlign = align
        paint.typeface =
            if (bold) Typeface.create("sans-serif", Typeface.BOLD)
            else Typeface.create("sans-serif", Typeface.NORMAL)
        canvas.drawText(text, x, y, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (running) {
            frameSource?.invoke()?.let { frame = it }
            val now = System.nanoTime()
            if (rateStart == 0L) rateStart = now
            drawCount++
            if (now - rateStart >= 1_000_000_000L) {
                onRenderRate?.invoke(drawCount * 1e9 / (now - rateStart))
                rateStart = now
                drawCount = 0
            }
        }
        val modes = settings.view.panels
        val space = dp(12f)
        plots.clear()
        panelAreas.clear()
        if (modes.size == 1)
            panel(canvas, modes[0], RectF(0f, 0f, width.toFloat(), height.toFloat()))
        else if (horizontal) {
            val divider = (width - space) * settings.split
            panel(canvas, modes[0], RectF(0f, 0f, divider, height.toFloat()))
            panel(canvas, modes[1], RectF(divider + space, 0f, width.toFloat(), height.toFloat()))
            paint.color = dim
            paint.strokeWidth = dp(3f)
            canvas.drawLine(
                divider + space / 2,
                height / 2f - dp(14f),
                divider + space / 2,
                height / 2f + dp(14f),
                paint,
            )
        } else {
            val divider = (height - space) * settings.split
            panel(canvas, modes[0], RectF(0f, 0f, width.toFloat(), divider))
            panel(canvas, modes[1], RectF(0f, divider + space, width.toFloat(), height.toFloat()))
            paint.color = dim
            paint.strokeWidth = dp(3f)
            canvas.drawLine(
                width / 2f - dp(14f),
                divider + space / 2,
                width / 2f + dp(14f),
                divider + space / 2,
                paint,
            )
        }
    }

    private fun panel(canvas: Canvas, mode: Int, box: RectF) {
        panelAreas.add(mode to box)
        paint.style = Paint.Style.FILL
        paint.color = colors.panel
        canvas.drawRoundRect(box, dp(18f), dp(18f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = grid
        canvas.drawRoundRect(box, dp(18f), dp(18f), paint)
        val names = arrayOf("时域波形", "幅度频谱", "历史声谱图")
        val english = arrayOf("WAVEFORM", "SPECTRUM", "SPECTROGRAM")
        text(canvas, names[mode], box.left + dp(14f), box.top + dp(24f), ink, 13f, bold = true)
        if (box.width() >= dp(250f))
            text(
                canvas,
                english[mode],
                box.right - dp(14f),
                box.top + dp(23f),
                dim,
                9f,
                Paint.Align.RIGHT,
            )
        val plot =
            RectF(
                box.left + dp(43f),
                box.top + dp(53f),
                box.right - dp(if (mode == 2) 32f else 16f),
                box.bottom - dp(27f),
            )
        if (plot.height() <= dp(20f) || plot.width() <= dp(30f)) return
        plots.add(mode to plot)
        when (mode) {
            0 -> {
                text(
                    canvas,
                    "${if(settings.waveAutoScale) "自动量程 · " else ""}幅度 FS · ${"%.1f".format((frame?.waveform?.size ?: 2048) / waveZoom * 1000.0 / (frame?.sampleRate ?: 48000))} ms",
                    box.left + dp(14f),
                    box.top + dp(41f),
                    dim,
                )
                wave(canvas, plot)
            }
            1 -> {
                if (box.width() >= dp(300f)) {
                    text(canvas, "● 实时", box.left + dp(14f), box.top + dp(41f), accent)
                    if (settings.peakHold)
                        text(canvas, "● 保持", box.left + dp(69f), box.top + dp(41f), colors.hold)
                }
                frame?.let { f ->
                    visiblePeak(f)?.let { peak ->
                        text(
                            canvas,
                            "${"%.1f".format(peak.second)} dBFS · ${formatHz(peak.first)} Hz",
                            box.right - dp(14f),
                            box.top + dp(41f),
                            ink,
                            if (box.width() < dp(300f)) 9f else 10f,
                            Paint.Align.RIGHT,
                        )
                    }
                }
                spectrum(canvas, plot)
            }
            2 -> {
                text(
                    canvas,
                    "${"%.1f".format(visibleHistorySeconds())} s · 最新 ↓ · ${spectrogramColorLabel(settings.colorMap)}",
                    box.left + dp(14f),
                    box.top + dp(41f),
                    dim,
                )
                if (box.width() >= dp(300f))
                    text(
                        canvas,
                        "${settings.floorDb.toInt()} … ${settings.ceilingDb.toInt()} dBFS",
                        box.right - dp(14f),
                        box.top + dp(41f),
                        dim,
                        9f,
                        Paint.Align.RIGHT,
                    )
                spectrogram(canvas, plot)
            }
        }
        if (frame == null)
            text(canvas, "等待音频数据", plot.centerX(), plot.centerY(), dim, 12f, Paint.Align.CENTER)
        cursor(canvas, mode, plot)
    }

    private fun frequencyGrid(canvas: Canvas, rect: RectF, lines: Boolean = true) {
        val (min, max) = frequencyBounds()
        val ticks =
            if (settings.logAxis)
                listOf(
                    10.0,
                    20.0,
                    31.25,
                    62.5,
                    125.0,
                    250.0,
                    500.0,
                    1000.0,
                    2000.0,
                    4000.0,
                    8000.0,
                    16000.0,
                )
            else (0..4).map { min + (max - min) * it / 4 }
        var previous = -1000f
        for (f in ticks.filter { it >= min && it <= max }) {
            val x = frequencyX(f, rect)
            if (lines) {
                paint.color = grid
                paint.strokeWidth = dp(.6f)
                canvas.drawLine(x, rect.top, x, rect.bottom, paint)
            }
            val label = formatHz(f)
            paint.textSize = 9 * fontScale
            if (x - previous >= paint.measureText(label) + dp(12f)) {
                text(
                    canvas,
                    label,
                    x,
                    rect.bottom + dp(17f),
                    dim,
                    9f,
                    if (x - rect.left < dp(12f)) Paint.Align.LEFT else Paint.Align.CENTER,
                )
                previous = x
            }
        }
        text(canvas, "Hz", rect.left - dp(8f), rect.bottom + dp(17f), dim, 9f, Paint.Align.RIGHT)
    }

    private fun spectrum(canvas: Canvas, rect: RectF) {
        frequencyGrid(canvas, rect)
        val ticks = (rect.height() / dp(24f)).toInt().coerceIn(2, 6)
        for (i in 0..ticks) {
            val db = dbFloor * i / ticks
            val y = rect.top + rect.height() * i / ticks
            paint.color = grid
            paint.strokeWidth = dp(.6f)
            canvas.drawLine(rect.left, y, rect.right, y, paint)
            text(
                canvas,
                (db + if (settings.positiveDb) 100 else 0).roundToInt().toString(),
                rect.left - dp(8f),
                y + dp(3f),
                dim,
                9f,
                Paint.Align.RIGHT,
            )
        }
        canvas.save()
        canvas.rotate(-90f, rect.left - dp(32f), rect.centerY())
        text(
            canvas,
            if (settings.positiveDb) "+100" else "dBFS",
            rect.left - dp(32f),
            rect.centerY(),
            dim,
            8f,
            Paint.Align.CENTER,
        )
        canvas.restore()
        val f = frame ?: return
        fun y(db: Float) = rect.top + rect.height() * (db / dbFloor).coerceIn(0f, 1f)
        canvas.save()
        canvas.clipRect(rect)
        val points = (rect.width() / dp(1f)).roundToInt().coerceIn(64, 700)
        fun value(values: FloatArray, hz: Double, x: Int): Float {
            val left = frequency(rect.left + rect.width() * (x - .5f) / points, rect) / f.spacingHz
            val right = frequency(rect.left + rect.width() * (x + .5f) / points, rect) / f.spacingHz
            return displayValue(values, hz, f, left, right)
        }
        if (settings.peakHold) {
            path.reset()
            for (i in 0..points) {
                val x = rect.left + rect.width() * i / points
                val yy = y(value(f.peaksDb, frequency(x, rect), i))
                if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = colors.hold
            canvas.drawPath(path, paint)
        }
        path.reset()
        path.moveTo(rect.left, rect.bottom)
        for (i in 0..points) {
            val x = rect.left + rect.width() * i / points
            path.lineTo(x, y(value(f.spectrumDb, frequency(x, rect), i)))
        }
        path.lineTo(rect.right, rect.bottom)
        path.close()
        paint.style = Paint.Style.FILL
        paint.shader =
            LinearGradient(
                0f,
                rect.top,
                0f,
                rect.bottom,
                intArrayOf((colors.live and 0x00FFFFFF) or (65 shl 24), colors.live and 0x00FFFFFF),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawPath(path, paint)
        paint.shader = null
        val liveColor = colors.live
        paint.shader =
            if (colors.live != colors.liveEnd)
                LinearGradient(
                    rect.left,
                    0f,
                    rect.right,
                    0f,
                    intArrayOf(colors.live, colors.liveEnd),
                    null,
                    Shader.TileMode.CLAMP,
                )
            else null
        if (
            settings.spectrumPalette == SpectrumPalette.GREEN ||
                settings.spectrumPalette == SpectrumPalette.YELLOW
        ) {
            paint.color = liveColor
            paint.strokeWidth = dp(1.4f)
            for (i in 0..points step 3) {
                val x = rect.left + rect.width() * i / points
                canvas.drawLine(
                    x,
                    y(value(f.spectrumDb, frequency(x, rect), i)),
                    x,
                    rect.bottom,
                    paint,
                )
            }
        }
        path.reset()
        for (i in 0..points) {
            val x = rect.left + rect.width() * i / points
            val yy = y(value(f.spectrumDb, frequency(x, rect), i))
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        paint.color = liveColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        canvas.drawPath(path, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
        canvas.restore()
        if (settings.showPeakMarker) peakMarker(canvas, rect, f)
    }

    private fun displayValue(
        values: FloatArray,
        hz: Double,
        f: AnalysisFrame,
        left: Double = hz / f.spacingHz,
        right: Double = left,
    ): Float {
        if (settings.octaveBands > 0) {
            val b = settings.octaveBands
            val center = 1000 * 2.0.pow(round(b * log2(hz / 1000)) / b)
            val factor = 2.0.pow(1.0 / (2 * b))
            return Levels.band(
                values,
                center / factor / f.spacingHz,
                center * factor / f.spacingHz,
                settings.octaveMax,
                fillEmpty = false,
            )
        }
        return if (settings.mapping == Mapping.BAND_POWER) Levels.pixelPower(values, left, right)
        else Levels.sample(values, hz / f.spacingHz, settings.mapping)
    }

    private fun visiblePeak(f: AnalysisFrame): Pair<Double, Float>? {
        val (minimum, maximum) = frequencyBounds()
        if (settings.octaveBands == 0)
            return SpectrumPeak.find(f.spectrumDb, f.spacingHz, minimum, maximum)?.let {
                it.hz to it.db
            }
        val b = settings.octaveBands
        val low = ceil(b * log2(minimum / 1000)).toInt()
        val high = floor(b * log2(maximum / 1000)).toInt()
        var frequency = 1000.0
        var peak = -180f
        for (i in low..high) {
            val center = 1000 * 2.0.pow(i.toDouble() / b)
            val level = displayValue(f.spectrumDb, center, f)
            if (level > peak) {
                peak = level
                frequency = center
            }
        }
        return if (peak > -150) frequency to peak else null
    }

    private fun peakMarker(canvas: Canvas, rect: RectF, frame: AnalysisFrame) {
        val peak = visiblePeak(frame) ?: return
        val x = frequencyX(peak.first, rect)
        val y = rect.top + rect.height() * (peak.second / dbFloor).coerceIn(0f, 1f)
        paint.color = colors.peak
        paint.strokeWidth = dp(1.2f)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x, rect.top, x, rect.bottom, paint)
        canvas.drawCircle(x, y, dp(3f), paint)
        val label = "峰值 ${formatHz(peak.first)} Hz  ${"%.1f".format(peak.second)} dBFS"
        paint.textSize = 10 * fontScale
        val size =
            (10f * ((rect.width() - dp(12f)) / paint.measureText(label)).coerceAtMost(1f))
                .coerceAtLeast(6f)
        paint.textSize = size * fontScale
        val width = paint.measureText(label) + dp(12f)
        val left = (x - width / 2).coerceIn(rect.left, max(rect.left, rect.right - width))
        val top =
            (y - dp(30f)).coerceIn(rect.top + dp(4f), max(rect.top + dp(4f), rect.bottom - dp(26f)))
        paint.style = Paint.Style.FILL
        paint.color = colors.panel
        canvas.drawRoundRect(RectF(left, top, left + width, top + dp(22f)), dp(6f), dp(6f), paint)
        text(canvas, label, left + dp(6f), top + dp(15f), colors.peak, size)
    }

    private fun wave(canvas: Canvas, rect: RectF) {
        val f = frame
        displayedWaveGain =
            waveGain *
                if (settings.waveAutoScale && f != null) waveScale.gain(f.waveform, f.timestampNs)
                else 1f
        val ticks = (rect.height() / dp(24f)).toInt().coerceIn(2, 4)
        for (i in 0..ticks) {
            val value = (1f - 2f * i / ticks) / displayedWaveGain
            val y = rect.top + rect.height() * i / ticks
            paint.color = grid
            paint.strokeWidth = dp(.6f)
            canvas.drawLine(rect.left, y, rect.right, y, paint)
            val label =
                if (1f / displayedWaveGain < .01f && value != 0f) "%.1e".format(value)
                else if (1f / displayedWaveGain < .1f) "%.3f".format(value)
                else "%.2f".format(value)
            text(canvas, label, rect.left - dp(6f), y + dp(3f), dim, 9f, Paint.Align.RIGHT)
        }
        if (f == null) return
        val count = (f.waveform.size / waveZoom).roundToInt().coerceAtLeast(8)
        val offset = f.waveform.size - count
        val duration = count * 1000.0 / f.sampleRate
        for (i in 0..4) {
            val x = rect.left + rect.width() * i / 4
            paint.color = grid
            canvas.drawLine(x, rect.top, x, rect.bottom, paint)
            text(
                canvas,
                "%.0f".format(duration * i / 4),
                x,
                rect.bottom + dp(17f),
                dim,
                9f,
                Paint.Align.CENTER,
            )
        }
        text(canvas, "ms", rect.left - dp(8f), rect.bottom + dp(17f), dim, 9f, Paint.Align.RIGHT)
        path.reset()
        val step = max(1, count / rect.width().roundToInt().coerceAtLeast(1))
        for (i in 0 until count step step) {
            val x = rect.left + rect.width() * i / (count - 1)
            val y = rect.centerY() - f.waveform[offset + i] * displayedWaveGain * rect.height() / 2
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.save()
        canvas.clipRect(rect)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(settings.waveThickness)
        paint.color = colors.wave
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun spectrogram(canvas: Canvas, rect: RectF) {
        val data = history ?: return
        val rows = data.snapshot()
        val current = frame?.timestampNs ?: 0
        val duration = visibleHistorySeconds()
        val key =
            "${data.version}/${settings.colorMap}/${settings.floorDb}/${settings.ceilingDb}/$duration/${frequencyBounds()}/${settings.logAxis}/${colors.dark}"
        if (key != cacheKey) {
            pixels.fill(if (colors.dark) Color.rgb(4, 8, 16) else Color.rgb(239, 243, 248))
            val lut = IntArray(256) { color(it / 255f, settings.colorMap) }
            val indices =
                IntArray(512) { x ->
                    val hz = frequency(rect.left + rect.width() * x / 511, rect)
                    (ln(hz / 10) / ln(data.maxFrequency / 10) * (data.width - 1))
                        .roundToInt()
                        .coerceIn(0, data.width - 1)
                }
            var index = 0
            for (y in 0 until 256) {
                val timestamp = current - ((255 - y) / 255.0 * duration * 1e9).toLong()
                while (index < rows.lastIndex && rows[index + 1].timestampNs <= timestamp) index++
                val row = rows.getOrNull(index)
                if (
                    row != null &&
                        timestamp >= row.timestampNs &&
                        timestamp - row.timestampNs <
                            max(
                                35_000_000L,
                                frame?.let {
                                    (it.fftSize * (1 - settings.overlap) * 1e9 / it.sampleRate * 2)
                                        .toLong()
                                } ?: 35_000_000L,
                            )
                ) {
                    for (x in 0 until 512) {
                        val u =
                            ((row.values[indices[x]] - settings.floorDb) /
                                    (settings.ceilingDb - settings.floorDb))
                                .coerceIn(0f, 1f)
                        pixels[y * 512 + x] = lut[(u * 255).roundToInt()]
                    }
                }
            }
            bitmap.setPixels(pixels, 0, 512, 0, 0, 512, 256)
            cacheKey = key
        }
        paint.isFilterBitmap = settings.bilinear
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, null, rect, paint)
        frequencyGrid(canvas, rect, false)
        val ticks = (rect.height() / dp(24f)).toInt().coerceIn(2, 3)
        for (i in 0..ticks) {
            val seconds = duration * (ticks - i) / ticks
            val y = rect.top + rect.height() * i / ticks
            text(
                canvas,
                (if (duration < 4) "%.1f" else "%.0f").format(seconds) + if (i == 0) " s" else "",
                rect.left - dp(8f),
                y + dp(3f),
                dim,
                9f,
                Paint.Align.RIGHT,
            )
        }
        val bar = RectF(rect.right + dp(9f), rect.top, rect.right + dp(14f), rect.bottom)
        for (i in 0 until 64) {
            paint.color = color(1 - i / 63f, settings.colorMap)
            canvas.drawRect(
                bar.left,
                bar.top + bar.height() * i / 64,
                bar.right,
                bar.top + bar.height() * (i + 1) / 64 + 1,
                paint,
            )
        }
    }

    private fun cursor(canvas: Canvas, mode: Int, rect: RectF) {
        val frequency = cursorFrequency ?: return
        if (mode == 0) return
        cursorAge = cursorAge.coerceIn(0.0, visibleHistorySeconds())
        val f = frame ?: return
        val x = frequencyX(frequency, rect)
        if (x < rect.left || x > rect.right) return
        val db =
            if (mode == 2) {
                val rows = history?.snapshot().orEmpty()
                val t = f.timestampNs - (cursorAge * 1e9).toLong()
                rows.minByOrNull { abs(it.timestampNs - t) }?.let { history?.value(it, frequency) }
                    ?: -180f
            } else
                displayValue(
                    f.spectrumDb,
                    frequency,
                    f,
                    this.frequency(x - dp(.5f), rect) / f.spacingHz,
                    this.frequency(x + dp(.5f), rect) / f.spacingHz,
                )
        val y =
            if (mode == 2)
                rect.bottom - (cursorAge / visibleHistorySeconds()).toFloat() * rect.height()
            else rect.top + rect.height() * (db / dbFloor).coerceIn(0f, 1f)
        paint.style = Paint.Style.STROKE
        paint.color = colors.cursor
        paint.strokeWidth = dp(1f)
        canvas.drawLine(x, rect.top, x, rect.bottom, paint)
        if (mode == cursorPanel) {
            canvas.drawLine(rect.left, y, rect.right, y, paint)
            canvas.drawCircle(x, y, dp(4f), paint)
        }
        paint.style = Paint.Style.FILL
        val label =
            "${formatHz(frequency)} Hz  ${"%.1f".format(db)} dBFS" +
                if (mode == 2) "  ${"%.2f".format(cursorAge)} s" else ""
        paint.textSize = 10 * fontScale
        val fontSize =
            (10f * ((rect.width() - dp(12f)) / paint.measureText(label)).coerceAtMost(1f))
                .coerceAtLeast(6f)
        paint.textSize = fontSize * fontScale
        val w = paint.measureText(label) + dp(12f)
        val left = (x - w / 2).coerceIn(rect.left, max(rect.left, rect.right - w))
        val top = (y - dp(29f)).coerceIn(rect.top, rect.bottom - dp(24f))
        paint.color = colors.cursorSurface
        canvas.drawRoundRect(RectF(left, top, left + w, top + dp(23f)), dp(6f), dp(6f), paint)
        text(canvas, label, left + dp(6f), top + dp(16f), colors.onCursor, fontSize)
    }

    private var lastTap = 0L
    private var downPanel: Int? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1 || scaling) {
            parent.requestDisallowInterceptTouchEvent(true)
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downPanel = panelAreas.firstOrNull { it.second.contains(event.x, event.y) }?.first
                movingDivider =
                    settings.view.panels.size == 2 &&
                        if (horizontal) abs(event.x - width * settings.split) < dp(14f)
                        else abs(event.y - height * settings.split) < dp(14f)
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (movingDivider) {
                    val split =
                        (if (horizontal) event.x / width else event.y / height).coerceIn(.2f, .8f)
                    settings = settings.copy(split = split)
                    onSplitChanged?.invoke(split)
                } else selectCursor(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!movingDivider && downPanel != 0) {
                    if (
                        event.eventTime - lastTap < 280 &&
                            abs(event.x - downX) < dp(10f) &&
                            abs(event.y - downY) < dp(10f)
                    )
                        resetViewport()
                    else selectCursor(event.x, event.y)
                    lastTap = event.eventTime
                    performClick()
                }
                movingDivider = false
                parent.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                movingDivider = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun selectCursor(x: Float, y: Float) {
        val (mode, rect) = plots.firstOrNull { it.second.contains(x, y) } ?: return
        if (mode == 0 || downPanel == 0) return
        cursorPanel = mode
        cursorFrequency = frequency(x, rect)
        cursorAge = ((rect.bottom - y) / rect.height()).coerceIn(0f, 1f) * visibleHistorySeconds()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        fun formatHz(f: Double): String =
            when {
                f >= 1000 ->
                    if (f % 1000 < .01) "${(f / 1000).toInt()}k" else "%.1fk".format(f / 1000)
                f < 100 -> "%.1f".format(f)
                else -> "%.0f".format(f)
            }

        fun color(u: Float, map: String): Int {
            val t = u.coerceIn(0f, 1f)
            if (map == "Gray")
                return Color.rgb((255 * t).toInt(), (255 * t).toInt(), (255 * t).toInt())
            if (map == "Jet") {
                fun channel(center: Float) =
                    (255 * (1.5f - abs(4 * t - center)).coerceIn(0f, 1f)).toInt()
                return Color.rgb(channel(3f), channel(2f), channel(1f))
            }
            val colors =
                intArrayOf(
                    Color.rgb(3, 9, 20),
                    Color.rgb(7, 57, 75),
                    Color.rgb(49, 65, 155),
                    Color.rgb(154, 91, 188),
                    Color.rgb(104, 225, 232),
                    Color.rgb(237, 251, 253),
                )
            val index = (t * 5).toInt().coerceAtMost(4)
            val f = t * 5 - index
            val a = colors[index]
            val b = colors[index + 1]
            return Color.rgb(
                (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
                (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
                (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
            )
        }
    }
}
