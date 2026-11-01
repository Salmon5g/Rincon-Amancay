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
    suspend fun llamar(operacion: String, solicitud: Map<String, Any?>): JSONObject = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: throw ApiEmprendedoraException("no-autenticado", "Inicia sesión para continuar.")
        val token = Tasks.await(user.getIdToken(false)).token
            ?: throw ApiEmprendedoraException("no-autenticado", "No se pudo obtener la sesión de Firebase.")
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
}
