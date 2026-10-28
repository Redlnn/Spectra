package top.redlnn.spectra

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import top.redlnn.spectra.capture.*
import top.redlnn.spectra.dsp.*
import top.redlnn.spectra.render.*
import top.redlnn.spectra.settings.*

class AnalyzerDspTest {
    @Test
    fun pitchFundamentalAndStrongestSpectralPeakHaveDifferentMeanings() {
        val processor = StftProcessor(AnalyzerSettings(), 48000)
        val pcm =
            FloatArray(processor.bufferSize) { i ->
                (.18 * sin(2 * PI * 440 * i / 48000) +
                        .65 * sin(2 * PI * 880 * i / 48000) +
                        .3 * sin(2 * PI * 1320 * i / 48000))
                    .toFloat()
            }
        val frame = processor.process(pcm, 1_000_000_000L)
        assertEquals(440.0, frame.pitch!!.hz, 2.0)
        assertEquals(
            880.0,
            SpectrumPeak.find(frame.spectrumDb, frame.spacingHz, 31.25, 24000.0)!!.hz,
            2.0,
        )
    }

    @Test
    fun pitchSurvivesOutOfBandRumbleAndHiss() {
        val pcm =
            FloatArray(8192) { i ->
                val t = i / 48000.0
                (.02 * sin(2 * PI * 440 * t) +
                        .12 * sin(2 * PI * 22 * t) +
                        .08 * sin(2 * PI * 6500 * t))
                    .toFloat()
            }
        val pitch = PitchDetector().detect(pcm, 48000)
        assertNotNull("A 440 Hz single note masked by out-of-band rumble and hiss", pitch)
        assertEquals(440.0, pitch!!.hz, 3.0)
    }

    @Test
    fun faintPeriodicNoteBelowOldGateCanStillBeDetected() {
        val pitch = PitchDetector().detect(sine(8192, 440.0, amplitude = .0003), 48000)
        assertNotNull("Periodic single note at about -73 dBFS RMS", pitch)
        assertEquals(440.0, pitch!!.hz, 2.0)
    }

    @Test
    fun pitchFilteringAndSensitivityNeverAlterSpectrumOrRecordedWaveform() {
        val pcm = sine(8192, 440.0, amplitude = .1)
        val strict =
            StftProcessor(
                    AnalyzerSettings(
                        pitchDenoise = false,
                        pitchSensitivity = PitchSensitivity.STRICT,
                    ),
                    48000,
                )
                .process(pcm, 1_000_000_000)
        val relaxed =
            StftProcessor(
                    AnalyzerSettings(
                        pitchDenoise = true,
                        pitchSensitivity = PitchSensitivity.SENSITIVE,
                    ),
                    48000,
                )
                .process(pcm, 1_000_000_000)
        assertArrayEquals(strict.amplitudes, relaxed.amplitudes, 0f)
        assertArrayEquals(strict.waveform, relaxed.waveform, 0f)
        assertEquals(strict.rmsDb, relaxed.rmsDb, 0f)
        assertNotNull(strict.pitch)
        assertNotNull(relaxed.pitch)
        for (seed in 0..8) {
            val random = Random(seed)
            assertNull(
                PitchDetector(true, .35)
                    .detect(FloatArray(8192) { random.nextFloat() - .5f }, 48000)
            )
        }
    }

    private fun sine(size: Int, frequency: Double, rate: Int = 48000, amplitude: Double = 1.0) =
        FloatArray(size) { (amplitude * sin(2 * PI * frequency * it / rate)).toFloat() }

    @Test
    fun alignedSineUsesCoherentGainForEveryWindow() {
        for (window in WindowType.entries) {
            val output = Fft(1024, window).amplitudes(sine(1024, 984.375))
            assertEquals(0.0, Levels.db(output[21].toDouble()).toDouble(), .015)
            assertEquals(21, output.indices.maxBy { output[it] })
        }
    }

