package com.example.amancay.buyer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.amancay.data.TiendaCatalogoComprador

@Composable
internal fun FormularioContactoComprador(
    tienda: TiendaCatalogoComprador,
    producto: String?,
    alCerrar: () -> Unit,
    alEnviar: (nombre: String, correo: String, mensaje: String) -> Unit
) {
    var nombre by remember(tienda.id, producto) { mutableStateOf("") }
    var correo by remember(tienda.id, producto) { mutableStateOf("") }
    var mensaje by remember(tienda.id, producto) { mutableStateOf("") }
    val correoValido = correo.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
    val habilitado = nombre.trim().length in 2..100 && correoValido && mensaje.trim().length in 5..2000

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Escribir a ${tienda.nombre}") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(if (producto.isNullOrBlank()) "Completa tus datos. La consulta llegará a la bandeja privada de la emprendedora." else "Consulta por «$producto». La emprendedora recibirá tu mensaje en su bandeja privada.")
                OutlinedTextField(nombre, { if (it.length <= 100) nombre = it }, label = { Text("Tu nombre") }, singleLine = true, supportingText = { if (nombre.isNotBlank() && nombre.trim().length < 2) Text("Escribe al menos 2 caracteres.") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(correo, { if (it.length <= 254) correo = it }, label = { Text("Tu correo") }, singleLine = true, isError = correo.isNotBlank() && !correoValido, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(mensaje, { if (it.length <= 2000) mensaje = it }, label = { Text("Mensaje") }, minLines = 3, maxLines = 6, supportingText = { Text("${mensaje.length}/2000") }, modifier = Modifier.fillMaxWidth())
                if (correo.isNotBlank() && !correoValido) Text("Escribe un correo válido.", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                if (mensaje.isNotBlank() && mensaje.trim().length < 5) Text("El mensaje debe tener al menos 5 caracteres.", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { alEnviar(nombre.trim(), correo.trim(), mensaje.trim()) }, enabled = habilitado) { Text("Enviar consulta") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
        containerColor = AmancaySuperficie
    )
}
