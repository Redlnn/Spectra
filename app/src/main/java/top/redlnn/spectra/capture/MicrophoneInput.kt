package top.redlnn.spectra.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import kotlin.math.*

class MicrophoneInput(context: Context, requestedRate: Int) : AudioInput {
    private val record: AudioRecord
    private val pcm: ShortArray
    private val effects = mutableListOf<android.media.audiofx.AudioEffect>()
    override val sampleRate: Int
    override val description: String
    private var total = 0L
    private val timestamp = AudioTimestamp()
    override var timestampNs = 0L
        private set

    init {
        if (
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        )
            throw SecurityException("尚未授权录音权限 · 点击输入来源选择麦克风以授权")
        var opened: AudioRecord? = null
        var usedSource = MediaRecorder.AudioSource.MIC
        for (rate in listOf(requestedRate, 48000, 44100).distinct()) {
            val minBuffer =
                AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            if (minBuffer <= 0) continue
            val sources =
                if (
                    context
                        .getSystemService(AudioManager::class.java)
                        .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) ==
                        "true"
                )
                    listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)
                else listOf(MediaRecorder.AudioSource.MIC)
            for (source in sources) {
                val candidate =
                    try {
                        AudioRecord.Builder()
                            .setAudioSource(source)
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setSampleRate(rate)
                                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .build()
                            )
                            .setBufferSizeInBytes(max(minBuffer * 2, rate / 5 * 2))
                            .build()
                    } catch (e: SecurityException) {
                        throw SecurityException("录音权限已撤销 · 请重新选择麦克风授权", e)
                    } catch (e: RuntimeException) {
                        null
                    }
                if (candidate?.state == AudioRecord.STATE_INITIALIZED) {
                    opened = candidate
                    usedSource = source
                    break
                }
                candidate?.release()
            }
            if (opened != null) break
        }
        record = opened ?: error("无法初始化麦克风；请连接输入设备或使用信号发生器")
        sampleRate = record.sampleRate
        pcm = ShortArray(32768)
        val session = record.audioSessionId
        if (android.media.audiofx.AutomaticGainControl.isAvailable())
            runCatching {
                android.media.audiofx.AutomaticGainControl.create(session)?.also {
                    it.enabled = false
                    effects += it
                }
            }
        if (android.media.audiofx.NoiseSuppressor.isAvailable())
            runCatching {
                android.media.audiofx.NoiseSuppressor.create(session)?.also {
                    it.enabled = false
                    effects += it
                }
            }
        if (android.media.audiofx.AcousticEchoCanceler.isAvailable())
            runCatching {
                android.media.audiofx.AcousticEchoCanceler.create(session)?.also {
                    it.enabled = false
                    effects += it
                }
            }
        description =
            "${if(usedSource == MediaRecorder.AudioSource.UNPROCESSED) "UNPROCESSED" else "MIC · 系统处理路径"} · ${sampleRate} Hz" +
                if (sampleRate != requestedRate) " · 不支持 $requestedRate Hz，已回退" else ""
        try {
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风未进入录音状态" }
        } catch (e: Exception) {
            effects.forEach { it.release() }
            record.release()
            throw e
        }
    }

    override fun read(destination: FloatArray, count: Int): Int {
        val read = record.read(pcm, 0, count.coerceAtMost(pcm.size), AudioRecord.READ_BLOCKING)
        check(read > 0) { "音频输入中断（$read）；请检查权限或输入设备" }
        for (i in 0 until read) destination[i] = pcm[i] / 32768f
        total += read
        timestampNs =
            if (
                record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC) ==
                    AudioRecord.SUCCESS
            )
                timestamp.nanoTime + (total - timestamp.framePosition) * 1_000_000_000L / sampleRate
            else System.nanoTime()
        return read
    }

    override fun interrupt() {
        runCatching { record.stop() }
    }

    override fun close() {
        interrupt()
        effects.forEach { it.release() }
        record.release()
    }
}
