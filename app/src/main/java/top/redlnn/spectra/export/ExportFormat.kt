package top.redlnn.spectra.export

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    PNG("png", "image/png", "图表快照 PNG"),
    CSV("csv", "text/csv", "频谱数据 CSV"),
    WAV("wav", "audio/wav", "最近 15 秒 WAV"),
    PCM("pcm", "application/octet-stream", "原始 PCM · s16le"),
}
