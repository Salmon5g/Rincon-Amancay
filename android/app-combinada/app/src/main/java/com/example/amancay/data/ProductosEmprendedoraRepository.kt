package com.example.amancay.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class CampoTipoProducto(val clave: String, val etiqueta: String, val obligatorio: Boolean)

data class TipoProductoEmprendedora(
    val id: String,
    val nombre: String,
    val version: Int,
    val campos: List<CampoTipoProducto>,
    val opcionesVariante: List<String>,
    val activo: Boolean = true
)

data class CategoriaEmprendedora(val id: String, val nombre: String, val activo: Boolean = true)

data class VarianteProductoEmprendedora(
    val id: String,
    val opciones: Map<String, String>,
    val precio: Long,
    val stock: Int?,
    val activa: Boolean
)

data class ProductoEmprendedora(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val tipoProductoId: String,
    val versionTipoProducto: Int,
    val categoriaId: String,
    val atributosEspecificos: Map<String, String>,
    val tieneVariantes: Boolean,
    val modalidad: String,
    val manejaStock: Boolean,
    val alAgotarse: String,
    val precioBase: Long?,
    val stock: Int?,
    val variantes: List<VarianteProductoEmprendedora>,
    val imagenes: List<String>,
    val estadoPublicacion: String,
    val versionEsperada: String
)

data class VarianteProductoSolicitud(
    val id: String,
    val opciones: Map<String, String>,
    val precio: Long,
    val stockInicial: Int? = null,
    val activa: Boolean = true
)

data class ProductoSolicitud(
    val id: String? = null,
    val versionEsperada: String? = null,
    val operacionId: String? = null,
    val productoIdNuevo: String? = null,
    val nombre: String,
    val descripcion: String,
    val tipo: TipoProductoEmprendedora,
    val categoriaId: String,
    val atributos: Map<String, String>,
    val modalidad: String,
    val manejaStock: Boolean,
    val alAgotarse: String,
    val precioBase: Long? = null,
    val stockInicial: Int? = null,
    val variantes: List<VarianteProductoSolicitud> = emptyList()
)

