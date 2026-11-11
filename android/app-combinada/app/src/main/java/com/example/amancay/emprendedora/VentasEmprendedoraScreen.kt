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
    historialConfiguracionCargada: Boolean,
    abrirHistorial: Boolean = false,
    cargando: Boolean, error: String?, errorCarga: String?, enviando: Boolean,
    alRegistrar: (ProductoEmprendedora, String?, Int, String, String?, String?, String, String, () -> Unit) -> Unit,
    alReintentarCarga: () -> Unit,
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
    var fechaVenta by remember(operacionId) { mutableStateOf<String?>(null) }
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
    val desdeParseado = desde.takeIf { it.isNotBlank() && !desdeInvalido }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val hastaParseado = hasta.takeIf { it.isNotBlank() && !hastaInvalido }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val rangoInvalido = desdeParseado != null && hastaParseado != null && desdeParseado.isAfter(hastaParseado)
    val productosFiltro = (ventas.map { it.productoId to it.productoNombre } + productos.map { it.id to it.nombre })
        .distinctBy { it.first }
    val lugaresFiltro = (ferias.map { it.id to it.nombre } + ventas.mapNotNull { venta ->
        venta.feriaId?.let { id -> id to (venta.lugarTexto ?: ferias.firstOrNull { feria -> feria.id == id }?.nombre ?: "Feria anterior") }
    } + ventas.mapNotNull { venta -> venta.lugarTexto?.let { it to it } }).distinctBy { it.first }
    Box(Modifier.fillMaxSize()) {
        BotanicalBackdrop()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp, vertical=8.dp)) {
        ProductWizardTopBar(onBack=alVolver)
        Text("ESPACIO DE EMPRENDIMIENTO", fontSize=12.sp, fontWeight=FontWeight.Bold, color=Wine)
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
        if (errorCarga != null || !historialConfiguracionCargada) {
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFFF3E8)), shape=RoundedCornerShape(18.dp), modifier=Modifier.fillMaxWidth().padding(top=10.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(
                        errorCarga ?: if (cargando) "Cargando la configuración y el historial…" else "No se pudo cargar la configuración de ventas.",
                        color=Dark
                    )
                    if (!cargando) OutlinedButton(onClick=alReintentarCarga, modifier=Modifier.fillMaxWidth()) { Text("Reintentar carga", color=Wine) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (seccion == "Registrar") {
            Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp,Line), modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Registrar una venta", color=Dark, fontWeight=FontWeight.Bold, fontSize=19.sp)
                    if (productos.isEmpty()) {
                        Text("Todavía no tienes productos para vender. Agrégalos desde Productos para registrar una venta.", color=Muted)
                    } else {
                        SelectorVentas("Producto", productos.map { it.id to it.nombre }, producto?.id.orEmpty(), enabled=!enviando) { productoId=it; varianteId="" }
                    }
                    if (producto?.tieneVariantes == true && variantes.isNotEmpty()) SelectorVentas("Variante", variantes.map { it.id to it.opciones.values.joinToString(" · ") }, varianteId, enabled=!enviando) { varianteId=it }
                    else if (producto?.tieneVariantes == true) Text("Este producto no tiene variantes activas. Activa o agrega una desde Productos.", color=Wine, fontSize=13.sp)
                    val variante = variantes.firstOrNull { it.id == varianteId }
                    val precio = variante?.precio ?: producto?.precioBase ?: 0L
                    Text("Precio unitario: ${pesosVenta(precio)}", color=Wine, fontWeight=FontWeight.Bold)
                    OutlinedTextField(cantidad, { cantidad=it.filter(Char::isDigit).take(5) }, label={Text("Cantidad")}, placeholder={Text("Ej.: 2", color=Muted)}, modifier=Modifier.fillMaxWidth(), enabled=!enviando)
                    val cantidadSolicitada = cantidad.toIntOrNull()
                    val stockDisponible = if (producto?.tieneVariantes == true) variante?.stock ?: 0 else producto?.stock
                    val stockInsuficiente = producto != null && producto.manejaStock && cantidadSolicitada != null && stockDisponible != null && cantidadSolicitada > stockDisponible
                    if (stockInsuficiente) {
                        Text("Stock insuficiente. Hay $stockDisponible unidades disponibles para esta selección.", color=Wine, fontSize=13.sp)
                    }
                    if (historialActivo) {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            SelectableChip("Feria", lugarTipo == "feria", enabled=!enviando) { lugarTipo="feria" }
                            SelectableChip("Otro lugar", lugarTipo == "otro", enabled=!enviando) { lugarTipo="otro" }
                        }
                        if (lugarTipo == "feria") {
                            if (ferias.isEmpty()) Text("No hay ferias publicadas disponibles. Elige «Otro lugar» para registrar dónde fue la venta.", color=Muted, fontSize=13.sp)
                            SelectorVentas("Feria", ferias.map { it.id to it.nombre }, feriaId, enabled=!enviando && ferias.isNotEmpty()) { feriaId=it }
                        } else OutlinedTextField(lugarOtro, { lugarOtro=it.take(200) }, label={Text("Lugar")}, placeholder={Text("Ej.: Feria Plaza Ñuñoa", color=Muted)}, modifier=Modifier.fillMaxWidth(), enabled=!enviando)
                    }
                    val puedeRegistrarVenta = if (producto == null) false else {
                        val seleccionValida = !producto.tieneVariantes || varianteId.isNotBlank()
                        val cantidadValida = cantidadSolicitada != null && cantidadSolicitada > 0
                        val stockValido = !producto.manejaStock || (stockDisponible ?: 0) >= (cantidadSolicitada ?: Int.MAX_VALUE)
                        val lugarValido = !historialActivo || if (lugarTipo == "feria") feriaId.isNotBlank() else lugarOtro.isNotBlank()
                        !enviando && historialConfiguracionCargada && seleccionValida && cantidadValida && stockValido && lugarValido
                    }
                    Button(
                        onClick={
                            val fechaOperacion = fechaVenta ?: java.time.Instant.now().toString().also { fechaVenta=it }
                            if (producto != null) alRegistrar(producto, if (producto.tieneVariantes) varianteId else null, cantidad.toIntOrNull() ?: 0, lugarTipo, feriaId.takeIf { lugarTipo == "feria" }, lugarOtro.takeIf { lugarTipo == "otro" }, operacionId, fechaOperacion) {
                                operacionId=java.util.UUID.randomUUID().toString()
                            }
                        },
                        enabled=puedeRegistrarVenta,
                        colors=ButtonDefaults.buttonColors(containerColor=Wine), modifier=Modifier.fillMaxWidth().height(52.dp)
                    ) { Text(if (enviando) "Guardando…" else "Guardar venta", color=Color.White, fontWeight=FontWeight.Bold) }
                }
            }
        } else {
            Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp,Line), modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Filtrar historial", color=Dark, fontWeight=FontWeight.Bold)
                    OutlinedTextField(desde, {desde=it.take(10)}, label={Text("Desde")}, placeholder={Text("Ej.: 2025-03-01", color=Muted)}, modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(hasta, {hasta=it.take(10)}, label={Text("Hasta")}, placeholder={Text("Ej.: 2025-03-31", color=Muted)}, modifier=Modifier.fillMaxWidth())
                    if (desdeInvalido || hastaInvalido) Text("Revisa las fechas: usa el formato AAAA-MM-DD.", color=Wine, fontSize=13.sp)
                    else if (rangoInvalido) Text("La fecha «Desde» debe ser anterior o igual a «Hasta».", color=Wine, fontSize=13.sp)
                    SelectorVentas("Producto", listOf("" to "Todos los productos") + productosFiltro, filtroProducto) { filtroProducto=it }
                    SelectorVentas("Feria o lugar", listOf("" to "Todos los lugares") + lugaresFiltro, filtroLugar) { filtroLugar=it }
                    Button(
                        onClick = { filtrosAplicados = FiltrosVentas(desde, hasta, filtroProducto, filtroLugar) },
                        enabled = !enviando && !desdeInvalido && !hastaInvalido && !rangoInvalido,
                        colors = ButtonDefaults.buttonColors(containerColor = Wine),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Aplicar filtros", color = Color.White, fontWeight = FontWeight.Bold) }
                    Text("${ventasFiltradas.size} ${if (ventasFiltradas.size == 1) "venta encontrada" else "ventas encontradas"}", color=Muted)
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
            if (!cargando && ventasFiltradas.isEmpty()) {
                Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(18.dp), modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {
                    Text(if (ventas.isEmpty()) "Todavía no hay ventas en el historial." else "No hay ventas que coincidan con estos filtros.", color=Muted, modifier=Modifier.padding(16.dp))
                }
            }
        }
        error?.let { Text(it, color=Wine, modifier=Modifier.padding(top=8.dp)) }
        if (cargando) Text("Cargando información…", color=Muted, modifier=Modifier.padding(top=10.dp))
        }
    }
}

@Composable
private fun SelectorVentas(titulo: String, opciones: List<Pair<String,String>>, seleccionado: String, enabled: Boolean = true, alElegir: (String)->Unit) {
    var expandido by remember(titulo) { mutableStateOf(false) }
    val actual = opciones.firstOrNull { it.first == seleccionado }?.second ?: "Seleccionar"
    Column {
        Text(titulo, color=Dark, fontSize=13.sp, fontWeight=FontWeight.Bold)
        OutlinedButton(onClick={expandido=true}, enabled=enabled, modifier=Modifier.fillMaxWidth().height(50.dp), shape=RoundedCornerShape(14.dp)) { Text(actual, color=Wine) }
        DropdownMenu(expanded=expandido, onDismissRequest={expandido=false}) {
            opciones.forEach { (id,nombre) -> DropdownMenuItem(text={Text(nombre)}, enabled=enabled, onClick={expandido=false; alElegir(id)}) }
        }
    }
}

private fun pesosVenta(valor: Long) = "$" + NumberFormat.getNumberInstance(Locale("es", "CL")).apply { maximumFractionDigits=0 }.format(valor)
