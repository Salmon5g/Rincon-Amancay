package cl.equipo.amancay.comprador

import cl.equipo.amancay.ErrorApi
import cl.equipo.amancay.data.ProductoPublico
import cl.equipo.amancay.data.RepositorioCatalogo
import cl.equipo.amancay.data.VariantePublica
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
 * Controlador del detalle de producto (RF-CMP-02 / RF-CMP-03).
 *
 * Carga el producto y, si tiene variantes, la lista de variantes activas con sus precios.
 * La selección de variante y el resaltado se manejan en la pantalla ([DetalleProductoScreen]).
 */
class DetalleProductoViewModel(
    private val repositorio: RepositorioCatalogo,
    private val tiendaId: String,
    private val productoId: String
) {
    /** Estado que observa la pantalla. */
    data class Estado(
        val producto: ProductoPublico? = null,
        val variantes: List<VariantePublica> = emptyList(),
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
                val producto = repositorio.obtenerProducto(tiendaId, productoId)
                val variantes = if (producto?.tieneVariantes == true) {
                    repositorio.listarVariantes(tiendaId, productoId)
                } else {
                    emptyList()
                }
                _estado.update {
                    it.copy(producto = producto, variantes = variantes, cargando = false, error = null)
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
        if (e is ErrorApi) e.message ?: "No se pudo cargar el producto."
        else "No se pudo cargar el producto. Verifica que los emuladores de Firebase estén encendidos."
}
