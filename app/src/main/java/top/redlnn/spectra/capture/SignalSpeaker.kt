package top.redlnn.spectra.capture

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import kotlin.math.max

/** Streams the same mono PCM used for analysis through the media volume channel. */
class SignalSpeaker(context: Context, sampleRate: Int) : AutoCloseable {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    private val track =
        AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setBufferSizeInBytes(
                max(
                    sampleRate / 5 * 4,
                    AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_FLOAT,
                    ),
                )
            )
            .build()
    @Volatile private var allowed = false
    @Volatile private var closed = false
    var framesWritten = 0L
        private set

    val playing
        get() = allowed && !closed

    private val focus =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(
                { change ->
                    if (!closed) {
                        allowed = change == AudioManager.AUDIOFOCUS_GAIN
                        if (allowed) runCatching { track.play() }
                        else
                            runCatching {
                                track.pause()
                                track.flush()
                            }
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            .build()

    init {
        try {
            check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                "未获得媒体音频焦点"
            }
            allowed = true
            track.play()
        } catch (e: Exception) {
            track.release()
            manager.abandonAudioFocusRequest(focus)
            throw e
        }
    }

    fun write(samples: FloatArray, count: Int) {
        var offset = 0
        while (offset < count && playing) {
            val written = track.write(samples, offset, count - offset, AudioTrack.WRITE_BLOCKING)
            if (!playing) return
            check(written > 0) { "媒体音频输出中断（$written）" }
            offset += written
            framesWritten += written
        }
    }

    fun interrupt() {
        allowed = false
        runCatching {
            track.pause()
            track.flush()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        interrupt()
        runCatching { track.stop() }
        track.release()
        manager.abandonAudioFocusRequest(focus)
    }
}
