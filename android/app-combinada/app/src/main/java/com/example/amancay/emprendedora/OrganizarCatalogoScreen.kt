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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.amancay.data.CategoriaEmprendedora
import com.example.amancay.data.ProductoEmprendedora
import com.example.amancay.data.unidadesDisponibles
import com.example.amancay.ui.theme.Dark
import com.example.amancay.ui.theme.Line
import com.example.amancay.ui.theme.Muted
import com.example.amancay.ui.theme.Wine
import com.example.amancay.ui.BotanicalBackdrop

@Composable
internal fun OrganizarCatalogoScreen(
    productos: List<ProductoEmprendedora>,
    categorias: List<CategoriaEmprendedora>,
    miniaturas: Map<String, Bitmap>,
    enviando: Boolean,
    alCambiarDisponibilidad: (List<ProductoEmprendedora>, Boolean) -> Unit,
    alEditar: (ProductoEmprendedora) -> Unit = {},
    alEliminar: (ProductoEmprendedora) -> Unit = {}
) {
    var categoriaId by remember { mutableStateOf("") }
    var seleccionados by remember { mutableStateOf(setOf<String>()) }
    var menuCategorias by remember { mutableStateOf(false) }
    var productoEliminar by remember { mutableStateOf<ProductoEmprendedora?>(null) }
    val filtrados = productos.filter { categoriaId.isBlank() || it.categoriaId == categoriaId }
    val todosSeleccionados = filtrados.isNotEmpty() && filtrados.all { it.id in seleccionados }
    val productosSeleccionados = productos.filter { it.id in seleccionados }

    Box(Modifier.fillMaxSize()) {
    BotanicalBackdrop()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text("ESPACIO DE EMPRENDIMIENTO", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color(0xFFEF5452))
        Text("Organizar\ncatálogo", fontSize = 38.sp, lineHeight = 39.sp, color = Wine, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        Text("Selecciona productos para organizarlos, cambiar su disponibilidad o eliminarlos.", fontSize = 15.sp, lineHeight = 21.sp, color = Muted)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { seleccionados = if (todosSeleccionados) seleccionados - filtrados.map { it.id }.toSet() else seleccionados + filtrados.map { it.id } },
                enabled = filtrados.isNotEmpty() && !enviando,
                modifier = Modifier.weight(1f).heightIn(min = 58.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Wine),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Icon(if (todosSeleccionados) Icons.Filled.Check else Icons.Filled.ViewList, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text(if (todosSeleccionados) "Quitar\nselección" else "Seleccionar\ntodos (${filtrados.size})", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2)
            }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { menuCategorias = true }, enabled = !enviando && categorias.any { it.activo }, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Wine), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                    Icon(Icons.Filled.GridView, contentDescription = null, tint = Wine, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Por\ncategoría", color = Wine, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2)
                }
                DropdownMenu(expanded = menuCategorias, onDismissRequest = { menuCategorias = false }) {
                    categorias.filter { it.activo }.forEach { categoria ->
                        DropdownMenuItem(text = { Text(categoria.nombre) }, onClick = {
                            categoriaId = categoria.id
                            seleccionados = productos.filter { it.categoriaId == categoria.id }.map { it.id }.toSet()
                            menuCategorias = false
                        })
                    }
                }
            }
            OutlinedButton(
                onClick = { categoriaId = ""; seleccionados = productos.map { it.id }.toSet() },
                enabled = productos.isNotEmpty() && !enviando,
                modifier = Modifier.weight(1f).heightIn(min = 58.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Wine),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.ViewList, contentDescription = null, tint = Wine, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Todo el\ncatálogo", color = Wine, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2)
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEE7)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFD8C8)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, tint = Wine, modifier = Modifier.size(23.dp))
                    }
                    Column {
                        Text("Cambiar disponibilidad", color = Dark, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(if (seleccionados.isEmpty()) "Selecciona uno o más productos" else "${seleccionados.size} seleccionados", color = Muted, fontSize = 13.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = { alCambiarDisponibilidad(productosSeleccionados, true) },
                        enabled = seleccionados.isNotEmpty() && !enviando,
                        colors = ButtonDefaults.buttonColors(containerColor = Wine, disabledContainerColor = Color(0xFFD8C9D0)),
                        modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp)
                    ) { Text("Disponible", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { alCambiarDisponibilidad(productosSeleccionados, false) },
                        enabled = seleccionados.isNotEmpty() && !enviando,
                        modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Wine)
                    ) { Text("No disponible", color = Wine, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Productos", fontSize = 25.sp, color = Dark, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${filtrados.size} ${if (filtrados.size == 1) "producto" else "productos"} · ${seleccionados.size} seleccionados", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        if (categorias.none { it.activo }) {
            Text("Aún no hay categorías activas para filtrar productos.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (filtrados.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (productos.isEmpty()) "Todavía no tienes productos en tu catálogo. Agrega uno para comenzar."
                    else "No hay productos en esta categoría.",
                    color = Muted,
                    modifier = Modifier.padding(18.dp)
                )
            }
        }

        filtrados.forEach { producto ->
            val seleccionado = producto.id in seleccionados
            val categoria = categorias.firstOrNull { it.id == producto.categoriaId }?.nombre.orEmpty()
            val stock = when {
                !producto.manejaStock -> "Sin control de stock"
                producto.tieneVariantes -> "Stock total: ${producto.variantes.filter { it.activa }.sumOf { it.stock ?: 0 }}"
                else -> "Stock: ${producto.stock ?: 0}"
            }
            val agotado = producto.manejaStock && unidadesDisponibles(producto) <= 0
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(if (seleccionado) 1.5.dp else 1.dp, if (seleccionado) Wine else Line),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Checkbox(checked = seleccionado, enabled = !enviando, onCheckedChange = { marcado -> seleccionados = if (marcado) seleccionados + producto.id else seleccionados - producto.id })
                    val imagen = miniaturas[producto.id]
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFFFE9DF)), contentAlignment = Alignment.Center) {
                        if (imagen != null) Image(imagen.asImageBitmap(), contentDescription = "Foto de ${producto.nombre}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Filled.Image, contentDescription = null, tint = Color(0xFFEE845F), modifier = Modifier.size(25.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(producto.nombre, color = Wine, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                        Text(categoria, color = Muted, fontSize = 12.sp, maxLines = 1)
                        Text(if (producto.tieneVariantes) "${producto.variantes.count { it.activa }} variantes · $stock" else stock, color = if (agotado) Color(0xFFE94848) else Muted, fontSize = 11.sp, maxLines = 2)
                        if (agotado) Text("Agotado · ${producto.alAgotarse.replace('_', ' ')}", color = Color(0xFFE94848), fontSize = 10.sp, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Disponible", color = Muted, fontSize = 10.sp, maxLines = 1)
                        Switch(
                            checked = producto.disponible,
                            onCheckedChange = { disponible -> alCambiarDisponibilidad(listOf(producto), disponible) },
                            enabled = !enviando,
                            colors = SwitchDefaults.colors(checkedTrackColor = Wine, checkedThumbColor = Color.White)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { alEditar(producto) }, enabled = !enviando, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Wine)) {
                        Text("Editar", color = Wine, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = { productoEliminar = producto }, enabled = !enviando, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFFFF6B62))) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFE94848), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Eliminar", color = Color(0xFFE94848), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    }

    productoEliminar?.let { producto ->
        AlertDialog(
            onDismissRequest = { productoEliminar = null },
            title = { Text("¿Eliminar producto?") },
            text = { Text("Se quitará ${producto.nombre} de tu catálogo. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { alEliminar(producto); productoEliminar = null }) {
                    Text("Eliminar", color = Color(0xFFE94848), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { productoEliminar = null }) { Text("Cancelar", color = Wine) } },
            containerColor = Color.White,
            shape = RoundedCornerShape(22.dp)
        )
    }
}

@Composable
private fun FiltroCatalogo(texto: String, activo: Boolean, onClick: () -> Unit) {
    val fondo = if (activo) Wine else Color.White
    val tinta = if (activo) Color.White else Wine
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(fondo).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, color = tinta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
