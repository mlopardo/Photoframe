package com.zambiotica.photoframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pantalla de la TabZambiótica: 1280x800. */
class ScalingRulesTest {

    private val screenW = 1280
    private val screenH = 800

    @Test
    fun `una foto de camara se achica para entrar en la pantalla`() {
        val scale = ScalingRules.displayScale(4000, 3000, screenW, screenH)
        assertEquals(800f / 3000f, scale, 0.0001f)
    }

    @Test
    fun `una foto del tamano de la pantalla se muestra tal cual`() {
        assertEquals(1f, ScalingRules.displayScale(1280, 800, screenW, screenH), 0.0001f)
    }

    @Test
    fun `una foto vieja y chica no se agranda mas alla del tope`() {
        // 640x480 entraria 1,66 veces: se limita a 1,3 para que no se vean los pixeles.
        assertEquals(
            ScalingRules.MAX_UPSCALE,
            ScalingRules.displayScale(640, 480, screenW, screenH),
            0.0001f
        )
    }

    @Test
    fun `una miniatura diminuta tampoco explota de tamano`() {
        assertEquals(
            ScalingRules.MAX_UPSCALE,
            ScalingRules.displayScale(160, 120, screenW, screenH),
            0.0001f
        )
    }

    @Test
    fun `Ken Burns solo con fotos que tienen resolucion de sobra`() {
        assertTrue(ScalingRules.allowsKenBurns(4000, 3000, screenW, screenH))
        assertTrue(ScalingRules.allowsKenBurns(1408, 880, screenW, screenH))
        assertFalse(ScalingRules.allowsKenBurns(1280, 800, screenW, screenH))
        assertFalse(ScalingRules.allowsKenBurns(640, 480, screenW, screenH))
    }

    @Test
    fun `una foto invalida no rompe el calculo`() {
        assertEquals(1f, ScalingRules.displayScale(0, 0, screenW, screenH), 0.0001f)
    }
}
