package top.redlnn.spectra

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.redlnn.spectra.capture.*
import top.redlnn.spectra.dsp.*
import top.redlnn.spectra.render.SpectrogramHistory
import top.redlnn.spectra.settings.*

data class AnalyzerState(
    val running: Boolean = false,
    val starting: Boolean = true,
    val message: String = "正在初始化音频输入…",
    val error: Boolean = false,
    val actualRate: Int = 48000,
    val processingMs: Double = 0.0,
    val frames: Long = 0,
    val needsMicrophonePermission: Boolean = false,
    val renderFps: Double = 0.0,
    val analysisHz: Double = 0.0,
    val playbackActive: Boolean = false,
    val outputFrames: Long = 0,
)

private data class PresentationSnapshot(
    val generation: Long,
    val frame: AnalysisFrame,
    val message: String,
    val processingMs: Double,
    val frames: Long,
    val analysisHz: Double,
    val playbackActive: Boolean,
    val outputFrames: Long,
)

class AnalyzerViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableSettings = MutableStateFlow(AnalyzerSettings())
    val settings = mutableSettings.asStateFlow()
    private val mutableState = MutableStateFlow(AnalyzerState())
    val state = mutableState.asStateFlow()
    private val mutableFrame = MutableStateFlow<AnalysisFrame?>(null)
    val frame = mutableFrame.asStateFlow()
    val history = SpectrogramHistory()
    private val mutableHistoryRevision = MutableStateFlow(0L)
    val historyRevision = mutableHistoryRevision.asStateFlow()
    private val mutableNotices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices = mutableNotices.asSharedFlow()
    private val store = SettingsStore(application)
    private val restartMutex = Mutex()
    private var job: Job? = null
    private var saving: Job? = null
    private var restartRequest: Job? = null
    @Volatile private var generation = 0L
    @Volatile private var activeInput: AudioInput? = null
    @Volatile private var activeSpeaker: SignalSpeaker? = null
    @Volatile private var recording: PcmRing? = null
    @Volatile private var latestSnapshot: PresentationSnapshot? = null

    fun chartFrame(): AnalysisFrame? = frame.value

    fun reportRenderRate(fps: Double) {
        mutableState.update { it.copy(renderFps = fps) }
    }

    private var initialized = false
    private var foreground = false
    @Volatile private var wanted = false
    private val resetPeakRequested = AtomicBoolean(false)
    private val audioManager = application.getSystemService(AudioManager::class.java)
    private val routeCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>) {
                routeChanged()
            }

            override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>) {
                routeChanged()
            }
        }

    init {
        audioManager.registerAudioDeviceCallback(
            routeCallback,
            android.os.Handler(android.os.Looper.getMainLooper()),
        )
        viewModelScope.launch {
            mutableSettings.value = store.load()
            initialized = true
            wanted = settings.value.startOn
            if (foreground && wanted) restart()
            else mutableState.value = AnalyzerState(starting = false, message = "已暂停 · 点击开始分析")
        }
        viewModelScope.launch {
            combine(
                    settings.map { it.fps }.distinctUntilChanged(),
                    state.map { it.running && !it.starting }.distinctUntilChanged(),
                ) { fps, active ->
                    fps to active
                }
                .collectLatest { (fps, active) ->
                    if (active) {
                        val interval = 1_000_000_000L / fps.coerceIn(10, 60)
                        var deadline = System.nanoTime()
                        while (currentCoroutineContext().isActive) {
                            publishLatest()
                            val now = System.nanoTime()
                            deadline += interval
                            if (deadline < now) deadline = now + interval
                            delay(((deadline - now + 999_999L) / 1_000_000L).coerceAtLeast(1))
                        }
                    }
                }
        }
    }

    private fun publishLatest() {
        val latest = latestSnapshot ?: return
        if (latest.generation != generation || !state.value.running || state.value.starting) return
        mutableFrame.value = latest.frame
        mutableState.update { current ->
            current.copy(
                message = latest.message,
                actualRate = latest.frame.sampleRate,
                processingMs = latest.processingMs,
                frames = latest.frames,
                analysisHz = latest.analysisHz,
                playbackActive = latest.playbackActive,
                outputFrames = latest.outputFrames,
            )
        }
    }

    private fun routeChanged() {
        if (initialized && settings.value.input == InputSource.MICROPHONE && wanted && foreground)
            restart()
    }

    fun foreground(active: Boolean) {
        foreground = active
        if (!initialized) return
        if (active && wanted) restart() else if (!active) stop("后台已停止输入 · 返回后恢复")
    }

    fun toggle() {
        wanted = !state.value.running && !state.value.starting
        if (wanted) restart() else stop("已冻结 · 可检查历史与光标")
    }

    fun update(next: AnalyzerSettings) {
        val previous = settings.value
        mutableSettings.value = next
        saving?.cancel()
        saving = viewModelScope.launch {
            delay(200)
            store.save(next)
        }
        if (previous.mapping != next.mapping) history.clear()
        if (previous.processingKey() != next.processingKey()) {
            generation++
            mutableFrame.value = null
            latestSnapshot = null
            history.clear()
            if (foreground && wanted) restart()
        }
    }

    fun selectInput(input: InputSource) {
        val same = settings.value.input == input
        wanted = true
        update(settings.value.copy(input = input))
        if (same && foreground) restart()
    }

    fun permissionDenied() {
        wanted = false
        stop("麦克风权限未授权 · 可使用测试信号", true)
    }

    fun resetPeaks() {
        resetPeakRequested.set(true)
        fun cleared(frame: AnalysisFrame) =
            frame.copy(peaksDb = FloatArray(frame.peaksDb.size) { -180f })
        mutableFrame.value = frame.value?.let(::cleared)
        latestSnapshot = latestSnapshot?.let { it.copy(frame = cleared(it.frame)) }
        mutableNotices.tryEmit("峰值保持已重置")
    }

    fun resetHistory() {
        history.clear()
        mutableHistoryRevision.value++
        mutableNotices.tryEmit("声谱图历史已清空")
    }

    fun saveProfile() {
        viewModelScope.launch {
            store.saveProfile(settings.value)
            mutableNotices.emit("预设已保存到本机")
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            store.profile()?.let {
                update(it)
                mutableNotices.emit("已载入保存的预设")
            } ?: mutableNotices.emit("还没有保存的预设")
        }
    }

    fun pcmSnapshot(): Pair<Int, FloatArray>? = recording?.let {
        state.value.actualRate to it.copy()
    }

    private fun stop(message: String, error: Boolean = false) {
        if (state.value.running)
            latestSnapshot
                ?.takeIf { it.generation == generation }
                ?.let { mutableFrame.value = it.frame }
        generation++
        restartRequest?.cancel()
        activeSpeaker?.interrupt()
        activeInput?.interrupt()
        job?.cancel()
        mutableState.value =
            state.value.copy(
                running = false,
                starting = false,
                message = message,
                error = error,
                needsMicrophonePermission = false,
                playbackActive = false,
            )
    }

    private fun restart() {
        val token = ++generation
        restartRequest?.cancel()
        restartRequest = viewModelScope.launch {
            restartMutex.withLock {
                activeSpeaker?.interrupt()
                activeInput?.interrupt()
                job?.cancelAndJoin()
                if (!foreground || !wanted || generation != token) return@withLock
                history.clear()
                mutableFrame.value = null
                latestSnapshot = null
                val config = settings.value
                if (
                    config.input == InputSource.MICROPHONE &&
                        getApplication<Application>()
                            .checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                            PackageManager.PERMISSION_GRANTED
                ) {
                    wanted = false
                    mutableState.value =
                        state.value.copy(
                            running = false,
                            starting = false,
                            error = false,
                            message = "需要麦克风权限",
                            needsMicrophonePermission = true,
                        )
                    return@withLock
                }
                mutableState.value =
                    state.value.copy(
                        running = false,
                        starting = true,
                        error = false,
                        message = "正在启动${config.input.label}…",
                    )
                job =
                    viewModelScope.launch(Dispatchers.IO) {
                        var input: AudioInput? = null
                        var speaker: SignalSpeaker? = null
                        try {
                            input =
                                when (config.input) {
                                    InputSource.GENERATOR -> SignalInput(config)
                                    InputSource.MICROPHONE ->
                                        MicrophoneInput(getApplication(), config.sampleRate)
                                }
                            activeInput = input
                            if (config.input == InputSource.GENERATOR && config.generatorPlayback) {
                                speaker =
                                    runCatching {
                                        SignalSpeaker(getApplication(), input.sampleRate)
                                    }
                                        .getOrElse { e ->
                                            mutableNotices.tryEmit("无法播放：${e.message}，分析继续")
                                            null
                                        }
                                activeSpeaker = speaker
                            }
                            val dsp = StftProcessor(config, input.sampleRate)
                            val ring = PcmRing(dsp.bufferSize)
                            val audio = PcmRing(input.sampleRate * 15)
                            recording = audio
                            val samples = FloatArray(min(dsp.hop, 1024))
                            val window = FloatArray(dsp.bufferSize)
                            val origin = System.nanoTime()
                            var total = 0L
                            var sinceFft = 0
                            var frames = 0L
                            ensureActive()
                            if (generation != token) return@launch
                            mutableState.value =
                                AnalyzerState(
                                    running = true,
                                    starting = false,
                                    message = input.description,
                                    actualRate = input.sampleRate,
                                )
                            while (isActive) {
                                val count = input.read(samples, samples.size)
                                ensureActive()
                                if (count <= 0) error("输入未返回 PCM 数据")
                                synchronized(audio) { audio.add(samples, count) }
                                ring.add(samples, count)
                                total += count
                                sinceFft += count
                                speaker?.let { output ->
                                    runCatching { output.write(samples, count) }
                                        .onFailure { e ->
                                            output.close()
                                            speaker = null
                                            activeSpeaker = null
                                            mutableNotices.tryEmit("音频播放已停止：${e.message}，分析继续")
                                        }
                                }
                                val end =
                                    if (input.paced)
                                        origin + total * 1_000_000_000L / input.sampleRate
                                    else input.timestampNs
                                if (input.paced) {
                                    val remaining = end - System.nanoTime()
                                    if (remaining > 0)
                                        delay((remaining / 1_000_000).coerceAtLeast(1))
                                }
                                if (ring.count >= dsp.bufferSize && sinceFft >= dsp.hop) {
                                    sinceFft = 0
                                    ring.latest(window)
                                    if (resetPeakRequested.getAndSet(false)) dsp.resetPeaks()
                                    val before = System.nanoTime()
                                    val result = dsp.process(window, end)
                                    val ms = (System.nanoTime() - before) / 1e6
                                    ensureActive()
                                    if (generation != token) break
                                    history.append(result, settings.value.mapping)
                                    frames++
                                    val now = System.nanoTime()
                                    latestSnapshot =
                                        PresentationSnapshot(
                                            token,
                                            result,
                                            input.description,
                                            ms,
                                            frames,
                                            frames * 1e9 / (now - origin),
                                            speaker?.playing == true,
                                            speaker?.framesWritten ?: 0,
                                        )
                                    if (
                                        config.engine == Engine.MULTI &&
                                            ms > dsp.hop * 1000.0 / input.sampleRate * 2 &&
                                            frames > 20
                                    ) {
                                        withContext(Dispatchers.Main) {
                                            mutableNotices.emit("多分辨率运算超时，已降级为 Enhanced 8192")
                                            update(
                                                settings.value.copy(
                                                    engine = Engine.ENHANCED,
                                                    fftSize = 8192,
                                                )
                                            )
                                        }
                                        break
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (isActive) {
                                wanted = false
                                mutableState.value =
                                    state.value.copy(
                                        running = false,
                                        starting = false,
                                        error = true,
                                        message = e.message ?: "输入失败，请重试",
                                    )
                            }
                        } finally {
                            speaker?.close()
                            if (activeSpeaker === speaker) activeSpeaker = null
                            input?.close()
                            if (activeInput === input) activeInput = null
                        }
                    }
            }
        }
    }

    override fun onCleared() {
        activeSpeaker?.interrupt()
        activeInput?.interrupt()
        audioManager.unregisterAudioDeviceCallback(routeCallback)
        super.onCleared()
    }
}
