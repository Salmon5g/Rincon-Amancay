package com.example.amancay.data

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VarianteCatalogoComprador(
    val id: String,
    val opciones: Map<String, String>,
    val precio: Long?,
    val estadoDisponibilidad: String,
    val imagenUrl: String? = null
)

data class ProductoCatalogoComprador(
    val id: String,
    val tiendaId: String,
    val tiendaNombre: String,
    val nombre: String,
    val descripcion: String,
    val categoriaId: String,
    val categoriaNombre: String,
    val tipoProductoId: String,
    val tipoProductoNombre: String,
    val moneda: String,
    val unidadVenta: String,
    val modalidad: String,
    val estadoDisponibilidad: String,
    val precioBase: Long?,
    val tieneVariantes: Boolean,
    val atributosEspecificos: Map<String, String>,
    val variantes: List<VarianteCatalogoComprador> = emptyList(),
    val imagenUrl: String? = null
)

data class ContactoCatalogoComprador(val tipo: String, val valor: String)

data class TiendaCatalogoComprador(
    val id: String,
    val nombre: String,
    val tipoId: String,
    val sectorId: String,
    val mostrarPrecios: Boolean = true,
    val formaContacto: String = "formulario",
    val contacto: ContactoCatalogoComprador? = null,
    val whatsappHabilitado: Boolean = false,
    val whatsappNumero: String = "",
    val formularioHabilitado: Boolean = true,
    val correoHabilitado: Boolean = false,
    val correoDireccion: String = "",
    val calificacionesHabilitadas: Boolean = false,
    val puntoRetiro: PuntoRetiroTienda? = null,
    val descripcion: String = ""
)

data class IntegranteAsociacionCatalogo(val tiendaId: String, val nombre: String)

data class AsociacionCatalogoComprador(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val sectorId: String,
    val integrantes: List<IntegranteAsociacionCatalogo>
)

interface CatalogoCompradorRepository {
    val directorio: StateFlow<List<TiendaCatalogoComprador>>
    val asociaciones: StateFlow<List<AsociacionCatalogoComprador>>
    val tiposEmprendimiento: StateFlow<Map<String, String>>
    val sectores: StateFlow<Map<String, String>>
    val modoNavegacion: StateFlow<String?>
    val productos: StateFlow<List<ProductoCatalogoComprador>>
    val cargando: StateFlow<Boolean>
    val cargandoProductos: StateFlow<Boolean>
    val hayMasProductos: StateFlow<Boolean>
    val productoEnDetalle: StateFlow<ProductoCatalogoComprador?>
    val error: StateFlow<String?>
    fun iniciar()
    fun pausar()
    fun cargarProductos(tiendaId: String)
    fun cargarMasProductos(tiendaId: String? = null)
    fun tieneMasProductos(tiendaId: String): Boolean
    fun observarDetalle(producto: ProductoCatalogoComprador)
    fun cerrarDetalle()
    fun cerrar()
}

fun crearCatalogoCompradorFirebase(db: FirebaseFirestore): CatalogoCompradorRepository = FirestoreCatalogoCompradorRepository(db)

