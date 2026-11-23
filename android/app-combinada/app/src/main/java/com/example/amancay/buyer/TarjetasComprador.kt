package com.example.amancay.buyer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
                        .aspectRatio(4f / 3f)
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
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(Fig.VASE_THUMB),
                contentDescription = null,
                contentScale = ContentScale.Fit,
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
    calificacionesActivas: Boolean,
    usuarioId: String?,
    firestore: FirebaseFirestore,
    alCerrar: () -> Unit,
    alEncargar: (com.example.amancay.data.VarianteCatalogoComprador?) -> Unit,
    alVerProductoCompleto: () -> Unit
) {
    val varianteSeleccionada = androidx.compose.runtime.remember(producto.tiendaId, producto.id) {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }
    val varianteActual = producto.variantes.firstOrNull { it.id == varianteSeleccionada.value }
        ?: producto.variantes.firstOrNull()
    val imagenActual = varianteActual?.imagenUrl ?: producto.imagenUrl
    val atributos = producto.atributosEspecificos.entries
        .filter { it.value.isNotBlank() }
    val modalidad = when (producto.modalidad.lowercase(Locale.ROOT)) {
        "a_pedido" -> "A pedido"
        "pieza_unica" -> "Pieza única"
        "regular" -> "Regular"
        else -> producto.modalidad.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
    val datos = listOfNotNull(
        producto.unidadVenta.takeIf(String::isNotBlank)?.let { "Venta" to it.replaceFirstChar { letra -> letra.uppercase() } },
        modalidad.takeIf(String::isNotBlank)?.let { "Modalidad" to it }
    )

    Dialog(
        onDismissRequest = alCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(horizontal = 14.dp),
            shape = RoundedCornerShape(34.dp),
            color = AmancaySuperficie,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        producto.nombre,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmancayBordoOscuro,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = alCerrar, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) {
                        Text("×", color = AmancayBordo, style = MaterialTheme.typography.headlineLarge)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (imagenActual != null) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(26.dp))
                                .background(AmancayNaranjaFondo),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imagenActual,
                                contentDescription = producto.nombre,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            producto.tiendaNombre,
                            style = MaterialTheme.typography.titleLarge,
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
                        Text(categoria, style = MaterialTheme.typography.bodyLarge, color = AmancayTintaSuave)
                    }
                    if (producto.descripcion.isNotBlank()) {
                        Text(producto.descripcion, style = MaterialTheme.typography.bodyLarge, color = AmancayTinta)
                    }
                    if (atributos.isNotEmpty() || datos.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, AmancayBorde)
                        ) {
                            Column {
                                Text(
                                    "Detalles del producto",
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AmancayBordo
                                )
                                HorizontalDivider(color = AmancayBorde)
                                atributos.forEachIndexed { index, atributo ->
                                    Row(
                                        Modifier.fillMaxWidth().background(if (index % 2 == 0) Color.White else AmancayChipDurazno)
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            etiquetaAtributoProducto(atributo.key) + ":",
                                            modifier = Modifier.weight(0.48f),
                                            color = AmancayBordo,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            atributo.value,
                                            modifier = Modifier.weight(0.52f),
                                            color = AmancayTinta,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                if (datos.isNotEmpty()) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        datos.forEach { dato ->
                                            Row(Modifier.weight(1f)) {
                                                Text(dato.first + ": ", color = AmancayBordo, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                                Text(dato.second, color = AmancayTinta, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (producto.variantes.isNotEmpty()) {
                        Text("Variantes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AmancayBordo)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            producto.variantes.forEach { variante ->
                                androidx.compose.material3.FilterChip(
                                    selected = variante.id == varianteActual?.id,
                                    onClick = { varianteSeleccionada.value = variante.id },
                                    label = { Text(variante.opciones.values.joinToString(" · ").ifBlank { "Opción" }) },
                                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmancayChipDurazno,
                                        selectedLabelColor = AmancayBordo,
                                        containerColor = Color.White,
                                        labelColor = AmancayTinta
                                    )
                                )
                            }
                        }
                        Text(varianteActual?.precio?.let(::formatoPrecio) ?: "Consultar precio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AmancayBordo)
                    } else {
                        Text(
                            producto.precioBase?.let(::formatoPrecio) ?: "Consultar precio",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmancayBordo
                        )
                    }
                    com.example.amancay.ui.BotonCompartirEnlace(
                        "Compartir producto",
                        producto.nombre,
                        com.example.amancay.ui.EnlacesPublicos.producto(producto.tiendaId, producto.id)
                    )
                    if (calificacionesActivas) {
                        HorizontalDivider(color = AmancayBorde)
                        Text("Califica tu experiencia", fontWeight = FontWeight.Bold, color = AmancayBordo)
                        CalificacionComprador("La tienda", producto.tiendaId, null, usuarioId, firestore)
                        CalificacionComprador("Este producto", producto.tiendaId, producto.id, usuarioId, firestore)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { alEncargar(varianteActual) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmancayBordo, contentColor = Color.White)
                ) { Text("Contactar a la emprendedora", fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = alVerProductoCompleto, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                    Text("Ver producto completo", color = AmancayBordo, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = alCerrar, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                    Text("Cerrar", color = AmancayTintaSuave)
                }
            }
        }
    }
}

private fun etiquetaAtributoProducto(clave: String): String {
    val separada = clave
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace('_', ' ')
        .trim()
        .replace(Regex("\\s+"), " ")
    return when (separada.lowercase(Locale.ROOT)) {
        "alergenos" -> "Alérgenos"
        "fecha elaboracion" -> "Fecha de elaboración"
        "fecha vencimiento" -> "Fecha de vencimiento"
        else -> separada.split(' ').joinToString(" ") { palabra ->
            palabra.replaceFirstChar { if (it.isLowerCase()) it.uppercase() else it.toString() }
        }
    }
}

@Composable
private fun CalificacionComprador(
    etiqueta: String,
    tiendaId: String,
    productoId: String?,
    usuarioId: String?,
    firestore: FirebaseFirestore
) {
    var puntuacion by remember(tiendaId, productoId, usuarioId) { mutableIntStateOf(0) }
    var guardada by remember(tiendaId, productoId, usuarioId) { mutableIntStateOf(0) }
    var mensaje by remember(tiendaId, productoId, usuarioId) { mutableStateOf<String?>(null) }
    var enviando by remember(tiendaId, productoId, usuarioId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tiendaId, productoId, usuarioId) {
        if (usuarioId.isNullOrBlank()) return@LaunchedEffect
        val referencia = if (productoId == null) firestore.collection("tiendasPublicas").document(tiendaId)
            .collection("calificaciones").document(usuarioId)
        else firestore.collection("tiendasPublicas").document(tiendaId).collection("productos").document(productoId)
            .collection("calificaciones").document(usuarioId)
        guardada = runCatching { referencia.get().await().getLong("puntuacion")?.toInt() ?: 0 }.getOrDefault(0)
        puntuacion = guardada
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, color = AmancayTinta, fontWeight = FontWeight.SemiBold)
        if (usuarioId.isNullOrBlank()) {
            Text("Inicia sesión para dejar una calificación.", color = AmancayTintaSuave, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (1..5).forEach { valor ->
                    TextButton(onClick = { puntuacion = valor }, enabled = !enviando) {
                        Text(if (valor <= puntuacion) "★" else "☆", color = AmancayBordo, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Button(
                onClick = {
                    val uid = usuarioId
                    if (puntuacion in 1..5) scope.launch {
                        enviando = true
                        val referencia = if (productoId == null) firestore.collection("tiendasPublicas").document(tiendaId)
                            .collection("calificaciones").document(uid)
                        else firestore.collection("tiendasPublicas").document(tiendaId).collection("productos").document(productoId)
                            .collection("calificaciones").document(uid)
                        runCatching { referencia.set(mapOf("puntuacion" to puntuacion, "actualizadoEn" to FieldValue.serverTimestamp())).await() }
                            .onSuccess { guardada = puntuacion; mensaje = "Calificación guardada." }
                            .onFailure { mensaje = "No se pudo guardar. Comprueba tu conexión y que las calificaciones sigan activas." }
                        enviando = false
                    }
                },
                enabled = !enviando && puntuacion in 1..5,
                colors = ButtonDefaults.buttonColors(containerColor = AmancayBordo)
            ) { Text(if (guardada > 0) "Actualizar calificación" else "Enviar calificación", color = Color.White) }
            mensaje?.let { Text(it, color = AmancayTintaSuave, style = MaterialTheme.typography.bodySmall) }
        }
    }
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
