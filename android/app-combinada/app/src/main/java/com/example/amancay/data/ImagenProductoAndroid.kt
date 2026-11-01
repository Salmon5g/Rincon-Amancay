package com.example.amancay.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class ImagenProductoPreparada(val bytes: ByteArray, val contentType: String = "image/jpeg")

suspend fun decodificarVistaPreviaProducto(bytes: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2 })
}

/** Normaliza fotos de cámara/galería a JPEG pequeño antes de enviarlas a la API. */
suspend fun prepararImagenProducto(
    contentResolver: ContentResolver,
    uri: Uri?,
    bitmap: Bitmap?
): ImagenProductoPreparada = withContext(Dispatchers.IO) {
    val source = try {
        when {
            bitmap != null -> bitmap
            uri != null -> {
                val imageSource = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
                    val scale = minOf(1f, 2000f / max(info.size.width, info.size.height))
                    if (scale < 1f) {
                        decoder.setTargetSize(
                            (info.size.width * scale).roundToInt().coerceAtLeast(1),
                            (info.size.height * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                    decoder.isMutableRequired = true
                }
            }
            else -> throw ApiEmprendedoraException("imagen-requerida", "Elige una foto antes de guardar el producto.")
        }
    } catch (error: ApiEmprendedoraException) {
        throw error
    } catch (_: Exception) {
        throw ApiEmprendedoraException("imagen-invalida", "No se pudo leer la foto. Elige otra imagen e inténtalo de nuevo.")
    }

    val maxBytes = 5 * 1024 * 1024
    val maxDimension = max(source.width, source.height)
    var current = if (maxDimension > 2000) {
        val scale = 2000f / maxDimension
        Bitmap.createScaledBitmap(source, (source.width * scale).roundToInt().coerceAtLeast(1), (source.height * scale).roundToInt().coerceAtLeast(1), true)
    } else source
    var ownsCurrent = current !== source
    try {
        repeat(10) {
            for (quality in intArrayOf(88, 80, 72, 64)) {
                val output = ByteArrayOutputStream()
                current.compress(Bitmap.CompressFormat.JPEG, quality, output)
                val result = output.toByteArray()
                if (result.isNotEmpty() && result.size <= maxBytes) return@withContext ImagenProductoPreparada(result)
            }
            val next = Bitmap.createScaledBitmap(
                current,
                (current.width * .82f).roundToInt().coerceAtLeast(1),
                (current.height * .82f).roundToInt().coerceAtLeast(1),
                true
            )
            if (ownsCurrent && current !== next) current.recycle()
            current = next
            ownsCurrent = current !== source
        }
    } finally {
        if (ownsCurrent && !current.isRecycled) current.recycle()
        if (source !== bitmap && !source.isRecycled) source.recycle()
    }
    throw ApiEmprendedoraException("imagen-grande", "No se pudo reducir la foto a 5 MiB. Elige una imagen más pequeña.")
}
