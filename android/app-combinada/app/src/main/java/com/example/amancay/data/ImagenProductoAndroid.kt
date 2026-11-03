package com.example.amancay.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAMANO_MAXIMO_IMAGEN = 5 * 1024 * 1024
private const val DIMENSION_MAXIMA_PROCESADA = 2000
private const val PIXELES_MAXIMOS_ORIGEN = 20_000_000L

data class ImagenProductoPreparada(val bytes: ByteArray, val contentType: String)

suspend fun decodificarVistaPreviaProducto(bytes: ByteArray): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val escala = minOf(1f, 1600f / max(info.size.width, info.size.height))
            if (escala < 1f) {
                decoder.setTargetSize(
                    (info.size.width * escala).roundToInt().coerceAtLeast(1),
                    (info.size.height * escala).roundToInt().coerceAtLeast(1)
                )
            }
        }
    }.getOrNull()
}

/** Conserva el archivo original cuando cumple los límites; solo transcodifica si hace falta. */
suspend fun prepararImagenProducto(
    contentResolver: ContentResolver,
    uri: Uri?,
    bitmap: Bitmap?
): ImagenProductoPreparada = withContext(Dispatchers.IO) {
    if (uri != null) {
        val bytesOriginales = leerHastaLimite(contentResolver, uri)
        val tipoDetectado = bytesOriginales?.let(::detectarTipoImagen)
        if (bytesOriginales != null && tipoDetectado != null && dimensionesPermitidas(bytesOriginales)) {
            return@withContext ImagenProductoPreparada(bytesOriginales, tipoDetectado)
        }
    }

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

    val maxDimension = max(source.width, source.height)
    var current = if (maxDimension > DIMENSION_MAXIMA_PROCESADA) {
        val scale = DIMENSION_MAXIMA_PROCESADA.toFloat() / maxDimension
        Bitmap.createScaledBitmap(source, (source.width * scale).roundToInt().coerceAtLeast(1), (source.height * scale).roundToInt().coerceAtLeast(1), true)
    } else source
    var ownsCurrent = current !== source
    try {
        for (intento in 0 until 15) {
            for (quality in intArrayOf(92, 88, 84)) {
                val output = ByteArrayOutputStream()
                current.compress(Bitmap.CompressFormat.JPEG, quality, output)
                val result = output.toByteArray()
                if (result.isNotEmpty() && result.size <= TAMANO_MAXIMO_IMAGEN) {
                    return@withContext ImagenProductoPreparada(result, "image/jpeg")
                }
            }
            val next = Bitmap.createScaledBitmap(
                current,
                (current.width * .9f).roundToInt().coerceAtLeast(1),
                (current.height * .9f).roundToInt().coerceAtLeast(1),
                true
            )
            if (next.width == current.width && next.height == current.height) break
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

private fun leerHastaLimite(contentResolver: ContentResolver, uri: Uri): ByteArray? = try {
    val salida = ByteArrayOutputStream()
    contentResolver.openInputStream(uri)?.use { entrada ->
        val bloque = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val leidos = entrada.read(bloque)
            if (leidos < 0) break
            total += leidos
            if (total > TAMANO_MAXIMO_IMAGEN) return null
            salida.write(bloque, 0, leidos)
        }
    } ?: return null
    salida.toByteArray()
} catch (_: Exception) {
    null
}

private fun detectarTipoImagen(bytes: ByteArray): String? = when {
    bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> "image/png"
    bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
        bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> "image/webp"
    else -> null
}

private fun dimensionesPermitidas(bytes: ByteArray): Boolean {
    val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)
    val ancho = opciones.outWidth
    val alto = opciones.outHeight
    return ancho > 0 && alto > 0 && ancho.toLong() * alto.toLong() <= PIXELES_MAXIMOS_ORIGEN
}
