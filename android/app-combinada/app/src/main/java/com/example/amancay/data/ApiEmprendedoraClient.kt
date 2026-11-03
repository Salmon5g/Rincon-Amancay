package com.example.amancay.data

import com.example.amancay.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiEmprendedoraException(
    val codigo: String,
    message: String
) : Exception(message)

class ApiEmprendedoraClient(private val auth: FirebaseAuth) {
    private suspend fun tokenActual(): String {
        val user = auth.currentUser ?: throw ApiEmprendedoraException("no-autenticado", "Inicia sesión para continuar.")
        return Tasks.await(user.getIdToken(false)).token
            ?: throw ApiEmprendedoraException("no-autenticado", "No se pudo obtener la sesión de Firebase.")
    }

    suspend fun llamar(operacion: String, solicitud: Map<String, Any?>): JSONObject = withContext(Dispatchers.IO) {
        val token = tokenActual()
        val contenido = JSONObject(solicitud).toString()
        if (contenido.toByteArray(Charsets.UTF_8).size > 16 * 1024) {
            throw ApiEmprendedoraException("datos-invalidos", "La solicitud supera el límite permitido.")
        }
        val connection = URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/" + operacion).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(contenido.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            val response = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val error = response.optJSONObject("error")
                throw ApiEmprendedoraException(
                    error?.optString("code")?.takeIf(String::isNotBlank) ?: "error-api",
                    error?.optString("mensaje")?.takeIf(String::isNotBlank) ?: "La API no pudo completar la operación ($status)."
                )
            }
            response.optJSONObject("datos") ?: JSONObject()
        } catch (error: ApiEmprendedoraException) {
            throw error
        } catch (_: Exception) {
            throw ApiEmprendedoraException("sin-conexion", "No se pudo conectar con la API local. Comprueba que esté encendida y que la dirección del emulador sea correcta.")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun subirImagenProducto(
        tiendaId: String,
        productoId: String,
        archivoId: String,
        contentType: String,
        bytes: ByteArray
    ): String = withContext(Dispatchers.IO) {
        if (!tiendaId.matches(Regex("[A-Za-z0-9_-]{1,128}")) ||
            !productoId.matches(Regex("[A-Za-z0-9_-]{1,128}")) ||
            !archivoId.matches(Regex("[0-9a-fA-F-]{36}")) ||
            contentType !in setOf("image/jpeg", "image/png", "image/webp") || bytes.isEmpty() || bytes.size > 5 * 1024 * 1024
        ) {
            throw ApiEmprendedoraException("datos-invalidos", "La imagen no cumple el formato o tamaño permitido.")
        }

        val token = tokenActual()
        val endpoint = BuildConfig.API_BASE_URL.trimEnd('/') + "/tiendas/$tiendaId/productos/$productoId/imagenes"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", contentType)
            connection.setRequestProperty("X-Archivo-Id", archivoId)
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            val response = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val error = response.optJSONObject("error")
                throw ApiEmprendedoraException(
                    error?.optString("code")?.takeIf(String::isNotBlank) ?: "error-api",
                    error?.optString("mensaje")?.takeIf(String::isNotBlank) ?: "No se pudo guardar la imagen ($status)."
                )
            }
            response.optJSONObject("datos")?.optString("ruta")
                ?.takeIf(String::isNotBlank)
                ?: throw ApiEmprendedoraException("respuesta-invalida", "La API no confirmó la ruta de la imagen.")
        } catch (error: ApiEmprendedoraException) {
            throw error
        } catch (_: Exception) {
            throw ApiEmprendedoraException("sin-conexion", "No se pudo conectar con la API local para subir la imagen. Comprueba que esté encendida y que el teléfono pueda acceder a ella.")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun leerImagenPrivadaProducto(tiendaId: String, productoId: String, indice: Int = 0): ByteArray = withContext(Dispatchers.IO) {
        if (!tiendaId.matches(Regex("[A-Za-z0-9_-]{1,128}")) ||
            !productoId.matches(Regex("[A-Za-z0-9_-]{1,128}")) || indice !in 0..4
        ) throw ApiEmprendedoraException("datos-invalidos", "La referencia de imagen no es válida.")

        val token = tokenActual()
        val endpoint = BuildConfig.API_BASE_URL.trimEnd('/') + "/tiendas/$tiendaId/productos/$productoId/imagenes/$indice"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "image/jpeg, image/png, image/webp")
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val response = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
                val error = response.optJSONObject("error")
                throw ApiEmprendedoraException(
                    error?.optString("code")?.takeIf(String::isNotBlank) ?: "error-api",
                    error?.optString("mensaje")?.takeIf(String::isNotBlank) ?: "No se pudo cargar la vista previa de la imagen ($status)."
                )
            }
            val contentType = connection.contentType?.substringBefore(';')
            if (contentType !in setOf("image/jpeg", "image/png", "image/webp")) {
                throw ApiEmprendedoraException("respuesta-invalida", "La API devolvió un formato de imagen no permitido.")
            }
            val length = connection.contentLengthLong
            if (length > 5L * 1024 * 1024) throw ApiEmprendedoraException("imagen-grande", "La vista previa supera el tamaño permitido.")
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 5 * 1024 * 1024) throw ApiEmprendedoraException("imagen-grande", "La vista previa supera el tamaño permitido.")
                    output.write(buffer, 0, count)
                }
            }
            output.toByteArray().takeIf { it.isNotEmpty() }
                ?: throw ApiEmprendedoraException("imagen-invalida", "La imagen guardada está vacía.")
        } catch (error: ApiEmprendedoraException) {
            throw error
        } catch (_: Exception) {
            throw ApiEmprendedoraException("sin-conexion", "No se pudo cargar la vista previa privada. Comprueba que la API local esté encendida y accesible desde el teléfono.")
        } finally {
            connection.disconnect()
        }
    }
}
