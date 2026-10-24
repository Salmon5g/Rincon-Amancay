package cl.equipo.amancay.data

/**
 * Modelos de datos públicos que el comprador puede ver.
 *
 * No son documentos crudos de Firestore: son versiones "limpias" que arma
 * [RepositorioCatalogo] con los campos que necesita la interfaz.
 */

/** Una tienda visible en el directorio. [mostrarPrecios] indica si se pueden mostrar precios. */
data class TiendaPublica(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val tipoEmprendimientoId: String,
    val sectorId: String,
    val mostrarPrecios: Boolean = true
)

/** Sector al que pertenece una tienda (para filtros y etiquetas). */
data class SectorPublico(val id: String, val nombre: String)

/** Tipo de emprendimiento de una tienda (para filtros y etiquetas). */
data class TipoEmprendimientoPublico(val id: String, val nombre: String)

/** Categoría de un producto (para mostrar su nombre en vez del id). */
data class CategoriaPublica(val id: String, val nombre: String)

/**
 * Un producto publicado de una tienda.
 *
 * Si [tieneVariantes] es true, el precio está en las variantes y [precioBase] puede venir vacío.
 */
data class ProductoPublico(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val categoriaId: String,
    val tipoProductoId: String,
    val tieneVariantes: Boolean,
    val moneda: String,
    val unidadVenta: String,
    val modalidad: String,
    val estadoDisponibilidad: String,
    val precioBase: Long?,
    val atributosEspecificos: Map<String, String>
)

/** Una variante activa de un producto (por ejemplo talla/color) con su propio precio. */
data class VariantePublica(
    val id: String,
    val opciones: Map<String, String>,
    val precio: Long?,
    val estadoDisponibilidad: String,
    val activa: Boolean
)
