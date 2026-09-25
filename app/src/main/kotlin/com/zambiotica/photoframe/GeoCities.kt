package com.zambiotica.photoframe

import android.content.Context
import java.io.DataInputStream
import java.io.InputStream
import java.io.EOFException
import kotlin.math.abs
import kotlin.math.cos

/**
 * Busca la ciudad más cercana a unas coordenadas, sin red y sin permisos.
 *
 * Los datos viven en `assets/ciudades.bin`, generado por `tools/build_geo.py` a partir de un
 * volcado de GeoNames (dominio público): 171.075 ciudades del mundo con su provincia en
 * Argentina y su país en el resto. El archivo está **ordenado por latitud**, así que la
 * búsqueda solo recorre la franja de latitudes cercana en vez de las 171.075 filas.
 */
class GeoCities internal constructor(
    private val lats: IntArray,
    private val lons: IntArray,
    private val nameOffsets: IntArray,
    private val regions: ShortArray,
    private val names: ByteArray,
    private val regionNames: Array<String>
) {

    data class Place(val city: String, val region: String)

    companion object {
        private const val ASSET = "ciudades.bin"
        private const val SCALE = 100_000.0

        /** Si la ciudad más cercana está más lejos que esto, no se muestra nada. */
        const val MAX_DISTANCE_KM = 60.0

        private const val KM_PER_DEGREE = 111.32

        @Volatile
        private var instance: GeoCities? = null

        /** Carga perezosa: solo ocurre la primera vez que una foto trae coordenadas. */
        fun get(context: Context): GeoCities? {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val loaded = try {
                    context.assets.open(ASSET).use { load(it) }
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
                instance = loaded
                return loaded
            }
        }

        /** Separado de [get] para que el test del CI pueda cargar el mismo archivo sin Android. */
        fun load(source: InputStream): GeoCities {
            DataInputStream(source.buffered()).use { input ->
                val magic = ByteArray(6)
                input.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != "PFGEO1") throw EOFException("formato desconocido")
                val count = input.readInt()
                val regionCount = input.readInt()

                val lats = IntArray(count)
                val lons = IntArray(count)
                for (i in 0 until count) {
                    lats[i] = input.readInt()
                    lons[i] = input.readInt()
                }
                val offsets = IntArray(count)
                for (i in 0 until count) offsets[i] = input.readInt()
                val regions = ShortArray(count)
                for (i in 0 until count) regions[i] = input.readShort()

                val namesLength = input.readInt()
                val names = ByteArray(namesLength)
                input.readFully(names)

                val regionOffsets = IntArray(regionCount)
                for (i in 0 until regionCount) regionOffsets[i] = input.readInt()
                val regionBlobLength = input.readInt()
                val regionBlob = ByteArray(regionBlobLength)
                input.readFully(regionBlob)

                val regionNames = Array(regionCount) { i ->
                    val start = regionOffsets[i]
                    val length = regionBlob[start].toInt() and 0xFF
                    String(regionBlob, start + 1, length, Charsets.UTF_8)
                }
                return GeoCities(lats, lons, offsets, regions, names, regionNames)
            }
        }
    }

    /** Ciudad más cercana, o null si no hay ninguna dentro de [MAX_DISTANCE_KM]. */
    fun nearest(latitude: Double, longitude: Double): Place? {
        if (lats.isEmpty()) return null
        val targetLat = (latitude * SCALE).toInt()
        val lonScale = cos(Math.toRadians(latitude)).coerceAtLeast(0.01)

        // Ventana de latitudes: 1 grado ~ 111 km, así que con el margen alcanza y sobra.
        val marginDegrees = MAX_DISTANCE_KM / KM_PER_DEGREE
        val margin = (marginDegrees * SCALE).toInt()
        var low = lowerBound(targetLat - margin)
        val high = lowerBound(targetLat + margin)

        var bestIndex = -1
        var bestDistance = Double.MAX_VALUE
        while (low < high) {
            val dLat = (lats[low] / SCALE) - latitude
            var dLon = (lons[low] / SCALE) - longitude
            if (dLon > 180) dLon -= 360
            if (dLon < -180) dLon += 360
            val x = dLon * lonScale
            val distance = (dLat * dLat + x * x)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = low
            }
            low++
        }
        if (bestIndex < 0) return null
        val km = Math.sqrt(bestDistance) * KM_PER_DEGREE
        if (km > MAX_DISTANCE_KM) return null
        return Place(nameAt(bestIndex), regionNames[regions[bestIndex].toInt() and 0xFFFF])
    }

    private fun nameAt(index: Int): String {
        val start = nameOffsets[index]
        val length = names[start].toInt() and 0xFF
        return String(names, start + 1, length, Charsets.UTF_8)
    }

    /** Primer índice cuya latitud es >= [value] (el archivo está ordenado por latitud). */
    private fun lowerBound(value: Int): Int {
        var low = 0
        var high = lats.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (lats[mid] < value) low = mid + 1 else high = mid
        }
        return low
    }

    val size: Int get() = lats.size
}
