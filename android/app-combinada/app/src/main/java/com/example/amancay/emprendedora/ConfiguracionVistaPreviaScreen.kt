package com.example.amancay.emprendedora

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.data.ProductoEmprendedora
import com.example.amancay.data.ValoresDefectoProducto
import com.example.amancay.data.etiquetaCatalogoCliente
import com.example.amancay.data.formatPrice
import com.example.amancay.data.visibleEnCatalogoPublico
import com.example.amancay.data.unidadesDisponibles
import com.example.amancay.ui.BotanicalBackdrop
import com.example.amancay.ui.ProductWizardTopBar
import com.example.amancay.ui.theme.Dark
import com.example.amancay.ui.theme.GreenText
import com.example.amancay.ui.theme.Line
import com.example.amancay.ui.theme.Muted
import com.example.amancay.ui.theme.Wine

@Composable
internal fun ConfiguracionVistaPreviaScreen(
    productos: List<ProductoEmprendedora>,
    miniaturas: Map<String, Bitmap>,
    historialActivo: Boolean,
    valoresDefecto: ValoresDefectoProducto,
    catalogoSinConexion: Boolean,
    enviando: Boolean,
    error: String?,
    alGuardarValoresDefecto: (ValoresDefectoProducto) -> Unit,
    alCambiarHistorial: (Boolean) -> Unit,
    alCambiarCatalogoSinConexion: (Boolean) -> Unit,
    alVerHistorial: () -> Unit,
    alVerTiendaPublica: () -> Unit,
    alVolver: () -> Unit
) {
    var defecto by remember(valoresDefecto) { mutableStateOf(valoresDefecto) }
    var editandoDefecto by remember { mutableStateOf(false) }
    val destacado = productos.firstOrNull { visibleEnCatalogoPublico(it) } ?: productos.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        BotanicalBackdrop()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
            ProductWizardTopBar(onBack = alVolver)
            Text("CONFIGURACIÓN", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color(0xFFEF5452))
            Text("Configuración\ny vista previa", fontSize = 36.sp, lineHeight = 38.sp, color = Wine, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Text("Define cómo funciona tu catálogo y revisa cómo se verá para tus clientes.", fontSize = 15.sp, lineHeight = 21.sp, color = Muted)
            Spacer(Modifier.height(16.dp))

            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    FilaAjuste("Historial de encargos", if (historialActivo) "Activo. Guarda precio, fecha y lugar de cada venta." else "Apagado. Las ventas nuevas no se registran en el historial.", Icons.Filled.ReceiptLong, alVerHistorial) {
                        Switch(
                            checked = historialActivo,
                            onCheckedChange = alCambiarHistorial,
                            enabled = !enviando,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            colors = SwitchDefaults.colors(checkedTrackColor = Wine, checkedThumbColor = Color.White)
                        )
                    }
                    HorizontalDivider(color = Line.copy(alpha = .65f), modifier = Modifier.padding(start = 72.dp, end = 16.dp))
                    FilaAjuste("Valores por defecto", "Modalidad, stock y qué pasa al agotarse en cada producto nuevo.", Icons.Filled.Settings, { editandoDefecto = !editandoDefecto })
                    HorizontalDivider(color = Line.copy(alpha = .65f), modifier = Modifier.padding(start = 72.dp, end = 16.dp))
                    FilaAjuste("Guardar catálogo sin conexión", if (catalogoSinConexion) "Mantén tu catálogo disponible aunque no haya internet." else "Activa esta opción para conservar una copia local.", Icons.Filled.WifiOff, null) {
                        Switch(
                            checked = catalogoSinConexion,
                            onCheckedChange = alCambiarCatalogoSinConexion,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                            colors = SwitchDefaults.colors(checkedTrackColor = Wine, checkedThumbColor = Color.White)
                        )
                    }
                }
            }
            error?.let { Text(it, color = Wine, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }

            if (editandoDefecto) {
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4EE)), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Valores por defecto", fontWeight = FontWeight.Bold, color = Dark, fontSize = 17.sp)
                        Text("Cada producto nuevo nace con esta modalidad y esta regla de stock.", color = Muted, fontSize = 13.sp)
                        Text("Modalidad", fontWeight = FontWeight.SemiBold, color = Dark)
                        listOf("regular" to "Regular" to "Siempre disponible de forma habitual.", "a_pedido" to "A pedido" to "Se elabora después de encargarlo.", "pieza_unica" to "Pieza única" to "No se repite.")
                            .forEach { (par, detalle) ->
                                val (id, nombre) = par
                                OpcionCatalogo(nombre, detalle, defecto.modalidad == id) {
                                    defecto = defecto.copy(modalidad = id, manejaStock = if (id == "pieza_unica") true else defecto.manejaStock)
                                }
                            }
                        Text("¿Maneja stock?", fontWeight = FontWeight.SemiBold, color = Dark)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7E4DC))) {
                            listOf(true to "Sí", false to "No").forEach { (valor, etiqueta) ->
                                val activo = defecto.manejaStock == valor
                                Box(
                                    Modifier.weight(1f).height(48.dp).background(if (activo) Wine else Color.Transparent)
                                        .clickable(enabled = defecto.modalidad != "pieza_unica") { defecto = defecto.copy(manejaStock = valor) },
                                    contentAlignment = Alignment.Center
                                ) { Text(etiqueta, color = if (activo) Color.White else Wine, fontWeight = FontWeight.Bold) }
                            }
                        }
                        if (defecto.manejaStock) {
                            Text("Cuando se agote", fontWeight = FontWeight.SemiBold, color = Dark)
                            listOf(
                                "mostrar_agotado" to "Mostrar agotado" to "Sigue visible, marcado como agotado.",
                                "ocultar" to "Ocultar producto" to "Deja de verse en la web hasta reponer.",
                                "pasar_a_pedido" to "Pasar a pedido" to "Se ofrece como encargo."
                            ).forEach { (par, detalle) ->
                                val (id, nombre) = par
                                OpcionCatalogo(nombre, detalle, defecto.alAgotarse == id) { defecto = defecto.copy(alAgotarse = id) }
                            }
                        }
                        Button(
                            onClick = { alGuardarValoresDefecto(defecto); editandoDefecto = false },
                            enabled = !enviando,
                            colors = ButtonDefaults.buttonColors(containerColor = Wine),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Guardar valores por defecto", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Así se verá tu producto", fontSize = 22.sp, color = Dark, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Text("Vista previa según modalidad, stock, agotamiento y visibilidad.", fontSize = 14.sp, color = Muted)
            Spacer(Modifier.height(10.dp))
            if (destacado == null) {
                Text("Cuando agregues un producto, aquí verás cómo lo verían tus clientes.", color = Muted)
            } else {
                TarjetaVistaCliente(destacado, miniaturas[destacado.id])
            }

            Spacer(Modifier.height(16.dp))
            Text("Ejemplos de disponibilidad", fontSize = 18.sp, color = Dark, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniEstado("Agotado", Color(0xFFF0E9EC), Muted, Modifier.weight(1f))
                MiniEstado("Disponible para encargar", Color(0xFFE4F0E6), GreenText, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            if (productos.isEmpty()) {
                Text("Cuando agregues productos, aquí verás cómo se verán en tu catálogo.", color = Muted)
            }
            productos.forEach { producto ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniaturaProducto(miniaturas[producto.id], producto.nombre)
                        Column(Modifier.weight(1f)) {
                            Text(producto.nombre, fontWeight = FontWeight.Bold, color = Dark, maxLines = 1)
                            Text(etiquetaCatalogoCliente(producto), color = if (visibleEnCatalogoPublico(producto)) GreenText else Muted, fontSize = 12.sp)
                            val precioVista = if (producto.tieneVariantes) producto.variantes.filter { it.activa }.minOfOrNull { it.precio } ?: 0L else producto.precioBase ?: 0L
                            Text(formatPrice(precioVista) + " CLP", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = alVerTiendaPublica,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF5452))
                ) { Text("Ver tienda pública", color = Color(0xFFEF5452), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Button(
                    onClick = { alGuardarValoresDefecto(defecto); editandoDefecto = false },
                    enabled = !enviando,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Wine),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Guardar cambios  →", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FilaAjuste(titulo: String, detalle: String, icono: androidx.compose.ui.graphics.vector.ImageVector, onClick: (() -> Unit)?, extra: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFFFEEE7)), contentAlignment = Alignment.Center) {
            Icon(icono, contentDescription = null, tint = Wine, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.Bold, color = Wine, fontSize = 16.sp)
            Text(detalle, color = Muted, fontSize = 13.sp, lineHeight = 17.sp)
        }
        extra?.invoke() ?: Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Wine, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun OpcionCatalogo(titulo: String, detalle: String, seleccionado: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (seleccionado) Color(0xFFFFEEE7) else Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (seleccionado) Wine else Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (seleccionado) Wine else Color.White).then(Modifier), contentAlignment = Alignment.Center) {
                if (seleccionado) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                else Box(Modifier.size(22.dp).clip(CircleShape).background(Color.Transparent))
            }
            Column {
                Text(titulo, fontWeight = FontWeight.Bold, color = Dark)
                Text(detalle, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TarjetaVistaCliente(producto: ProductoEmprendedora, miniatura: Bitmap?) {
    val precio = if (producto.tieneVariantes) producto.variantes.filter { it.activa }.minOfOrNull { it.precio } ?: 0L else producto.precioBase ?: 0L
    val visible = visibleEnCatalogoPublico(producto)
    val unidades = unidadesDisponibles(producto)
    val agotadoVisible = producto.manejaStock && unidades <= 0 && producto.alAgotarse == "mostrar_agotado"
    val puedeEncargar = visible && !agotadoVisible
    var cantidad by remember(producto.id) { mutableIntStateOf(1) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniaturaProducto(miniatura, producto.nombre, 112)
                Column(Modifier.weight(1f)) {
                    Text(producto.nombre, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 22.sp, color = Dark, fontFamily = FontFamily.Serif, maxLines = 2)
                    Text(formatPrice(precio) + " CLP", color = Color(0xFFE94848), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (producto.tieneVariantes) EtiquetaVistaCliente("${producto.variantes.count { it.activa }} variantes", Color(0xFFF3EAF7), Wine)
                        val estadoStock = when {
                            producto.modalidad == "pieza_unica" && unidades <= 0 -> "Vendida"
                            agotadoVisible -> "Agotado"
                            producto.manejaStock && unidades > 0 -> "En stock ($unidades)"
                            producto.modalidad == "a_pedido" || producto.alAgotarse == "pasar_a_pedido" -> "A pedido"
                            else -> "Disponible"
                        }
                        EtiquetaVistaCliente(estadoStock, if (puedeEncargar) Color(0xFFE4F0E6) else Color(0xFFF0E9EC), if (puedeEncargar) GreenText else Muted)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .then(Modifier),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { cantidad = (cantidad - 1).coerceAtLeast(1) }, enabled = cantidad > 1, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Filled.Remove, contentDescription = "Quitar una unidad", tint = Wine)
                    }
                    Text(cantidad.toString(), color = Dark, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { cantidad = if (producto.manejaStock && unidades > 0) (cantidad + 1).coerceAtMost(unidades) else cantidad + 1 }, enabled = !producto.manejaStock || cantidad < unidades, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Agregar una unidad", tint = Wine)
                    }
                }
                Button(
                    onClick = {},
                    enabled = puedeEncargar,
                    modifier = Modifier.weight(1.25f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Wine, disabledContainerColor = Color(0xFFE9DFE4)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(if (agotadoVisible) "Agotado" else if (puedeEncargar) "Encargar" else "No disponible", color = if (puedeEncargar) Color.White else Muted, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (puedeEncargar) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            if (!visible) {
                Text("Tus clientes no verían este producto ahora. Sigue en tu tienda para que lo edites.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EtiquetaVistaCliente(texto: String, fondo: Color, tinta: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(fondo).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(texto, color = tinta, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun MiniaturaProducto(imagen: Bitmap?, nombre: String, size: Int = 56) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFE9DF)), contentAlignment = Alignment.Center) {
        if (imagen != null) Image(imagen.asImageBitmap(), contentDescription = "Foto de $nombre", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.Image, contentDescription = null, tint = Color(0xFFEE845F), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MiniEstado(texto: String, fondo: Color, tinta: Color, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFE9DF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Inventory2, contentDescription = null, tint = Color(0xFFEE845F), modifier = Modifier.size(28.dp))
            }
            Box(Modifier.clip(RoundedCornerShape(50)).background(fondo).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(texto, color = tinta, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
