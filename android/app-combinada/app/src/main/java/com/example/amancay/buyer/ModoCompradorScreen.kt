package com.example.amancay.buyer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.amancay.data.CatalogoCompradorRepository
import com.example.amancay.data.ProductoCatalogoComprador
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ModoCompradorScreen(repositorio: CatalogoCompradorRepository, alVolver: () -> Unit) {
    val productos by repositorio.productos.collectAsState()
    val cargando by repositorio.cargando.collectAsState()
    val error by repositorio.error.collectAsState()
    var busqueda by remember { mutableStateOf("") }
    var categoriaSeleccionada by remember { mutableStateOf<String?>(null) }
    var productoSeleccionado by remember { mutableStateOf<ProductoCatalogoComprador?>(null) }

    BackHandler(onBack = alVolver)

    val categorias = remember(productos) {
        productos.map { it.categoriaNombre }.filter(String::isNotBlank).distinct().sorted()
    }
    val filtrados = remember(productos, busqueda, categoriaSeleccionada) {
        productos.filter { producto ->
            val coincideTipo = categoriaSeleccionada == null || producto.categoriaNombre == categoriaSeleccionada
            val texto = busqueda.trim()
            val coincideBusqueda = texto.isBlank() || listOfNotNull(
                producto.nombre,
                producto.descripcion,
                producto.categoriaNombre,
                producto.tipoProductoNombre,
                producto.tiendaNombre,
                producto.unidadVenta,
                producto.modalidad,
                *producto.atributosEspecificos.values.toTypedArray()
            ).any { it.contains(texto, ignoreCase = true) }
            coincideTipo && coincideBusqueda
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AmancayFondo,
        topBar = { BarraSuperiorAmancay(titulo = "Modo comprador", alSalir = alVolver) }
    ) { inset ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inset).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            EtiquetaSeccion("Catálogo")
            TituloPantalla(
                titulo = "Encuentra tu próximo favorito",
                bajada = "Productos publicados de los emprendimientos Amancay."
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar productos…") },
                singleLine = true,
                shape = RoundedCornerShape(50.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AmancaySuperficie,
                    unfocusedContainerColor = AmancaySuperficie,
                    focusedBorderColor = AmancayBordo,
                    unfocusedBorderColor = AmancayBorde
                )
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FiltroProducto("Todos", categoriaSeleccionada == null) { categoriaSeleccionada = null }
                categorias.forEach { categoria ->
                    FiltroProducto(categoria, categoriaSeleccionada == categoria) { categoriaSeleccionada = categoria }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (filtrados.size == 1) "1 producto" else "${filtrados.size} productos", style = MaterialTheme.typography.labelMedium, color = AmancayTintaSuave)
            Spacer(Modifier.height(6.dp))
            if (error != null) {
                MensajeCatalogo(error!!)
                Spacer(Modifier.height(6.dp))
            }
            when {
                cargando && productos.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AmancayBordo)
                    Spacer(Modifier.height(12.dp))
                    Text("Cargando catálogo…", color = AmancayTintaSuave)
                }
                productos.isEmpty() -> MensajeCatalogo("Aún no hay productos publicados para comprar.")
                filtrados.isEmpty() -> MensajeCatalogo("No hay productos que coincidan con tu búsqueda o filtro.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtrados, key = { it.id }) { producto ->
                        TarjetaProductoEmprendedora(producto) { productoSeleccionado = producto }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }

    productoSeleccionado?.let { producto ->
        DialogoDetalleProducto(producto) { productoSeleccionado = null }
    }
}

@Composable
private fun FiltroProducto(texto: String, seleccionado: Boolean, alSeleccionar: () -> Unit) {
    FilterChip(
        selected = seleccionado,
        onClick = alSeleccionar,
        label = { Text(texto) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AmancayBordo,
            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
            containerColor = AmancaySuperficie,
            labelColor = AmancayTinta
        ),
        shape = RoundedCornerShape(50.dp)
    )
}

@Composable
private fun TarjetaProductoEmprendedora(producto: ProductoCatalogoComprador, alAbrir: () -> Unit) {
    TarjetaAmancay(modifier = Modifier.clickable(onClick = alAbrir)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniaturaAmancay("🛍️")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                EtiquetaCategoria(producto.categoriaNombre.ifBlank { producto.tipoProductoNombre })
                Text(
                    producto.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = AmancayTinta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(producto.tiendaNombre, style = MaterialTheme.typography.bodySmall, color = AmancayTintaSuave)
                Spacer(Modifier.height(4.dp))
                Text(
                    etiquetaDisponibilidad(producto.estadoDisponibilidad),
                    style = MaterialTheme.typography.labelMedium,
                    color = AmancayVerdeTexto
                )
                Spacer(Modifier.height(4.dp))
                Text(precioProducto(producto), style = MaterialTheme.typography.titleSmall, color = AmancayBordo)
            }
        }
    }
}

@Composable
private fun MensajeCatalogo(texto: String) {
    TarjetaAmancay(modifier = Modifier.padding(top = 12.dp)) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = AmancayTinta)
    }
}

@Composable
private fun DialogoDetalleProducto(producto: ProductoCatalogoComprador, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(producto.nombre, fontWeight = FontWeight.Bold, color = AmancayTinta) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(producto.tiendaNombre, color = AmancayBordo)
                Text(producto.categoriaNombre.ifBlank { producto.tipoProductoNombre }, color = AmancayTintaSuave)
                if (producto.descripcion.isNotBlank()) Text(producto.descripcion)
                producto.atributosEspecificos.forEach { (nombre, valor) -> Text("$nombre: $valor") }
                if (producto.unidadVenta.isNotBlank()) Text("Venta: ${producto.unidadVenta}")
                if (producto.modalidad.isNotBlank()) Text("Modalidad: ${producto.modalidad}")
                Text(etiquetaDisponibilidad(producto.estadoDisponibilidad), color = AmancayVerdeTexto)
                if (producto.variantes.isEmpty() && producto.precioBase != null) {
                    Text(formatoPrecio(producto.precioBase), color = AmancayBordo)
                }
                producto.variantes.forEach { variante ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(variante.opciones.values.joinToString(" · ").ifBlank { "Opción" }, modifier = Modifier.weight(1f))
                        Text(variante.precio?.let(::formatoPrecio) ?: "Consultar", color = AmancayBordo)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
        containerColor = AmancaySuperficie
    )
}

private fun etiquetaDisponibilidad(estado: String): String = when (estado) {
    "disponible" -> "Disponible"
    "a_pedido" -> "Disponible a pedido"
    "agotado" -> "Agotado"
    "no_disponible" -> "No disponible"
    else -> "Consultar disponibilidad"
}

private fun precioProducto(producto: ProductoCatalogoComprador): String {
    val precios = producto.variantes.mapNotNull { it.precio }.filter { it > 0 }
    if (precios.isEmpty()) return producto.precioBase?.let(::formatoPrecio) ?: "Consultar precio"
    val minimo = precios.minOrNull() ?: return "Consultar precio"
    return if (precios.distinct().size > 1) "Desde ${formatoPrecio(minimo)}" else formatoPrecio(minimo)
}

private fun formatoPrecio(valor: Long): String {
    val numero = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL")).format(valor)
    return "\$$numero CLP"
}