private class FirestoreCatalogoCompradorRepository(private val db: FirebaseFirestore) : CatalogoCompradorRepository {
    private val _productos = MutableStateFlow<List<ProductoCatalogoComprador>>(emptyList())
    override val productos: StateFlow<List<ProductoCatalogoComprador>> = _productos.asStateFlow()
    private val _cargando = MutableStateFlow(true)
    override val cargando: StateFlow<Boolean> = _cargando.asStateFlow()
    private val _cargandoProductos = MutableStateFlow(false)
    override val cargandoProductos: StateFlow<Boolean> = _cargandoProductos.asStateFlow()
    private val _hayMasProductos = MutableStateFlow(false)
    override val hayMasProductos: StateFlow<Boolean> = _hayMasProductos.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val _directorio = MutableStateFlow<List<TiendaCatalogoComprador>>(emptyList())
    override val directorio = _directorio.asStateFlow()
    private val _asociaciones = MutableStateFlow<List<AsociacionCatalogoComprador>>(emptyList())
    override val asociaciones = _asociaciones.asStateFlow()
    private val _tipos = MutableStateFlow<Map<String, String>>(emptyMap())
    override val tiposEmprendimiento = _tipos.asStateFlow()
    private val _sectores = MutableStateFlow<Map<String, String>>(emptyMap())
    override val sectores = _sectores.asStateFlow()
    // Espera la política confirmada por servidor para no abrir el catálogo por
    // defecto mientras todavía no se conoce la opción guardada por administración.
    private val _modo = MutableStateFlow<String?>(null)
    override val modoNavegacion = _modo.asStateFlow()
    private val _productoEnDetalle = MutableStateFlow<ProductoCatalogoComprador?>(null)
    override val productoEnDetalle: StateFlow<ProductoCatalogoComprador?> = _productoEnDetalle.asStateFlow()

    private val tiendas = mutableMapOf<String, TiendaCatalogoComprador>()
    private val productosPorTienda = mutableMapOf<String, List<ProductoCatalogoComprador>>()
    private val tiendasConPaginaCargada = mutableSetOf<String>()
    private val cursoresProductos = mutableMapOf<String, DocumentSnapshot>()
    private val tiendasConMasProductos = mutableSetOf<String>()
    private val cargasProductosEnCurso = mutableSetOf<String>()
    private val nombresCategorias = mutableMapOf<String, String>()
    private val nombresTiposProducto = mutableMapOf<String, String>()
    private val suscripciones = mutableListOf<ListenerRegistration>()
    private val alcanceApi = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activo = false
    private var catalogosCargados = false
    private var asociacionesCargadas = false
    private var consultaModoEnCurso = false
    private var suscripcionProductoDetalle: ListenerRegistration? = null
    private var suscripcionVariantesDetalle: ListenerRegistration? = null
    private val tamanoPaginaGlobal = 5
    private val tamanoPaginaTienda = 20

    private val bloqueoProductos = Any()

