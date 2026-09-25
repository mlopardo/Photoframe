package com.zambiotica.photoframe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Decodificación con poca memoria: primero se leen las dimensiones, después se
 * decodifica submuestreada al tamaño de la pantalla. En equipos con poca RAM se
 * usa RGB_565 (la mitad de memoria que ARGB_8888).
 */
object PhotoDecoder {

    /** Ancho de la miniatura que se usa como fondo. Chico = rápido; el desenfoque hace el resto. */
    private const val BACKGROUND_WIDTH = 160

    /** Radio del desenfoque, en píxeles de la miniatura. */
    private const val BLUR_RADIUS = 6

    /** Cuánto se oscurece el fondo para que la foto de adelante resalte (0 = negro, 1 = sin tocar). */
    private const val BACKGROUND_DIM = 0.55f

    /** Foto lista para dibujar, más los datos del archivo original (para el diagnóstico). */
    data class Decoded(
        val bitmap: Bitmap,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val sampleSize: Int
    )

    /**
     * Decodifica la foto y la deja **exactamente** en el tamaño en que se va a ver.
     *
     * `inSampleSize` solo divide por potencias de 2, así que por sí solo deja bitmaps más
     * grandes que la pantalla: una foto de 15 MP queda en 2500 px de ancho. En la Galaxy Tab 2
     * (GPU Mali-400) el máximo de textura es 2048 px por lado: por encima de eso Android no
     * puede subir la imagen a la GPU y la dibuja degradada, que es el "desenfoque" que apareció
     * solo en algunas fotos —las más grandes— y con el efecto Ken Burns tanto encendido como
     * apagado. Reducir acá, con filtrado, evita el problema, baja la memoria y dibuja 1:1.
     */
    fun decode(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int, lowMemory: Boolean): Decoded? {
        if (reqWidth <= 0 || reqHeight <= 0) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
                inPreferredConfig = if (lowMemory) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val rotation = readRotation(context, uri)
            val upright = if (rotation == 0) bitmap else rotate(bitmap, rotation)
            val fitted = fitToFrame(upright, reqWidth, reqHeight)
            Decoded(fitted, bounds.outWidth, bounds.outHeight, options.inSampleSize)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /**
     * Fondo para el modo "encajar con fondo difuminado": miniatura + desenfoque real + oscurecido.
     *
     * Antes se estiraba una miniatura de 48 px sin desenfocar y el resultado se veía como un
     * recorte pixelado (reportado en la primera prueba en la TabZambiótica, 2026-09-17).
     * El desenfoque corre sobre 160 px de ancho: son ~16.000 píxeles, nada para la CPU.
     */
    fun blurredBackground(source: Bitmap): Bitmap? = try {
        val width = BACKGROUND_WIDTH
        val height = (width * source.height / source.width.toFloat()).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== source) small.recycle()

        boxBlur(pixels, width, height, BLUR_RADIUS)
        dim(pixels, BACKGROUND_DIM)

        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Desenfoque de caja en dos pasadas (horizontal y vertical). Equivale a un gaussiano suave. */
    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
        if (radius <= 0 || width <= 1 || height <= 1) return
        val temp = IntArray(pixels.size)
        blurPass(pixels, temp, width, height, radius)   // filas
        blurPass(temp, pixels, height, width, radius)   // columnas (con la matriz transpuesta)
    }

    /** Recorre `input` por filas y escribe el promedio en `output` transpuesto. */
    private fun blurPass(input: IntArray, output: IntArray, width: Int, height: Int, radius: Int) {
        for (row in 0 until height) {
            val base = row * width
            var red = 0
            var green = 0
            var blue = 0
            var count = 0
            for (x in 0..radius.coerceAtMost(width - 1)) {
                val color = input[base + x]
                red += (color shr 16) and 0xFF
                green += (color shr 8) and 0xFF
                blue += color and 0xFF
                count++
            }
            for (x in 0 until width) {
                output[x * height + row] =
                    (0xFF shl 24) or ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
                val leaving = x - radius
                val entering = x + radius + 1
                if (leaving >= 0) {
                    val color = input[base + leaving]
                    red -= (color shr 16) and 0xFF
                    green -= (color shr 8) and 0xFF
                    blue -= color and 0xFF
                    count--
                }
                if (entering < width) {
                    val color = input[base + entering]
                    red += (color shr 16) and 0xFF
                    green += (color shr 8) and 0xFF
                    blue += color and 0xFF
                    count++
                }
            }
        }
    }

    private fun dim(pixels: IntArray, factor: Float) {
        for (i in pixels.indices) {
            val color = pixels[i]
            val red = (((color shr 16) and 0xFF) * factor).toInt()
            val green = (((color shr 8) and 0xFF) * factor).toInt()
            val blue = ((color and 0xFF) * factor).toInt()
            pixels[i] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }

    /** Fecha de captura del EXIF, o null si la foto no la trae. */
    fun readTakenAt(context: Context, uri: Uri): ExifDates.TakenAt? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            ExifDates.parse(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                ?: ExifDates.parse(exif.getAttribute(ExifInterface.TAG_DATETIME))
        }
    } catch (e: Exception) {
        null
    }

    /** Reduce el bitmap al tamaño exacto en que se va a ver. Nunca lo agranda. */
    private fun fitToFrame(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): Bitmap {
        val scale = ScalingRules.displayScale(bitmap.width, bitmap.height, frameWidth, frameHeight)
        if (scale >= 1f) return bitmap
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            if (scaled !== bitmap) bitmap.recycle()
            scaled
        } catch (e: OutOfMemoryError) {
            bitmap
        }
    }

    /**
     * Coordenadas del geotag, o null si la foto no las trae.
     *
     * La sobrecarga de ExifInterface que llena un arreglo trabaja con `FloatArray`, no con
     * `DoubleArray`. La precision de un float alcanza de sobra: ~7 digitos significativos son
     * menos de 2 metros de error en latitud, y aca solo se usa para buscar la ciudad mas cercana
     * dentro de un radio de 60 km.
     */
    fun readLatLong(context: Context, uri: Uri): DoubleArray? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val coordinates = FloatArray(2)
            if (ExifInterface(stream).getLatLong(coordinates)) {
                doubleArrayOf(coordinates[0].toDouble(), coordinates[1].toDouble())
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }

    fun sampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= reqWidth && height / (sample * 2) >= reqHeight) {
            sample *= 2
        }
        return sample
    }

    private fun readRotation(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap = try {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        rotated
    } catch (e: OutOfMemoryError) {
        bitmap
    }
}
