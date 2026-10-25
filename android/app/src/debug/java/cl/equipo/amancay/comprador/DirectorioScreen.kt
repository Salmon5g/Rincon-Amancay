package cl.equipo.amancay.comprador

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.equipo.amancay.data.TiendaPublica
import cl.equipo.amancay.ui.theme.AmancayBordo
import cl.equipo.amancay.ui.theme.AmancayBorde
import cl.equipo.amancay.ui.theme.AmancayFondo
import cl.equipo.amancay.ui.theme.AmancaySuperficie
import cl.equipo.amancay.ui.theme.AmancayTinta
import cl.equipo.amancay.ui.theme.AmancayTintaSuave

/**
 * Pantalla del directorio (RF-CMP-01) con el sistema visual Amancay:
 * barra superior de la marca, título serif, buscador redondo, chips bordó
 * y tarjetas crema. Mantiene búsqueda, filtros por tipo/sector y paginación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorioScreen(
    viewModel: DirectorioViewModel,
    alAbrirTienda: (String) -> Unit
) {
    val estado by viewModel.estado.collectAsState()
    val tipoPorId = remember(estado.tiposEmprendimiento) { estado.tiposEmprendimiento.associate { it.id to it.nombre } }
    val sectorPorId = remember(estado.sectores) { estado.sectores.associate { it.id to it.nombre } }
    val visibles = remember(estado.tiendas, estado.busqueda) {
        filtrarPorBusqueda(estado.tiendas, estado.busqueda)
    }
    val error = estado.error

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AmancayFondo,
        topBar = { BarraSuperiorAmancay() }
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            EtiquetaSeccion("Directorio")
            TituloPantalla(
                titulo = "¿Qué buscas hoy?",
                bajada = "Explora tiendas y encuentra eso que necesitas."
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = estado.busqueda,
                onValueChange = viewModel::buscar,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar tienda, producto…") },
                singleLine = true,
                shape = RoundedCornerShape(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AmancaySuperficie,
                    unfocusedContainerColor = AmancaySuperficie,
                    focusedBorderColor = AmancayBordo,
                    unfocusedBorderColor = AmancayBorde
                )
            )
            Spacer(Modifier.height(12.dp))
            FilaFiltros(
                etiqueta = "Tipo de emprendimiento",
                opciones = estado.tiposEmprendimiento.map { FiltroOpcion(it.id, it.nombre) },
                seleccionado = estado.tipoEmprendimientoId,
                alSeleccionar = viewModel::seleccionarTipoEmprendimiento
            )
            FilaFiltros(
                etiqueta = "Sector",
                opciones = estado.sectores.map { FiltroOpcion(it.id, it.nombre) },
                seleccionado = estado.sectorId,
                alSeleccionar = viewModel::seleccionarSector
            )
            Spacer(Modifier.height(8.dp))
            when {
                estado.cargando && visibles.isEmpty() -> CargandoPantalla()
                error != null && visibles.isEmpty() -> ErrorCatalogo(
                    mensaje = error,
                    alReintentar = viewModel::reintentar
                )
                visibles.isEmpty() -> MensajeDirectorioVacio(
                    hayFiltros = estado.busqueda.isNotBlank() ||
                        estado.tipoEmprendimientoId != null ||
                        estado.sectorId != null
                )
                else -> ListaTiendas(
                    tiendas = visibles,
                    tipoPorId = tipoPorId,
                    sectorPorId = sectorPorId,
                    recargando = estado.cargando,
                    hayMas = estado.hayMas,
                    alCargarMas = viewModel::cargarMas,
                    alAbrirTienda = alAbrirTienda
                )
            }
        }
    }
}

private data class FiltroOpcion(val id: String, val nombre: String)

@Composable
private fun FilaFiltros(
    etiqueta: String,
    opciones: List<FiltroOpcion>,
    seleccionado: String?,
    alSeleccionar: (String?) -> Unit
) {
    if (opciones.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = AmancayTinta)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChipFiltroAmancay("Todos", seleccionado == null) { alSeleccionar(null) }
            opciones.forEach { opcion ->
                ChipFiltroAmancay(
                    opcion.nombre,
                    seleccionado == opcion.id
                ) { alSeleccionar(opcion.id) }
            }
        }
    }
}

@Composable
private fun ListaTiendas(
    tiendas: List<TiendaPublica>,
    tipoPorId: Map<String, String>,
    sectorPorId: Map<String, String>,
    recargando: Boolean,
    hayMas: Boolean,
    alCargarMas: () -> Unit,
    alAbrirTienda: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tiendas, key = { it.id }) { tienda ->
            TarjetaTienda(
                tienda = tienda,
                tipo = tipoPorId[tienda.tipoEmprendimientoId] ?: tienda.tipoEmprendimientoId,
                sector = sectorPorId[tienda.sectorId] ?: tienda.sectorId,
                alAbrir = { alAbrirTienda(tienda.id) }
            )
        }
        item {
            when {
                recargando -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(32.dp), color = AmancayBordo)
                }
                hayMas -> BotonPrimarioAmancay("Cargar más tiendas", alCargarMas)
                else -> Text(
                    text = "Ya no hay más tiendas.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = AmancayTintaSuave,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TarjetaTienda(tienda: TiendaPublica, tipo: String, sector: String, alAbrir: () -> Unit) {
    TarjetaAmancay(
        modifier = Modifier.clickable { alAbrir() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniaturaAmancay("🏠")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tienda.nombre, style = MaterialTheme.typography.titleMedium, color = AmancayTinta)
                if (tienda.descripcion.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tienda.descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = AmancayTintaSuave,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                val etiqueta = listOf(tipo, sector).filter { it.isNotBlank() }.joinToString(" • ")
                if (etiqueta.isNotBlank()) {
                    EtiquetaCategoria(etiqueta)
                }
            }
        }
    }
}

@Composable
private fun MensajeDirectorioVacio(hayFiltros: Boolean) {
    TarjetaAmancay(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = if (hayFiltros) {
                "No encontramos tiendas con esos filtros. Prueba con otras opciones."
            } else {
                "Todavía no hay tiendas para mostrar. Vuelve pronto."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AmancayTinta
        )
    }
}

@Composable
private fun CargandoPantalla() {
    Box(
        Modifier
            .fillMaxSize()
            .background(AmancayFondo),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = AmancayBordo)
    }
}

@Composable
private fun ErrorCatalogo(mensaje: String, alReintentar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AmancaySuperficie)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(mensaje, style = MaterialTheme.typography.bodyMedium, color = AmancayTinta)
        Spacer(Modifier.height(16.dp))
        BotonPrimarioAmancay("Reintentar", alReintentar)
    }
}

private fun filtrarPorBusqueda(tiendas: List<TiendaPublica>, busqueda: String): List<TiendaPublica> {
    val texto = busqueda.trim()
    if (texto.isEmpty()) return tiendas
    return tiendas.filter { tienda ->
        tienda.nombre.contains(texto, ignoreCase = true) ||
            tienda.descripcion.contains(texto, ignoreCase = true)
    }
}