    override fun iniciar() {
        if (activo) return
        activo = true
        suscripciones += db.document("configuracionAplicacion/navegacion")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, exception ->
                if (exception != null) informarError(exception)
                else if (snapshot != null && !snapshot.metadata.isFromCache) {
                    _modo.value = snapshot.getString("modo") ?: "permitir"
                }
            }
        if (_modo.value == null && !consultaModoEnCurso) {
            consultaModoEnCurso = true
            alcanceApi.launch {
                delay(2_000)
                if (_modo.value == null && isActive) {
                    try {
                        _modo.value = ApiNavegacionComprador.leerModo()
                    } catch (error: Exception) {
                        if (_modo.value == null) _error.value = error.message ?: "No se pudo consultar la configuración de acceso."
                    }
                }
                consultaModoEnCurso = false
            }
        }
        if (!catalogosCargados) cargarCatalogos()
        if (!asociacionesCargadas) cargarAsociaciones()
        escucharDirectorio()
        _productoEnDetalle.value?.let(::conectarDetalle)
    }

    private fun cargarCatalogos() {
        catalogosCargados = true
        listOf("tiposEmprendimiento" to _tipos, "sectores" to _sectores).forEach { (coleccion, destino) ->
            db.collection(coleccion).whereEqualTo("activo", true).limit(200).get()
                .addOnSuccessListener { snapshot ->
                    destino.value = snapshot.documents.associate { it.id to (it.getString("nombre") ?: it.id) }
                }
                .addOnFailureListener { exception -> catalogosCargados = false; informarError(exception) }
        }
        db.collection("categorias").whereEqualTo("activo", true).limit(200).get()
            .addOnSuccessListener { snapshot ->
                nombresCategorias.clear()
                snapshot.documents.forEach { nombresCategorias[it.id] = it.getString("nombre") ?: it.id }
                reconstruirCatalogo()
            }
            .addOnFailureListener { exception -> catalogosCargados = false; informarError(exception) }
        db.collection("tiposProducto").whereEqualTo("activo", true).limit(200).get()
            .addOnSuccessListener { snapshot ->
                nombresTiposProducto.clear()
                snapshot.documents.forEach { nombresTiposProducto[it.id] = it.getString("nombre") ?: it.id }
                reconstruirCatalogo()
            }
            .addOnFailureListener { exception -> catalogosCargados = false; informarError(exception) }
    }

    private fun cargarAsociaciones() {
        asociacionesCargadas = true
        db.collection("asociacionesPublicas").whereEqualTo("activo", true).limit(20).get()
            .addOnSuccessListener { snapshot ->
                _asociaciones.value = snapshot.documents.map { documento ->
                    val integrantes = (documento.get("integrantes") as? List<*>).orEmpty().mapNotNull { item ->
                        val datos = item as? Map<*, *> ?: return@mapNotNull null
                        val tiendaId = datos["tiendaId"] as? String ?: return@mapNotNull null
                        IntegranteAsociacionCatalogo(tiendaId, datos["nombre"] as? String ?: tiendaId)
                    }
                    AsociacionCatalogoComprador(
                        id = documento.id,
                        nombre = documento.getString("nombre") ?: documento.id,
                        descripcion = documento.getString("descripcion").orEmpty(),
                        sectorId = documento.getString("sectorId").orEmpty(),
                        integrantes = integrantes
                    )
                }.sortedBy { it.nombre.lowercase() }
            }
            .addOnFailureListener { exception -> asociacionesCargadas = false; informarError(exception) }
    }

    private fun escucharDirectorio() {
        _cargando.value = true
        suscripciones += db.collection("tiendasPublicas")
            .whereEqualTo("estadoPublicacion", "publicado")
            .whereEqualTo("habilitada", true)
            .limit(20)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    _cargando.value = false
                    return@addSnapshotListener
                }
                val documentos = snapshot?.documents.orEmpty()
                val tiendasNuevas = documentos.associate { documento ->
                    val contacto = (documento.get("contacto") as? Map<*, *>)?.let { datos ->
                        val tipo = datos["tipo"] as? String
                        val valor = datos["valor"] as? String
                        if (tipo != null && valor != null) ContactoCatalogoComprador(tipo, valor) else null
                    }
                    val canales = documento.get("canalesContacto") as? Map<*, *>
                    val whatsapp = canales?.get("whatsapp") as? Map<*, *>
                    val formulario = canales?.get("formulario") as? Map<*, *>
                    val correo = canales?.get("correo") as? Map<*, *>
                    val formaContacto = documento.getString("formaContacto") ?: "formulario"
                    documento.id to TiendaCatalogoComprador(
                        id = documento.id,
                        nombre = documento.getString("nombre")?.takeIf(String::isNotBlank) ?: documento.id,
                        tipoId = documento.getString("tipoEmprendimientoId").orEmpty(),
                        sectorId = documento.getString("sectorId").orEmpty(),
                        descripcion = documento.getString("descripcion").orEmpty().trim(),
                        mostrarPrecios = documento.getBoolean("mostrarPrecios") == true,
                        formaContacto = formaContacto,
                        contacto = contacto,
                        whatsappHabilitado = whatsapp?.get("habilitado") as? Boolean
                            ?: (formaContacto in listOf("whatsapp", "ambos") && contacto?.tipo == "whatsapp"),
                        whatsappNumero = (whatsapp?.get("numero") as? String)
                            ?: contacto?.takeIf { it.tipo == "whatsapp" }?.valor.orEmpty(),
                        formularioHabilitado = formulario?.get("habilitado") as? Boolean
                            ?: (formaContacto in listOf("formulario", "ambos")),
                        correoHabilitado = correo?.get("habilitado") as? Boolean ?: contacto?.tipo == "correo",
                        correoDireccion = (correo?.get("direccion") as? String)
                            ?: contacto?.takeIf { it.tipo == "correo" }?.valor.orEmpty(),
                        calificacionesHabilitadas = documento.getBoolean("calificacionesHabilitadas") == true,
                        puntoRetiro = (documento.get("puntoRetiro") as? Map<*, *>)?.let { punto ->
                            val latitud = punto["latitud"] as? Number
                            val longitud = punto["longitud"] as? Number
                            if (latitud != null && longitud != null) PuntoRetiroTienda(
                                latitud = latitud.toDouble(), longitud = longitud.toDouble(),
                                descripcion = punto["descripcion"] as? String ?: ""
                            ) else null
                        }
                    )
                }
                synchronized(bloqueoProductos) {
                    tiendasNuevas.values.forEach { nueva ->
                        val anterior = tiendas[nueva.id]
                        if (anterior != null && anterior.mostrarPrecios != nueva.mostrarPrecios) {
                            productosPorTienda.remove(nueva.id)
                            tiendasConPaginaCargada.remove(nueva.id)
                            cursoresProductos.remove(nueva.id)
                            tiendasConMasProductos.remove(nueva.id)
                        }
                    }
                    tiendas.clear()
                    tiendas.putAll(tiendasNuevas)
                    (productosPorTienda.keys - tiendasNuevas.keys).forEach { id ->
                        productosPorTienda.remove(id)
                        tiendasConPaginaCargada.remove(id)
                        cursoresProductos.remove(id)
                        tiendasConMasProductos.remove(id)
                    }
                }
                _directorio.value = tiendas.values.sortedBy { it.nombre.lowercase() }
                _error.value = null
                _cargando.value = false
                actualizarProductos()
                actualizarHayMasProductos()
            }
    }

    override fun cargarProductos(tiendaId: String) {
        cargarPaginaTienda(tiendaId, tamanoPaginaTienda, reiniciar = true)
    }

    override fun cargarMasProductos(tiendaId: String?) {
        if (tiendaId != null) {
            cargarPaginaTienda(tiendaId, tamanoPaginaTienda)
            return
        }
        val pendientes = _directorio.value.filter { tienda ->
            synchronized(bloqueoProductos) { tienda.id in tiendasConMasProductos || tienda.id !in tiendasConPaginaCargada }
        }
        if (pendientes.isEmpty()) {
            actualizarHayMasProductos()
            return
        }
        _cargandoProductos.value = true
        alcanceApi.launch {
            pendientes.map { tienda -> async { cargarPagina(tienda, tamanoPaginaGlobal) } }.awaitAll()
            _cargandoProductos.value = synchronized(bloqueoProductos) { cargasProductosEnCurso.isNotEmpty() }
            actualizarHayMasProductos()
        }
    }

    override fun tieneMasProductos(tiendaId: String): Boolean = synchronized(bloqueoProductos) {
        tiendaId in tiendasConMasProductos
    }

    private fun cargarPaginaTienda(tiendaId: String, limite: Int, reiniciar: Boolean = false) {
        val tienda = tiendas[tiendaId] ?: return
        _cargandoProductos.value = true
        alcanceApi.launch {
            while (synchronized(bloqueoProductos) { tiendaId in cargasProductosEnCurso }) delay(25)
            cargarPagina(tienda, limite, reiniciar)
            _cargandoProductos.value = synchronized(bloqueoProductos) { cargasProductosEnCurso.isNotEmpty() }
            actualizarHayMasProductos()
        }
    }

    private suspend fun cargarPagina(tienda: TiendaCatalogoComprador, limite: Int, reiniciar: Boolean = false) {
        val inicio = synchronized(bloqueoProductos) { limite > 0 && cargasProductosEnCurso.add(tienda.id) }
        if (!inicio) return
        try {
            val coleccion = if (tienda.mostrarPrecios) "productos" else "productosConsulta"
            var consulta = db.collection("tiendasPublicas").document(tienda.id).collection(coleccion)
                .whereEqualTo("estadoPublicacion", "publicado").limit(limite.toLong())
            if (!reiniciar) synchronized(bloqueoProductos) { cursoresProductos[tienda.id] }?.let { consulta = consulta.startAfter(it) }
            val resultado = consulta.get().await()
            val pagina = resultado.documents.map { it.aProducto(tienda) }
            synchronized(bloqueoProductos) {
                productosPorTienda[tienda.id] = if (reiniciar) pagina else
                    (productosPorTienda[tienda.id].orEmpty() + pagina).distinctBy { it.id }
                tiendasConPaginaCargada.add(tienda.id)
                resultado.documents.lastOrNull()?.let { cursoresProductos[tienda.id] = it }
                if (resultado.size() < limite) tiendasConMasProductos.remove(tienda.id)
                else tiendasConMasProductos.add(tienda.id)
            }
            _error.value = null
            actualizarProductos()
        } catch (exception: Exception) {
            informarError(exception)
        } finally {
            synchronized(bloqueoProductos) { cargasProductosEnCurso.remove(tienda.id) }
        }
    }

    private fun actualizarHayMasProductos() {
        _hayMasProductos.value = synchronized(bloqueoProductos) {
            tiendasConMasProductos.isNotEmpty() || _directorio.value.any { tienda -> tienda.id !in tiendasConPaginaCargada }
        }
    }

    override fun observarDetalle(producto: ProductoCatalogoComprador) {
        if (_productoEnDetalle.value?.let { it.tiendaId == producto.tiendaId && it.id == producto.id } == true) return
        cerrarDetalle()
        _productoEnDetalle.value = producto
        if (activo) conectarDetalle(producto)
    }

    private fun conectarDetalle(producto: ProductoCatalogoComprador) {
        if (suscripcionProductoDetalle != null) return
        val tienda = tiendas[producto.tiendaId] ?: return
        val coleccion = if (tienda.mostrarPrecios) "productos" else "productosConsulta"
        val referencia = db.collection("tiendasPublicas").document(tienda.id).collection(coleccion).document(producto.id)
        suscripcionProductoDetalle = referencia.addSnapshotListener { documento, exception ->
            if (exception != null) {
                informarError(exception)
                return@addSnapshotListener
            }
            if (documento == null || !documento.exists()) {
                cerrarDetalle()
                return@addSnapshotListener
            }
            val variantes = _productoEnDetalle.value?.variantes.orEmpty()
            val actualizado = documento.aProducto(tienda).copy(variantes = variantes)
            _productoEnDetalle.value = actualizado
            synchronized(bloqueoProductos) {
                productosPorTienda[tienda.id] = productosPorTienda[tienda.id].orEmpty().map {
                    if (it.id == actualizado.id) actualizado.copy(variantes = emptyList()) else it
                }
            }
            actualizarProductos()
        }
        if (producto.tieneVariantes) {
            suscripcionVariantesDetalle = referencia.collection("variantes").whereEqualTo("activa", true).limit(20)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        informarError(exception)
                        return@addSnapshotListener
                    }
                    val base = _productoEnDetalle.value ?: producto
                    _productoEnDetalle.value = base.copy(variantes = snapshot?.documents.orEmpty().map { it.aVariante(base) })
                }
        }
    }

    override fun cerrarDetalle() {
        suscripcionProductoDetalle?.remove()
        suscripcionVariantesDetalle?.remove()
        suscripcionProductoDetalle = null
        suscripcionVariantesDetalle = null
        _productoEnDetalle.value = null
    }
    private fun DocumentSnapshot.aProducto(tienda: TiendaCatalogoComprador) = ProductoCatalogoComprador(
        id = id,
        tiendaId = tienda.id,
        tiendaNombre = tienda.nombre,
        nombre = getString("nombre")?.takeIf(String::isNotBlank) ?: id,
        descripcion = getString("descripcion").orEmpty(),
        categoriaId = getString("categoriaId").orEmpty(),
        categoriaNombre = nombresCategorias[getString("categoriaId")] ?: getString("categoriaId").orEmpty(),
        tipoProductoId = getString("tipoProductoId").orEmpty(),
        tipoProductoNombre = nombresTiposProducto[getString("tipoProductoId")] ?: getString("tipoProductoId").orEmpty(),
        moneda = getString("moneda") ?: "CLP",
        unidadVenta = getString("unidadVenta").orEmpty(),
        modalidad = getString("modalidad").orEmpty(),
        estadoDisponibilidad = getString("estadoDisponibilidad") ?: "consultar",
        precioBase = getLong("precioBase"),
        tieneVariantes = getBoolean("tieneVariantes") == true,
        atributosEspecificos = mapaTexto("atributosEspecificos"),
        imagenUrl = urlImagenCatalogo(tienda.id, id, (get("imagenes") as? List<*>).orEmpty().filterIsInstance<String>())
    )

    private fun DocumentSnapshot.aVariante(producto: ProductoCatalogoComprador) = VarianteCatalogoComprador(
        id = id,
        opciones = mapaTexto("opciones"),
        precio = getLong("precio"),
        estadoDisponibilidad = getString("estadoDisponibilidad") ?: "consultar",
        imagenUrl = urlImagenCatalogo(producto.tiendaId, producto.id, listOfNotNull(getString("imagen")))
    )

    private fun DocumentSnapshot.mapaTexto(campo: String): Map<String, String> =
        (get(campo) as? Map<*, *>)
            ?.mapNotNull { (key, value) ->
                if (key is String && value is String) key to value else null
            }
            ?.toMap()
            .orEmpty()

    private fun reconstruirCatalogo() {
        synchronized(bloqueoProductos) {
            _productos.value = productosPorTienda.values.flatten().map { producto ->
                producto.copy(
                    categoriaNombre = nombresCategorias[producto.categoriaId] ?: producto.categoriaId,
                    tipoProductoNombre = nombresTiposProducto[producto.tipoProductoId] ?: producto.tipoProductoId
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.nombre })
        }
    }

    private fun actualizarProductos() = reconstruirCatalogo()

    private fun informarError(exception: Exception) {
        _error.value = when ((exception as? com.google.firebase.firestore.FirebaseFirestoreException)?.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Firestore no permitió leer el catálogo público. Revisa las reglas publicadas."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                "No hay conexión con Firebase. Revisa tu internet e inténtalo otra vez."
            else -> "No se pudo cargar el catálogo desde Firebase."
        }
    }

    override fun pausar() {
        if (!activo) return
        activo = false
        suscripciones.forEach(ListenerRegistration::remove)
        suscripcionProductoDetalle?.remove()
        suscripcionVariantesDetalle?.remove()
        suscripcionProductoDetalle = null
        suscripcionVariantesDetalle = null
        suscripciones.clear()
    }

    override fun cerrar() {
        pausar()
        alcanceApi.cancel()
    }
}

private fun urlImagenCatalogo(tiendaId: String, productoId: String, rutas: List<String>): String? {
    val patron = Regex("^catalogo/${Regex.escape(tiendaId)}/productos/${Regex.escape(productoId)}/[0-9a-f]{64}_1600\\.webp$")
    val ruta = rutas.firstOrNull(patron::matches) ?: return null
    val segmentos = ruta.split('/').joinToString("/") { Uri.encode(it) }
    return "https://supabase-fixmol.codepulse.cl/storage/v1/object/public/amancay-catalogo/$segmentos"
}
