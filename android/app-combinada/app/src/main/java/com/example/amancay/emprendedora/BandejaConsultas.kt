package com.example.amancay.emprendedora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.data.ConsultaComprador
import com.example.amancay.ui.Fig
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.random.Random

private val Vino = Color(0xFF741342)
private val Coral = Color(0xFFE95A54)
private val Crema = Color(0xFFFFFAF4)
private val Rosa = Color(0xFFFBEAEC)
private val RosaClaro = Color(0xFFFFF1EB)
private val Borde = Color(0xFFF1D3C4)
private val Suave = Color(0xFF806B79)
private val Texto = Color(0xFF45383D)
private val FormatoFecha = SimpleDateFormat("d MMM yyyy · HH:mm", Locale("es", "CL"))

private data class GranoPapel(val x: Float, val y: Float, val radio: Float, val color: Color)
private data class FibraPapel(val x: Float, val y: Float, val largo: Float, val angulo: Float, val color: Color)

@Composable
internal fun BandejaConsultasScreen(
    consultas: List<ConsultaComprador>,
    error: String?,
    alMarcarLeida: (ConsultaComprador) -> Unit,
    alResponder: (ConsultaComprador, String) -> Unit,
    alVolver: () -> Unit,
    alAyuda: () -> Unit
) {
    var busqueda by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("Todas") }
    var seleccionada by remember { mutableStateOf<String?>(null) }
    val detalle = consultas.firstOrNull { it.id == seleccionada }
    var respuesta by remember(seleccionada, detalle?.respuesta) { mutableStateOf(detalle?.respuesta.orEmpty()) }
    val nuevas = consultas.count { it.estado == "nueva" && it.notificable }
    val filtradas = consultas.filter { consulta ->
        val coincideFiltro = when (filtro) {
            "No leídas" -> consulta.estado == "nueva"
            "Respondidas" -> consulta.respuesta != null
            else -> true
        }
        coincideFiltro && (busqueda.isBlank() || listOf(consulta.nombre, consulta.correo, consulta.producto.orEmpty())
            .any { it.contains(busqueda.trim(), ignoreCase = true) })
    }

    Box(Modifier.fillMaxSize().background(Crema)) {
        Image(
            painter = painterResource(Fig.FLORAL_DERECHA),
            contentDescription = null,
            contentScale = ContentScale.Fit,
                alpha = 0.74f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                    .offset(x = 62.dp, y = 10.dp)
                    .width(180.dp)
                    .height(257.dp)
        )

        TexturaPapel()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { CabeceraConsultas(alVolver = { if (detalle != null) seleccionada = null else alVolver() }, alAyuda = alAyuda) }

            if (detalle == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Consultas",
                            color = Vino,
                            fontFamily = FontFamily.Serif,
                            fontSize = 49.sp,
                            lineHeight = 54.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Mensajes que llegaron desde tu tienda",
                            color = Suave,
                            fontSize = 18.sp,
                            lineHeight = 24.sp
                        )
                    }
                }
                item { ResumenConsultas(nuevas) }
                item { BusquedaConsultas(busqueda = busqueda, alCambiar = { busqueda = it }) }
                item { FiltrosConsultas(seleccionado = filtro, alSeleccionar = { filtro = it }) }
                if (error != null) {
                    item {
                        Text(
                            "No pudimos cargar las consultas. Comprueba tu conexión e inténtalo de nuevo.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                if (filtradas.isEmpty()) {
                    item {
                        EstadoVacioConsultas(hayConsultas = consultas.isNotEmpty())
                    }
                } else {
                    items(filtradas, key = { it.id }) { consulta ->
                        TarjetaConsulta(
                            consulta = consulta,
                            alAbrir = {
                                seleccionada = consulta.id
                                if (consulta.estado == "nueva") alMarcarLeida(consulta)
                            }
                        )
                    }
                }
            } else {
                item { TituloDetalleConsulta(detalle.nombre) }
                item { TarjetaDatoConsulta("${detalle.nombre}\n${detalle.correo}\n${FormatoFecha.format(detalle.fecha)}") }
                detalle.producto?.let { producto -> item { TarjetaDatoConsulta("Consulta por: $producto") } }
                item { TarjetaDatoConsulta(detalle.mensaje) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Tu respuesta", color = Vino, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = respuesta,
                            onValueChange = { if (it.length <= 2000) respuesta = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 7,
                            placeholder = { Text("Escribe aquí la respuesta que verá en su app…") },
                            supportingText = { Text("${respuesta.length}/2000") },
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Coral,
                                unfocusedBorderColor = Borde,
                                focusedTextColor = Texto,
                                unfocusedTextColor = Texto,
                                cursorColor = Coral
                            )
                        )
                        Button(
                            onClick = { alResponder(detalle, respuesta.trim()) },
                            enabled = respuesta.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Vino),
                            shape = RoundedCornerShape(28.dp)
                        ) { Text("Enviar respuesta a la app compradora", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        Text("La respuesta aparecerá en la sección Respuestas de la app compradora.", color = Suave, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TexturaPapel() {
    val granos = remember {
        val azar = Random(8421)
        List(1_600) {
            GranoPapel(
                x = azar.nextFloat(),
                y = azar.nextFloat(),
                radio = azar.nextFloat() * 1.1f + 0.25f,
                color = if (azar.nextBoolean()) Color(0xFF8C6B5F).copy(alpha = 0.045f)
                else Color(0xFFD59878).copy(alpha = 0.055f)
            )
        }
    }
    val fibras = remember {
        val azar = Random(317)
        List(150) {
            FibraPapel(
                x = azar.nextFloat(),
                y = azar.nextFloat(),
                largo = azar.nextFloat() * 4.5f + 1.5f,
                angulo = azar.nextFloat() * 6.28318f,
                color = Color(0xFF9B776A).copy(alpha = 0.035f)
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        granos.forEach { grano ->
            drawCircle(
                color = grano.color,
                radius = grano.radio,
                center = Offset(grano.x * size.width, grano.y * size.height)
            )
        }
        fibras.forEach { fibra ->
            val inicio = Offset(fibra.x * size.width, fibra.y * size.height)
            drawLine(
                color = fibra.color,
                start = inicio,
                end = Offset(
                    inicio.x + kotlin.math.cos(fibra.angulo.toDouble()).toFloat() * fibra.largo,
                    inicio.y + kotlin.math.sin(fibra.angulo.toDouble()).toFloat() * fibra.largo
                ),
                strokeWidth = 0.55f
            )
        }
    }
}

@Composable
private fun CabeceraConsultas(alVolver: () -> Unit, alAyuda: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(70.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Crema).border(1.5.dp, Borde, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(Fig.LOGO), contentDescription = null, modifier = Modifier.size(45.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column {
                Text("Rincón", color = Suave, fontSize = 14.sp, lineHeight = 16.sp)
                Text("Amancay", color = Vino, fontFamily = FontFamily.Serif, fontSize = 21.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = alVolver, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Vino, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(5.dp))
                Text("Volver", color = Vino, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Surface(
                onClick = alAyuda,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = Rosa,
                contentColor = Vino
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.HelpOutline, contentDescription = "Ayuda", modifier = Modifier.size(25.dp))
                }
            }
        }
    }
}

@Composable
private fun ResumenConsultas(nuevas: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = RosaClaro),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(modifier = Modifier.size(68.dp), shape = CircleShape, color = Color(0xFFFFD9CE)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Coral, modifier = Modifier.size(38.dp))
                }
            }
            Text(nuevas.toString(), color = Coral, fontSize = 51.sp, lineHeight = 56.sp, fontWeight = FontWeight.Light)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (nuevas == 1) "consulta nueva" else "consultas nuevas",
                    color = Vino,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Responde para seguir conectando con más personas.",
                    color = Suave,
                    fontSize = 16.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
private fun BusquedaConsultas(busqueda: String, alCambiar: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.5.dp, Borde, RoundedCornerShape(22.dp))
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Coral, modifier = Modifier.size(27.dp))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = busqueda,
            onValueChange = alCambiar,
            modifier = Modifier.weight(1f),
            singleLine = true,
            cursorBrush = SolidColor(Coral),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Texto, fontSize = 17.sp),
            decorationBox = { campo ->
                if (busqueda.isEmpty()) Text("Buscar por nombre o producto", color = Suave, fontSize = 17.sp)
                campo()
            }
        )
    }
}

