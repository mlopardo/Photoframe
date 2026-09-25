package com.zambiotica.photoframe

import kotlin.math.min

/**
 * Cuánto se puede agrandar una foto sin que se vean los píxeles, y cuándo tiene sentido
 * aplicarle el efecto Ken Burns.
 *
 * Motivo (prueba del 2026-09-17): unas pocas fotos viejas, de baja resolución, se veían
 * "extremadamente amplificadas y pixeladas". Estirarlas a pantalla completa no inventa
 * detalle: solo agranda los píxeles. Y encima el zoom del efecto lo empeoraba.
 *
 * Kotlin puro: lo cubre el test del CI.
 */
object ScalingRules {

    /**
     * Tope de ampliación: **no se amplía nada**.
     *
     * La v0.1.2 permitía 1,3×, pensando que era un punto medio. Mariano reportó que esas fotos
     * se veían "desenfocadas": ampliar no inventa detalle y el suavizado del escalado, en lugar
     * de mostrar píxeles grandes, deja la imagen blanda. Una foto chica y nítida, rodeada del
     * fondo difuminado, se ve mucho mejor que una foto grande y borrosa.
     */
    const val MAX_UPSCALE = 1.0f

    /** Para animar el zoom hace falta resolución de sobra: al menos este múltiplo de la pantalla. */
    const val KEN_BURNS_MIN_RATIO = 1.1f

    /**
     * Escala uniforme con la que se dibuja la foto: entra completa en la pantalla, pero
     * nunca se agranda más allá de [MAX_UPSCALE].
     */
    fun displayScale(
        bitmapWidth: Int,
        bitmapHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Float {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return 1f
        val fit = min(screenWidth / bitmapWidth.toFloat(), screenHeight / bitmapHeight.toFloat())
        return min(fit, MAX_UPSCALE)
    }

    /** Verdadero si la foto tiene píxeles de sobra como para animarle un zoom sin degradarla. */
    fun allowsKenBurns(
        bitmapWidth: Int,
        bitmapHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean =
        bitmapWidth >= screenWidth * KEN_BURNS_MIN_RATIO &&
            bitmapHeight >= screenHeight * KEN_BURNS_MIN_RATIO
}
