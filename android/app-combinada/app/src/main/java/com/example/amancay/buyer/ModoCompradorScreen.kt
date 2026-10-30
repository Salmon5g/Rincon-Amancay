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
import com.example.amancay.data.AmancayRepository
import com.example.amancay.data.Product
import com.example.amancay.data.ProductType
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ModoCompradorScreen(repositorio: AmancayRepository, alVolver: () -> Unit) {
    val productos by repositorio.productos.collectAsState()
    var busqueda by remember { mutableStateOf("") }
    var tipoSeleccionado by remember { mutableStateOf<ProductType?>(null) }
    var productoSeleccionado by remember { mutableStateOf<Product?>(null) }

    BackHandler(onBack = alVolver)

    val disponibles = remember(productos) {
        productos.filter { producto ->
            producto.publicado && !producto.esBorrador &&
                (producto.stockTotal() > 0 || producto.disponibleEncargo)
        }
    }
    val tipos = ProductType.entries
    val filtrados = remember(disponibles, busqueda, tipoSeleccionado) {
        disponibles.filter { producto ->
            val coincideTipo = tipoSeleccionado == null || producto.tipo == tipoSeleccionado
            val texto = busqueda.trim()
            val coincideBusqueda = texto.isBlank() || listOfNotNull(
                producto.nombre,
                producto.tipo?.label,
                producto.material,
                producto.tamano,
                producto.cuidados
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
                FiltroProducto("Todos", tipoSeleccionado == null) { tipoSeleccionado = null }
                tipos.forEach { tipo ->
                    FiltroProducto(tipo.label, tipoSeleccionado == tipo) { tipoSeleccionado = tipo }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (filtrados.size == 1) "1 producto" else "${filtrados.size} productos", style = MaterialTheme.typography.labelMedium, color = AmancayTintaSuave)
            Spacer(Modifier.height(6.dp))
            when {
                disponibles.isEmpty() -> MensajeCatalogo("Aún no hay productos disponibles para comprar.")
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
private fun TarjetaProductoEmprendedora(producto: Product, alAbrir: () -> Unit) {
    TarjetaAmancay(modifier = Modifier.clickable(onClick = alAbrir)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniaturaAmancay("🏺")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                EtiquetaCategoria(producto.tipo?.label.orEmpty())
                Text(
                    producto.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = AmancayTinta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    estadoProducto(producto),
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
private fun DialogoDetalleProducto(producto: Product, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(producto.nombre, fontWeight = FontWeight.Bold, color = AmancayTinta) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(producto.tipo?.label.orEmpty(), color = AmancayBordo)
                if (producto.material.isNotBlank()) Text("Material o ingredientes: ${producto.material}")
                if (producto.tamano.isNotBlank()) Text("Tamaño o presentación: ${producto.tamano}")
                if (producto.cuidados.isNotBlank()) Text("Detalles: ${producto.cuidados}")
                Text(estadoProducto(producto), color = AmancayVerdeTexto)
                producto.variantes.forEach { variante ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(variante.nombre.ifBlank { "Variante" }, modifier = Modifier.weight(1f))
                        Text(formatoPrecio(variante.precioLong()), color = AmancayBordo)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
        containerColor = AmancaySuperficie
    )
}

private fun estadoProducto(producto: Product): String = when {
    producto.stockTotal() > 0 -> "Disponible"
    producto.disponibleEncargo -> "Disponible a pedido"
    else -> "Agotado"
}

private fun precioProducto(producto: Product): String {
    val precios = producto.variantes.map { it.precioLong() }.filter { it > 0 }
    if (precios.isEmpty()) return "Consultar precio"
    val minimo = precios.minOrNull() ?: return "Consultar precio"
    return if (precios.distinct().size > 1) "Desde ${formatoPrecio(minimo)}" else formatoPrecio(minimo)
}

private fun formatoPrecio(valor: Long): String {
    val numero = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL")).format(valor)
    return "\$$numero CLP"
}
