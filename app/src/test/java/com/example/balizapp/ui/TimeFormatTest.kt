package com.example.balizapp.ui

import com.example.balizapp.ui.components.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test fun `menos de una hora muestra solo minutos`() =
        assertEquals("45 min", formatDuration(45 * 60_000L))

    @Test fun `hora exacta omite los minutos`() =
        assertEquals("2 h", formatDuration(2 * 60 * 60_000L))

    @Test fun `hora y minutos rellena con cero`() =
        assertEquals("1 h 05 min", formatDuration((60 + 5) * 60_000L))

    @Test fun `duracion negativa se muestra en valor absoluto`() =
        assertEquals("10 min", formatDuration(-10 * 60_000L))

    @Test fun `cero es 0 min`() = assertEquals("0 min", formatDuration(0))
}
