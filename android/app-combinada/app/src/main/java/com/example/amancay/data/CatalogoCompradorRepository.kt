package com.example.amancay.data

import android.net.Uri
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
    val puntoRetiro: PuntoRetiroTienda? = null
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
    val error: StateFlow<String?>
    fun cerrar()
}

fun crearCatalogoCompradorFirebase(db: FirebaseFirestore): CatalogoCompradorRepository = FirestoreCatalogoCompradorRepository(db)

private class FirestoreCatalogoCompradorRepository(private val db: FirebaseFirestore) : CatalogoCompradorRepository {
    private val _productos = MutableStateFlow<List<ProductoCatalogoComprador>>(emptyList())
    override val productos: StateFlow<List<ProductoCatalogoComprador>> = _productos.asStateFlow()
    private val _cargando = MutableStateFlow(true)
    override val cargando: StateFlow<Boolean> = _cargando.asStateFlow()
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

    private val tiendas = mutableMapOf<String, TiendaCatalogoComprador>()
    private val productosPorTienda = mutableMapOf<String, List<ProductoCatalogoComprador>>()
    private val variantesPorProducto = mutableMapOf<String, List<VarianteCatalogoComprador>>()
    private val suscripcionesProductos = mutableMapOf<String, ListenerRegistration>()
    private val coleccionProductosPorTienda = mutableMapOf<String, String>()
    private val suscripcionesVariantes = mutableMapOf<String, ListenerRegistration>()
    private val nombresCategorias = mutableMapOf<String, String>()
    private val nombresTiposProducto = mutableMapOf<String, String>()
    private val suscripciones = mutableListOf<ListenerRegistration>()

