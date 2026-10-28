package top.redlnn.spectra.render

/** All non-spectrogram colors are resolved once from the app's global theme. */
data class GraphColors(
    val dark: Boolean = true,
    val panel: Int = 0xFF080D15.toInt(),
    val ink: Int = 0xFFB7C3D4.toInt(),
    val dim: Int = 0xFF63748D.toInt(),
    val grid: Int = 0xFF1D2838.toInt(),
    val live: Int = 0xFF5CE0C0.toInt(),
    val hold: Int = 0xFF6C839B.toInt(),
    val wave: Int = 0xFF53E6AE.toInt(),
    val cursor: Int = 0xFF79D4FF.toInt(),
    val cursorSurface: Int = 0xFF0E263A.toInt(),
    val onCursor: Int = 0xFF79D4FF.toInt(),
    val liveEnd: Int = live,
    val peak: Int = 0xFFFFBE68.toInt(),
)
