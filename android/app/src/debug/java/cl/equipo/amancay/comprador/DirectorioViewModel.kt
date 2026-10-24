package cl.equipo.amancay.comprador

import cl.equipo.amancay.data.RepositorioCatalogo
import cl.equipo.amancay.data.SectorPublico
import cl.equipo.amancay.data.TiendaPublica
import cl.equipo.amancay.data.TipoEmprendimientoPublico
import com.google.firebase.firestore.DocumentSnapshot
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
 * Controlador del directorio de tiendas (RF-CMP-01).
 *
 * Carga la primera página, los catálogos de filtros y maneja la búsqueda, los filtros y la
 * paginación ("Cargar más"). Expone todo como estado ([Estado]) para [DirectorioScreen].
 */
class DirectorioViewModel(private val repositorio: RepositorioCatalogo) {

    data class Estado(
        val tiendas: List<TiendaPublica> = emptyList(),
        val sectores: List<SectorPublico> = emptyList(),
        val tiposEmprendimiento: List<TipoEmprendimientoPublico> = emptyList(),
        val busqueda: String = "",
        val tipoEmprendimientoId: String? = null,
        val sectorId: String? = null,
        val cargando: Boolean = false,
        val error: String? = null,
        val hayMas: Boolean = false
    )

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ultimoVisor: DocumentSnapshot? = null
    private var cargando = false

    init {
        cargarOpciones()
        cargarPagina()
    }

    /** Actualiza el texto de búsqueda (el filtrado por nombre es local, en la pantalla). */
    fun buscar(texto: String) {
        _estado.update { it.copy(busqueda = texto) }
    }

    /** Aplica el filtro por tipo de emprendimiento y recarga desde la primera página. */
    fun seleccionarTipoEmprendimiento(id: String?) {
        _estado.update { it.copy(tipoEmprendimientoId = id) }
        reiniciar()
    }

    /** Aplica el filtro por sector y recarga desde la primera página. */
    fun seleccionarSector(id: String?) {
        _estado.update { it.copy(sectorId = id) }
        reiniciar()
    }

    /** Trae la siguiente página de tiendas (botón "Cargar más"). */
    fun cargarMas() = cargarPagina()

    /** Reinicia y vuelve a cargar (botón "Reintentar"). */
    fun reintentar() = reiniciar()

    private fun reiniciar() {
        ultimoVisor = null
        _estado.update { it.copy(tiendas = emptyList(), hayMas = false, error = null) }
        cargarPagina()
    }

    private fun cargarOpciones() = scope.launch {
        try {
            val sectores = repositorio.listarSectores()
            val tipos = repositorio.listarTiposEmprendimiento()
            _estado.update { it.copy(sectores = sectores, tiposEmprendimiento = tipos) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Las opciones de filtro no bloquean el directorio.
        }
    }

    private fun cargarPagina() {
        if (cargando) return
        cargando = true
        scope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            try {
                val actual = _estado.value
                val pagina = repositorio.listarTiendas(
                    tipoEmprendimientoId = actual.tipoEmprendimientoId,
                    sectorId = actual.sectorId,
                    ultimoVisor = ultimoVisor
                )
                ultimoVisor = pagina.ultimoVisor
                _estado.update { previo ->
                    val combinadas = previo.tiendas + pagina.tiendas.filter { nueva ->
                        previo.tiendas.none { it.id == nueva.id }
                    }
                    previo.copy(tiendas = combinadas, cargando = false, hayMas = pagina.hayMas)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update { it.copy(cargando = false, error = textoError(e)) }
            } finally {
                cargando = false
            }
        }
    }

    private fun textoError(e: Exception): String =
        if (e is cl.equipo.amancay.ErrorApi) e.message ?: "No se pudo cargar el catálogo."
        else "No se pudo cargar el catálogo. Verifica que los emuladores de Firebase estén encendidos."
}