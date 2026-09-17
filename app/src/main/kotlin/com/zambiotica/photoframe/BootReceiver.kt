package com.zambiotica.photoframe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Arranque tras reiniciar el equipo.
 *
 * Solo funciona hasta Android 9 (API 28): desde Android 10 el sistema no deja
 * abrir una actividad desde segundo plano. En la TabZambiótica (API 25) anda.
 * Para equipos nuevos, el plan es ofrecer el modo protector de pantalla o usar
 * la app como pantalla de inicio (queda para M4).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.autostart(context)) return
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return

        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launch)
    }
}
