package com.zambiotica.photoframe

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoDecoderTest {

    @Test
    fun `la foto del portarretrato no se submuestrea`() {
        // 1280x800 en la pantalla de la Tab 2 (1280x800): se usa entera.
        assertEquals(1, PhotoDecoder.sampleSize(1280, 800, 1280, 800))
    }

    @Test
    fun `una foto de camara se submuestrea para entrar en memoria`() {
        // 4000x3000 en una pantalla de 1280x800: 1/2 deja 2000x1500, 1/4 queda corto.
        assertEquals(2, PhotoDecoder.sampleSize(4000, 3000, 1280, 800))
    }

    @Test
    fun `una foto mas chica que la pantalla no se toca`() {
        assertEquals(1, PhotoDecoder.sampleSize(640, 480, 1280, 800))
    }
}
