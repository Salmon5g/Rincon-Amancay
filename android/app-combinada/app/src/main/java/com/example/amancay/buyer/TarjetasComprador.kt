package com.example.amancay.buyer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.amancay.data.ProductoCatalogoComprador
import com.example.amancay.ui.Fig
import java.text.NumberFormat
import java.util.Locale

/**
 * Grilla de dos columnas del modo comprador: tarjeta vertical con foto,
 * badge de disponibilidad, nombre, tienda y precio.
 */

/** Renderiza la lista en filas de dos tarjetas dentro de un LazyColumn. */
fun LazyListScope.grillaProductos(
    productos: List<ProductoCatalogoComprador>,
    alAbrir: (ProductoCatalogoComprador) -> Unit
) {
    productos.chunked(2).forEach { fila ->
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TarjetaProductoComprador(
                    producto = fila[0],
                    alAbrir = { alAbrir(fila[0]) },
                    modifier = Modifier.weight(1f)
                )
                if (fila.size > 1) {
                    TarjetaProductoComprador(
                        producto = fila[1],
                        alAbrir = { alAbrir(fila[1]) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun TarjetaProductoComprador(
    producto: ProductoCatalogoComprador,
    alAbrir: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = alAbrir,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AmancaySuperficie),
        border = BorderStroke(1.dp, AmancayBorde),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box {
                FotoProductoCatalogo(
                    producto = producto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                )
                BadgeProducto(
                    estado = producto.estadoDisponibilidad,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
            Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 6.dp)) {
                Text(
                    producto.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AmancayTinta,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    producto.tiendaNombre,
                    style = MaterialTheme.typography.bodySmall,
                    color = AmancayTintaSuave,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    precioProducto(producto),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AmancayTinta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun FotoProductoCatalogo(
    producto: ProductoCatalogoComprador,
    modifier: Modifier = Modifier
) {
    val url = producto.imagenUrl
    Box(
        modifier = modifier
            .background(AmancayNaranjaFondo)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = producto.nombre,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(Fig.VASE_THUMB),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Badge de disponibilidad con los colores de la imagen de referencia. */
@Composable
fun BadgeProducto(estado: String, modifier: Modifier = Modifier) {
    val texto = etiquetaCortaDisponibilidad(estado)
    val fondo: Color
    val tinta: Color
    when (estado) {
        "disponible" -> {
            fondo = AmancayVerdeFondo
            tinta = AmancayVerdeTexto
        }
        "a_pedido" -> {
            fondo = AmancayBordo
            tinta = Color.White
        }
        "agotado", "no_disponible" -> {
            fondo = AmancayBordoSuave
            tinta = AmancayBordoOscuro
        }
        else -> {
            fondo = AmancayMostazaSuave
            tinta = AmancayTinta
        }
    }
    Surface(
        color = fondo,
        shape = RoundedCornerShape(50.dp),
        modifier = modifier
    ) {
        Text(
            texto,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MensajeCatalogo(texto: String) {
    TarjetaAmancay(modifier = Modifier.padding(top = 12.dp)) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = AmancayTinta)
    }
}

@Composable
fun DialogoDetalleProducto(
    producto: ProductoCatalogoComprador,
    alCerrar: () -> Unit,
    alEncargar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = {
            Text(
                producto.nombre,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AmancayTinta,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (producto.imagenUrl != null) {
                    AsyncImage(
                        model = producto.imagenUrl,
                        contentDescription = producto.nombre,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AmancayNaranjaFondo)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        producto.tiendaNombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AmancayBordo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    BadgeProducto(estado = producto.estadoDisponibilidad)
                }
                val categoria = producto.categoriaNombre.ifBlank { producto.tipoProductoNombre }
                if (categoria.isNotBlank()) {
                    Text(categoria, style = MaterialTheme.typography.bodySmall, color = AmancayTintaSuave)
                }
                if (producto.descripcion.isNotBlank()) {
                    Text(
                        producto.descripcion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmancayTinta
                    )
                }
                producto.atributosEspecificos.forEach { (nombre, valor) ->
                    Text(
                        "$nombre: $valor",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmancayTintaSuave
                    )
                }
                val datos = listOfNotNull(
                    producto.unidadVenta.takeIf(String::isNotBlank)?.let { "Venta: $it" },
                    producto.modalidad.takeIf(String::isNotBlank)?.let { "Modalidad: $it" }
                )
                if (datos.isNotEmpty()) {
                    Text(
                        datos.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmancayTintaSuave
                    )
                }
                HorizontalDivider(color = AmancayBorde)
                if (producto.variantes.isNotEmpty()) {
                    Text(
                        "Variantes y precios",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AmancayBordo
                    )
                    producto.variantes.forEach { variante ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                variante.opciones.values.joinToString(" · ").ifBlank { "Opción" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AmancayTinta
                            )
                            Text(
                                variante.precio?.let(::formatoPrecio) ?: "Consultar",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AmancayBordo
                            )
                        }
                    }
                } else {
                    producto.precioBase?.let { precio ->
                        Text(
                            formatoPrecio(precio),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmancayBordo
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = alEncargar,
                shape = RoundedCornerShape(50.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmancayBordo,
                    contentColor = Color.White
                )
            ) {
                Text("Encargar producto", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) {
                Text("Cerrar", color = AmancayTintaSuave)
            }
        },
        containerColor = AmancaySuperficie
    )
}

/** Texto largo, para el detalle del producto. */
fun etiquetaDisponibilidad(estado: String): String = when (estado) {
    "disponible" -> "Disponible"
    "a_pedido" -> "Disponible a pedido"
    "agotado" -> "Agotado"
    "no_disponible" -> "No disponible"
    else -> "Consultar disponibilidad"
}

/** Texto breve para el badge de la tarjeta. */
fun etiquetaCortaDisponibilidad(estado: String): String = when (estado) {
    "disponible" -> "Disponible"
    "a_pedido" -> "Por encargo"
    "agotado" -> "Agotado"
    "no_disponible" -> "No disponible"
    else -> "Consultar"
}

fun precioProducto(producto: ProductoCatalogoComprador): String {
    val precios = producto.variantes.mapNotNull { it.precio }.filter { it > 0 }
    if (precios.isEmpty()) return producto.precioBase?.let(::formatoPrecio) ?: "Consultar precio"
    val minimo = precios.minOrNull() ?: return "Consultar precio"
    return if (precios.distinct().size > 1) "Desde ${formatoPrecio(minimo)}" else formatoPrecio(minimo)
}

fun formatoPrecio(valor: Long): String {
    val numero = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL")).format(valor)
    return "\$$numero CLP"
}
