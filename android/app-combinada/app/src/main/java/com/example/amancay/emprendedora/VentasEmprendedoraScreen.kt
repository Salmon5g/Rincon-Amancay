package com.example.amancay.emprendedora

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.buyer.distanciaMetros
import com.example.amancay.buyer.formatoDistancia
import com.example.amancay.buyer.obtenerUbicacionActualNueva
import com.example.amancay.buyer.tienePermisoUbicacion
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

private val zonaHorariaVentas = ZoneId.of("America/Santiago")
private const val RADIO_DETECCION_FERIA_METROS = 500f
private data class FiltrosVentas(val desde: String = "", val hasta: String = "", val productoId: String = "", val lugarId: String = "")

private fun ubicacionDelDispositivoActiva(contexto: Context): Boolean {
    val servicio = contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return runCatching { servicio.isLocationEnabled }.getOrDefault(false)
}

@Composable
internal fun VentasEmprendedoraScreen(
    productos: List<ProductoEmprendedora>, ventas: List<VentaRegistrada>, ferias: List<FeriaParaVenta>, historialActivo: Boolean,
    historialConfiguracionCargada: Boolean,
    abrirHistorial: Boolean = false,
    cargando: Boolean, error: String?, errorCarga: String?, enviando: Boolean,
    cargandoHistorial: Boolean = false, errorHistorial: String? = null,
    cargandoFerias: Boolean = false, errorFerias: String? = null,
    alCambiarSeccionHistorial: (Boolean) -> Unit = {},
    alReintentarHistorial: () -> Unit = {},
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
    val contexto = LocalContext.current
    var permisoUbicacion by remember(contexto) { mutableStateOf(tienePermisoUbicacion(contexto)) }
    var ubicacionSistemaActiva by remember(contexto) { mutableStateOf(ubicacionDelDispositivoActiva(contexto)) }
    var ubicacionActual by remember { mutableStateOf<Location?>(null) }
    var buscandoUbicacion by remember { mutableStateOf(false) }
    var feriaPorUbicacionId by remember { mutableStateOf<String?>(null) }
    var intentoUbicacion by remember { mutableIntStateOf(0) }
    var permisoSolicitadoAutomaticamente by remember { mutableStateOf(false) }
    var mostrarDialogoActivarUbicacion by remember { mutableStateOf(false) }
    val pedirPermisoUbicacion = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permisoUbicacion = tienePermisoUbicacion(contexto)
        ubicacionSistemaActiva = ubicacionDelDispositivoActiva(contexto)
        if (permisoUbicacion) intentoUbicacion++
    }
    val abrirAjustesUbicacion = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permisoUbicacion = tienePermisoUbicacion(contexto)
        ubicacionSistemaActiva = ubicacionDelDispositivoActiva(contexto)
        if (permisoUbicacion && ubicacionSistemaActiva) intentoUbicacion++
    }
    val producto = productos.firstOrNull { it.id == productoId } ?: productos.firstOrNull()
    val variantes = producto?.variantes?.filter { it.activa }.orEmpty()
    val hoyEnChile = LocalDate.now(zonaHorariaVentas)
    val feriasDeHoy = ferias.filter { feria ->
        val inicio = feria.fechaInicio.toInstant().atZone(zonaHorariaVentas).toLocalDate()
        val termino = feria.fechaFin.toInstant().atZone(zonaHorariaVentas).toLocalDate()
        !hoyEnChile.isBefore(inicio) && !hoyEnChile.isAfter(termino)
    }.sortedBy { it.fechaInicio }
    val feriasConUbicacion = remember(ferias) {
        ferias.filter { feria ->
            feria.latitud?.let { it.isFinite() && it in -90.0..90.0 } == true &&
                feria.longitud?.let { it.isFinite() && it in -180.0..180.0 } == true
        }
    }
    val feriaConfirmadaConUbicacionHoy = feriasDeHoy.any { feria ->
        feria.estadoParticipacion == "confirmada" && feria in feriasConUbicacion
    }
    LaunchedEffect(seccion, historialActivo, permisoUbicacion, ubicacionSistemaActiva, intentoUbicacion, feriasConUbicacion, feriaConfirmadaConUbicacionHoy) {
        if (seccion != "Registrar" || !historialActivo || !feriaConfirmadaConUbicacionHoy) {
            ubicacionActual = null
            feriaPorUbicacionId = null
            return@LaunchedEffect
        }
        if (!permisoUbicacion) {
            ubicacionActual = null
            feriaPorUbicacionId = null
            if (!permisoSolicitadoAutomaticamente) {
                permisoSolicitadoAutomaticamente = true
                pedirPermisoUbicacion.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            return@LaunchedEffect
        }
        if (!ubicacionSistemaActiva) {
            ubicacionActual = null
            feriaPorUbicacionId = null
            mostrarDialogoActivarUbicacion = true
            return@LaunchedEffect
        }
        buscandoUbicacion = true
        try {
            val posicion = obtenerUbicacionActualNueva(contexto)
            ubicacionActual = posicion
            val ahora = System.currentTimeMillis()
            feriaPorUbicacionId = posicion?.let { ubicacion ->
                val edadMs = ahora - ubicacion.time
                if (edadMs !in 0..60_000 || !ubicacion.hasAccuracy() || ubicacion.accuracy >= RADIO_DETECCION_FERIA_METROS) {
                    null
                } else {
                    feriasConUbicacion.asSequence()
                        .filter { feria ->
                            feria.estadoParticipacion == "confirmada" &&
                                feria.fechaInicio.time <= ahora && ahora <= feria.fechaFin.time
                        }
                        .mapNotNull { feria ->
                            val latitud = feria.latitud ?: return@mapNotNull null
                            val longitud = feria.longitud ?: return@mapNotNull null
                            val distancia = distanciaMetros(ubicacion.latitude to ubicacion.longitude, latitud, longitud)
                            if (distancia + ubicacion.accuracy <= RADIO_DETECCION_FERIA_METROS) feria to distancia else null
                        }
                        .minByOrNull { it.second }
                        ?.first
                        ?.id
                }
            }
        } catch (_: SecurityException) {
            ubicacionActual = null
            feriaPorUbicacionId = null
        } finally {
            buscandoUbicacion = false
        }
    }
    val feriaSugeridaPorUbicacion = feriasDeHoy.firstOrNull { it.id == feriaPorUbicacionId }
    val feriaSugerida = feriaSugeridaPorUbicacion ?: feriasDeHoy.singleOrNull()
    val distanciaAferiaSugerida = feriaSugeridaPorUbicacion?.let { feria ->
        val ubicacion = ubicacionActual
        val latitud = feria.latitud
        val longitud = feria.longitud
        if (ubicacion != null && latitud != null && longitud != null) {
            distanciaMetros(ubicacion.latitude to ubicacion.longitude, latitud, longitud)
        } else null
    }
    fun reintentarDeteccionUbicacion() {
        permisoUbicacion = tienePermisoUbicacion(contexto)
        ubicacionSistemaActiva = ubicacionDelDispositivoActiva(contexto)
        when {
            !permisoUbicacion -> pedirPermisoUbicacion.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            !ubicacionSistemaActiva -> mostrarDialogoActivarUbicacion = true
            else -> intentoUbicacion++
        }
    }
    val feriaSeleccionadaId = feriaId.takeIf { id -> feriasDeHoy.any { it.id == id } }
        ?: feriaSugerida?.id.orEmpty()
    val lugarTipoVenta = if (lugarTipo == "feria" && feriasDeHoy.isEmpty() && historialConfiguracionCargada && !cargandoFerias && errorFerias == null) "otro" else lugarTipo
    if (producto != null && producto.id != productoId) productoId = producto.id
    if (variantes.none { it.id == varianteId }) varianteId = variantes.firstOrNull()?.id.orEmpty()
    val ventasFiltradas = ventas.filter { venta ->
        val fechaVenta = venta.fecha.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val desdeOk = filtrosAplicados.desde.isBlank() || runCatching { !fechaVenta.isBefore(LocalDate.parse(filtrosAplicados.desde)) }.getOrDefault(false)
        val hastaOk = filtrosAplicados.hasta.isBlank() || runCatching { !fechaVenta.isAfter(LocalDate.parse(filtrosAplicados.hasta)) }.getOrDefault(false)
        (filtrosAplicados.productoId.isBlank() || venta.productoId == filtrosAplicados.productoId) &&
            (filtrosAplicados.lugarId.isBlank() || venta.feriaId == filtrosAplicados.lugarId ||
                (venta.lugarTipo != "feria" && venta.lugarTexto == filtrosAplicados.lugarId)) && desdeOk && hastaOk
    }
    val desdeInvalido = desde.isNotBlank() && runCatching { LocalDate.parse(desde) }.isFailure
    val hastaInvalido = hasta.isNotBlank() && runCatching { LocalDate.parse(hasta) }.isFailure
    val desdeParseado = desde.takeIf { it.isNotBlank() && !desdeInvalido }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val hastaParseado = hasta.takeIf { it.isNotBlank() && !hastaInvalido }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val rangoInvalido = desdeParseado != null && hastaParseado != null && desdeParseado.isAfter(hastaParseado)
    val productosFiltro = (ventas.map { it.productoId to it.productoNombre } + productos.map { it.id to it.nombre })
        .distinctBy { it.first }
    val lugaresFiltro = (ferias.map { it.id to it.nombre } + ventas.mapNotNull { venta ->
        venta.feriaId?.let { id -> id to (ferias.firstOrNull { feria -> feria.id == id }?.nombre ?: venta.feriaNombre ?: "Feria anterior") }
    } + ventas.mapNotNull { venta -> venta.lugarTexto?.takeIf { venta.lugarTipo != "feria" }?.let { it to it } }).distinctBy { it.first }
    fun nombreLugarVenta(venta: VentaRegistrada): String = if (venta.lugarTipo == "feria") {
        venta.feriaId?.let { id -> ferias.firstOrNull { it.id == id }?.nombre }
            ?: venta.feriaNombre
            ?: venta.lugarTexto
            ?: "Feria"
    } else venta.lugarTexto.orEmpty()
    Box(Modifier.fillMaxSize()) {
        BotanicalBackdrop()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp, vertical=8.dp)) {
        ProductWizardTopBar(onBack=alVolver)
        Text("ESPACIO DE EMPRENDIMIENTO", fontSize=12.sp, fontWeight=FontWeight.Bold, color=Wine)
        Text(if (seccion == "Historial") "Historial de encargos" else "Registrar una venta", fontSize=31.sp, color=Dark, fontFamily=FontFamily.Serif)
        Text(if (seccion == "Historial") "Aquí puedes ver tus ventas y encargos realizados." else "Registra una venta y actualiza el stock de tu catálogo.", fontSize=15.sp, color=Muted)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SelectableChip("Registrar venta", seccion == "Registrar") {
                seccion = "Registrar"
                alCambiarSeccionHistorial(false)
            }
            SelectableChip("Historial", seccion == "Historial") {
                seccion = "Historial"
                alCambiarSeccionHistorial(true)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors=CardDefaults.cardColors(containerColor=if (historialActivo) Color(0xFFE2F4E4) else Color(0xFFF0E9EC)), shape=RoundedCornerShape(24.dp), modifier=Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    if (historialActivo) "✓  Historial activado" else "Historial desactivado",
                    color = if (historialActivo) Color(0xFF17643B) else Dark,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (!historialActivo) Text("El stock igual se actualiza", color = Dark, fontSize = 12.sp, textAlign = TextAlign.Center)
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
                        feriaSugeridaPorUbicacion?.let { feria ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F4E4)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("Feria en curso detectada", color = Color(0xFF17643B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(feria.nombre, color = Dark, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "La seleccionamos como lugar de la venta porque estás dentro del recinto.",
                                        color = Muted,
                                        fontSize = 13.sp
                                    )
                                    distanciaAferiaSugerida?.let { distancia ->
                                        Text("Distancia al punto: ${formatoDistancia(distancia)}", color = Muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        if (feriaConfirmadaConUbicacionHoy) {
                            when {
                                buscandoUbicacion -> Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Wine)
                                    Text("Comprobando si estás en una feria en curso…", color = Muted, fontSize = 12.sp)
                                }
                                !permisoUbicacion -> TextButton(onClick = { reintentarDeteccionUbicacion() }) {
                                    Text("Permitir ubicación para sugerir la feria en curso", color = Wine, fontSize = 13.sp)
                                }
                                !ubicacionSistemaActiva -> TextButton(onClick = { reintentarDeteccionUbicacion() }) {
                                    Text("Activar ubicación del dispositivo", color = Wine, fontSize = 13.sp)
                                }
                                feriaPorUbicacionId == null -> TextButton(onClick = { reintentarDeteccionUbicacion() }) {
                                    Text(
                                        if (ubicacionActual == null) "No pudimos obtener tu ubicación. Reintentar"
                                        else "No detectamos una feria cercana. Actualizar ubicación",
                                        color = Wine,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            SelectableChip("Feria", lugarTipoVenta == "feria", enabled=!enviando && feriasDeHoy.isNotEmpty()) { lugarTipo="feria" }
                            SelectableChip("Otro lugar", lugarTipoVenta == "otro", enabled=!enviando) { lugarTipo="otro" }
                        }
                        when {
                            errorFerias != null -> {
                                Text("No se pudieron consultar tus ferias. Elige «Otro lugar» o vuelve a cargar la información.", color=Wine, fontSize=13.sp)
                                OutlinedButton(onClick=alReintentarCarga, enabled=!cargando && !cargandoFerias, modifier=Modifier.fillMaxWidth()) {
                                    Text("Reintentar ferias", color=Wine)
                                }
                            }
                            cargandoFerias -> Text("Cargando tus ferias…", color=Muted, fontSize=13.sp)
                            feriasDeHoy.isEmpty() -> Text("No tienes una feria confirmada para hoy. Puedes registrar el lugar manualmente.", color=Muted, fontSize=13.sp)
                            lugarTipoVenta == "feria" -> {
                                Text(
                                    if (feriaSugeridaPorUbicacion != null) "Feria en curso sugerida por ubicación: ${feriaSugeridaPorUbicacion.nombre}"
                                    else if (feriaSugerida != null) "Feria sugerida para hoy: ${feriaSugerida.nombre}"
                                    else "Tienes ${feriasDeHoy.size} ferias confirmadas para hoy. Selecciona dónde fue la venta.",
                                    color=Wine, fontWeight=FontWeight.Bold, fontSize=14.sp
                                )
                                SelectorVentas("Feria de hoy", feriasDeHoy.map { it.id to it.nombre }, feriaSeleccionadaId, enabled=!enviando) { feriaId=it }
                            }
                        }
                        if (lugarTipoVenta == "otro") OutlinedTextField(lugarOtro, { lugarOtro=it.take(200) }, label={Text("Lugar")}, placeholder={Text("Ej.: Feria Plaza Ñuñoa", color=Muted)}, modifier=Modifier.fillMaxWidth(), enabled=!enviando)
                    }
                    val puedeRegistrarVenta = if (producto == null) false else {
                        val seleccionValida = !producto.tieneVariantes || varianteId.isNotBlank()
                        val cantidadValida = cantidadSolicitada != null && cantidadSolicitada > 0
                        val stockValido = !producto.manejaStock || (stockDisponible ?: 0) >= (cantidadSolicitada ?: Int.MAX_VALUE)
                        val lugarValido = !historialActivo || if (lugarTipoVenta == "feria") feriaSeleccionadaId.isNotBlank() else lugarOtro.isNotBlank()
                        !enviando && historialConfiguracionCargada && seleccionValida && cantidadValida && stockValido && lugarValido
                    }
                    Button(
                        onClick={
                            val fechaOperacion = fechaVenta ?: java.time.Instant.now().toString().also { fechaVenta=it }
                            if (producto != null) alRegistrar(producto, if (producto.tieneVariantes) varianteId else null, cantidad.toIntOrNull() ?: 0, lugarTipoVenta, feriaSeleccionadaId.takeIf { lugarTipoVenta == "feria" }, lugarOtro.takeIf { lugarTipoVenta == "otro" }, operacionId, fechaOperacion) {
                                operacionId=java.util.UUID.randomUUID().toString()
                            }
                        },
                        enabled=puedeRegistrarVenta,
                        colors=ButtonDefaults.buttonColors(containerColor=Wine), modifier=Modifier.fillMaxWidth().height(52.dp)
                    ) { Text(if (enviando) "Guardando…" else "Guardar venta", color=Color.White, fontWeight=FontWeight.Bold) }
                }
            }
        } else {
            if (cargandoHistorial || errorHistorial != null) {
                Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFFFF3E8)), shape=RoundedCornerShape(18.dp), modifier=Modifier.fillMaxWidth().padding(top=10.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(errorHistorial ?: "Cargando el historial de ventas…", color=Dark)
                        if (errorHistorial != null) OutlinedButton(onClick=alReintentarHistorial, modifier=Modifier.fillMaxWidth()) {
                            Text("Reintentar carga", color=Wine)
                        }
                    }
                }
            }
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
                            Text("${SimpleDateFormat("d 'de' MMMM yyyy", Locale("es", "CL")).format(venta.fecha)} · ${nombreLugarVenta(venta)}", color=Muted, fontSize=13.sp)
                        }
                        Text(pesosVenta(venta.total), color=Wine, fontWeight=FontWeight.Bold)
                    }
                }
            }
            if (!cargandoHistorial && errorHistorial == null && ventasFiltradas.isEmpty()) {
                Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(18.dp), modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {
                    Text(if (ventas.isEmpty()) "Todavía no hay ventas en el historial." else "No hay ventas que coincidan con estos filtros.", color=Muted, modifier=Modifier.padding(16.dp))
                }
            }
        }
        error?.let { Text(it, color=Wine, modifier=Modifier.padding(top=8.dp)) }
        if (cargando) Text("Cargando información…", color=Muted, modifier=Modifier.padding(top=10.dp))
        }
        if (mostrarDialogoActivarUbicacion) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoActivarUbicacion = false },
                title = { Text("Activa la ubicación", color = Dark, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "El permiso de ubicación está concedido, pero la ubicación del teléfono está apagada. Actívala para comprobar si estás cerca de una feria en curso.",
                        color = Muted
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        mostrarDialogoActivarUbicacion = false
                        abrirAjustesUbicacion.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }) { Text("Abrir ajustes", color = Wine, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogoActivarUbicacion = false }) {
                        Text("Ahora no", color = Muted)
                    }
                },
                containerColor = Color(0xFFFFFAF4)
            )
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