    @Test
    fun dcAndNyquistAreNotDoubled() {
        assertEquals(
            1.0,
            Fft(1024, WindowType.NONE).amplitudes(FloatArray(1024) { 1f })[0].toDouble(),
            1e-6,
        )
        val alternating = FloatArray(1024) { if (it % 2 == 0) 1f else -1f }
        assertEquals(1.0, Fft(1024, WindowType.NONE).amplitudes(alternating)[512].toDouble(), 1e-6)
    }

    @Test
    fun fftSpacingAndHopAreIndependent() {
        for (overlap in listOf(.5, .75, .875)) {
            val p = StftProcessor(AnalyzerSettings(fftSize = 1024, overlap = overlap), 48000)
            val frame = p.process(sine(p.bufferSize, 984.375), 2_000_000_000)
            assertEquals(46.875, frame.spacingHz, 1e-6)
            assertEquals((1024 * (1 - overlap)).roundToInt(), p.hop)
            assertEquals(1_989_333_334L, frame.timestampNs)
        }
        val p = StftProcessor(AnalyzerSettings(fftSize = 8192), 48000)
        assertEquals(5.859375, p.process(sine(p.bufferSize, 100.0), 2_000_000_000).spacingHz, 1e-6)
    }

    @Test
    fun averageIsPowerAverageRatherThanDbAverage() {
        val p = StftProcessor(AnalyzerSettings(fftSize = 1024, average = 2), 48000)
        p.process(sine(p.bufferSize, 984.375), 1_000_000_000)
        val frame = p.process(sine(p.bufferSize, 984.375, amplitude = .25), 1_010_000_000)
        assertEquals(10 * log10((1 + .0625) / 2), frame.spectrumDb[21].toDouble(), .015)
    }

    @Test
    fun tiltPivotAndWeightingReferenceAreCorrect() {
        assertEquals(3.0, Levels.tilt(2000.0, 3.0), 1e-9)
        assertEquals(-3.0, Levels.tilt(500.0, 3.0), 1e-9)
        assertEquals(0.0, Levels.tilt(0.0, 3.0), 1e-9)
        assertEquals(0.0, Levels.weighting(1000.0, Weighting.A), .02)
        assertEquals(0.0, Levels.weighting(1000.0, Weighting.C), .02)
        assertTrue(Levels.weighting(100.0, Weighting.A) < -18)
    }

    @Test
    fun tiltDoesNotChangeRawBinsOrPitchAndHeatCanOptOut() {
        val neutral = StftProcessor(AnalyzerSettings(), 48000)
        val tilted = StftProcessor(AnalyzerSettings(tilt = 3.0, applyTilt = false), 48000)
        val pcm = sine(neutral.bufferSize, 440.0)
        val a = neutral.process(pcm, 1_000_000_000)
        val b = tilted.process(pcm, 1_000_000_000)
        assertArrayEquals(a.amplitudes, b.amplitudes, 0f)
        assertArrayEquals(a.heatDb, b.heatDb, .0001f)
        assertEquals(a.pitch!!.hz, b.pitch!!.hz, 1e-9)
        assertEquals(Levels.tilt(2000.0 / a.spacingHz * a.spacingHz, 3.0), 3.0, 1e-9)
    }

    @Test
    fun yinFindsFundamentalWithStrongerHarmonicsAndRejectsSilenceAndNoise() {
        val detector = PitchDetector()
        val pcm =
            FloatArray(8192) { i ->
                (.18 * sin(2 * PI * 440 * i / 48000) +
                        .65 * sin(2 * PI * 880 * i / 48000) +
                        .3 * sin(2 * PI * 1320 * i / 48000))
                    .toFloat()
            }
        val pitch = detector.detect(pcm, 48000)!!
        assertEquals(440.0, pitch.hz, 2.0)
        assertEquals("A4", pitch.note(true))
        assertNull(detector.detect(FloatArray(8192), 48000))
        val random = Random(123)
        assertNull(detector.detect(FloatArray(8192) { random.nextFloat() * 2 - 1 }, 48000))
    }

