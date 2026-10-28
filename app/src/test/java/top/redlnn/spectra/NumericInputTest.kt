package top.redlnn.spectra

import org.junit.Assert.*
import org.junit.Test
import top.redlnn.spectra.presentation.NumericInputSpec

class NumericInputTest {
    @Test
    fun signedDecimalEntryRejectsInvalidValuesWithoutChangingPrecision() {
        val tilt = NumericInputSpec(-6.0, 6.0, 1)
        assertEquals(1.3, tilt.parse("1.3")!!, 0.0)
        assertEquals(-.5, tilt.parse("-0.5")!!, 0.0)
        assertEquals(1.3, tilt.parse("1,3")!!, 0.0)
        for (text in listOf("", "NaN", "Infinity", "7", "-6.1", "1.33", "abc")) assertNull(
            text,
            tilt.parse(text),
        )
        assertEquals("1.3", tilt.format(1.29999995))
    }

    @Test
    fun physicalHertzAndHistoryEntryUseTheirOwnBounds() {
        val hertz = NumericInputSpec(20.0, 20000.0, 0)
        assertEquals(440.0, hertz.parse("440")!!, 0.0)
        assertEquals(20000.0, hertz.parse("20000")!!, 0.0)
        for (text in listOf("19", "20001", "2.64", "440.5")) assertNull(hertz.parse(text))
        val seconds = NumericInputSpec(4.0, 15.0, 0)
        assertEquals(12.0, seconds.parse("12")!!, 0.0)
        assertNull(seconds.parse("12.5"))
        assertNull(seconds.parse("3"))
    }
}
