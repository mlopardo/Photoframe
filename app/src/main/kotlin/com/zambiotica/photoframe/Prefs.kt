package com.zambiotica.photoframe

import android.app.ActivityManager
import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Único punto de lectura de la configuración. Los valores por defecto viven acá
 * y en res/xml/prefs.xml: si cambia uno, cambian los dos.
 */
object Prefs {

    const val KEY_INTERVAL = "interval_sec"
    const val KEY_FADE = "fade_ms"
    const val KEY_ORDER = "order"
    const val KEY_SCALE = "scale"
    const val KEY_KEN_BURNS = "ken_burns"
    const val KEY_FOLDER_URI = "folder_uri"
    const val KEY_SUBFOLDERS = "subfolders"
    const val KEY_CLOCK = "clock"
    const val KEY_CLOCK_POSITION = "clock_position"
    const val KEY_PHOTO_DATE = "photo_date"
    const val KEY_ORIENTATION = "orientation"
    const val KEY_AUTOSTART = "autostart"

    const val DEFAULT_INTERVAL_SEC = 30
    const val DEFAULT_FADE_MS = 1500

    private fun sp(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    fun intervalMs(context: Context): Long =
        sp(context).getInt(KEY_INTERVAL, DEFAULT_INTERVAL_SEC).coerceIn(5, 3600) * 1000L

    fun fadeMs(context: Context): Long =
        sp(context).getInt(KEY_FADE, DEFAULT_FADE_MS).coerceIn(0, 5000).toLong()

    fun order(context: Context): String = sp(context).getString(KEY_ORDER, "shuffle") ?: "shuffle"

    fun scale(context: Context): String = sp(context).getString(KEY_SCALE, "fit_blur") ?: "fit_blur"

    fun kenBurns(context: Context): Boolean =
        sp(context).getBoolean(KEY_KEN_BURNS, defaultKenBurns(context))

    fun folderUri(context: Context): String? = sp(context).getString(KEY_FOLDER_URI, null)

    fun setFolderUri(context: Context, value: String?) =
        sp(context).edit().putString(KEY_FOLDER_URI, value).apply()

    fun subfolders(context: Context): Boolean = sp(context).getBoolean(KEY_SUBFOLDERS, true)

    fun clock(context: Context): Boolean = sp(context).getBoolean(KEY_CLOCK, true)

    fun photoDate(context: Context): Boolean = sp(context).getBoolean(KEY_PHOTO_DATE, true)

    fun clockPosition(context: Context): String =
        sp(context).getString(KEY_CLOCK_POSITION, "bottom_end") ?: "bottom_end"

    fun orientation(context: Context): String =
        sp(context).getString(KEY_ORIENTATION, "sensor") ?: "sensor"

    fun autostart(context: Context): Boolean = sp(context).getBoolean(KEY_AUTOSTART, true)

    /**
     * Escribe el valor por defecto de Ken Burns la primera vez, porque depende del equipo
     * y no se puede declarar en res/xml/prefs.xml. Asi la pantalla de Ajustes muestra el
     * mismo valor que usa la app.
     */
    fun persistKenBurnsDefault(context: Context) {
        val preferences = sp(context)
        if (!preferences.contains(KEY_KEN_BURNS)) {
            preferences.edit().putBoolean(KEY_KEN_BURNS, defaultKenBurns(context)).apply()
        }
    }

    /**
     * Ken Burns viene encendido solo en equipos con memoria de sobra.
     * La Galaxy Tab 2 (1 GB) cae del lado apagado.
     */
    fun defaultKenBurns(context: Context): Boolean = !isLowMemoryDevice(context)

    fun isLowMemoryDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.isLowRamDevice) return true
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem < 1_900_000_000L
    }
}
