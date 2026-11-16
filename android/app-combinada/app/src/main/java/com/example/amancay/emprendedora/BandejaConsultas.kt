package com.example.amancay.emprendedora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.amancay.data.ConsultaComprador
import java.text.DateFormat

private val Vino = Color(0xFF741342)
private val Coral = Color(0xFFE95A54)
private val Crema = Color(0xFFFFFAF4)
private val Rosa = Color(0xFFFBEAEC)
private val Suave = Color(0xFF806B79)

@Composable
internal fun BandejaConsultasScreen(
    consultas: List<ConsultaComprador>,
    error: String?,
    alMarcarLeida: (ConsultaComprador) -> Unit,
    alResponder: (ConsultaComprador, String) -> Unit,
    alVolver: () -> Unit
) {
    var busqueda by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("Todas") }
    var seleccionada by remember { mutableStateOf<String?>(null) }
    val detalle = consultas.firstOrNull { it.id == seleccionada }
    var respuesta by remember(seleccionada, detalle?.respuesta) { mutableStateOf(detalle?.respuesta.orEmpty()) }
    val nuevas = consultas.count { it.estado == "nueva" }
    val filtradas = consultas.filter { consulta ->
        val coincideFiltro = when (filtro) {
            "No leídas" -> consulta.estado == "nueva"
            "Respondidas" -> consulta.respuesta != null
            else -> true
        }
        coincideFiltro && (busqueda.isBlank() || listOf(consulta.nombre, consulta.correo, consulta.producto.orEmpty())
            .any { it.contains(busqueda.trim(), ignoreCase = true) })
    }

    Column(Modifier.fillMaxSize().background(Crema).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { if (detalle != null) seleccionada = null else alVolver() }) { Text("← Volver", color = Vino) }
            Text("Rincón Amancay", color = Vino, fontWeight = FontWeight.Bold)
            Text("?", color = Vino, fontWeight = FontWeight.Bold)
        }
        if (detalle == null) {
            Text("Consultas", style = MaterialTheme.typography.headlineLarge, color = Vino, fontWeight = FontWeight.Bold)
            Text("Mensajes que llegaron desde tu tienda", color = Suave, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(18.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Rosa), shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(color = Color(0xFFF8D7D6), shape = CircleShape) { Text("●", Modifier.padding(18.dp), color = Coral) }
                    Text("$nuevas", color = Coral, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Column {
                        Text(if (nuevas == 1) "consulta nueva" else "consultas nuevas", color = Vino, fontWeight = FontWeight.Bold)
                        Text("Responde para seguir conectando con más personas.", color = Suave)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(busqueda, { busqueda = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Buscar por nombre o producto") }, shape = RoundedCornerShape(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                listOf("Todas", "No leídas", "Respondidas").forEach { opcion ->
                    Surface(onClick = { filtro = opcion }, color = if (filtro == opcion) Vino else Rosa, shape = RoundedCornerShape(24.dp)) {
                        Text(opcion, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = if (filtro == opcion) Color.White else Vino, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (error != null) Text("No pudimos cargar la bandeja. Comprueba tu conexión e inténtalo de nuevo.", color = MaterialTheme.colorScheme.error)
            if (filtradas.isEmpty()) {
                Surface(Modifier.fillMaxWidth().weight(1f), color = Color.Transparent, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFFDCCBD3))) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Aquí aparecerán tus respuestas", color = Vino, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Cuando respondas una consulta, la verás aquí.", color = Suave)
                    }
                }
            } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtradas, key = { it.id }) { consulta ->
                    Card(Modifier.fillMaxWidth().clickable { seleccionada = consulta.id; if (consulta.estado == "nueva") alMarcarLeida(consulta) },
                        shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF1E5E7))) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(consulta.nombre, color = Vino, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(if (consulta.respuesta != null) "Respondida" else if (consulta.estado == "nueva") "Nueva" else "Leída",
                                    color = if (consulta.estado == "nueva") Coral else Vino, fontWeight = FontWeight.SemiBold)
                            }
                            Text(consulta.correo, color = Suave)
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(consulta.fecha), color = Suave)
                            consulta.producto?.let { Text("Consulta por: $it", Modifier.fillMaxWidth().padding(8.dp), color = Vino, fontWeight = FontWeight.SemiBold) }
                            Text(consulta.mensaje, color = Vino, maxLines = 2)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Detalle de consulta", style = MaterialTheme.typography.headlineMedium, color = Vino, fontWeight = FontWeight.Bold)
                Text("Revisa el mensaje y responde a tu clienta.", color = Suave)
                Spacer(Modifier.height(4.dp))
                TarjetaDetalle("${detalle.nombre}\n${detalle.correo}\n${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(detalle.fecha)}")
                detalle.producto?.let { TarjetaDetalle("Producto consultado\n$it") }
                TarjetaDetalle("Mensaje de la clienta\n${detalle.mensaje}")
                Text("Tu respuesta", color = Vino, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(respuesta, { if (it.length <= 2000) respuesta = it }, modifier = Modifier.fillMaxWidth(),
                    minLines = 4, maxLines = 7, placeholder = { Text("Escribe aquí la respuesta que verá en su app…") },
                    supportingText = { Text("${respuesta.length}/2000") }, shape = RoundedCornerShape(18.dp))
                Button(onClick = { alResponder(detalle, respuesta.trim()) }, enabled = respuesta.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Vino),
                    shape = RoundedCornerShape(28.dp)) { Text("Enviar respuesta a la app compradora") }
                Text("La respuesta aparecerá en la sección Respuestas de la app compradora.", color = Suave)
                TextButton(onClick = { seleccionada = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Volver a consultas", color = Vino) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TarjetaDetalle(texto: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1E5E7))) {
        Text(texto, Modifier.padding(18.dp), color = Vino, style = MaterialTheme.typography.bodyLarge)
    }
}
