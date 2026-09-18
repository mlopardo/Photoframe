package com.zambiotica.photoframe

/**
 * Dónde se ubica cada texto superpuesto para que no se pisen entre sí.
 * Kotlin puro: lo cubre el test del CI.
 */
object OverlayRules {

    /**
     * La fecha de la foto va en la esquina inferior libre: la contraria al reloj si el reloj
     * está abajo, y la izquierda si el reloj está arriba.
     */
    fun photoDateGravity(clockPosition: String): String = when (clockPosition) {
        "bottom_end" -> "bottom_start"
        "bottom_start" -> "bottom_end"
        else -> "bottom_start"
    }
}
