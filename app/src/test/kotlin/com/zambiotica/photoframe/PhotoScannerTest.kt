package com.zambiotica.photoframe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoScannerTest {

    @Test
    fun `acepta jpg png y webp sin importar mayusculas`() {
        assertTrue(PhotoScanner.isSupported("foto.jpg"))
        assertTrue(PhotoScanner.isSupported("foto.JPEG"))
        assertTrue(PhotoScanner.isSupported("captura.png"))
        assertTrue(PhotoScanner.isSupported("imagen.WebP"))
    }

    @Test
    fun `rechaza videos, archivos ocultos y archivos sin extension`() {
        assertFalse(PhotoScanner.isSupported("video.mp4"))
        assertFalse(PhotoScanner.isSupported("foto.heic"))
        assertFalse(PhotoScanner.isSupported(".thumbnail.jpg"))
        assertFalse(PhotoScanner.isSupported("README"))
    }
}
