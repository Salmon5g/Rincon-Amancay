package cl.equipo.amancay.data

import cl.equipo.amancay.ConexionLocal
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Única puerta de entrada a Firestore para el modo comprador.
 *
 * La interfaz (composables) nunca llama a Firestore directamente: siempre pasa por aquí.
 * Todas las lecturas se hacen fuera del hilo principal ([Dispatchers.IO]) y respetan las
 * reglas de seguridad de Firestore (solo lee lo que está publicado y habilitado).
 */
class RepositorioCatalogo(private val servicios: ConexionLocal.Servicios) {

    /** Resultado de una página del directorio: la lista, el cursor y si hay más. */
    data class PaginaTiendas(
        val tiendas: List<TiendaPublica>,
        val ultimoVisor: DocumentSnapshot?,
        val hayMas: Boolean
    )

    private val tamanoPagina = 20L

    /** Sectores activos, para el filtro "Sector". */
    suspend fun listarSectores(): List<SectorPublico> =
        listarActivos("sectores") { id, nombre -> SectorPublico(id, nombre) }

    /** Tipos de emprendimiento activos, para el filtro "Tipo de emprendimiento". */
    suspend fun listarTiposEmprendimiento(): List<TipoEmprendimientoPublico> =
        listarActivos("tiposEmprendimiento") { id, nombre -> TipoEmprendimientoPublico(id, nombre) }

    /** Categorías activas, para mostrar el nombre de la categoría de cada producto. */
    suspend fun listarCategorias(): List<CategoriaPublica> =
        listarActivos("categorias") { id, nombre -> CategoriaPublica(id, nombre) }

    /**
     * Directorio paginado: tiendas publicadas y habilitadas, con filtros opcionales.
     * [ultimoVisor] es el cursor para traer la página siguiente (ver [PaginaTiendas.hayMas]).
     */
    suspend fun listarTiendas(
        tipoEmprendimientoId: String? = null,
        sectorId: String? = null,
        ultimoVisor: DocumentSnapshot? = null
    ): PaginaTiendas = withContext(Dispatchers.IO) {
        var query = servicios.db.collection("tiendasPublicas")
            .whereEqualTo("estadoPublicacion", "publicado")
            .whereEqualTo("habilitada", true)
        if (tipoEmprendimientoId != null) query = query.whereEqualTo("tipoEmprendimientoId", tipoEmprendimientoId)
        if (sectorId != null) query = query.whereEqualTo("sectorId", sectorId)
        query = query.limit(tamanoPagina)
        if (ultimoVisor != null) query = query.startAfter(ultimoVisor)
        val documentos = query.get().await().documents
        PaginaTiendas(
            tiendas = documentos.map { it.aTiendaPublica() },
            ultimoVisor = documentos.lastOrNull(),
            hayMas = documentos.size.toLong() == tamanoPagina
        )
    }

    /** Una tienda por su id (o null si no existe o no se puede leer). */
    suspend fun obtenerTienda(tiendaId: String): TiendaPublica? = withContext(Dispatchers.IO) {
        val documento = servicios.db.collection("tiendasPublicas").document(tiendaId).get().await()
        if (documento.exists()) documento.aTiendaPublica() else null
    }

    /** Productos publicados de una tienda. Requiere que la tienda muestre precios (reglas). */
    suspend fun listarProductos(tiendaId: String): List<ProductoPublico> = withContext(Dispatchers.IO) {
        servicios.db.collection("tiendasPublicas").document(tiendaId)
            .collection("productos")
            .whereEqualTo("estadoPublicacion", "publicado")
            .get().await().documents.map { it.aProductoPublico() }
    }

    /** Un producto por su id. Requiere que la tienda muestre precios (reglas). */
    suspend fun obtenerProducto(tiendaId: String, productoId: String): ProductoPublico? =
        withContext(Dispatchers.IO) {
            val documento = servicios.db.collection("tiendasPublicas").document(tiendaId)
                .collection("productos").document(productoId).get().await()
            if (documento.exists()) documento.aProductoPublico() else null
        }

    /** Variantes activas de un producto. Requiere que la tienda muestre precios (reglas). */
    suspend fun listarVariantes(tiendaId: String, productoId: String): List<VariantePublica> =
        withContext(Dispatchers.IO) {
            servicios.db.collection("tiendasPublicas").document(tiendaId)
                .collection("productos").document(productoId)
                .collection("variantes")
                .whereEqualTo("activa", true)
                .get().await().documents.map { it.aVariantePublica() }
        }

    /** Lee una colección de catálogo filtrando solo los documentos con activo = true. */
    private suspend fun <T> listarActivos(coleccion: String, construir: (String, String) -> T): List<T> =
        withContext(Dispatchers.IO) {
            servicios.db.collection(coleccion)
                .whereEqualTo("activo", true)
                .get().await().documents.mapNotNull { documento ->
                    val nombre = documento.getString("nombre") ?: return@mapNotNull null
                    construir(documento.id, nombre)
                }
        }

    /** Convierte un documento de Firestore en el modelo de tienda que usa la interfaz. */
    private fun DocumentSnapshot.aTiendaPublica() = TiendaPublica(
        id = id,
        nombre = getString("nombre") ?: "",
        descripcion = getString("descripcion") ?: "",
        tipoEmprendimientoId = getString("tipoEmprendimientoId") ?: "",
        sectorId = getString("sectorId") ?: "",
        mostrarPrecios = getBoolean("mostrarPrecios") ?: true
    )

    /** Convierte un documento de Firestore en el modelo de producto que usa la interfaz. */
    private fun DocumentSnapshot.aProductoPublico() = ProductoPublico(
        id = id,
        nombre = getString("nombre") ?: "",
        descripcion = getString("descripcion") ?: "",
        categoriaId = getString("categoriaId") ?: "",
        tipoProductoId = getString("tipoProductoId") ?: "",
        tieneVariantes = getBoolean("tieneVariantes") ?: false,
        moneda = getString("moneda") ?: "CLP",
        unidadVenta = getString("unidadVenta") ?: "",
        modalidad = getString("modalidad") ?: "",
        estadoDisponibilidad = getString("estadoDisponibilidad") ?: "",
        precioBase = getLong("precioBase"),
        atributosEspecificos = mapaTexto("atributosEspecificos")
    )

    /** Convierte un documento de Firestore en el modelo de variante que usa la interfaz. */
    private fun DocumentSnapshot.aVariantePublica() = VariantePublica(
        id = id,
        opciones = mapaTexto("opciones"),
        precio = getLong("precio"),
        estadoDisponibilidad = getString("estadoDisponibilidad") ?: "",
        activa = getBoolean("activa") ?: true
    )

    /** Lee un campo tipo mapa de Firestore y lo deja como Map<String, String> simple. */
    private fun DocumentSnapshot.mapaTexto(campo: String): Map<String, String> =
        (get(campo) as? Map<*, *>)
            ?.mapNotNull { (clave, valor) ->
                if (clave is String && valor != null) clave to valor.toString() else null
            }?.toMap().orEmpty()
}
