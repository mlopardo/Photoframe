package com.zambiotica.photoframe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionRulesTest {

    @Test
    fun `los toques fantasma al despertar la pantalla se ignoran`() {
        val resumed = 10_000L
        assertTrue(InteractionRules.ignoreTouch(10_100L, resumed))
        assertTrue(InteractionRules.ignoreTouch(11_900L, resumed))
        assertFalse(InteractionRules.ignoreTouch(12_100L, resumed))
    }

    @Test
    fun `un toque normal muestra los controles`() {
        assertTrue(InteractionRules.isTap(heldMs = 120, movedPx = 3))
    }

    @Test
    fun `un roce muy corto o arrastrado no cuenta como toque`() {
        assertFalse(InteractionRules.isTap(heldMs = 10, movedPx = 0))
        assertFalse(InteractionRules.isTap(heldMs = 120, movedPx = 80))
        assertFalse(InteractionRules.isTap(heldMs = 900, movedPx = 0))
    }

    @Test
    fun `los Ajustes necesitan dos segundos de dedo quieto`() {
        assertFalse(InteractionRules.isDeliberateLongPress(heldMs = 600, movedPx = 0))
        assertFalse(InteractionRules.isDeliberateLongPress(heldMs = 2_500, movedPx = 60))
        assertTrue(InteractionRules.isDeliberateLongPress(heldMs = 2_000, movedPx = 5))
    }

    @Test
    fun `el toque largo de Android por defecto ya no alcanza`() {
        // 500 ms era el umbral del GestureDetector que abria Ajustes sin querer.
        assertFalse(InteractionRules.isDeliberateLongPress(heldMs = 500, movedPx = 0))
    }
}