    @Test
    fun quietSingleNotesRemainDetectableAcrossInputRates() {
        for (rate in listOf(32000, 44100, 48000, 96000, 192000)) {
            for (hz in listOf(55.0, 110.0, 416.27, 440.0, 1000.0, 1800.0)) {
                val pitch = PitchDetector().detect(sine(max(8192, rate / 10), hz, rate, .001), rate)
                assertNotNull("rate=$rate hz=$hz at about -63 dBFS RMS", pitch)
                assertEquals("rate=$rate hz=$hz", hz, pitch!!.hz, max(1.0, hz * .015))
            }
        }
    }

    @Test
    fun peakHoldPersistsAndResets() {
        val p = StftProcessor(AnalyzerSettings(fftSize = 1024), 48000)
        p.process(sine(p.bufferSize, 984.375), 1_000_000_000)
        val quiet = p.process(sine(p.bufferSize, 984.375, amplitude = .1), 1_010_000_000)
        assertEquals(0.0, quiet.peaksDb[21].toDouble(), .02)
        assertEquals(-20.0, quiet.spectrumDb[21].toDouble(), .02)
        p.resetPeaks()
        assertEquals(
            -20.0,
            p.process(sine(p.bufferSize, 984.375, amplitude = .1), 1_020_000_000)
                .peaksDb[21]
                .toDouble(),
            .02,
        )
    }

    @Test
    fun mappingOnlyChangesDisplayAndPowerInterpolationIsDefined() {
        val db = floatArrayOf(-120f, -20f, -40f)
        assertEquals(-20f, Levels.sample(db, .1, Mapping.LINEAR_POWER), 0f)
        assertEquals(-20f, Levels.sample(db, 1.49, Mapping.NEAREST), 0f)
        assertEquals(
            10 * log10((.01 + .0001) / 2),
            Levels.sample(db, 1.5, Mapping.LINEAR_POWER).toDouble(),
            .0001,
        )
        assertEquals(-20f, Levels.band(db, 1.0, 2.0, true), 0f)
        assertEquals(
            10 * log10((.01 + .0001) / 2),
            Levels.band(db, 1.0, 2.0, false).toDouble(),
            .0001,
        )
    }

    @Test
    fun lowFrequencyTonesFallInCorrectBinsAndLongFftSeparatesNeighbors() {
        val output =
            Fft(8192, WindowType.HANN)
                .amplitudes(
                    FloatArray(8192) { i ->
                        (.5 * sin(2 * PI * 100 * i / 48000) + .5 * sin(2 * PI * 125 * i / 48000))
                            .toFloat()
                    }
                )
        assertTrue(output[17] > .35)
        assertTrue(output[21] > .35)
        assertTrue(output[19] < .05)
        for (frequency in listOf(100.0, 125.0, 250.0)) {
            val bins = Fft(8192, WindowType.HANN).amplitudes(sine(8192, frequency))
            assertEquals(frequency, bins.indices.maxBy { bins[it] } * 48000.0 / 8192, 5.86)
        }
    }

    @Test
    fun historyUsesActualTimestampsAndRetainsFifteenSeconds() {
        val h = SpectrogramHistory()
        val p = StftProcessor(AnalyzerSettings(), 48000)
        val base = p.process(sine(p.bufferSize, 984.375), 1_000_000_000)
        for (i in 0..200) h.append(base.copy(timestampNs = i * 100_000_000L), Mapping.LINEAR_POWER)
        val rows = h.snapshot()
        assertEquals(20_000_000_000L, rows.last().timestampNs)
        assertEquals(5_000_000_000L, rows.first().timestampNs)
        assertEquals(61, rows.count { it.timestampNs >= 14_000_000_000L })
        h.clear()
        assertTrue(h.snapshot().isEmpty())
    }

