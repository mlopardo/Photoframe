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

    fun decode(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int, lowMemory: Boolean): Bitmap? {
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
            if (rotation == 0) bitmap else rotate(bitmap, rotation)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Miniatura muy chica que, estirada a pantalla completa, funciona como fondo difuminado. */
    fun blurredBackground(source: Bitmap): Bitmap? = try {
        val width = 48
        val height = (width * source.height / source.width.toFloat()).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(source, width, height, true)
    } catch (e: OutOfMemoryError) {
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
