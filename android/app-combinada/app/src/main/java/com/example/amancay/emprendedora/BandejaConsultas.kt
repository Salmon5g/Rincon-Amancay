package com.example.amancay.emprendedora

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.amancay.data.ConsultaComprador
import java.text.DateFormat

@Composable
internal fun BandejaConsultasScreen(
    consultas: List<ConsultaComprador>,
    error: String?,
    alMarcarLeida: (ConsultaComprador) -> Unit,
    alVolver: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Consultas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Mensajes que llegaron desde tu tienda", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = alVolver) { Text("Volver") }
        }
        Spacer(Modifier.height(12.dp))
        if (error != null) Text("No pudimos cargar la bandeja. Comprueba tu conexión e inténtalo de nuevo.", color = MaterialTheme.colorScheme.error)
        if (consultas.isEmpty()) {
            Text("Aún no tienes consultas. Cuando alguien te escriba desde el catálogo, aparecerá aquí.", style = MaterialTheme.typography.bodyLarge)
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(consultas, key = { it.id }) { consulta ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(consulta.nombre, fontWeight = FontWeight.Bold)
                            Text(if (consulta.leida) "Leída" else "Nueva", color = if (consulta.leida) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                        Text(consulta.correo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(consulta.fecha), style = MaterialTheme.typography.labelSmall)
                        consulta.producto?.let { Text("Consulta por: $it", fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.height(8.dp))
                        Text(consulta.mensaje, style = MaterialTheme.typography.bodyMedium)
                        if (!consulta.leida) {
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { alMarcarLeida(consulta) }, colors = ButtonDefaults.buttonColors()) { Text("Marcar como leída") }
                        }
                    }
                }
            }
        }
    }
}
