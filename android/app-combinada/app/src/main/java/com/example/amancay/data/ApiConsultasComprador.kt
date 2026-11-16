package com.example.amancay.data

import com.example.amancay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID

object ApiConsultasComprador {
    suspend fun enviar(tiendaId: String, producto: String?, nombre: String, correo: String, mensaje: String): String = withContext(Dispatchers.IO) {
        val operacionId = UUID.randomUUID().toString()
        val datos = JSONObject().apply {
            put("tiendaId", tiendaId)
            put("operacionId", operacionId)
            put("nombre", nombre)
            put("correo", correo)
            put("mensaje", mensaje)
            if (!producto.isNullOrBlank()) put("producto", producto)
        }.toString().toByteArray(Charsets.UTF_8)
        val conexion = URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/consultasPublicas").openConnection() as HttpURLConnection
        try {
            conexion.requestMethod = "POST"
            conexion.connectTimeout = 15_000
            conexion.readTimeout = 20_000
            conexion.doOutput = true
            conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conexion.setRequestProperty("Accept", "application/json")
            conexion.outputStream.use { it.write(datos) }
            val status = conexion.responseCode
            val contenido = (if (status in 200..299) conexion.inputStream else conexion.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val mensajeError = runCatching { JSONObject(contenido).getJSONObject("error").getString("mensaje") }.getOrNull()
                throw ApiEmprendedoraException("consulta-rechazada", mensajeError ?: "No se pudo enviar la consulta ($status).")
            }
            operacionId
        } catch (error: ApiEmprendedoraException) {
            throw error
        } catch (_: ConnectException) {
            throw ApiEmprendedoraException("api-desconectada", "No se pudo conectar con la API local del proyecto. Comprueba que siga ejecutándose en el puerto 8080.")
        } catch (_: SocketTimeoutException) {
            throw ApiEmprendedoraException("api-tiempo-agotado", "La API local tardó demasiado en responder. Comprueba que siga ejecutándose e inténtalo de nuevo.")
        } catch (_: Exception) {
            throw ApiEmprendedoraException("sin-conexion", "No se pudo enviar la consulta. Comprueba que el Pixel 4 tenga acceso a la API local.")
        } finally {
            conexion.disconnect()
        }
    }

    suspend fun leerRespuesta(tiendaId: String, operacionId: String): String? = withContext(Dispatchers.IO) {
        val conexion = URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/consultasPublicas/$tiendaId/$operacionId").openConnection() as HttpURLConnection
        try {
            conexion.requestMethod = "GET"
            conexion.connectTimeout = 10_000
            conexion.readTimeout = 15_000
            conexion.setRequestProperty("Accept", "application/json")
            val status = conexion.responseCode
            val contenido = (if (status in 200..299) conexion.inputStream else conexion.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw ApiEmprendedoraException("consulta-no-disponible", "No se pudo actualizar esta respuesta.")
            val datos = JSONObject(contenido).getJSONObject("datos")
            if (datos.optBoolean("respondida")) datos.optString("respuesta").takeIf(String::isNotBlank) else null
        } catch (error: ApiEmprendedoraException) {
            throw error
        } catch (_: ConnectException) {
            throw ApiEmprendedoraException("api-desconectada", "No se pudo conectar con la API local del proyecto. Comprueba que siga ejecutándose en el puerto 8080.")
        } catch (_: SocketTimeoutException) {
            throw ApiEmprendedoraException("api-tiempo-agotado", "La API local tardó demasiado en responder. Comprueba que siga ejecutándose e inténtalo de nuevo.")
        } catch (_: Exception) {
            throw ApiEmprendedoraException("sin-conexion", "No se pudieron actualizar tus respuestas. Comprueba que el Pixel 4 tenga acceso a la API local.")
        } finally {
            conexion.disconnect()
        }
    }
}
