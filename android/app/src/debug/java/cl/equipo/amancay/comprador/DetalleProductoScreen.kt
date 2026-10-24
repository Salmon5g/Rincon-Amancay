package cl.equipo.amancay.comprador

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.equipo.amancay.data.ProductoPublico
import cl.equipo.amancay.data.VariantePublica
import cl.equipo.amancay.ui.theme.AmancayBordo
import cl.equipo.amancay.ui.theme.AmancayFondo
import cl.equipo.amancay.ui.theme.AmancayTinta
import cl.equipo.amancay.ui.theme.AmancayTintaSuave
import cl.equipo.amancay.ui.theme.AmancayVerdeFondo
import cl.equipo.amancay.ui.theme.AmancayVerdeTexto

/**
 * Detalle de producto (RF-CMP-02 / RF-CMP-03) estilo Figma 09 "Así ven tu catálogo":
 * foto grande, categoría en mayúsculas, título serif, descripción, precio grande,
 * badge de disponibilidad y selector de variantes con chips. La foto real queda
 * pendiente (los datos de prueba no traen imágenes): se usa placeholder cálido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleProductoScreen(
    viewModel: DetalleProductoViewModel,
    alVolver: () -> Unit
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
            else -> ContenidoProducto(estado, alVolver, Modifier.fillMaxSize().padding(relleno))
        }
    }
}

@Composable
private fun ContenidoProducto(
    estado: DetalleProductoViewModel.Estado,
    alVolver: () -> Unit,
    modifier: Modifier
) {
    val producto = estado.producto
    var seleccion by remember(estado.variantes) { mutableStateOf(estado.variantes.firstOrNull()?.id) }
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = alVolver) { Text("← Volver", color = AmancayBordo) }
        }
        item {
            // Foto principal: cuando haya Storage, cargar imagenes[0] aquí.
            FotoProductoAmancay()
        }
        item {
            Column {
                EtiquetaCategoria(
                    listOfNotNull(
                        producto?.categoriaId?.takeIf { it.isNotBlank() },
                        producto?.tipoProductoId?.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                )
                Text(
                    producto?.nombre.orEmpty().ifBlank { "Producto" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = AmancayTinta
                )
                if (!producto?.descripcion.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        producto?.descripcion.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmancayTintaSuave
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = precioPrincipal(producto, estado.variantes, seleccion),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AmancayTinta
                )
                Spacer(Modifier.height(8.dp))
                BadgeDisponibilidad(etiquetaDisponibilidad(producto?.estadoDisponibilidad.orEmpty()))
            }
        }
        if (estado.variantes.isNotEmpty()) {
            item {
                Text("Elige tu variante", style = MaterialTheme.typography.titleMedium, color = AmancayTinta)
            }
            item {
                // Chips de variantes como en "Registrar venta" Figma 07.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    estado.variantes.chunked(2).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fila.forEach { variante ->
                                val nombre = nombreVariante(variante)
                                ChipFiltroAmancay(
                                    nombre,
                                    seleccionado = seleccion == variante.id
                                ) { seleccion = variante.id }
                            }
                        }
                    }
                }
            }
            items(estado.variantes, key = { it.id }) { variante ->
                TarjetaVariante(
                    variante = variante,
                    moneda = producto?.moneda.orEmpty(),
                    seleccionada = seleccion == variante.id,
                    alSeleccionar = { seleccion = variante.id }
                )
            }
        } else {
            item {
                TarjetaAmancay {
                    FichaAtributo("Disponibilidad", etiquetaDisponibilidad(producto?.estadoDisponibilidad.orEmpty()))
                    FichaAtributo("Modalidad", etiquetaModalidad(producto?.modalidad.orEmpty()))
                    FichaAtributo("Unidad de venta", producto?.unidadVenta.orEmpty())
                }
            }
        }
        if (producto != null && producto.atributosEspecificos.isNotEmpty()) {
            item { Text("Detalles", style = MaterialTheme.typography.titleMedium, color = AmancayTinta) }
            item {
                TarjetaAmancay {
                    producto.atributosEspecificos.entries.forEach { entrada ->
                        FichaAtributo(
                            entrada.key.replaceFirstChar { it.uppercase() },
                            entrada.value
                        )
                    }
                }
            }
        }
        item {
            // Equivalente comprador del "Contactar a Ana" de Figma 09.
            // TODO: deep-link / contacto con la tienda.
            BotonMostazaAmancay(texto = "Contactar a la tienda", alClick = {})
            Spacer(Modifier.height(4.dp))
            Text(
                "El precio y la disponibilidad los define la tienda. El stock exacto no se muestra.",
                style = MaterialTheme.typography.bodySmall,
                color = AmancayTintaSuave
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun nombreVariante(variante: VariantePublica): String {
    if (variante.opciones.isEmpty()) return "Única"
    return variante.opciones.values.firstOrNull()?.ifBlank { "Variante" } ?: "Variante"
}

private fun precioPrincipal(
    producto: ProductoPublico?,
    variantes: List<VariantePublica>,
    seleccion: String?
): String {
    if (producto == null) return ""
    return if (producto.tieneVariantes) {
        val variante = variantes.firstOrNull { it.id == seleccion } ?: variantes.firstOrNull()
        if (variante == null) "Consultar precio" else precioConMoneda(variante.precio, producto.moneda)
    } else {
        precioConMoneda(producto.precioBase, producto.moneda)
    }
}

@Composable
private fun FichaAtributo(etiqueta: String, valor: String) {
    if (valor.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = AmancayTintaSuave, modifier = Modifier.weight(0.4f))
        Text(valor, style = MaterialTheme.typography.bodyMedium, color = AmancayTinta, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun TarjetaVariante(
    variante: VariantePublica,
    moneda: String,
    seleccionada: Boolean,
    alSeleccionar: () -> Unit
) {
    val detalle = variante.opciones.entries.joinToString(", ") { entrada ->
        "${entrada.key.replaceFirstChar { it.uppercase() }}: ${entrada.value}"
    }
    TarjetaAmancay(
        modifier = Modifier.clickable { alSeleccionar() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniaturaAmancay("🏺")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(detalle.ifBlank { "Variante" }, style = MaterialTheme.typography.bodyLarge, color = AmancayTinta)
                val disponibilidad = etiquetaDisponibilidad(variante.estadoDisponibilidad)
                if (disponibilidad.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    BadgeDisponibilidad(disponibilidad)
                }
            }
            Text(
                precioConMoneda(variante.precio, moneda),
                style = MaterialTheme.typography.titleMedium,
                color = AmancayBordo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (seleccionada) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = AmancayVerdeFondo,
                shape = RoundedCornerShape(50.dp)
            ) {
                Text(
                    "✓ Elegida",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = AmancayVerdeTexto
                )
            }
        }
    }
}
