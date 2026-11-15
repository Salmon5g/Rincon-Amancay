package com.example.amancay.buyer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.amancay.data.TiendaCatalogoComprador

@Composable
internal fun DirectorioComprador(
    tiendas: List<TiendaCatalogoComprador>,
    tipos: Map<String, String>,
    sectores: Map<String, String>,
    cargando: Boolean,
    error: String?,
    alCerrar: () -> Unit,
    alAbrir: (String) -> Unit
) {
    var texto by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("") }
    val visibles = tiendas.filter {
        it.nombre.contains(texto.trim(), ignoreCase = true) &&
            (tipo.isEmpty() || it.tipoId == tipo) && (sector.isEmpty() || it.sectorId == sector)
    }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Directorio de emprendimientos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text("Buscar emprendimiento") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FiltroDirectorio("Tipo de emprendimiento", tipos, tipo) { tipo = it }
                FiltroDirectorio("Sector", sectores, sector) { sector = it }
                if (visibles.isEmpty()) Text(error ?: if (cargando) "Cargando directorio…" else "No hay emprendimientos que coincidan.")
                visibles.forEach { tienda ->
                    TextButton(onClick = { alAbrir(tienda.id) }) {
                        Column {
                            Text(tienda.nombre)
                            Text(listOfNotNull(tipos[tienda.tipoId], sectores[tienda.sectorId]).joinToString(" · "))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } }
    )
}

@Composable
private fun FiltroDirectorio(
    etiqueta: String,
    opciones: Map<String, String>,
    seleccionado: String,
    alSeleccionar: (String) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        TextButton(onClick = { abierto = true }) { Text("$etiqueta: ${opciones[seleccionado] ?: "Todos"}") }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = { abierto = false; alSeleccionar("") })
            opciones.entries.sortedBy { it.value.lowercase() }.forEach { (id, nombre) ->
                DropdownMenuItem(text = { Text(nombre) }, onClick = { abierto = false; alSeleccionar(id) })
            }
        }
    }
}