    init {
        suscripciones += db.document("configuracionAplicacion/navegacion")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, exception ->
                if (exception != null) informarError(exception)
                else if (snapshot != null && !snapshot.metadata.isFromCache) {
                    _modo.value = snapshot.getString("modo") ?: "permitir"
                }
            }
        listOf("tiposEmprendimiento" to _tipos, "sectores" to _sectores).forEach { (coleccion, destino) ->
            suscripciones += db.collection(coleccion).whereEqualTo("activo", true)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) informarError(exception)
                    else destino.value = snapshot?.documents.orEmpty().associate { it.id to (it.getString("nombre") ?: it.id) }
                }
        }
        suscripciones += db.collection("categorias")
            .whereEqualTo("activo", true)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    return@addSnapshotListener
                }
                nombresCategorias.clear()
                snapshot?.documents?.forEach { documento ->
                    nombresCategorias[documento.id] = documento.getString("nombre") ?: documento.id
                }
                reconstruirCatalogo()
            }

        suscripciones += db.collection("tiposProducto")
            .whereEqualTo("activo", true)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    return@addSnapshotListener
                }
                nombresTiposProducto.clear()
                snapshot?.documents?.forEach { documento ->
                    nombresTiposProducto[documento.id] = documento.getString("nombre") ?: documento.id
                }
                reconstruirCatalogo()
            }

        suscripciones += db.collection("asociacionesPublicas")
            .whereEqualTo("activo", true)
            .limit(20)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    return@addSnapshotListener
                }
                _asociaciones.value = snapshot?.documents.orEmpty().map { documento ->
                    val integrantes = (documento.get("integrantes") as? List<*>).orEmpty().mapNotNull { item ->
                        val datos = item as? Map<*, *> ?: return@mapNotNull null
                        val tiendaId = datos["tiendaId"] as? String ?: return@mapNotNull null
                        val nombre = datos["nombre"] as? String ?: tiendaId
                        IntegranteAsociacionCatalogo(tiendaId, nombre)
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
                        puntoRetiro = (documento.get("puntoRetiro") as? Map<*, *>)?.let { punto ->
                            val latitud = punto["latitud"] as? Number
                            val longitud = punto["longitud"] as? Number
                            if (latitud != null && longitud != null) PuntoRetiroTienda(
                                latitud = latitud.toDouble(),
                                longitud = longitud.toDouble(),
                                descripcion = punto["descripcion"] as? String ?: ""
                            ) else null
                        }
                    )
                }
                tiendas.clear()
                tiendas.putAll(tiendasNuevas)
                _directorio.value = tiendas.values.sortedBy { it.nombre.lowercase() }

                val tiendasPublicadas = documentos.map { it.id }.toSet()
                (suscripcionesProductos.keys - tiendasPublicadas).forEach { id ->
                    suscripcionesProductos.remove(id)?.remove()
                    coleccionProductosPorTienda.remove(id)
                    productosPorTienda.remove(id).orEmpty().forEach { producto ->
                        quitarSuscripcionVariantes(clave(producto.tiendaId, producto.id))
                    }
                }
                tiendasNuevas.values.forEach { tienda ->
                    val coleccion = if (tienda.mostrarPrecios) "productos" else "productosConsulta"
                    if (coleccionProductosPorTienda[tienda.id] != coleccion) {
                        suscripcionesProductos.remove(tienda.id)?.remove()
                        productosPorTienda.remove(tienda.id).orEmpty().forEach { producto ->
                            quitarSuscripcionVariantes(clave(producto.tiendaId, producto.id))
                        }
                        escucharProductos(tienda.id, coleccion)
                    }
                }
                _error.value = null
                _cargando.value = false
                reconstruirCatalogo()
            }
    }

    private fun escucharProductos(tiendaId: String, coleccion: String) {
        coleccionProductosPorTienda[tiendaId] = coleccion
        suscripcionesProductos[tiendaId] = db.collection("tiendasPublicas")
            .document(tiendaId)
            .collection(coleccion)
            .whereEqualTo("estadoPublicacion", "publicado")
            .limit(20)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    return@addSnapshotListener
                }
                val tienda = tiendas[tiendaId] ?: return@addSnapshotListener
                val lista = snapshot?.documents.orEmpty().map { it.aProducto(tienda) }
                val productosPrevios = productosPorTienda.put(tiendaId, lista).orEmpty()
                val clavesActuales = lista.map { clave(it.tiendaId, it.id) }.toSet()
                productosPrevios
                    .map { clave(it.tiendaId, it.id) }
                    .filterNot(clavesActuales::contains)
                    .forEach(::quitarSuscripcionVariantes)
                lista.filter { it.tieneVariantes }.forEach(::escucharVariantes)
                _error.value = null
                reconstruirCatalogo()
            }
    }

    private fun escucharVariantes(producto: ProductoCatalogoComprador) {
        val id = clave(producto.tiendaId, producto.id)
        if (suscripcionesVariantes[id] != null) return
        val coleccion = coleccionProductosPorTienda[producto.tiendaId] ?: "productos"
        suscripcionesVariantes[id] = db.collection("tiendasPublicas")
            .document(producto.tiendaId)
            .collection(coleccion)
            .document(producto.id)
            .collection("variantes")
            .whereEqualTo("activa", true)
            .limit(20)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    return@addSnapshotListener
                }
                variantesPorProducto[id] = snapshot?.documents.orEmpty().map { it.aVariante(producto) }
                _error.value = null
                reconstruirCatalogo()
            }
    }

    private fun quitarSuscripcionVariantes(id: String) {
        suscripcionesVariantes.remove(id)?.remove()
        variantesPorProducto.remove(id)
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
        _productos.value = productosPorTienda.values.flatten()
            .map { producto ->
                producto.copy(variantes = variantesPorProducto[clave(producto.tiendaId, producto.id)].orEmpty())
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.nombre })
    }

    private fun informarError(exception: Exception) {
        _error.value = when ((exception as? com.google.firebase.firestore.FirebaseFirestoreException)?.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Firestore no permitió leer el catálogo público. Revisa las reglas publicadas."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                "No hay conexión con Firebase. Revisa tu internet e inténtalo otra vez."
            else -> "No se pudo cargar el catálogo desde Firebase."
        }
    }

    override fun cerrar() {
        suscripciones.forEach(ListenerRegistration::remove)
        suscripcionesProductos.values.forEach(ListenerRegistration::remove)
        suscripcionesVariantes.values.forEach(ListenerRegistration::remove)
        suscripciones.clear()
        suscripcionesProductos.clear()
        coleccionProductosPorTienda.clear()
        suscripcionesVariantes.clear()
    }

    private fun clave(tiendaId: String, productoId: String) = "$tiendaId/$productoId"
}

private fun urlImagenCatalogo(tiendaId: String, productoId: String, rutas: List<String>): String? {
    val patron = Regex("^catalogo/${Regex.escape(tiendaId)}/productos/${Regex.escape(productoId)}/[0-9a-f]{64}_1600\\.webp$")
    val ruta = rutas.firstOrNull(patron::matches) ?: return null
    val segmentos = ruta.split('/').joinToString("/") { Uri.encode(it) }
    return "https://supabase-fixmol.codepulse.cl/storage/v1/object/public/amancay-catalogo/$segmentos"
}
