package com.example.amancay.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VarianteCatalogoComprador(
    val id: String,
    val opciones: Map<String, String>,
    val precio: Long?,
    val estadoDisponibilidad: String
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
    val variantes: List<VarianteCatalogoComprador> = emptyList()
)

interface CatalogoCompradorRepository {
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

    private data class Tienda(val id: String, val nombre: String)

    private val tiendas = mutableMapOf<String, Tienda>()
    private val productosPorTienda = mutableMapOf<String, List<ProductoCatalogoComprador>>()
    private val variantesPorProducto = mutableMapOf<String, List<VarianteCatalogoComprador>>()
    private val suscripcionesProductos = mutableMapOf<String, ListenerRegistration>()
    private val suscripcionesVariantes = mutableMapOf<String, ListenerRegistration>()
    private val nombresCategorias = mutableMapOf<String, String>()
    private val nombresTiposProducto = mutableMapOf<String, String>()
    private val suscripciones = mutableListOf<ListenerRegistration>()

    init {
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

        suscripciones += db.collection("tiendasPublicas")
            .whereEqualTo("estadoPublicacion", "publicado")
            .whereEqualTo("habilitada", true)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    _cargando.value = false
                    return@addSnapshotListener
                }
                val documentos = snapshot?.documents.orEmpty()
                val tiendasNuevas = documentos.associate { documento ->
                    documento.id to Tienda(
                        id = documento.id,
                        nombre = documento.getString("nombre")?.takeIf(String::isNotBlank) ?: documento.id
                    )
                }
                tiendas.clear()
                tiendas.putAll(tiendasNuevas)

                val habilitadasParaCatalogo = documentos
                    .filter { it.getBoolean("mostrarPrecios") == true }
                    .map { it.id }
                    .toSet()
                (suscripcionesProductos.keys - habilitadasParaCatalogo).forEach { id ->
                    suscripcionesProductos.remove(id)?.remove()
                    productosPorTienda.remove(id).orEmpty().forEach { producto ->
                        quitarSuscripcionVariantes(clave(producto.tiendaId, producto.id))
                    }
                }
                habilitadasParaCatalogo.forEach { tiendaId ->
                    if (suscripcionesProductos[tiendaId] == null) escucharProductos(tiendaId)
                }
                _error.value = null
                _cargando.value = false
                reconstruirCatalogo()
            }
    }

    private fun escucharProductos(tiendaId: String) {
        suscripcionesProductos[tiendaId] = db.collection("tiendasPublicas")
            .document(tiendaId)
            .collection("productos")
            .whereEqualTo("estadoPublicacion", "publicado")
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
        suscripcionesVariantes[id] = db.collection("tiendasPublicas")
            .document(producto.tiendaId)
            .collection("productos")
            .document(producto.id)
            .collection("variantes")
            .whereEqualTo("activa", true)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    informarError(exception)
                    return@addSnapshotListener
                }
                variantesPorProducto[id] = snapshot?.documents.orEmpty().map { it.aVariante() }
                _error.value = null
                reconstruirCatalogo()
            }
    }

    private fun quitarSuscripcionVariantes(id: String) {
        suscripcionesVariantes.remove(id)?.remove()
        variantesPorProducto.remove(id)
    }

    private fun DocumentSnapshot.aProducto(tienda: Tienda) = ProductoCatalogoComprador(
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
        atributosEspecificos = mapaTexto("atributosEspecificos")
    )

    private fun DocumentSnapshot.aVariante() = VarianteCatalogoComprador(
        id = id,
        opciones = mapaTexto("opciones"),
        precio = getLong("precio"),
        estadoDisponibilidad = getString("estadoDisponibilidad") ?: "consultar"
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
        suscripcionesVariantes.clear()
    }

    private fun clave(tiendaId: String, productoId: String) = "$tiendaId/$productoId"
}
