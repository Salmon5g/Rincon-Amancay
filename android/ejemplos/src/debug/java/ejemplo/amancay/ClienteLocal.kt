package ejemplo.amancay

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

class ErrorApi(val code: String, message: String, val status: Int = 0, val resultadoIncierto: Boolean = false) : Exception(message)

class ClienteLocal(private val servicios: ConexionLocal.Servicios) {
    private val operaciones = setOf("publicarTienda", "retirarTienda", "publicarProducto", "retirarProducto",
        "altaEmprendedora", "crearProducto", "editarProducto", "ajustarStock", "desactivarEmprendedora")
    private fun id(value: String): String {
        require(value.matches(Regex("[a-zA-Z0-9_-]{1,128}"))) { "ID inválido" }; return value
    }
    suspend fun iniciarSesion(correo: String, clave: String) = servicios.auth.signInWithEmailAndPassword(correo, clave).await()
    fun cerrarSesion() = servicios.auth.signOut()
    suspend fun consultarAcceso(): Map<String, Any>? {
        val user = servicios.auth.currentUser ?: return null
        return servicios.db.collection("accesos").document(user.uid).get().await().data
    }
    suspend fun listarTiendas(ultimo: DocumentSnapshot? = null): List<DocumentSnapshot> {
        var query = servicios.db.collection("tiendasPublicas")
            .whereEqualTo("estadoPublicacion", "publicado").whereEqualTo("habilitada", true).limit(20)
        if (ultimo != null) query = query.startAfter(ultimo)
        return query.get().await().documents
    }
    suspend fun listarProductos(tiendaId: String, ultimo: DocumentSnapshot? = null): List<DocumentSnapshot> {
        var query = servicios.db.collection("tiendasPublicas").document(id(tiendaId)).collection("productos")
            .whereEqualTo("estadoPublicacion", "publicado").limit(20)
        if (ultimo != null) query = query.startAfter(ultimo)
        return query.get().await().documents
    }
    suspend fun listarVariantes(tiendaId: String, productoId: String) = servicios.db.collection("tiendasPublicas")
        .document(id(tiendaId)).collection("productos").document(id(productoId)).collection("variantes")
        .whereEqualTo("activa", true).limit(100).get().await().documents

    // Una intención conserva su operacionId. No se reintenta automáticamente.
    suspend fun llamar(operacion: String, solicitud: JSONObject): JSONObject {
        require(operacion in operaciones) { "Operación desconocida" }
        val user = servicios.auth.currentUser ?: throw ErrorApi("no-autenticado", "Iniciar sesión", 401)
        val token = user.getIdToken(false).await().token ?: throw ErrorApi("no-autenticado", "Sesión inválida", 401)
        val bytes = solicitud.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 16384) { "Máximo 16 KiB" }
        return withContext(Dispatchers.IO) {
            val connection = URL(ConexionLocal.API + operacion).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10000; connection.readTimeout = 20000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.outputStream.use { it.write(bytes) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
                val body = try { JSONObject(text) } catch (e: org.json.JSONException) {
                    throw ErrorApi("respuesta-invalida", "Revisar el resultado antes de reintentar", status, true)
                }
                if (status !in 200..299) {
                    val error = body.optJSONObject("error")
                    throw ErrorApi(error?.optString("code") ?: "error-api", error?.optString("mensaje") ?: "Operación fallida", status, status >= 500)
                }
                body.optJSONObject("datos") ?: throw ErrorApi("respuesta-invalida", "Respuesta sin datos", status, true)
            } catch (e: IOException) {
                throw ErrorApi("conexion-interrumpida", "Resultado no confirmado. Conservar solicitud y operacionId para reintentar", 0, true)
            } finally { connection.disconnect() }
        }
    }
    companion object { fun version(fecha: Timestamp) = "${fecha.seconds}:${fecha.nanoseconds}" }
}
