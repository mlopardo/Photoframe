package com.zambiotica.photoframe

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayRulesTest {

    @Test
    fun `la fecha no se pisa con el reloj`() {
        assertEquals("bottom_start", OverlayRules.photoDateGravity("bottom_end"))
        assertEquals("bottom_end", OverlayRules.photoDateGravity("bottom_start"))
    }

    @Test
    fun `con el reloj arriba la fecha va abajo a la izquierda`() {
        assertEquals("bottom_start", OverlayRules.photoDateGravity("top_end"))
        assertEquals("bottom_start", OverlayRules.photoDateGravity("top_start"))
    }
}
