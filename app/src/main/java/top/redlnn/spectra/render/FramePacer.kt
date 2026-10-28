package top.redlnn.spectra.render

/** Keep phase across vsync jitter; dropping a late frame never shifts every later deadline. */
class FramePacer {
    private var next = 0L
    private var previousFps = 0

    fun due(now: Long, fps: Int): Boolean {
        if (fps != previousFps) {
            previousFps = fps
            next = now
        }
        val interval = 1_000_000_000L / fps.coerceIn(1, 120)
        if (now + 1_000_000L < next) return false
        next += interval
        if (next < now - interval) next = now + interval
        return true
    }
}
