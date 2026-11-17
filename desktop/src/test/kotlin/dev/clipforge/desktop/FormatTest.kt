package dev.clipforge.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun formatsZero() {
        assertEquals("00:00.0", formatTime(0))
    }

    @Test
    fun formatsSubSecondAndSeconds() {
        assertEquals("00:01.5", formatTime(1_500))
        assertEquals("00:59.9", formatTime(59_999))
    }

    @Test
    fun formatsMinutes() {
        assertEquals("02:03.4", formatTime(123_450))
    }
}
