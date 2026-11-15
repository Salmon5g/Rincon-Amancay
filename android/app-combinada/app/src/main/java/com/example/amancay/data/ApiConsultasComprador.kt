package com.example.amancay.data

import com.example.amancay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object ApiConsultasComprador {
    suspend fun enviar(tiendaId: String, producto: String?, nombre: String, correo: String, mensaje: String) = withContext(Dispatchers.IO) {
        val datos = JSONObject().apply {
            put("tiendaId", tiendaId)
            put("operacionId", UUID.randomUUID().toString())
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
        } catch (error: ApiEmprendedoraException) {
            throw error
        } catch (_: Exception) {
            throw ApiEmprendedoraException("sin-conexion", "No se pudo enviar la consulta. Comprueba tu conexión e inténtalo de nuevo.")
        } finally {
            conexion.disconnect()
        }
    }
}
