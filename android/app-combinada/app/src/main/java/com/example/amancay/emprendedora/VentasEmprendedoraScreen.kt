package com.example.amancay.emprendedora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.data.FeriaParaVenta
import com.example.amancay.data.ProductoEmprendedora
import com.example.amancay.data.VentaRegistrada
import com.example.amancay.ui.SelectableChip
import com.example.amancay.ui.BotanicalBackdrop
import com.example.amancay.ui.ProductWizardTopBar
import com.example.amancay.ui.theme.Dark
import com.example.amancay.ui.theme.Line
import com.example.amancay.ui.theme.Muted
import com.example.amancay.ui.theme.Wine
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private data class FiltrosVentas(val desde: String = "", val hasta: String = "", val productoId: String = "", val lugarId: String = "")

@Composable
internal fun VentasEmprendedoraScreen(
    productos: List<ProductoEmprendedora>, ventas: List<VentaRegistrada>, ferias: List<FeriaParaVenta>, historialActivo: Boolean,
    abrirHistorial: Boolean = false,
    cargando: Boolean, error: String?, enviando: Boolean,
    alRegistrar: (ProductoEmprendedora, String?, Int, String, String?, String?, String, String, () -> Unit) -> Unit,
    alVolver: () -> Unit
) {
    var seccion by remember(abrirHistorial) { mutableStateOf(if (abrirHistorial) "Historial" else "Registrar") }
    var productoId by remember { mutableStateOf("") }
    var varianteId by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("1") }
    var lugarTipo by remember { mutableStateOf("feria") }
    var feriaId by remember { mutableStateOf("") }
    var lugarOtro by remember { mutableStateOf("") }
    var filtroProducto by remember { mutableStateOf("") }
    var filtroLugar by remember { mutableStateOf("") }
    var desde by remember { mutableStateOf("") }
    var hasta by remember { mutableStateOf("") }
    var filtrosAplicados by remember { mutableStateOf(FiltrosVentas()) }
    var operacionId by remember(productoId, varianteId, cantidad, lugarTipo, feriaId, lugarOtro) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var fechaVenta by remember(operacionId) { mutableStateOf(java.time.Instant.now().toString()) }
    val producto = productos.firstOrNull { it.id == productoId } ?: productos.firstOrNull()
    val variantes = producto?.variantes?.filter { it.activa }.orEmpty()
    if (producto != null && producto.id != productoId) productoId = producto.id
    if (variantes.none { it.id == varianteId }) varianteId = variantes.firstOrNull()?.id.orEmpty()
    val ventasFiltradas = ventas.filter { venta ->
        val fechaVenta = venta.fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val desdeOk = filtrosAplicados.desde.isBlank() || runCatching { !fechaVenta.isBefore(LocalDate.parse(filtrosAplicados.desde)) }.getOrDefault(false)
        val hastaOk = filtrosAplicados.hasta.isBlank() || runCatching { !fechaVenta.isAfter(LocalDate.parse(filtrosAplicados.hasta)) }.getOrDefault(false)
        (filtrosAplicados.productoId.isBlank() || venta.productoId == filtrosAplicados.productoId) &&
            (filtrosAplicados.lugarId.isBlank() || venta.feriaId == filtrosAplicados.lugarId || venta.lugarTexto == filtrosAplicados.lugarId) && desdeOk && hastaOk
    }
    val desdeInvalido = desde.isNotBlank() && runCatching { LocalDate.parse(desde) }.isFailure
    val hastaInvalido = hasta.isNotBlank() && runCatching { LocalDate.parse(hasta) }.isFailure
    val productosFiltro = (ventas.map { it.productoId to it.productoNombre } + productos.map { it.id to it.nombre })
        .distinctBy { it.first }
    val lugaresFiltro = (ferias.map { it.id to it.nombre } + ventas.mapNotNull { venta ->
        venta.feriaId?.let { id -> id to (venta.lugarTexto ?: ferias.firstOrNull { feria -> feria.id == id }?.nombre ?: "Feria anterior") }
    } + ventas.mapNotNull { venta -> venta.lugarTexto?.let { it to it } }).distinctBy { it.first }
    Box(Modifier.fillMaxSize()) {
        BotanicalBackdrop()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp, vertical=8.dp)) {
        ProductWizardTopBar(onBack=alVolver)
        Text("ESPACIO EMPRENDEDORA", fontSize=12.sp, fontWeight=FontWeight.Bold, color=Wine)
        Text(if (seccion == "Historial") "Historial de encargos" else "Registrar una venta", fontSize=31.sp, color=Dark, fontFamily=FontFamily.Serif)
        Text(if (seccion == "Historial") "Aquí puedes ver tus ventas y encargos realizados." else "Registra una venta y actualiza el stock de tu catálogo.", fontSize=15.sp, color=Muted)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SelectableChip("Registrar venta", seccion == "Registrar") { seccion = "Registrar" }
            SelectableChip("Historial", seccion == "Historial") { seccion = "Historial" }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors=CardDefaults.cardColors(containerColor=if (historialActivo) Color(0xFFE2F4E4) else Color(0xFFF0E9EC)), shape=RoundedCornerShape(24.dp), modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=10.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                Text(if (historialActivo) "✓  Historial activado" else "Historial desactivado", color=if (historialActivo) Color(0xFF17643B) else Muted, fontWeight=FontWeight.Bold)
                if (!historialActivo) Text("El stock igual se actualiza", color=Muted, fontSize=12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (seccion == "Registrar") {
            Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp,Line), modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Registrar una venta", color=Dark, fontWeight=FontWeight.Bold, fontSize=19.sp)
                    SelectorVentas("Producto", productos.map { it.id to it.nombre }, producto?.id.orEmpty()) { productoId=it; varianteId="" }
                    if (producto?.tieneVariantes == true) SelectorVentas("Variante", variantes.map { it.id to it.opciones.values.joinToString(" · ") }, varianteId) { varianteId=it }
                    val variante = variantes.firstOrNull { it.id == varianteId }
                    val precio = variante?.precio ?: producto?.precioBase ?: 0L
                    Text("Precio unitario: ${pesosVenta(precio)}", color=Wine, fontWeight=FontWeight.Bold)
                    OutlinedTextField(cantidad, { cantidad=it.filter(Char::isDigit).take(5) }, label={Text("Cantidad")}, modifier=Modifier.fillMaxWidth(), enabled=!enviando)
                    if (historialActivo) {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            SelectableChip("Feria", lugarTipo == "feria") { lugarTipo="feria" }
                            SelectableChip("Otro lugar", lugarTipo == "otro") { lugarTipo="otro" }
                        }
                        if (lugarTipo == "feria") SelectorVentas("Feria", ferias.map { it.id to it.nombre }, feriaId) { feriaId=it }
                        else OutlinedTextField(lugarOtro, { lugarOtro=it.take(200) }, label={Text("Lugar")}, modifier=Modifier.fillMaxWidth(), enabled=!enviando)
                    }
                    Button(
                        onClick={ if (producto != null) alRegistrar(producto, if (producto.tieneVariantes) varianteId else null, cantidad.toIntOrNull() ?: 0, lugarTipo, feriaId.takeIf { lugarTipo == "feria" }, lugarOtro.takeIf { lugarTipo == "otro" }, operacionId, fechaVenta) { operacionId=java.util.UUID.randomUUID().toString(); fechaVenta=java.time.Instant.now().toString() } },
                        enabled=!enviando && producto != null && (!producto.tieneVariantes || varianteId.isNotBlank()) && (cantidad.toIntOrNull() ?: 0) > 0 &&
                            (!producto.manejaStock || (variante?.stock ?: producto.stock ?: 0) >= (cantidad.toIntOrNull() ?: Int.MAX_VALUE)) &&
                            (!historialActivo || if (lugarTipo == "feria") feriaId.isNotBlank() else lugarOtro.isNotBlank()),
                        colors=ButtonDefaults.buttonColors(containerColor=Wine), modifier=Modifier.fillMaxWidth().height(52.dp)
                    ) { Text(if (enviando) "Guardando…" else "Guardar venta", color=Color.White, fontWeight=FontWeight.Bold) }
                }
            }
        } else {
            Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp,Line), modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Filtrar historial", color=Dark, fontWeight=FontWeight.Bold)
                    OutlinedTextField(desde, {desde=it.take(10)}, label={Text("Desde (AAAA-MM-DD)")}, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(hasta, {hasta=it.take(10)}, label={Text("Hasta (AAAA-MM-DD)")}, modifier=Modifier.fillMaxWidth())
                    if (desdeInvalido || hastaInvalido) Text("Revisa las fechas: usa el formato AAAA-MM-DD.", color=Wine, fontSize=13.sp)
                    SelectorVentas("Producto", listOf("" to "Todos los productos") + productosFiltro, filtroProducto) { filtroProducto=it }
                    SelectorVentas("Feria o lugar", listOf("" to "Todos los lugares") + lugaresFiltro, filtroLugar) { filtroLugar=it }
                    Button(
                        onClick = { filtrosAplicados = FiltrosVentas(desde, hasta, filtroProducto, filtroLugar) },
                        enabled = !enviando,
                        colors = ButtonDefaults.buttonColors(containerColor = Wine),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Aplicar filtros", color = Color.White, fontWeight = FontWeight.Bold) }
                    Text("${ventasFiltradas.size} ventas", color=Muted)
                }
            }
            ventasFiltradas.forEach { venta ->
                Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(18.dp), modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(venta.productoNombre, color=Dark, fontWeight=FontWeight.Bold)
                            venta.varianteNombre?.let { Text("Variante: $it", color=Muted, fontSize=13.sp) }
                            Text("${venta.cantidad} × ${pesosVenta(venta.precioUnitario)}", color=Muted)
                            Text("${SimpleDateFormat("d 'de' MMMM yyyy", Locale("es", "CL")).format(venta.fecha)} · ${venta.lugarTexto ?: ferias.firstOrNull { it.id == venta.feriaId }?.nombre.orEmpty()}", color=Muted, fontSize=13.sp)
                        }
                        Text(pesosVenta(venta.total), color=Wine, fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
        error?.let { Text(it, color=Wine, modifier=Modifier.padding(top=8.dp)) }
        if (cargando) Text("Cargando información…", color=Muted, modifier=Modifier.padding(top=10.dp))
        }
    }
}

@Composable
private fun SelectorVentas(titulo: String, opciones: List<Pair<String,String>>, seleccionado: String, alElegir: (String)->Unit) {
    var expandido by remember(titulo) { mutableStateOf(false) }
    val actual = opciones.firstOrNull { it.first == seleccionado }?.second ?: "Seleccionar"
    Column {
        Text(titulo, color=Dark, fontSize=13.sp, fontWeight=FontWeight.Bold)
        OutlinedButton(onClick={expandido=true}, modifier=Modifier.fillMaxWidth().height(50.dp), shape=RoundedCornerShape(14.dp)) { Text(actual, color=Wine) }
        DropdownMenu(expanded=expandido, onDismissRequest={expandido=false}) {
            opciones.forEach { (id,nombre) -> DropdownMenuItem(text={Text(nombre)}, onClick={expandido=false; alElegir(id)}) }
        }
    }
}

private fun pesosVenta(valor: Long) = "$" + NumberFormat.getNumberInstance(Locale("es", "CL")).apply { maximumFractionDigits=0 }.format(valor)
