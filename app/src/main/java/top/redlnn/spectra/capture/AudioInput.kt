package top.redlnn.spectra.capture

interface AudioInput : AutoCloseable {
    val sampleRate: Int
    val description: String
    val paced: Boolean
        get() = false

    val timestampNs: Long
        get() = 0

    fun read(destination: FloatArray, count: Int): Int

    fun interrupt() {}
}
