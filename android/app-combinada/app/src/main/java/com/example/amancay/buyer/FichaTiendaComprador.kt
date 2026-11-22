package com.example.amancay.buyer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.amancay.R
import com.example.amancay.data.ProductoCatalogoComprador
import com.example.amancay.data.TiendaCatalogoComprador
import com.google.firebase.firestore.FirebaseFirestore

@Composable
internal fun FichaTiendaComprador(
    tienda: TiendaCatalogoComprador,
    tipo: String,
    sector: String,
    productos: List<ProductoCatalogoComprador>,
    usuarioId: String?,
    firestore: FirebaseFirestore,
    alPedirInicioSesion: () -> Unit,
    alConsultar: () -> Unit,
    alCompartir: @Composable () -> Unit,
    alMostrarQr: @Composable () -> Unit,
    alComoLlegar: () -> Unit,
    alAbrirProducto: (ProductoCatalogoComprador) -> Unit,
    alVolver: () -> Unit
) {
    var busqueda by remember(tienda.id) { mutableStateOf("") }
    var categoria by remember(tienda.id) { mutableStateOf("Todas las categorías") }
    var menuCategorias by remember { mutableStateOf(false) }
    val categorias = remember(productos) { productos.map { it.categoriaNombre }.filter(String::isNotBlank).distinct().sorted() }
    val visibles = productos.filter {
        it.nombre.contains(busqueda.trim(), true) && (categoria == "Todas las categorías" || it.categoriaNombre == categoria)
    }

    Dialog(onDismissRequest = alVolver, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = AmancayFondo, contentColor = AmancayTinta) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.amancay_floral_derecha),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp).width(220.dp).height(430.dp),
                    contentScale = ContentScale.Fit,
                    alpha = 0.34f
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)
                ) {
                    item {
                        IconButton(onClick = alVolver, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.ArrowBack, "Volver al directorio", tint = AmancayBordo, modifier = Modifier.size(30.dp))
                        }
                        Text(tienda.nombre, color = AmancayBordo, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Filled.LocationOn, null, tint = AmancayBordo, modifier = Modifier.size(20.dp))
                            Text(listOf(tipo, sector).filter(String::isNotBlank).joinToString("  •  "), color = AmancayTinta, fontSize = 17.sp)
                        }
                    }
                    item {
                        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5D7)), border = BorderStroke(1.dp, Color(0xFFF7D4C4))) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${tipo.ifBlank { "Emprendimiento local" }} de la comunidad", color = AmancayTinta, fontSize = 18.sp)
                                Surface(shape = CircleShape, color = Color(0xFFF8D0BD)) {
                                    Text("Tienda activa", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = AmancayBordo, fontSize = 14.sp)
                                }
                                ControlSeguimientoTienda(
                                    tiendaId = tienda.id,
                                    nombreTienda = tienda.nombre,
                                    usuarioId = usuarioId,
                                    firestore = firestore,
                                    alPedirInicioSesion = alPedirInicioSesion
                                )
                                alCompartir()
                                alMostrarQr()
                            }
                        }
                    }
                    item {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0E5))) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Contacto configurado: ${tienda.formaContacto.ifBlank { "ninguno" }.replaceFirstChar { it.uppercase() }}", color = AmancayTinta, fontSize = 17.sp)
                                OutlinedButton(onClick = alConsultar, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp), border = BorderStroke(1.5.dp, AmancayBordo), colors = ButtonDefaults.outlinedButtonColors(contentColor = AmancayBordo), enabled = tienda.formularioHabilitado || tienda.whatsappHabilitado || tienda.correoHabilitado) {
                                    Text("Enviar consulta", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    tienda.puntoRetiro?.let { punto ->
                        item {
                            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5D7)), border = BorderStroke(1.dp, Color(0xFFF7D4C4))) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Punto de venta o retiro", color = AmancayBordo, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    if (punto.descripcion.isNotBlank()) Text(punto.descripcion, color = AmancayTintaSuave, fontSize = 15.sp)
                                    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp))) {
                                        MapaOsmdroid(
                                            ferias = listOf(FeriaComprador(
                                                id = tienda.id, nombre = tienda.nombre, descripcion = "", organizador = "",
                                                direccion = punto.descripcion, fechaInicio = null, fechaFin = null,
                                                latitud = punto.latitud, longitud = punto.longitud
                                            )),
                                            ubicacionUsuario = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    OutlinedButton(onClick = alComoLlegar, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp), border = BorderStroke(1.5.dp, AmancayBordo), colors = ButtonDefaults.outlinedButtonColors(contentColor = AmancayBordo)) {
                                        Icon(Icons.Filled.LocationOn, null); Spacer(Modifier.width(8.dp)); Text("Cómo llegar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text("Productos de ${tienda.nombre}", color = AmancayBordo, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = busqueda, onValueChange = { busqueda = it },
                            placeholder = { Text("Buscar productos") }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = AmancayBordo) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(28.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmancayBordo, unfocusedBorderColor = AmancayBorde, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                        )
                        Box {
                            OutlinedButton(onClick = { menuCategorias = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, AmancayBorde)) {
                                Text(categoria, color = AmancayTinta); Spacer(Modifier.width(8.dp)); Text("⌄", color = AmancayBordo)
                            }
                            DropdownMenu(expanded = menuCategorias, onDismissRequest = { menuCategorias = false }) {
                                (listOf("Todas las categorías") + categorias).forEach { opcion ->
                                    DropdownMenuItem(text = { Text(opcion) }, onClick = { categoria = opcion; menuCategorias = false })
                                }
                            }
                        }
                    }
                    if (visibles.isEmpty()) item { Text("No hay productos que coincidan con la búsqueda.", color = AmancayTintaSuave) }
                    items(visibles.size.chunked(2).size) { fila ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            visibles.chunked(2)[fila].forEach { producto -> TarjetaProductoTienda(producto, tienda.mostrarPrecios, Modifier.weight(1f), onClick = { alAbrirProducto(producto) }) }
                            if (visibles.size % 2 == 1 && fila == visibles.chunked(2).lastIndex) Spacer(Modifier.weight(1f))
                        }
                    }
                    item {
                        Button(onClick = alVolver, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE1D1), contentColor = AmancayBordo)) {
                            Icon(Icons.Filled.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text("Volver al directorio", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaProductoTienda(producto: ProductoCatalogoComprador, mostrarPrecios: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE5D7))) {
        Column(Modifier.padding(8.dp)) {
            if (!producto.imagenUrl.isNullOrBlank()) {
                AsyncImage(producto.imagenUrl, producto.nombre, Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFEAE7E3)), contentScale = ContentScale.Crop)
            } else {
                Image(androidx.compose.ui.res.painterResource(R.drawable.fig_72), null, Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(8.dp))
            Text(producto.nombre, color = AmancayBordoOscuro, fontSize = 16.sp, maxLines = 2, fontWeight = FontWeight.Medium)
            if (mostrarPrecios) Text(precioProducto(producto), color = AmancayBordo, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}