@Composable
private fun FiltrosConsultas(seleccionado: String, alSeleccionar: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("Todas", "No leídas", "Respondidas").forEach { opcion ->
            val activa = seleccionado == opcion
            Surface(
                onClick = { alSeleccionar(opcion) },
                shape = RoundedCornerShape(30.dp),
                color = if (activa) Vino else RosaClaro,
                modifier = Modifier.weight(1f)
            ) {
                Box(Modifier.height(50.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    Text(
                        opcion,
                        color = if (activa) Color.White else Vino,
                        fontSize = 14.sp,
                        fontWeight = if (activa) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TarjetaConsulta(consulta: ConsultaComprador, alAbrir: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = alAbrir),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, Borde),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    consulta.nombre,
                    modifier = Modifier.weight(1f),
                    color = Vino,
                    fontFamily = FontFamily.Serif,
                    fontSize = 29.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (consulta.respuesta != null) {
                    Surface(shape = RoundedCornerShape(24.dp), color = RosaClaro) {
                        Text("Respondida", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Coral, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (consulta.estado == "nueva") {
                    Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFFFE2D8)) {
                        Text("Nueva", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Coral, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = "Abrir consulta", tint = Vino, modifier = Modifier.padding(start = 3.dp).size(27.dp))
            }
            Text(consulta.correo, color = Suave, fontSize = 16.sp, lineHeight = 21.sp)
            Text(FormatoFecha.format(consulta.fecha), color = Suave, fontSize = 16.sp, lineHeight = 21.sp)
            consulta.producto?.let {
                Text("Consulta por: $it", color = Vino, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(consulta.mensaje, color = Texto, fontSize = 18.sp, lineHeight = 25.sp, maxLines = 3)
        }
    }
}

@Composable
private fun EstadoVacioConsultas(hayConsultas: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            if (hayConsultas) "No encontramos consultas" else "Aún no tienes consultas",
            color = Vino,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            if (hayConsultas) "Prueba otro filtro o cambia la búsqueda." else "Cuando alguien escriba desde tu tienda, aparecerá aquí.",
            color = Suave,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun TituloDetalleConsulta(nombre: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Detalle de consulta", color = Vino, fontFamily = FontFamily.Serif, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Text("Revisa el mensaje y responde a tu clienta.", color = Suave, fontSize = 17.sp)
        Spacer(Modifier.height(3.dp))
        Text(nombre, color = Vino, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TarjetaDatoConsulta(texto: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, Borde),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(texto, Modifier.padding(18.dp), color = Texto, fontSize = 17.sp, lineHeight = 25.sp)
    }
}
