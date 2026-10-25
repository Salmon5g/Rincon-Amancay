package cl.equipo.amancay.comprador

import cl.equipo.amancay.ErrorApi
import cl.equipo.amancay.data.ProductoPublico
import cl.equipo.amancay.data.RepositorioCatalogo
import cl.equipo.amancay.data.TiendaPublica
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Controlador del detalle de tienda (RF-CMP-02).
 *
 * Carga la tienda y, si la tienda muestra precios, su lista de productos publicados.
 * Expone el resultado como estado ([Estado]) para que [DetalleTiendaScreen] lo dibuje.
 */
class DetalleTiendaViewModel(
    private val repositorio: RepositorioCatalogo,
    private val tiendaId: String
) {
    /** Estado que observa la pantalla. [cargando] y [error] controlan qué se muestra. */
    data class Estado(
        val tienda: TiendaPublica? = null,
        val productos: List<ProductoPublico> = emptyList(),
        val categoriasPorId: Map<String, String> = emptyMap(),
        val cargando: Boolean = true,
        val error: String? = null
    )

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        cargar()
    }

    /** Vuelve a intentar la carga (botón "Reintentar"). */
    fun reintentar() = cargar()

    private fun cargar() {
        scope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            try {
                val categorias = repositorio.listarCategorias()
                val tienda = repositorio.obtenerTienda(tiendaId)
                // Si la tienda no muestra precios, las reglas bloquean el catálogo derivado.
                val productos = if (tienda?.mostrarPrecios == true) {
                    repositorio.listarProductos(tiendaId)
                } else {
                    emptyList()
                }
                _estado.update {
                    it.copy(
                        tienda = tienda,
                        productos = productos,
                        categoriasPorId = categorias.associate { categoria -> categoria.id to categoria.nombre },
                        cargando = false,
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = textoError(e)) }
            }
        }
    }

    /** Mensaje de error en español, distinguiendo errores de la API de los de conexión. */
    private fun textoError(e: Exception): String =
        if (e is ErrorApi) e.message ?: "No se pudo cargar la tienda."
        else "No se pudo cargar la tienda. Verifica que los emuladores de Firebase estén encendidos."
}
