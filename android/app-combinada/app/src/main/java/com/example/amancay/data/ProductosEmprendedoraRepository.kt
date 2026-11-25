package com.example.amancay.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import java.util.Date

data class CampoTipoProducto(val clave: String, val etiqueta: String, val obligatorio: Boolean)

data class TipoProductoEmprendedora(
    val id: String,
    val nombre: String,
    val version: Int,
    val campos: List<CampoTipoProducto>,
    val opcionesVariante: List<String>,
    val activo: Boolean = true
)

data class CategoriaEmprendedora(
    val id: String,
    val nombre: String,
    val activo: Boolean = true,
    val tipoProductoId: String? = null
)

data class VarianteProductoEmprendedora(
    val id: String,
    val opciones: Map<String, String>,
    val precio: Long,
    val stock: Int?,
    val activa: Boolean,
    val imagen: String? = null
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
    val versionEsperada: String,
    val disponible: Boolean = true
)

fun unidadesDisponibles(producto: ProductoEmprendedora): Int =
    if (producto.tieneVariantes) producto.variantes.filter { it.activa }.sumOf { it.stock ?: 0 }
    else producto.stock ?: 0

fun visibleEnCatalogoPublico(producto: ProductoEmprendedora): Boolean {
    if (producto.estadoPublicacion != "publicado" || !producto.disponible) return false
    val unidades = unidadesDisponibles(producto)
    if (producto.modalidad == "pieza_unica" && unidades <= 0) return false
    if (producto.manejaStock && unidades <= 0 && producto.alAgotarse == "ocultar") return false
    return true
}

fun etiquetaCatalogoCliente(producto: ProductoEmprendedora): String {
    if (!producto.disponible) return "No disponible · solo en tu tienda"
    if (producto.estadoPublicacion != "publicado") return "Borrador · no aparece en la web"
    val unidades = unidadesDisponibles(producto)
    if (producto.modalidad == "pieza_unica" && unidades <= 0) return "Pieza única vendida · no aparece en la web"
    if (producto.manejaStock && unidades <= 0) return when (producto.alAgotarse) {
        "ocultar" -> "Oculto al agotarse"
        "pasar_a_pedido" -> "A pedido"
        else -> "Agotado"
    }
    return when (producto.modalidad) {
        "a_pedido" -> "A pedido"
        "pieza_unica" -> "Pieza única · disponible"
        else -> if (producto.manejaStock) "En stock ($unidades)" else "Disponible para encargar"
    }
}

data class VentaRegistrada(
    val id: String,
    val productoId: String,
    val productoNombre: String,
    val varianteId: String?,
    val varianteNombre: String?,
    val precioUnitario: Long,
    val cantidad: Int,
    val total: Long,
    val fecha: Date,
    val lugarTipo: String,
    val feriaId: String?,
    val feriaNombre: String?,
    val lugarTexto: String?
)

data class ConsultaComprador(
    val id: String,
    val nombre: String,
    val correo: String,
    val mensaje: String,
    val producto: String?,
    val fecha: Date,
    val estado: String,
    val respuesta: String? = null,
    val respondidaEn: Date? = null,
    val notificable: Boolean = true
)

private object CacheCatalogosEmprendedora {
    private val definiciones = mutableMapOf<Pair<String, Int>, TipoProductoEmprendedora>()
    var tipos: List<TipoProductoEmprendedora>? = null
    var categorias: List<CategoriaEmprendedora>? = null

    @Synchronized fun definicion(id: String, version: Int): TipoProductoEmprendedora? = definiciones[id to version]
    @Synchronized fun guardarDefinicion(tipo: TipoProductoEmprendedora) { definiciones[tipo.id to tipo.version] = tipo }
}

data class FeriaParaVenta(
    val id: String,
    val nombre: String,
    val direccion: String,
    val fechaInicio: Date,
    val fechaFin: Date,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val estadoParticipacion: String = "confirmada"
)

