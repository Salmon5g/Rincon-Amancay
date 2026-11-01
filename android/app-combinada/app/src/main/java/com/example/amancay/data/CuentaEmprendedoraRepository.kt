package com.example.amancay.data

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

data class ContactoEmprendedora(val tipo: String, val valor: String)

data class CuentaEmprendedora(
    val uid: String,
    val tiendaId: String,
    val nombreMostrar: String,
    val tienda: TiendaEmprendedora
)

data class TiendaEmprendedora(
    val nombre: String,
    val descripcion: String,
    val tipoEmprendimientoId: String,
    val sectorId: String,
    val contacto: ContactoEmprendedora?,
    val mostrarContacto: Boolean,
    val estadoPublicacion: String,
    val versionEsperada: String
)

data class OpcionCatalogoEmprendedora(val id: String, val nombre: String)

class CuentaEmprendedoraRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val api: ApiEmprendedoraClient
) {
    private val _tienda = MutableStateFlow<TiendaEmprendedora?>(null)
    val tienda: StateFlow<TiendaEmprendedora?> = _tienda.asStateFlow()
    private val _sectores = MutableStateFlow<List<OpcionCatalogoEmprendedora>>(emptyList())
    val sectores: StateFlow<List<OpcionCatalogoEmprendedora>> = _sectores.asStateFlow()
    private val _tiposEmprendimiento = MutableStateFlow<List<OpcionCatalogoEmprendedora>>(emptyList())
    val tiposEmprendimiento: StateFlow<List<OpcionCatalogoEmprendedora>> = _tiposEmprendimiento.asStateFlow()
    private var tiendaSubscription: ListenerRegistration? = null
    private var sectorSubscription: ListenerRegistration? = null
    private var tipoSubscription: ListenerRegistration? = null
    private val operationIds = mutableMapOf<String, String>()

    suspend fun iniciarSesion(correo: String, contrasena: String): CuentaEmprendedora = withContext(Dispatchers.IO) {
        try {
            val resultado = Tasks.await(auth.signInWithEmailAndPassword(correo.trim(), contrasena))
            val usuario = resultado.user ?: throw ApiEmprendedoraException("no-autenticado", "Firebase no devolvió la cuenta iniciada.")
            leerCuenta(usuario.uid)
        } catch (error: Exception) {
            auth.signOut()
            throw normalizarError(error)
        }
    }

    suspend fun validarSesion(): CuentaEmprendedora? = withContext(Dispatchers.IO) {
        val usuario = auth.currentUser ?: return@withContext null
        try {
            leerCuenta(usuario.uid)
        } catch (error: Exception) {
            auth.signOut()
            throw normalizarError(error)
        }
    }

    private fun leerCuenta(uid: String): CuentaEmprendedora {
        val acceso = Tasks.await(db.collection("accesos").document(uid).get()).data
            ?: throw ApiEmprendedoraException("sin-permiso", "Esta cuenta todavía no tiene un perfil habilitado en Rincón Amancay.")
        if (acceso["estado"] != "activo" || "emprendedora" !in (acceso["roles"] as? List<*>).orEmpty()) {
            throw ApiEmprendedoraException("sin-permiso", "Esta cuenta no tiene acceso activo como emprendedora.")
        }
        val tiendaId = acceso["tiendaId"] as? String
            ?: throw ApiEmprendedoraException("sin-permiso", "La cuenta no tiene una tienda asignada.")
        val perfil = Tasks.await(db.collection("emprendedoras").document(uid).get()).data
            ?: throw ApiEmprendedoraException("sin-permiso", "No se encontró el perfil de emprendedora.")
        if (perfil["tiendaId"] != tiendaId) throw ApiEmprendedoraException("sin-permiso", "El perfil y el acceso no coinciden con la tienda asignada.")
        val tiendaDatos = Tasks.await(db.collection("tiendasPrivadas").document(tiendaId).get()).data
            ?: throw ApiEmprendedoraException("sin-permiso", "No se encontró la ficha privada de la tienda.")
        if (tiendaDatos["propietarioUid"] != uid) throw ApiEmprendedoraException("sin-permiso", "La tienda asignada no pertenece a esta cuenta.")
        return CuentaEmprendedora(
            uid = uid,
            tiendaId = tiendaId,
            nombreMostrar = Tasks.await(db.collection("usuarios").document(uid).get()).getString("nombreMostrar").orEmpty(),
            tienda = mapearTienda(tiendaDatos)
        )
    }

    fun observarTienda(tiendaId: String) {
        tiendaSubscription?.remove()
        tiendaSubscription = db.collection("tiendasPrivadas").document(tiendaId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                _tienda.value = snapshot?.takeIf { it.exists() }?.data?.let(::mapearTienda)
            }
        sectorSubscription?.remove()
        sectorSubscription = db.collection("sectores").whereEqualTo("activo", true)
            .addSnapshotListener { snapshot, _ ->
                _sectores.value = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> doc.getString("nombre")?.let { OpcionCatalogoEmprendedora(doc.id, it) } }
                    .sortedBy { it.nombre.lowercase() }
            }
        tipoSubscription?.remove()
        tipoSubscription = db.collection("tiposEmprendimiento").whereEqualTo("activo", true)
            .addSnapshotListener { snapshot, _ ->
                _tiposEmprendimiento.value = snapshot?.documents.orEmpty()
                    .mapNotNull { doc -> doc.getString("nombre")?.let { OpcionCatalogoEmprendedora(doc.id, it) } }
                    .sortedBy { it.nombre.lowercase() }
            }
    }

    suspend fun guardarTienda(cuenta: CuentaEmprendedora, propuesta: TiendaEmprendedora) = withContext(Dispatchers.IO) {
        val contacto = propuesta.contacto?.let { mapOf("tipo" to it.tipo, "valor" to it.valor) }
        api.llamar(
            "editarTiendaEmprendedora",
            mapOf(
                "tiendaId" to cuenta.tiendaId,
                "operacionId" to operationId(
                    "editarTienda", cuenta.tiendaId, propuesta.versionEsperada, propuesta.nombre,
                    propuesta.descripcion, propuesta.tipoEmprendimientoId, propuesta.sectorId,
                    propuesta.contacto?.tipo.orEmpty(), propuesta.contacto?.valor.orEmpty(), propuesta.mostrarContacto.toString()
                ),
                "versionEsperada" to propuesta.versionEsperada,
                "nombre" to propuesta.nombre,
                "descripcion" to propuesta.descripcion,
                "tipoEmprendimientoId" to propuesta.tipoEmprendimientoId,
                "sectorId" to propuesta.sectorId,
                "contacto" to contacto,
                "mostrarContacto" to propuesta.mostrarContacto
            )
        )
        val fresh = Tasks.await(db.collection("tiendasPrivadas").document(cuenta.tiendaId).get())
        _tienda.value = fresh.takeIf { it.exists() }?.data?.let(::mapearTienda)
    }

    suspend fun publicarTienda(cuenta: CuentaEmprendedora, tienda: TiendaEmprendedora) = withContext(Dispatchers.IO) {
        api.llamar("publicarTienda", mapOf(
            "tiendaId" to cuenta.tiendaId,
            "operacionId" to operationId("publicarTienda", cuenta.tiendaId, tienda.versionEsperada),
            "versionEsperada" to tienda.versionEsperada
        ))
    }

    suspend fun retirarTienda(cuenta: CuentaEmprendedora, tienda: TiendaEmprendedora) = withContext(Dispatchers.IO) {
        api.llamar("retirarTienda", mapOf(
            "tiendaId" to cuenta.tiendaId,
            "operacionId" to operationId("retirarTienda", cuenta.tiendaId, tienda.versionEsperada),
            "versionEsperada" to tienda.versionEsperada
        ))
    }

    fun cerrarSesion() {
        tiendaSubscription?.remove(); tiendaSubscription = null
        sectorSubscription?.remove(); sectorSubscription = null
        tipoSubscription?.remove(); tipoSubscription = null
        _tienda.value = null
        _sectores.value = emptyList()
        _tiposEmprendimiento.value = emptyList()
        auth.signOut()
    }

    fun cerrar() {
        tiendaSubscription?.remove()
        sectorSubscription?.remove()
        tipoSubscription?.remove()
    }

    private fun mapearTienda(data: Map<String, Any>): TiendaEmprendedora {
        val actualizadoEn = data["actualizadoEn"] as? Timestamp
            ?: throw ApiEmprendedoraException("datos-invalidos", "La tienda no tiene una versión válida para editar.")
        val contactoMap = data["contacto"] as? Map<*, *>
        val contacto = if (contactoMap == null) null else {
            val tipo = contactoMap["tipo"] as? String
            val valor = contactoMap["valor"] as? String
            if (tipo == null || valor == null) null else ContactoEmprendedora(tipo, valor)
        }
        return TiendaEmprendedora(
            nombre = data["nombre"] as? String ?: "",
            descripcion = data["descripcion"] as? String ?: "",
            tipoEmprendimientoId = data["tipoEmprendimientoId"] as? String ?: "",
            sectorId = data["sectorId"] as? String ?: "",
            contacto = contacto,
            mostrarContacto = data["mostrarContacto"] == true,
            estadoPublicacion = data["estadoPublicacion"] as? String ?: "borrador",
            versionEsperada = "${actualizadoEn.seconds}:${actualizadoEn.nanoseconds}"
        )
    }

    private fun operationId(vararg partes: String): String = synchronized(operationIds) {
        operationIds.getOrPut(partes.joinToString("/")) { UUID.randomUUID().toString() }
    }

    private fun normalizarError(error: Exception): Exception {
        if (error is ApiEmprendedoraException) return error
        val code = (error as? com.google.firebase.FirebaseException)?.message.orEmpty()
        return when {
            code.contains("network", true) -> ApiEmprendedoraException("sin-conexion", "No se pudo conectar con Firebase.")
            error is com.google.firebase.firestore.FirebaseFirestoreException && error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                ApiEmprendedoraException("sin-permiso", "Firestore no autorizó leer el perfil o la tienda asignada.")
            else -> ApiEmprendedoraException("error-cuenta", error.message ?: "No se pudo validar el acceso de emprendedora.")
        }
    }
}