    @Test
    fun ringBufferWrapKeepsNewestSamples() {
        val r = PcmRing(8)
        r.add(FloatArray(6) { it.toFloat() }, 6)
        r.add(floatArrayOf(6f, 7f, 8f, 9f), 4)
        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f), r.copy(), 0f)
        val latest = FloatArray(4)
        r.latest(latest)
        assertArrayEquals(floatArrayOf(6f, 7f, 8f, 9f), latest, 0f)
    }

    @Test
    fun displayHistoryBucketsKeepShortTransientPeaksAndLatestTimestamp() {
        val h = SpectrogramHistory()
        val p = StftProcessor(AnalyzerSettings(fftSize = 1024), 48000)
        val a = p.process(sine(p.bufferSize, 984.375), 1_000_000_000)
        h.append(a.copy(timestampNs = 100_000_000), Mapping.NEAREST)
        h.append(
            a.copy(timestampNs = 105_000_000, heatDb = FloatArray(a.heatDb.size) { -180f }),
            Mapping.NEAREST,
        )
        assertEquals(1, h.snapshot().size)
        assertEquals(105_000_000L, h.snapshot().last().timestampNs)
        assertTrue(h.value(h.snapshot().last(), 984.375) > -1f)
    }

    @Test
    fun multiResolutionHasNoLargeGainStepsAtBoundaries() {
        val p = StftProcessor(AnalyzerSettings(engine = Engine.MULTI), 48000)
        for (frequency in
            listOf(230.0, 250.0, 270.0, 1900.0, 2000.0, 2100.0, 7500.0, 8000.0, 8500.0)) {
            val f = p.process(sine(p.bufferSize, frequency, amplitude = .5), 1_000_000_000)
            assertTrue("$frequency: ${f.peakDb}", f.peakDb in -11f..-5.5f)
        }
    }

    @Test
    fun clickLongWindowCenterIsCorrectAndClippingIsReported() {
        val p = StftProcessor(AnalyzerSettings(fftSize = 8192), 48000)
        val click = FloatArray(p.bufferSize)
        click[click.size / 2] = 1f
        val f = p.process(click, 2_000_000_000)
        assertEquals(1_914_666_667L, f.timestampNs)
        assertTrue(f.clipping)
        assertNull(f.pitch)
    }

    @Test
    fun allGeneratedWaveformsAreFiniteAndFrequencyIsClampedBelowNyquist() {
        for (shape in WaveShape.entries) SignalInput(
                AnalyzerSettings(shape = shape, sampleRate = 32000, generatorHz = 20000.0)
            )
            .use { input ->
                val buffer = FloatArray(4096)
                input.read(buffer, buffer.size)
                assertTrue(buffer.all { it.isFinite() && abs(it) <= 1f })
            }
    }

    @Test
    fun whiteNoisePowerAverageReducesVariance() {
        val random = Random(90210)
        val one = StftProcessor(AnalyzerSettings(average = 1), 48000)
        val eight = StftProcessor(AnalyzerSettings(average = 8), 48000)
        val valuesOne = mutableListOf<Double>()
        val valuesEight = mutableListOf<Double>()
        for (i in 0 until 120) {
            val noise = FloatArray(one.bufferSize) { (random.nextFloat() - .5f) * .4f }
            val a = one.process(noise, i * 6_000_000L)
            val b = eight.process(noise, i * 6_000_000L)
            if (i > 8) {
                valuesOne += 10.0.pow(a.spectrumDb[40] / 10.0)
                valuesEight += 10.0.pow(b.spectrumDb[40] / 10.0)
            }
        }
        fun variance(values: List<Double>): Double {
            val mean = values.average()
            return values.sumOf { (it - mean).pow(2) } / values.size
        }
        assertTrue(variance(valuesEight) < variance(valuesOne) * .4)
    }

    @Test
    fun inactiveGeneratorSettingsDoNotResetMicrophone() {
        val s = AnalyzerSettings()
        assertEquals(
            s.processingKey(),
            s.copy(generatorHz = 8000.0, shape = WaveShape.SQUARE).processingKey(),
        )
        assertNotEquals(s.processingKey(), s.copy(sampleRate = 192000).processingKey())
        assertNotEquals(
            s.copy(input = InputSource.GENERATOR).processingKey(),
            s.copy(input = InputSource.GENERATOR, generatorHz = 8000.0).processingKey(),
        )
        val p = StftProcessor(s.copy(peakHold = false), 48000)
        assertTrue(p.process(sine(p.bufferSize, 440.0), 1_000_000_000).peaksDb.all { it == -180f })
    }

    @Test
    fun pitchStatusExplainsSilenceQuietSignalAndNonPeriodicSound() {
        val detector = PitchDetector()
        assertNull(detector.detect(FloatArray(8192), 48000))
        assertEquals(PitchStatus.SILENCE, detector.status)
        assertNull(detector.detect(sine(8192, 440.0, amplitude = .0001), 48000))
        assertEquals(PitchStatus.TOO_QUIET, detector.status)
        val random = Random(42)
        assertNull(detector.detect(FloatArray(8192) { random.nextFloat() - .5f }, 48000))
        assertEquals(PitchStatus.UNSTABLE, detector.status)
        assertNotNull(detector.detect(sine(8192, 440.0), 48000))
        assertEquals(PitchStatus.DETECTED, detector.status)
    }

    @Test
    fun yinLowBoundaryNeverCrashesNoteConversion() {
        val detector = PitchDetector()
        for (step in 0..800) {
            val frequency = 20.0 + step * .05
            for (phase in listOf(0.0, .7, 1.4)) {
                val pcm =
                    FloatArray(8192) { i ->
                        (.5 * sin(2 * PI * frequency * i / 48000 + phase) +
                                .05 * sin(2 * PI * frequency * 2 * i / 48000))
                            .toFloat()
                    }
                val pitch = detector.detect(pcm, 48000)
                if (pitch != null) {
                    // Exercises the same UI conversion that crashed on real MP3 input.
                    val note = pitch.note(true)
                    assertTrue(
                        "f=$frequency phase=$phase hz=${pitch.hz} note=$note",
                        pitch.hz.isFinite() && pitch.hz in 50.0..2000.0,
                    )
                }
            }
        }
    }

    @Test
    fun capturedMusicWindowNeverProducesInvalidPitch() {
        val bytes = javaClass.getResourceAsStream("/pitch-regression.f32")!!.use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val pcm = FloatArray(bytes.size / 4) { buffer.float }.takeLast(3072).toFloatArray()
        assertEquals(12288, bytes.size)
        assertTrue(pcm.all { it.isFinite() && abs(it) <= 1f })
        val pitch = PitchDetector().detect(pcm, 44100)
        if (pitch != null) {
            pitch.note(true)
            assertTrue("invalid hz=${pitch.hz}", pitch.hz.isFinite() && pitch.hz in 50.0..2000.0)
        }
    }

    @Test
    fun invalidPitchCannotCrashPresentationAndLowerBoundaryWorksAtAllRates() {
        for (hz in listOf(Double.NaN, Double.NEGATIVE_INFINITY, -229.0, 0.0)) {
            assertEquals("—", Pitch(hz, .9).note(true))
            assertEquals(0.0, Pitch(hz, .9).cents, 0.0)
        }
        for (rate in listOf(32000, 44100, 48000, 96000, 192000)) {
            val count = max(8192, rate / 10)
            val pitch = PitchDetector().detect(sine(count, 55.0, rate), rate)!!
            assertEquals(55.0, pitch.hz, .5)
        }
    }

    @Test
    fun pinkNoiseSweepAndPulseTrainProduceFiniteAnalysisWithoutInventedEmptyOctaves() {
        val p = StftProcessor(AnalyzerSettings(fftSize = 8192), 48000)
        val random = Random(2026)
        var low = 0f
        val pinkLike =
            FloatArray(p.bufferSize) {
                low = .98f * low + .02f * (random.nextFloat() * 2 - 1)
                low
            }
        val sweep =
            FloatArray(p.bufferSize) { i ->
                val t = i / 48000.0
                sin(2 * PI * (100 * t + 12000 * t * t)).toFloat()
            }
        val pulses = FloatArray(p.bufferSize) { if (it % 480 == 0) 1f else 0f }
        for (input in listOf(pinkLike, sweep, pulses)) {
            val f = p.process(input, 1_000_000_000)
            assertTrue(f.spectrumDb.all(Float::isFinite))
            assertTrue(f.amplitudes.all { it >= 0 && it.isFinite() })
        }
        assertEquals(
            -180f,
            Levels.band(floatArrayOf(-180f, -20f, -50f), 1.1, 1.2, false, fillEmpty = false),
            0f,
        )
    }
}
