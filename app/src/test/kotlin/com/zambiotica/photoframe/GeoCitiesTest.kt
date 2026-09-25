package com.zambiotica.photoframe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifica el archivo de ciudades que viaja en el APK (`assets/ciudades.bin`).
 *
 * Si alguien regenera ese archivo y sale mal, la app mostraría lugares equivocados debajo de
 * las fotos familiares, que es peor que no mostrar nada. Por eso el CI lo revisa en cada push.
 */
class GeoCitiesTest {

    private val cities: GeoCities by lazy {
        val asset = File("src/main/assets/ciudades.bin")
        assertTrue("falta assets/ciudades.bin", asset.exists())
        asset.inputStream().use { GeoCities.load(it) }
    }

    private fun place(latitude: Double, longitude: Double): String? =
        cities.nearest(latitude, longitude)?.let { "${it.city}, ${it.region}" }

    @Test
    fun `el archivo trae las ciudades esperadas`() {
        assertEquals(171_075, cities.size)
    }

    @Test
    fun `ubica lugares conocidos de Argentina con su provincia`() {
        assertEquals("Pilar, Buenos Aires", place(-34.4587, -58.9142))
        assertEquals("Tigre, Buenos Aires", place(-34.4264, -58.5796))
        assertEquals("Mar del Plata, Buenos Aires", place(-38.0055, -57.5426))
        assertEquals("Córdoba, Córdoba", place(-31.4201, -64.1888))
        assertEquals("Mendoza, Mendoza", place(-32.8895, -68.8458))
        assertEquals("San Carlos de Bariloche, Río Negro", place(-41.1335, -71.3103))
        assertEquals("Ushuaia, Tierra del Fuego", place(-54.8019, -68.3030))
    }

    @Test
    fun `fuera de Argentina muestra la ciudad y el pais`() {
        assertEquals("Punta del Este, Uruguay", place(-34.9611, -54.9500))
        assertEquals("Rio de Janeiro, Brasil", place(-22.9068, -43.1729))
    }

    @Test
    fun `en medio del oceano no inventa una ubicacion`() {
        assertNull(place(0.0, -30.0))
        assertNull(place(-40.0, -40.0))
    }

    @Test
    fun `coordenadas invalidas o extremas no rompen la busqueda`() {
        assertNull(place(90.0, 0.0))        // polo norte
        assertNull(place(-89.9, 179.9))     // antartida, cerca del antimeridiano
        assertNotNull(cities.nearest(-34.6037, -58.3816))
    }
}
