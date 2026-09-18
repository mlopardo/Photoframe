package com.zambiotica.photoframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExifDatesTest {

    @Test
    fun `lee la fecha de captura de una foto de camara`() {
        val date = ExifDates.parse("2020:02:29 17:05:33")
        assertEquals(ExifDates.TakenAt(2020, 2, 29), date)
    }

    @Test
    fun `descarta el campo vacio o en cero que dejan muchas apps`() {
        assertNull(ExifDates.parse(null))
        assertNull(ExifDates.parse(""))
        assertNull(ExifDates.parse("    "))
        assertNull(ExifDates.parse("0000:00:00 00:00:00"))
    }

    @Test
    fun `descarta fechas imposibles`() {
        assertNull(ExifDates.parse("2021:02:29 10:00:00"))   // 2021 no es bisiesto
        assertNull(ExifDates.parse("2020:13:01 10:00:00"))
        assertNull(ExifDates.parse("2020:00:10 10:00:00"))
        assertNull(ExifDates.parse("1899:05:10 10:00:00"))
    }

    @Test
    fun `descarta basura sin romperse`() {
        assertNull(ExifDates.parse("no es una fecha"))
        assertNull(ExifDates.parse("2020-02-29 17:05:33"))   // separadores equivocados
        assertNull(ExifDates.parse("2020:02"))
    }

    @Test
    fun `acepta el ultimo dia de meses de 30 y 31`() {
        assertEquals(ExifDates.TakenAt(2019, 4, 30), ExifDates.parse("2019:04:30 08:00:00"))
        assertEquals(ExifDates.TakenAt(2019, 12, 31), ExifDates.parse("2019:12:31 23:59:59"))
        assertNull(ExifDates.parse("2019:04:31 08:00:00"))
    }
}
