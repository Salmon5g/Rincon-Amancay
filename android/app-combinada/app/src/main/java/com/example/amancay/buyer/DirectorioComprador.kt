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
import com.example.amancay.data.AsociacionCatalogoComprador

@Composable
internal fun DirectorioComprador(
    tiendas: List<TiendaCatalogoComprador>,
    asociaciones: List<AsociacionCatalogoComprador>,
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
    var asociacionFiltro by remember { mutableStateOf("") }
    var asociacionAbierta by remember { mutableStateOf<String?>(null) }
    val asociacionActual = asociaciones.firstOrNull { it.id == asociacionAbierta }
    val tiendasDeAsociacion = asociaciones.firstOrNull { it.id == asociacionFiltro }
        ?.integrantes?.map { it.tiendaId }?.toSet()
    val visibles = tiendas.filter {
        it.nombre.contains(texto.trim(), ignoreCase = true) &&
            (tipo.isEmpty() || it.tipoId == tipo) && (sector.isEmpty() || it.sectorId == sector) &&
            (tiendasDeAsociacion == null || it.id in tiendasDeAsociacion)
    }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(asociacionActual?.nombre ?: "Directorio de emprendimientos y asociaciones") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (asociacionActual != null) {
                    TextButton(onClick = { asociacionAbierta = null }) { Text("← Volver a los directorios") }
                    if (asociacionActual.descripcion.isNotBlank()) Text(asociacionActual.descripcion)
                    Text("Integrantes", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    val integrantesVisibles = asociacionActual.integrantes.mapNotNull { integrante ->
                        tiendas.firstOrNull { it.id == integrante.tiendaId }?.let { it to integrante }
                    }
                    if (integrantesVisibles.isEmpty()) {
                        Text("Esta asociación aún no tiene tiendas publicadas entre sus integrantes.")
                    }
                    integrantesVisibles.forEach { (tienda, integrante) ->
                        TextButton(onClick = { alAbrir(tienda.id) }) {
                            Column {
                                Text(integrante.nombre.ifBlank { tienda.nombre })
                                Text(listOfNotNull(tipos[tienda.tipoId], sectores[tienda.sectorId]).joinToString(" · "))
                            }
                        }
                    }
                    return@Column
                }
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text("Buscar emprendimiento") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FiltroDirectorio("Tipo de emprendimiento", tipos, tipo) { tipo = it }
                FiltroDirectorio("Sector", sectores, sector) { sector = it }
                FiltroDirectorio("Asociación", asociaciones.associate { it.id to it.nombre }, asociacionFiltro) { asociacionFiltro = it }
                if (asociaciones.isNotEmpty()) {
                    Text("Asociaciones", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    asociaciones.forEach { asociacion ->
                        TextButton(onClick = { asociacionAbierta = asociacion.id }) {
                            Column {
                                Text(asociacion.nombre)
                                Text("${asociacion.integrantes.size} tiendas integrantes")
                            }
                        }
                    }
                }
                Text("Emprendimientos", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
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
