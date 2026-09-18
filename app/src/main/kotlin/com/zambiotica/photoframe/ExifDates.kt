package com.zambiotica.photoframe

/**
 * Lectura de la fecha de captura del EXIF, en Kotlin puro para poder testearla en el CI.
 *
 * El EXIF guarda la fecha como "yyyy:MM:dd HH:mm:ss" (con dos puntos también en la fecha).
 * Muchas cámaras y apps dejan el campo en cero o vacío cuando no la conocen, y eso no se
 * debe mostrar como "1 de enero del año 0".
 */
object ExifDates {

    /** Rango aceptable: nada anterior a la fotografía digital ni posterior a un siglo. */
    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2100

    data class TakenAt(val year: Int, val month: Int, val day: Int)

    /** Devuelve la fecha o null si el campo falta, está en cero o no tiene sentido. */
    fun parse(raw: String?): TakenAt? {
        val value = raw?.trim() ?: return null
        if (value.length < 10) return null
        val year = value.substring(0, 4).toIntOrNull() ?: return null
        val month = value.substring(5, 7).toIntOrNull() ?: return null
        val day = value.substring(8, 10).toIntOrNull() ?: return null
        if (value[4] != ':' || value[7] != ':') return null
        if (year !in MIN_YEAR..MAX_YEAR) return null
        if (month !in 1..12) return null
        if (day !in 1..daysInMonth(year, month)) return null
        return TakenAt(year, month, day)
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 0
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}
