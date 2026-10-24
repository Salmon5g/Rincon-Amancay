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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.equipo.amancay.data.ProductoPublico
import cl.equipo.amancay.ui.theme.AmancayBorde
import cl.equipo.amancay.ui.theme.AmancayBordo
import cl.equipo.amancay.ui.theme.AmancayFondo
import cl.equipo.amancay.ui.theme.AmancaySuperficie
import cl.equipo.amancay.ui.theme.AmancayTinta
import cl.equipo.amancay.ui.theme.AmancayTintaSuave

/**
 * Detalle de tienda (RF-CMP-02) estilo "vista pÃºblica" Figma 09:
 * encabezado de tienda, badge de visibilidad y lista de productos
 * en tarjetas crema con miniatura, categorÃ­a y precio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleTiendaScreen(
    viewModel: DetalleTiendaViewModel,
    alVolver: () -> Unit,
    alAbrirProducto: (String) -> Unit
) {
    val estado by viewModel.estado.collectAsState()
    val error = estado.error
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AmancayFondo,
        topBar = { BarraSuperiorAmancay() }
    ) { relleno ->
        when {
            estado.cargando -> Box(
                Modifier.fillMaxSize().padding(relleno),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AmancayBordo) }
            error != null -> MensajeError(error, viewModel::reintentar, Modifier.fillMaxSize().padding(relleno))
            else -> ContenidoTienda(estado, alVolver, alAbrirProducto, Modifier.fillMaxSize().padding(relleno))
        }
    }
}

@Composable
private fun ContenidoTienda(
    estado: cl.equipo.amancay.comprador.DetalleTiendaViewModel.Estado,
    alVolver: () -> Unit,
    alAbrirProducto: (String) -> Unit,
    modifier: Modifier
) {
    val tienda = estado.tienda
    var busqueda by remember { mutableStateOf("") }
    var categoriaSeleccionada by remember { mutableStateOf<String?>(null) }

    val categoriasDisponibles = remember(estado.productos, estado.categoriasPorId) {
        estado.productos
            .map { estado.categoriasPorId[it.categoriaId] ?: it.categoriaId }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val productosFiltrados = remember(estado.productos, estado.categoriasPorId, busqueda, categoriaSeleccionada) {
        estado.productos.filter { producto ->
            val cat = estado.categoriasPorId[producto.categoriaId] ?: producto.categoriaId
            val coincideCat = categoriaSeleccionada == null || cat == categoriaSeleccionada
            val coincideBusqueda = busqueda.isBlank() ||
                producto.nombre.contains(busqueda, ignoreCase = true) ||
                producto.descripcion.contains(busqueda, ignoreCase = true)
            coincideCat && coincideBusqueda
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (alVolver != { }) {
            item {
                TextButton(onClick = alVolver) { Text("← Volver", color = AmancayBordo) }
            }
        }
        item {
            Column {
                EtiquetaSeccion("Tienda")
                Text(
                    tienda?.nombre.orEmpty().ifBlank { "Tienda" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = AmancayTinta
                )
                if (!tienda?.descripcion.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tienda?.descripcion.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmancayTintaSuave
                    )
                }
                val etiquetas = listOf(tienda?.tipoEmprendimientoId.orEmpty(), tienda?.sectorId.orEmpty())
                    .filter { it.isNotBlank() }
                if (etiquetas.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    EtiquetaCategoria(etiquetas.joinToString(" · "))
                }
                Spacer(Modifier.height(8.dp))
                BadgeDisponibilidad("Tienda visible")
            }
        }
        if (tienda != null && !tienda.mostrarPrecios) {
            item {
                TarjetaAmancay {
                    Text(
                        "Esta tienda no muestra precios. Escríbele para consultar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmancayTinta
                    )
                }
            }
        }
        item {
            Text("Productos", style = MaterialTheme.typography.titleMedium, color = AmancayTinta)
        }
        item {
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar producto...") },
                singleLine = true,
                shape = RoundedCornerShape(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AmancaySuperficie,
                    unfocusedContainerColor = AmancaySuperficie,
                    focusedBorderColor = AmancayBordo,
                    unfocusedBorderColor = AmancayBorde
                )
            )
        }
        if (categoriasDisponibles.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChipFiltro(texto = "Todos", seleccionado = categoriaSeleccionada == null) { categoriaSeleccionada = null }
                    categoriasDisponibles.forEach { cat ->
                        ChipFiltro(texto = cat, seleccionado = categoriaSeleccionada == cat) { categoriaSeleccionada = cat }
                    }
                }
            }
        }
        if (productosFiltrados.isEmpty()) {
            item {
                TarjetaAmancay {
                    Text(
                        if (estado.productos.isEmpty()) "Esta tienda aún no tiene productos publicados." else "No hay productos que coincidan con tu búsqueda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmancayTintaSuave
                    )
                }
            }
        } else {
            items(productosFiltrados, key = { it.id }) { producto ->
                TarjetaProductoTienda(
                    producto = producto,
                    categoria = estado.categoriasPorId[producto.categoriaId] ?: producto.categoriaId,
                    mostrarPrecios = tienda?.mostrarPrecios != false,
                    alAbrir = { alAbrirProducto(producto.id) }
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun ChipFiltro(texto: String, seleccionado: Boolean, alSeleccionar: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .clickable { alSeleccionar() }
            .background(if (seleccionado) AmancayBordo else AmancaySuperficie)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(texto, style = MaterialTheme.typography.bodySmall, color = if (seleccionado) AmancaySuperficie else AmancayTinta)
    }
}

@Composable
private fun TarjetaProductoTienda(
    producto: ProductoPublico,
    categoria: String,
    mostrarPrecios: Boolean,
    alAbrir: () -> Unit
) {
    TarjetaAmancay(modifier = Modifier.clickable { alAbrir() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniaturaAmancay()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(categoria, style = MaterialTheme.typography.labelSmall, color = AmancayBordo)
                Text(
                    producto.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    color = AmancayTinta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val disponibilidad = etiquetaDisponibilidad(producto.estadoDisponibilidad)
                if (disponibilidad.isNotBlank()) {
                    BadgeDisponibilidad(disponibilidad)
                }
            }
            if (mostrarPrecios) {
                Text(
                    precioConMoneda(producto.precioBase, producto.moneda),
                    style = MaterialTheme.typography.titleSmall,
                    color = AmancayBordo,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}