data class PreferenciasInteraccionTienda(
    val avisosContacto: Boolean = false,
    val horaInicioAvisos: String = "09:00",
    val horaFinAvisos: String = "18:00",
    val calificacionesHabilitadas: Boolean = false
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
    val tieneVariantes: Boolean,
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
    private val _consultas = MutableStateFlow<List<ConsultaComprador>>(emptyList())
    val consultas: StateFlow<List<ConsultaComprador>> = _consultas.asStateFlow()
    private val _hayMasProductos = MutableStateFlow(false)
    val hayMasProductos: StateFlow<Boolean> = _hayMasProductos.asStateFlow()
    private val _cargandoMasProductos = MutableStateFlow(false)
    val cargandoMasProductos: StateFlow<Boolean> = _cargandoMasProductos.asStateFlow()
    private val _hayMasConsultas = MutableStateFlow(false)
    val hayMasConsultas: StateFlow<Boolean> = _hayMasConsultas.asStateFlow()
    private val _cargandoMasConsultas = MutableStateFlow(false)
    val cargandoMasConsultas: StateFlow<Boolean> = _cargandoMasConsultas.asStateFlow()

    private val productSubscriptions = mutableMapOf<Int, ListenerRegistration>()
    private var typeSubscription: ListenerRegistration? = null
    private var categorySubscription: ListenerRegistration? = null
    private var inquirySubscription: ListenerRegistration? = null
    private val inquiryPageSubscriptions = mutableMapOf<Int, ListenerRegistration>()
    private val productPages = mutableMapOf<Int, List<ProductoEmprendedora>>()
    private val productPageEnds = mutableMapOf<Int, DocumentSnapshot>()
    private val productPageCounts = mutableMapOf<Int, Int>()
    private val inquiryPages = mutableMapOf<Int, List<ConsultaComprador>>()
    private val inquiryPageEnds = mutableMapOf<Int, DocumentSnapshot>()
    private val inquiryPageCounts = mutableMapOf<Int, Int>()
    private val visibleProductIds = mutableSetOf<String>()
    private val variantSubscriptions = mutableMapOf<String, ListenerRegistration>()
    private val variantsById = mutableMapOf<String, List<VarianteProductoEmprendedora>>()
    private val definitionsByVersion = mutableMapOf<Pair<String, Int>, TipoProductoEmprendedora>()
    private val definitionLoadsPending = mutableSetOf<Pair<String, Int>>()
    private val operationIds = mutableMapOf<String, String>()

    private val pageSizeProducts = 20L
    private val pageSizeInquiries = 50L

    suspend fun leerTipo(id: String, version: Int): TipoProductoEmprendedora = withContext(Dispatchers.IO) {
        CacheCatalogosEmprendedora.definicion(id, version)?.let { return@withContext it }
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
            .also(CacheCatalogosEmprendedora::guardarDefinicion)
    }

    private var tiendaObservadaId: String? = null

    fun observarProductos(tiendaId: String) {
        val cambioTienda = tiendaObservadaId != null && tiendaObservadaId != tiendaId
        cerrar()
        if (cambioTienda) limpiarDatosDeTiendaAnterior()
        tiendaObservadaId = tiendaId
        cargarCatalogosEstables()
        observarPaginaProductos(tiendaId, 0)
        productPages.keys.filter { it > 0 }.sorted().forEach { pagina ->
            productPageEnds[pagina - 1]?.let { observarPaginaProductos(tiendaId, pagina, it) }
        }
    }

    fun observarConsultas(tiendaId: String) {
        val cambioTienda = tiendaObservadaId != null && tiendaObservadaId != tiendaId
        cerrar()
        if (cambioTienda) limpiarDatosDeTiendaAnterior()
        tiendaObservadaId = tiendaId
        observarPaginaConsultas(tiendaId, 0)
        inquiryPages.keys.filter { it > 0 }.sorted().forEach { pagina ->
            inquiryPageEnds[pagina - 1]?.let { observarPaginaConsultas(tiendaId, pagina, it) }
        }
    }

    private fun limpiarDatosDeTiendaAnterior() {
        productPages.clear()
        productPageEnds.clear()
        productPageCounts.clear()
        inquiryPages.clear()
        inquiryPageEnds.clear()
        inquiryPageCounts.clear()
        visibleProductIds.clear()
        variantsById.clear()
        _productos.value = emptyList()
        _consultas.value = emptyList()
        _hayMasProductos.value = false
        _hayMasConsultas.value = false
    }

    fun cargarMasProductos(tiendaId: String) {
        if (tiendaObservadaId != tiendaId || !_hayMasProductos.value || _cargandoMasProductos.value) return
        val pagina = (productPages.keys.maxOrNull() ?: 0) + 1
        val anterior = productPageEnds[pagina - 1] ?: return
        _cargandoMasProductos.value = true
        observarPaginaProductos(tiendaId, pagina, anterior)
    }

    fun cargarMasConsultas() {
        val tiendaId = tiendaObservadaId ?: return
        if (!_hayMasConsultas.value || _cargandoMasConsultas.value) return
        val pagina = (inquiryPages.keys.maxOrNull() ?: 0) + 1
        val anterior = inquiryPageEnds[pagina - 1] ?: return
        _cargandoMasConsultas.value = true
        observarPaginaConsultas(tiendaId, pagina, anterior)
    }

    fun actualizarVisibilidadVariante(tiendaId: String, productoId: String, visible: Boolean) {
        if (visible) {
            visibleProductIds.add(productoId)
            val producto = _productos.value.firstOrNull { it.id == productoId } ?: return
            if (producto.tieneVariantes) observarVariantes(tiendaId, productoId)
        } else {
            visibleProductIds.remove(productoId)
            variantSubscriptions.remove(productoId)?.remove()
            reconstruir()
        }
    }

    private fun observarPaginaProductos(
        tiendaId: String,
        pagina: Int,
        desde: DocumentSnapshot? = null
    ) {
        if (productSubscriptions[pagina] != null) return
        var consulta = db.collection("tiendasPrivadas").document(tiendaId).collection("productos")
            .orderBy(com.google.firebase.firestore.FieldPath.documentId())
            .limit(pageSizeProducts)
        desde?.let { consulta = consulta.startAfter(it) }
        productSubscriptions[pagina] = consulta.addSnapshotListener { snapshot, exception ->
            if (exception != null) {
                productSubscriptions.remove(pagina)?.remove()
                if (pagina > 0) _cargandoMasProductos.value = false
                informarError(exception)
                return@addSnapshotListener
            }
            val documentos = snapshot?.documents.orEmpty()
            val actuales = documentos.mapNotNull { runCatching { it.aProducto().copy(variantes = emptyList()) }.getOrNull() }
                .filterNot { it.estadoPublicacion == "eliminado" }
            productPages[pagina] = actuales
            productPageCounts[pagina] = documentos.size
            documentos.lastOrNull()?.let { productPageEnds[pagina] = it }
            _productos.value = productPages.toSortedMap().values.flatten().distinctBy { it.id }
            val ultimaPagina = productPages.keys.maxOrNull() ?: pagina
            _hayMasProductos.value = (productPageCounts[ultimaPagina] ?: documentos.size).toLong() >= pageSizeProducts
            if (pagina > 0) _cargandoMasProductos.value = false
            visibleProductIds.intersect(_productos.value.filter { it.tieneVariantes }.map { it.id }.toSet())
                .forEach { observarVariantes(tiendaId, it) }
            val idsCargados = _productos.value.filter { it.tieneVariantes }.map { it.id }.toSet()
            (variantSubscriptions.keys - idsCargados - visibleProductIds).forEach { id ->
                variantSubscriptions.remove(id)?.remove()
                variantsById.remove(id)
            }
            _error.value = null
            reconstruir()
        }
    }

    private fun observarPaginaConsultas(
        tiendaId: String,
        pagina: Int,
        desde: DocumentSnapshot? = null
    ) {
        if (inquiryPageSubscriptions[pagina] != null) return
        var consulta = db.collection("tiendasPrivadas").document(tiendaId).collection("consultas")
            .orderBy("creadaEn", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(pageSizeInquiries)
        desde?.let { consulta = consulta.startAfter(it) }
        val listener = consulta.addSnapshotListener { snapshot, exception ->
            if (exception != null) {
                inquiryPageSubscriptions.remove(pagina)?.remove()
                if (pagina == 0) inquirySubscription = null
                if (pagina > 0) _cargandoMasConsultas.value = false
                informarError(exception)
                return@addSnapshotListener
            }
            val documentos = snapshot?.documents.orEmpty()
            inquiryPageCounts[pagina] = documentos.size
            inquiryPages[pagina] = documentos.mapNotNull { doc ->
                val fecha = doc.getTimestamp("creadaEn")?.toDate() ?: return@mapNotNull null
                ConsultaComprador(doc.id, doc.getString("nombre").orEmpty(), doc.getString("correo").orEmpty(),
                    doc.getString("mensaje").orEmpty(), doc.getString("producto"), fecha,
                    doc.getString("estado") ?: "nueva", doc.getString("respuesta"), doc.getTimestamp("respondidaEn")?.toDate(),
                    doc.getBoolean("notificable") ?: true)
            }
            documentos.lastOrNull()?.let { inquiryPageEnds[pagina] = it }
            _consultas.value = inquiryPages.toSortedMap().values.flatten().distinctBy { it.id }
            val ultimaPagina = inquiryPages.keys.maxOrNull() ?: pagina
            _hayMasConsultas.value = (inquiryPageCounts[ultimaPagina] ?: documentos.size).toLong() >= pageSizeInquiries
            if (pagina > 0) _cargandoMasConsultas.value = false
        }
        inquiryPageSubscriptions[pagina] = listener
        if (pagina == 0) inquirySubscription = listener
    }

    private fun cargarCatalogosEstables() {
        CacheCatalogosEmprendedora.categorias?.let { categorias ->
            _categorias.value = categorias
        } ?: db.collection("categorias").limit(200).get()
            .addOnSuccessListener { snapshot ->
                val categorias = snapshot.documents.mapNotNull { doc ->
                    doc.getString("nombre")?.let { CategoriaEmprendedora(doc.id, it, doc.getBoolean("activo") == true, doc.getString("tipoProductoId")) }
                }.sortedBy { it.nombre.lowercase() }
                CacheCatalogosEmprendedora.categorias = categorias
                _categorias.value = categorias
            }
            .addOnFailureListener(::informarError)

        CacheCatalogosEmprendedora.tipos?.let { tipos ->
            _tipos.value = tipos
        } ?: db.collection("tiposProducto").limit(200).get()
            .addOnSuccessListener { snapshot ->
                val documentos = snapshot.documents
                if (documentos.isEmpty()) {
                    CacheCatalogosEmprendedora.tipos = emptyList()
                    _tipos.value = emptyList()
                    return@addOnSuccessListener
                }
                val pendientes = java.util.concurrent.atomic.AtomicInteger(documentos.size)
                val tiposPorId = java.util.concurrent.ConcurrentHashMap<String, TipoProductoEmprendedora>()
                fun terminarCarga() {
                    if (pendientes.decrementAndGet() == 0) {
                        val tipos = tiposPorId.values.sortedBy { it.nombre.lowercase() }
                        CacheCatalogosEmprendedora.tipos = tipos
                        _tipos.value = tipos
                    }
                }
                documentos.forEach { documento ->
                    val version = documento.getLong("versionActual")?.toInt()
                    if (version == null) {
                        terminarCarga()
                        return@forEach
                    }
                    val clave = documento.id to version
                    val nombre = documento.getString("nombre") ?: documento.id
                    val activo = documento.getBoolean("activo") == true
                    val cache = CacheCatalogosEmprendedora.definicion(documento.id, version)
                    if (cache != null) {
                        val actualizado = cache.copy(nombre = nombre, activo = activo)
                        definitionsByVersion[clave] = actualizado
                        tiposPorId[documento.id] = actualizado
                        terminarCarga()
                    } else if (!definitionLoadsPending.add(clave)) {
                        terminarCarga()
                    } else {
                        documento.reference.collection("versiones").document(version.toString()).get()
                            .addOnSuccessListener { definicion ->
                                if (definicion.exists()) {
                                    val campos = (definicion.get("campos") as? List<*>).orEmpty().mapNotNull { valor ->
                                        val map = valor as? Map<*, *> ?: return@mapNotNull null
                                        val key = map["clave"] as? String ?: return@mapNotNull null
                                        CampoTipoProducto(key, map["etiqueta"] as? String ?: key, map["obligatorio"] == true)
                                    }
                                    val opciones = (definicion.get("opcionesVariante") as? List<*>).orEmpty().filterIsInstance<String>()
                                    val tipo = TipoProductoEmprendedora(documento.id, nombre, version, campos, opciones, activo)
                                    CacheCatalogosEmprendedora.guardarDefinicion(tipo)
                                    definitionsByVersion[clave] = tipo
                                    tiposPorId[documento.id] = tipo
                                }
                                definitionLoadsPending.remove(clave)
                                terminarCarga()
                            }
                            .addOnFailureListener { error ->
                                definitionLoadsPending.remove(clave)
                                informarError(error)
                                terminarCarga()
                            }
                    }
                }
            }
            .addOnFailureListener(::informarError)
    }
    private fun observarVariantes(tiendaId: String, productoId: String) {
        if (variantSubscriptions[productoId] != null) return
        variantSubscriptions[productoId] = db.collection("tiendasPrivadas").document(tiendaId)
            .collection("productos").document(productoId).collection("variantes")
            .limit(20)
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
            "tieneVariantes" to solicitud.tieneVariantes,
            "moneda" to "CLP",
            "unidadVenta" to "unidad",
            "pasoCantidad" to 1,
            "modalidad" to solicitud.modalidad,
            "manejaStock" to solicitud.manejaStock,
            "alAgotarse" to solicitud.alAgotarse
        )
        if (!solicitud.tieneVariantes) {
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
        imagen: ImagenProductoPreparada,
        reemplazarIndice: Int? = null
    ): String = api.subirImagenProducto(
        tiendaId = tiendaId,
        productoId = productoId,
        archivoId = archivoId,
        contentType = imagen.contentType,
        bytes = imagen.bytes,
        reemplazarIndice = reemplazarIndice
    )

    suspend fun leerImagenPrivada(tiendaId: String, productoId: String, indice: Int = 0): ByteArray =
        api.leerImagenPrivadaProducto(tiendaId, productoId, indice)

    suspend fun asignarImagenVariante(tiendaId: String, productoId: String, varianteId: String, imagen: String) {
        api.llamar("asignarImagenVariante", mapOf(
            "tiendaId" to tiendaId,
            "productoId" to productoId,
            "varianteId" to varianteId,
            "operacionId" to UUID.randomUUID().toString(),
            "imagen" to imagen
        ))
    }

    suspend fun cambiarDisponibilidad(
        tiendaId: String,
        productos: List<ProductoEmprendedora>,
        disponible: Boolean,
        operacionIds: List<String> = emptyList()
    ) {
        require(productos.isNotEmpty())
        productos.distinctBy { it.id }.chunked(20).forEachIndexed { indice, grupo ->
            api.llamar("cambiarDisponibilidadProductos", mapOf(
                "tiendaId" to tiendaId,
                "operacionId" to operacionIds.getOrNull(indice).orEmpty().ifBlank { UUID.randomUUID().toString() },
                "disponible" to disponible,
                "items" to grupo.map { mapOf("productoId" to it.id, "versionEsperada" to it.versionEsperada) }
            ))
        }
    }

    suspend fun leerVersionesDisponibilidad(tiendaId: String, productoIds: List<String>): Map<String, String?> = withContext(Dispatchers.IO) {
        productoIds.distinct().associateWith { productoId ->
            val documento = Tasks.await(
                db.collection("tiendasPrivadas").document(tiendaId)
                    .collection("productos").document(productoId).get(Source.SERVER)
            )
            if (!documento.exists() || documento.getString("estadoPublicacion") == "eliminado") null
            else documento.versionEsperada()
        }
    }

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

    suspend fun eliminar(tiendaId: String, producto: ProductoEmprendedora, operacionId: String? = null) {
        api.llamar("eliminarProducto", mapOf(
            "tiendaId" to tiendaId,
            "productoId" to producto.id,
            "operacionId" to (operacionId ?: operationId("eliminar", tiendaId, producto.id, producto.versionEsperada)),
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

    suspend fun registrarVenta(
        tiendaId: String,
        productoId: String,
        varianteId: String?,
        cantidad: Int,
        lugarTipo: String,
        feriaId: String? = null,
        lugarTexto: String? = null,
        fecha: String? = null,
        operacionId: String = UUID.randomUUID().toString()
    ): Boolean {
        val solicitud = linkedMapOf<String, Any?>(
            "tiendaId" to tiendaId,
            "productoId" to productoId,
            "operacionId" to operacionId,
            "cantidad" to cantidad,
            "lugarTipo" to lugarTipo
        )
        varianteId?.let { solicitud["varianteId"] = it }
        feriaId?.let { solicitud["feriaId"] = it }
        lugarTexto?.let { solicitud["lugarTexto"] = it }
        fecha?.let { solicitud["fecha"] = it }
        return api.llamar("registrarVenta", solicitud).optBoolean("historialRegistrado", false)
    }

    suspend fun configurarHistorialVentas(tiendaId: String, activo: Boolean) {
        api.llamar("configurarHistorialVentas", mapOf("tiendaId" to tiendaId, "historialVentasActivo" to activo))
    }

    suspend fun configurarMostrarPrecios(tiendaId: String, mostrar: Boolean) {
        api.llamar("configurarMostrarPrecios", mapOf("tiendaId" to tiendaId, "mostrarPrecios" to mostrar))
    }

    suspend fun leerPreferenciasInteraccion(tiendaId: String): PreferenciasInteraccionTienda = withContext(Dispatchers.IO) {
        val doc = Tasks.await(db.collection("tiendasPrivadas").document(tiendaId).collection("configuracion").document("general").get(Source.SERVER))
        val horario = doc.get("notificacionesContacto") as? Map<*, *>
        PreferenciasInteraccionTienda(
            avisosContacto = horario?.get("habilitado") == true,
            horaInicioAvisos = horario?.get("horaInicio") as? String ?: "09:00",
            horaFinAvisos = horario?.get("horaFin") as? String ?: "18:00",
            calificacionesHabilitadas = doc.getBoolean("calificacionesHabilitadas") == true
        )
    }

    suspend fun configurarAvisosContacto(tiendaId: String, habilitado: Boolean, horaInicio: String, horaFin: String) {
        api.llamar("configurarAvisosContacto", mapOf(
            "tiendaId" to tiendaId, "habilitado" to habilitado,
            "horaInicio" to horaInicio, "horaFin" to horaFin, "zonaHoraria" to "America/Santiago"
        ))
        val tema = "amancay-contacto-$tiendaId"
        if (habilitado) com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic(tema).await()
        else com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(tema).await()
    }

    suspend fun restaurarAvisosContacto(tiendaId: String) = withContext(Dispatchers.IO) {
        val doc = Tasks.await(db.collection("tiendasPrivadas").document(tiendaId).collection("configuracion").document("general").get(Source.SERVER))
        val tema = "amancay-contacto-$tiendaId"
        if ((doc.get("notificacionesContacto") as? Map<*, *>)?.get("habilitado") == true) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic(tema).await()
        } else {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(tema).await()
        }
    }

    suspend fun desactivarAvisosContactoEnEsteDispositivo(tiendaId: String) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
            .unsubscribeFromTopic("amancay-contacto-$tiendaId").await()
    }

    suspend fun configurarCalificaciones(tiendaId: String, habilitado: Boolean) {
        api.llamar("configurarCalificaciones", mapOf("tiendaId" to tiendaId, "habilitado" to habilitado))
    }

    suspend fun leerMostrarPrecios(tiendaId: String): Boolean = withContext(Dispatchers.IO) {
        val referencia = db.collection("tiendasPrivadas").document(tiendaId).collection("configuracion").document("general")
        val documento = Tasks.await(referencia.get(Source.SERVER))
        if (!documento.exists()) throw ApiEmprendedoraException("no-encontrado", "No existe la configuración de la tienda.")
        documento.getBoolean("mostrarPrecios") ?: true
    }

    suspend fun configurarValoresDefecto(tiendaId: String, valores: ValoresDefectoProducto) {
        api.llamar("configurarValoresDefecto", mapOf(
            "tiendaId" to tiendaId,
            "modalidad" to valores.modalidad,
            "manejaStock" to valores.manejaStock,
            "alAgotarse" to valores.alAgotarse
        ))
    }

    suspend fun leerValoresDefecto(tiendaId: String): ValoresDefectoProducto = withContext(Dispatchers.IO) {
        val referencia = db.collection("tiendasPrivadas").document(tiendaId).collection("configuracion").document("general")
        val doc = try {
            Tasks.await(referencia.get(Source.SERVER))
        } catch (_: Exception) {
            val cache = runCatching { Tasks.await(referencia.get(Source.CACHE)) }.getOrNull()
            if (cache == null || !cache.exists()) {
                throw ApiEmprendedoraException("sin-conexion", "No se pudo comprobar la configuración de productos. Conéctate para iniciar un alta segura.")
            }
            cache
        }
        ValoresDefectoProducto(
            modalidad = doc.getString("modalidadDefecto") ?: "regular",
            manejaStock = doc.getBoolean("manejaStockDefecto") ?: true,
            alAgotarse = doc.getString("alAgotarseDefecto") ?: "mostrar_agotado"
        )
    }

    suspend fun leerHistorialActivo(tiendaId: String): Boolean = withContext(Dispatchers.IO) {
        Tasks.await(db.collection("tiendasPrivadas").document(tiendaId).collection("configuracion").document("general").get())
            .getBoolean("historialVentasActivo") == true
    }

    suspend fun marcarConsultaLeida(tiendaId: String, consultaId: String) = withContext(Dispatchers.IO) {
        Tasks.await(db.collection("tiendasPrivadas").document(tiendaId).collection("consultas").document(consultaId)
            .update(mapOf("estado" to "leida", "leidaEn" to com.google.firebase.firestore.FieldValue.serverTimestamp())))
    }

    suspend fun responderConsulta(tiendaId: String, consultaId: String, respuesta: String) = withContext(Dispatchers.IO) {
        val texto = respuesta.trim()
        require(texto.length in 1..2000) { "La respuesta debe tener entre 1 y 2000 caracteres." }
        api.llamar("responderConsulta", mapOf("tiendaId" to tiendaId, "consultaId" to consultaId, "respuesta" to texto))
    }

    suspend fun listarVentas(tiendaId: String): List<VentaRegistrada> = withContext(Dispatchers.IO) {
        Tasks.await(db.collection("tiendasPrivadas").document(tiendaId).collection("ventas")
            .orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING).get())
            .documents.mapNotNull { doc ->
                val fecha = doc.getTimestamp("fecha")?.toDate() ?: (doc.get("fecha") as? Date) ?: return@mapNotNull null
                VentaRegistrada(
                    id=doc.id, productoId=doc.getString("productoId").orEmpty(), productoNombre=doc.getString("productoNombre").orEmpty(),
                    varianteId=doc.getString("varianteId"),
                    varianteNombre=(doc.get("opcionesVariante") as? Map<*, *>)?.values?.filterIsInstance<String>()?.joinToString(" · ")?.takeIf(String::isNotBlank),
                    precioUnitario=doc.getLong("precioUnitario") ?: 0L,
                    cantidad=doc.getLong("cantidad")?.toInt() ?: 0, total=doc.getLong("total") ?: 0L, fecha=fecha,
                    lugarTipo=doc.getString("lugarTipo") ?: "otro", feriaId=doc.getString("feriaId"),
                    feriaNombre=doc.getString("feriaNombre"), lugarTexto=doc.getString("lugarTexto")
                )
            }
    }

    suspend fun listarFeriasParaVenta(tiendaId: String): List<FeriaParaVenta> = withContext(Dispatchers.IO) {
        val elementos = api.llamar("listarFeriasVenta", mapOf("tiendaId" to tiendaId)).optJSONArray("ferias")
            ?: return@withContext emptyList()
        (0 until elementos.length()).mapNotNull { indice ->
            val feria = elementos.optJSONObject(indice) ?: return@mapNotNull null
            val id = feria.optString("id").trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val fechaInicio = feria.fecha("fechaInicio") ?: return@mapNotNull null
            val fechaFin = feria.fecha("fechaFin") ?: return@mapNotNull null
            FeriaParaVenta(
                id = id,
                nombre = feria.optString("nombre").trim().ifBlank { "Feria" },
                direccion = feria.optString("direccion").trim(),
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                latitud = feria.optDouble("latitud", Double.NaN).takeIf { it.isFinite() && it in -90.0..90.0 },
                longitud = feria.optDouble("longitud", Double.NaN).takeIf { it.isFinite() && it in -180.0..180.0 },
                estadoParticipacion = feria.optString("estadoParticipacion").trim()
            )
        }
    }

    fun cerrar() {
        inquirySubscription?.remove()
        productSubscriptions.values.forEach(ListenerRegistration::remove)
        inquiryPageSubscriptions.values.forEach(ListenerRegistration::remove)
        typeSubscription?.remove()
        categorySubscription?.remove()
        variantSubscriptions.values.forEach(ListenerRegistration::remove)
        productSubscriptions.clear()
        inquiryPageSubscriptions.clear()
        variantSubscriptions.clear()
        _cargandoMasProductos.value = false
        _cargandoMasConsultas.value = false
        typeSubscription = null
        categorySubscription = null
        inquirySubscription = null
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
        versionEsperada = versionEsperada(),
        disponible = getBoolean("disponible") != false
    )

    private fun DocumentSnapshot.aVariante() = VarianteProductoEmprendedora(
        id = id,
        opciones = mapaTexto("opciones"),
        precio = getLong("precio") ?: 0L,
        stock = getLong("stock")?.toInt(),
        activa = getBoolean("activa") != false,
        imagen = getString("imagen")
    )

    private fun DocumentSnapshot.mapaTexto(campo: String) =
        (get(campo) as? Map<*, *>)?.mapNotNull { (key, value) ->
            if (key is String && value is String) key to value else null
        }?.toMap().orEmpty()

    private fun JSONObject.fecha(clave: String): Date? =
        if (isNull(clave)) null else runCatching { Date.from(Instant.parse(optString(clave))) }.getOrNull()

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
