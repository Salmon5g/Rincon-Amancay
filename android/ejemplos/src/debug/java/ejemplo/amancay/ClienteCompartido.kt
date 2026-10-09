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

// Cliente compartido: misma superficie que ClienteLocal, pero contra el proyecto
// real y la API HTTPS. `apiBase` debe terminar en /api/v1/.
class ClienteCompartido(private val servicios: ConexionCompartida.Servicios, apiBase: String) {
    private val api = if (apiBase.endsWith("/")) apiBase else "$apiBase/"
    private val operaciones = setOf("publicarTienda", "retirarTienda", "publicarProducto", "retirarProducto",
        "altaEmprendedora", "crearProducto", "editarProducto", "ajustarStock", "desactivarEmprendedora", "registrarComprador", "invitarEmprendedora", "consultarInvitacion",
        "aceptarInvitacion", "cancelarInvitacion", "reactivarEmprendedora")
    private fun id(value: String): String {
        require(value.matches(Regex("[a-zA-Z0-9_-]{1,128}"))) { "ID inválido" }; return value
    }
    suspend fun iniciarSesion(correo: String, clave: String) = servicios.auth.signInWithEmailAndPassword(correo, clave).await()
    suspend fun crearIdentidad(correo: String, clave: String) = servicios.auth.createUserWithEmailAndPassword(correo, clave).await()
    suspend fun enviarVerificacion() {
        val user = servicios.auth.currentUser ?: throw ErrorApi("no-autenticado", "Iniciar sesión", 401)
        user.sendEmailVerification().await()
    }
    suspend fun confirmarCorreo(codigo: String) {
        servicios.auth.applyActionCode(codigo).await()
        servicios.auth.currentUser?.let { it.reload().await(); it.getIdToken(true).await() }
    }
    suspend fun solicitarRecuperacion(correo: String): String {
        try { servicios.auth.sendPasswordResetEmail(correo).await() }
        catch (e: com.google.firebase.auth.FirebaseAuthException) {
            if (e.errorCode != "ERROR_USER_NOT_FOUND") throw e
        }
        return "Si el correo corresponde a una cuenta, recibirás instrucciones."
    }
    suspend fun comprobarRecuperacion(codigo: String) = servicios.auth.verifyPasswordResetCode(codigo).await()
    suspend fun confirmarRecuperacion(codigo: String, clave: String) {
        servicios.auth.confirmPasswordReset(codigo, clave).await(); servicios.auth.signOut()
    }
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
            val connection = URL(api + operacion).openConnection() as HttpURLConnection
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
