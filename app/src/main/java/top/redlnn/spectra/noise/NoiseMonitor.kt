package top.redlnn.spectra.noise

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** Bounded PCM handoff. Epoch and sequence checks prevent stale or discontinuous evidence. */
class NoiseMonitor(scope: CoroutineScope) {
    private data class Block(
        val epoch: Long,
        val sequence: Long,
        val rate: Int,
        val pcm: FloatArray,
        val timestampNs: Long,
    )

    private val blocks = Channel<Block>(8)
    private val mutableResult = MutableStateFlow(NoiseReference())
    val result = mutableResult.asStateFlow()
    private var epoch = 0L
    private var sequence = 0L
    private var enabled = false
    private var stream = 0L
    private var accepting = true

    init {
        scope.launch(Dispatchers.Default) {
            var current = -1L
            var expected = 0L
            var rate = 0
            var lastTimestamp = 0L
            var processor: NoiseProcessor? = null
            for (block in blocks) {
                if (block.rate == 0) {
                    processor = null
                    current = -1L
                    lastTimestamp = 0L
                    continue
                }
                if (!isCurrent(block.epoch)) continue
                if (
                    block.epoch != current ||
                        block.sequence != expected ||
                        rate != block.rate ||
                        (lastTimestamp != 0L &&
                            (block.timestampNs <= lastTimestamp ||
                                block.timestampNs - lastTimestamp > 250_000_000L))
                ) {
                    processor = NoiseProcessor(block.rate)
                    current = block.epoch
                    rate = block.rate
                }
                lastTimestamp = block.timestampNs
                expected = block.sequence + 1
                val result = processor!!.accept(block.pcm)
                if (result != null) publish(block.epoch, result)
            }
        }
    }

    @Synchronized
    fun configure(active: Boolean) {
        enabled = active
        reset()
    }

    @Synchronized
    fun reset(captureStream: Long = stream) {
        stream = captureStream
        accepting = true
        epoch++
        sequence = 0
        while (blocks.tryReceive().isSuccess) {}
        blocks.trySend(Block(epoch, 0, 0, FloatArray(0), 0))
        mutableResult.value = NoiseReference()
    }

    @Synchronized
    fun pause() {
        accepting = false
        epoch++
        while (blocks.tryReceive().isSuccess) {}
        blocks.trySend(Block(epoch, 0, 0, FloatArray(0), 0))
    }

    @Synchronized
    fun submit(samples: FloatArray, count: Int, rate: Int, captureStream: Long, timestampNs: Long) {
        if (!enabled || !accepting || captureStream != stream) return
        blocks.trySend(Block(epoch, sequence++, rate, samples.copyOf(count), timestampNs))
    }

    @Synchronized private fun isCurrent(value: Long) = enabled && value == epoch

    @Synchronized
    private fun publish(value: Long, result: NoiseReference) {
        if (isCurrent(value)) mutableResult.value = result
    }
}