class ProductosEmprendedoraRepository(
    private val db: FirebaseFirestore,
    private val api: ApiEmprendedoraClient
) {
    private val _productos = MutableStateFlow<List<ProductoEmprendedora>>(emptyList())
    val productos: StateFlow<List<ProductoEmprendedora>> = _productos.asStateFlow()
    private val _tipos = MutableStateFlow<List<TipoProductoEmprendedora>>(emptyList())
    val tipos: StateFlow<List<TipoProductoEmprendedora>> = _tipos.asStateFlow()
    private val _categorias = MutableStateFlow<List<CategoriaEmprendedora>>(emptyList())
    val categorias: StateFlow<List<CategoriaEmprendedora>> = _categorias.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var productSubscription: ListenerRegistration? = null
    private var typeSubscription: ListenerRegistration? = null
    private var categorySubscription: ListenerRegistration? = null
    private val variantSubscriptions = mutableMapOf<String, ListenerRegistration>()
    private val variantsById = mutableMapOf<String, List<VarianteProductoEmprendedora>>()
    private val definitionsById = mutableMapOf<String, TipoProductoEmprendedora>()
    private val operationIds = mutableMapOf<String, String>()

    suspend fun leerTipo(id: String, version: Int): TipoProductoEmprendedora = withContext(Dispatchers.IO) {
        val type = Tasks.await(db.collection("tiposProducto").document(id).get())
        val definition = Tasks.await(db.collection("tiposProducto").document(id).collection("versiones").document(version.toString()).get())
        if (!type.exists() || !definition.exists()) throw ApiEmprendedoraException("datos-invalidos", "No se encontró la definición histórica del tipo de producto.")
        val fields = (definition.get("campos") as? List<*>).orEmpty().mapNotNull { value ->
            val map = value as? Map<*, *> ?: return@mapNotNull null
            val key = map["clave"] as? String ?: return@mapNotNull null
            CampoTipoProducto(key, map["etiqueta"] as? String ?: key, map["obligatorio"] == true)
        }
        val options = (definition.get("opcionesVariante") as? List<*>).orEmpty().filterIsInstance<String>()
        TipoProductoEmprendedora(id, type.getString("nombre") ?: id, version, fields, options, type.getBoolean("activo") == true)
    }

    fun observar(tiendaId: String) {
        productSubscription?.remove()
        productSubscription = db.collection("tiendasPrivadas").document(tiendaId).collection("productos")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                val actuales = docs.mapNotNull { runCatching { it.aProducto() }.getOrNull() }
                    .filterNot { it.estadoPublicacion == "eliminado" }
                val ids = actuales.filter { it.tieneVariantes }.map { it.id }.toSet()
                (variantSubscriptions.keys - ids).forEach { id ->
                    variantSubscriptions.remove(id)?.remove()
                    variantsById.remove(id)
                }
                actuales.filter { it.tieneVariantes }.forEach { producto -> observarVariantes(tiendaId, producto.id) }
                _productos.value = actuales
                _error.value = null
                reconstruir()
            }

        typeSubscription?.remove()
        typeSubscription = db.collection("tiposProducto")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    return@addSnapshotListener
                }
                snapshot?.documents.orEmpty().forEach { documento ->
                    val version = documento.getLong("versionActual")?.toInt() ?: return@forEach
                    documento.reference.collection("versiones").document(version.toString()).get()
                        .addOnSuccessListener { definicion ->
                            if (!definicion.exists()) return@addOnSuccessListener
                            val campos = (definicion.get("campos") as? List<*>).orEmpty().mapNotNull { valor ->
                                val map = valor as? Map<*, *> ?: return@mapNotNull null
                                val key = map["clave"] as? String ?: return@mapNotNull null
                                CampoTipoProducto(key, map["etiqueta"] as? String ?: key, map["obligatorio"] == true)
                            }
                            val opciones = (definicion.get("opcionesVariante") as? List<*>).orEmpty().filterIsInstance<String>()
                            definitionsById[documento.id] = TipoProductoEmprendedora(documento.id, documento.getString("nombre") ?: documento.id, version, campos, opciones, documento.getBoolean("activo") == true)
                            _tipos.value = definitionsById.values.sortedBy { it.nombre.lowercase() }
                        }
                }
            }

        categorySubscription?.remove()
        categorySubscription = db.collection("categorias")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    return@addSnapshotListener
                }
                _categorias.value = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    doc.getString("nombre")?.let { CategoriaEmprendedora(doc.id, it, doc.getBoolean("activo") == true) }
                }.sortedBy { it.nombre.lowercase() }
                _error.value = null
            }
    }

    private fun observarVariantes(tiendaId: String, productoId: String) {
        if (variantSubscriptions[productoId] != null) return
        variantSubscriptions[productoId] = db.collection("tiendasPrivadas").document(tiendaId)
            .collection("productos").document(productoId).collection("variantes")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    return@addSnapshotListener
                }
                variantsById[productoId] = snapshot?.documents.orEmpty().mapNotNull { runCatching { it.aVariante() }.getOrNull() }
                _error.value = null
                reconstruir()
            }
    }

    suspend fun guardar(tiendaId: String, solicitud: ProductoSolicitud): String {
        val productoId = solicitud.id ?: solicitud.productoIdNuevo ?: UUID.randomUUID().toString()
        val product = linkedMapOf<String, Any?>(
            "nombre" to solicitud.nombre.trim(),
            "descripcion" to solicitud.descripcion.trim(),
            "tipoProductoId" to solicitud.tipo.id,
            "versionTipoProducto" to solicitud.tipo.version,
            "categoriaId" to solicitud.categoriaId,
            "atributosEspecificos" to solicitud.atributos,
            "tieneVariantes" to solicitud.tipo.opcionesVariante.isNotEmpty(),
            "moneda" to "CLP",
            "unidadVenta" to "unidad",
            "pasoCantidad" to 1,
            "modalidad" to solicitud.modalidad,
            "manejaStock" to solicitud.manejaStock,
            "alAgotarse" to solicitud.alAgotarse
        )
        if (solicitud.tipo.opcionesVariante.isEmpty()) {
            solicitud.precioBase?.let { product["precioBase"] = it }
            if (solicitud.manejaStock && solicitud.id == null) solicitud.stockInicial?.let { product["stockInicial"] = it }
        }
        val variantes = solicitud.variantes.map { variante ->
            linkedMapOf<String, Any?>(
                "varianteId" to variante.id,
                "opciones" to variante.opciones,
                "precio" to variante.precio,
                "activa" to variante.activa
            ).apply { variante.stockInicial?.let { put("stockInicial", it) } }
        }
        val body = linkedMapOf<String, Any?>(
            "tiendaId" to tiendaId,
            "productoId" to productoId,
            "operacionId" to (solicitud.operacionId ?: UUID.randomUUID().toString()),
            "producto" to product,
            "variantes" to variantes
        )
        val accion = if (solicitud.id == null) "crearProducto" else {
            body["versionEsperada"] = solicitud.versionEsperada
            "editarProducto"
        }
        api.llamar(accion, body)
        return productoId
    }

    suspend fun subirImagen(
        tiendaId: String,
        productoId: String,
        archivoId: String,
        imagen: ImagenProductoPreparada
    ): String = api.subirImagenProducto(
        tiendaId = tiendaId,
        productoId = productoId,
        archivoId = archivoId,
        contentType = imagen.contentType,
        bytes = imagen.bytes
    )

    suspend fun leerImagenPrivada(tiendaId: String, productoId: String, indice: Int = 0): ByteArray =
        api.leerImagenPrivadaProducto(tiendaId, productoId, indice)

    suspend fun leerCompleto(tiendaId: String, productoId: String): ProductoEmprendedora = withContext(Dispatchers.IO) {
        val referencia = db.collection("tiendasPrivadas").document(tiendaId).collection("productos").document(productoId)
        val documento = Tasks.await(referencia.get())
        if (!documento.exists()) throw ApiEmprendedoraException("no-encontrado", "El producto ya no existe en esta tienda.")
        val variantes = Tasks.await(referencia.collection("variantes").get()).documents
            .mapNotNull { runCatching { it.aVariante() }.getOrNull() }
        documento.aProducto().copy(variantes = variantes)
    }

    suspend fun publicar(tiendaId: String, producto: ProductoEmprendedora) {
        api.llamar("publicarProducto", mapOf(
            "tiendaId" to tiendaId,
            "productoId" to producto.id,
            "operacionId" to operationId("publicar", tiendaId, producto.id, producto.versionEsperada),
            "versionEsperada" to producto.versionEsperada
        ))
    }

    suspend fun retirar(tiendaId: String, producto: ProductoEmprendedora) {
        api.llamar("retirarProducto", mapOf(
            "tiendaId" to tiendaId,
            "productoId" to producto.id,
            "operacionId" to operationId("retirar", tiendaId, producto.id, producto.versionEsperada),
            "versionEsperada" to producto.versionEsperada
        ))
    }

    suspend fun eliminar(tiendaId: String, producto: ProductoEmprendedora) {
        api.llamar("eliminarProducto", mapOf(
            "tiendaId" to tiendaId,
            "productoId" to producto.id,
            "operacionId" to operationId("eliminar", tiendaId, producto.id, producto.versionEsperada),
            "versionEsperada" to producto.versionEsperada
        ))
    }

    suspend fun ajustarStock(
        tiendaId: String,
        producto: ProductoEmprendedora,
        varianteId: String?,
        cantidad: Int,
        motivo: String,
        nota: String?,
        operacionId: String
    ) {
        val solicitud = linkedMapOf<String, Any?>(
            "tiendaId" to tiendaId,
            "productoId" to producto.id,
            "operacionId" to operacionId,
            "versionEsperada" to producto.versionEsperada,
            "cantidad" to cantidad,
            "motivo" to motivo
        )
        varianteId?.let { solicitud["varianteId"] = it }
        nota?.trim()?.takeIf(String::isNotEmpty)?.let { solicitud["nota"] = it }
        api.llamar("ajustarStock", solicitud)
    }

    fun cerrar() {
        productSubscription?.remove()
        typeSubscription?.remove()
        categorySubscription?.remove()
        variantSubscriptions.values.forEach(ListenerRegistration::remove)
        variantSubscriptions.clear()
    }

    private fun operationId(vararg partes: String): String = synchronized(operationIds) {
        operationIds.getOrPut(partes.joinToString("/")) { UUID.randomUUID().toString() }
    }

    private fun DocumentSnapshot.aProducto() = ProductoEmprendedora(
        id = id,
        nombre = getString("nombre") ?: id,
        descripcion = getString("descripcion").orEmpty(),
        tipoProductoId = getString("tipoProductoId").orEmpty(),
        versionTipoProducto = getLong("versionTipoProducto")?.toInt() ?: 1,
        categoriaId = getString("categoriaId").orEmpty(),
        atributosEspecificos = mapaTexto("atributosEspecificos"),
        tieneVariantes = getBoolean("tieneVariantes") == true,
        modalidad = getString("modalidad") ?: "regular",
        manejaStock = getBoolean("manejaStock") == true,
        alAgotarse = getString("alAgotarse") ?: "mostrar_agotado",
        precioBase = getLong("precioBase"),
        stock = getLong("stock")?.toInt(),
        variantes = variantsById[id].orEmpty(),
        imagenes = (get("imagenes") as? List<*>).orEmpty().filterIsInstance<String>(),
        estadoPublicacion = getString("estadoPublicacion") ?: "borrador",
        versionEsperada = versionEsperada()
    )

    private fun DocumentSnapshot.aVariante() = VarianteProductoEmprendedora(
        id = id,
        opciones = mapaTexto("opciones"),
        precio = getLong("precio") ?: 0L,
        stock = getLong("stock")?.toInt(),
        activa = getBoolean("activa") != false
    )

    private fun DocumentSnapshot.mapaTexto(campo: String) =
        (get(campo) as? Map<*, *>)?.mapNotNull { (key, value) ->
            if (key is String && value is String) key to value else null
        }?.toMap().orEmpty()

    private fun DocumentSnapshot.versionEsperada(): String {
        val timestamp = getTimestamp("actualizadoEn")
            ?: throw ApiEmprendedoraException("datos-invalidos", "El producto no tiene una versión válida para editar.")
        return "${timestamp.seconds}:${timestamp.nanoseconds}"
    }

    private fun reconstruir() {
        _productos.value = _productos.value.map { producto ->
            producto.copy(variantes = variantsById[producto.id] ?: producto.variantes)
        }
    }

    private fun informarError(exception: Exception) {
        _error.value = when ((exception as? com.google.firebase.firestore.FirebaseFirestoreException)?.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Firestore no permitió leer el catálogo privado de esta tienda."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> "No hay conexión con Firebase."
            else -> "No se pudo cargar la información de productos."
        }
    }
}
