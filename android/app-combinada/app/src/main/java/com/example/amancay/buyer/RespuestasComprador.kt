package com.example.amancay.buyer

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class ConsultaSeguida(
    val tiendaId: String,
    val tienda: String,
    val operacionId: String,
    val correo: String,
    val producto: String?,
    val mensaje: String,
    val respuesta: String? = null
)

internal fun leerConsultasGuardadas(context: Context): List<ConsultaSeguida> = runCatching {
    val json = JSONArray(context.getSharedPreferences("consultas_comprador", Context.MODE_PRIVATE).getString("consultas", "[]"))
    (0 until json.length()).mapNotNull { i ->
        val item = json.optJSONObject(i) ?: return@mapNotNull null
        ConsultaSeguida(item.getString("tiendaId"), item.getString("tienda"), item.getString("id"), item.getString("correo"),
            item.optString("producto").takeIf(String::isNotBlank), item.getString("mensaje"), item.optString("respuesta").takeIf(String::isNotBlank))
    }
}.getOrDefault(emptyList())

internal fun guardarConsultas(context: Context, consultas: List<ConsultaSeguida>) {
    val json = JSONArray().apply {
        consultas.forEach { item -> put(JSONObject().apply {
            put("tiendaId", item.tiendaId); put("tienda", item.tienda); put("id", item.operacionId)
            put("correo", item.correo); put("producto", item.producto ?: ""); put("mensaje", item.mensaje)
            put("respuesta", item.respuesta ?: "")
        }) }
    }
    context.getSharedPreferences("consultas_comprador", Context.MODE_PRIVATE).edit().putString("consultas", json.toString()).apply()
}

@Composable
internal fun RespuestasCompradorScreen(
    consultas: List<ConsultaSeguida>,
    cargando: Boolean,
    error: String?,
    alActualizar: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text("Mis consultas", style = MaterialTheme.typography.headlineLarge, color = AmancayBordo, fontWeight = FontWeight.Bold)
        Text("Aquí podrás leer las respuestas de las emprendedoras.", color = AmancayTintaSuave)
        Spacer(Modifier.height(12.dp))
        Button(onClick = alActualizar, enabled = !cargando, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AmancayBordo), shape = RoundedCornerShape(24.dp)) {
            if (cargando) CircularProgressIndicator(modifier = Modifier.height(20.dp), color = AmancaySuperficie)
            else Text("Actualizar respuestas")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        if (consultas.isEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Todavía no has enviado consultas desde este dispositivo.", color = AmancayTintaSuave)
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            items(consultas, key = { it.operacionId }) { consulta ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = AmancaySuperficie)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(consulta.tienda, color = AmancayBordo, fontWeight = FontWeight.Bold)
                            Text(if (consulta.respuesta == null) "Pendiente" else "Respondida",
                                color = if (consulta.respuesta == null) AmancayTintaSuave else AmancayVerdeTexto, fontWeight = FontWeight.SemiBold)
                        }
                        consulta.producto?.let { Text("Producto: $it", color = AmancayBordo) }
                        Text("Tu consulta: ${consulta.mensaje}", color = AmancayTintaSuave)
                        consulta.respuesta?.let {
                            Text("Respuesta de la emprendedora", color = AmancayNaranjaIcono, fontWeight = FontWeight.Bold)
                            Text(it, color = AmancayTinta)
                        } ?: Text("Te avisaremos aquí cuando llegue una respuesta. Se consulta al actualizar.", color = AmancayTintaSuave)
                    }
                }
            }
        }
    }
}
