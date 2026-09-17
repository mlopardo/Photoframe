package com.zambiotica.photoframe

/**
 * Reglas del toque, en Kotlin puro para poder testearlas en el CI.
 *
 * Motivo (prueba del 2026-09-17): el toque largo de Android (500 ms) abría los Ajustes
 * con demasiada facilidad y el marco se quedaba ahí. Ahora el toque largo es deliberado
 * y se ignoran los toques de los primeros segundos después de encender la pantalla,
 * que es cuando aparecen los toques fantasma.
 */
object InteractionRules {

    /** Los toques se ignoran durante este tiempo después de que la app vuelve al frente. */
    const val WAKE_GRACE_MS = 2_000L

    /** Cuánto hay que mantener el dedo apoyado para abrir los Ajustes. */
    const val LONG_PRESS_MS = 2_000L

    /** Cuánto se puede mover el dedo sin dejar de ser un toque quieto, en píxeles. */
    const val MOVE_SLOP_PX = 24

    /** Un toque tan corto que no llega ni a toque simple (roce, gota de agua). */
    const val MIN_TAP_MS = 40L

    /** Máximo para considerarlo un toque simple. */
    const val MAX_TAP_MS = 500L

    fun ignoreTouch(nowMs: Long, resumedAtMs: Long, graceMs: Long = WAKE_GRACE_MS): Boolean =
        nowMs - resumedAtMs < graceMs

    fun isTap(heldMs: Long, movedPx: Int): Boolean =
        heldMs in MIN_TAP_MS..MAX_TAP_MS && movedPx <= MOVE_SLOP_PX

    fun isDeliberateLongPress(heldMs: Long, movedPx: Int, thresholdMs: Long = LONG_PRESS_MS): Boolean =
        heldMs >= thresholdMs && movedPx <= MOVE_SLOP_PX
}
