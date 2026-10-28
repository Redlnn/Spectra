package top.redlnn.spectra.capture

import kotlin.math.min

class PcmRing(private val capacity: Int) {
    private val data = FloatArray(capacity)
    private var index = 0
    var count = 0
        private set

    fun add(input: FloatArray, length: Int) {
        for (i in 0 until length) {
            data[index] = input[i]
            index = (index + 1) % capacity
        }
        count = min(capacity, count + length)
    }

    fun latest(destination: FloatArray) {
        val start = (index - destination.size + capacity) % capacity
        for (i in destination.indices) destination[i] = data[(start + i) % capacity]
    }

    @Synchronized
    fun copy(): FloatArray =
        FloatArray(count).also { output ->
            val start = (index - count + capacity) % capacity
            for (i in output.indices) output[i] = data[(start + i) % capacity]
        }
}
