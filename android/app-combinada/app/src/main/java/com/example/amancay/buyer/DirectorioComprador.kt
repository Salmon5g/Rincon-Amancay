package com.example.amancay.buyer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    asociacionesIniciales: Boolean = false,
    alCerrar: () -> Unit,
    alAbrir: (String) -> Unit
) {
    var texto by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("") }
    var asociacionFiltro by remember { mutableStateOf("") }
    var pestana by remember(asociacionesIniciales) { mutableStateOf(asociacionesIniciales) }
    var asociacionAbierta by remember { mutableStateOf<String?>(null) }
    val asociacionActual = asociaciones.firstOrNull { it.id == asociacionAbierta }
    val idsTiendasAsociadas = asociaciones.firstOrNull { it.id == asociacionFiltro }
        ?.integrantes?.map { it.tiendaId }?.toSet()
    val visibles = tiendas.filter {
        it.nombre.contains(texto.trim(), ignoreCase = true) &&
            (tipo.isEmpty() || it.tipoId == tipo) && (sector.isEmpty() || it.sectorId == sector) &&
            (idsTiendasAsociadas == null || it.id in idsTiendasAsociadas)
    }
    val integrantes = asociacionActual?.integrantes.orEmpty().mapNotNull { integrante ->
        tiendas.firstOrNull { it.id == integrante.tiendaId }?.let { it to integrante }
    }

    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AmancayFondo,
            contentColor = AmancayTinta
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(Modifier.padding(top = 16.dp)) {
                        TextButton(onClick = { if (asociacionActual != null) asociacionAbierta = null else alCerrar() }) {
                            Text(if (asociacionActual != null) "← Volver a los directorios" else "← Volver", color = AmancayBordo)
                        }
                        Text("CONOCE NUESTRA COMUNIDAD", style = MaterialTheme.typography.labelLarge, color = AmancayNaranjaIcono, fontWeight = FontWeight.Bold)
                        Text(asociacionActual?.nombre ?: "Directorio", style = MaterialTheme.typography.headlineLarge, color = AmancayBordo, fontWeight = FontWeight.Bold)
                        Text(
                            if (asociacionActual != null) asociacionActual.descripcion.ifBlank { "Conecta con redes que impulsan el talento local." }
                            else "Descubre emprendimientos y asociaciones que dan vida a Rincón Amancay.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AmancayTintaSuave
                        )
                        if (asociacionActual != null) {
                            Image(
                                painter = painterResource(com.example.amancay.R.drawable.flor_inicio_comprador),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(118.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                    }
                }

                if (asociacionActual != null) {
                    item {
                        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = AmancaySuperficie), border = BorderStroke(1.dp, AmancayBorde)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(modifier = Modifier.size(54.dp), shape = CircleShape, color = AmancayChipRosa) {
                                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                            Text("♧", color = AmancayBordo, style = MaterialTheme.typography.headlineMedium)
                                        }
                                    }
                                    Column {
                                        Text("${integrantes.size}", color = AmancayBordo, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                        Text(if (integrantes.size == 1) "emprendimiento integrante" else "emprendimientos integrantes", color = AmancayTintaSuave)
                                    }
                                }
                                Text("Integrantes", style = MaterialTheme.typography.headlineSmall, color = AmancayBordo, fontWeight = FontWeight.Bold)
                                Text("Conoce sus productos y entra a sus tiendas.", color = AmancayTintaSuave)
                                com.example.amancay.ui.BotonCodigoQr(
                                    "Código QR de la asociación",
                                    asociacionActual.nombre,
                                    com.example.amancay.ui.EnlacesPublicos.asociacion(asociacionActual.id)
                                )
                                com.example.amancay.ui.BotonCompartirEnlace(
                                    "Compartir asociación",
                                    asociacionActual.nombre,
                                    com.example.amancay.ui.EnlacesPublicos.asociacion(asociacionActual.id)
                                )
                            }
                        }
                    }
                    if (integrantes.isEmpty()) item { Text(error ?: if (cargando) "Cargando integrantes…" else "Esta asociación aún no tiene emprendimientos publicados.", color = AmancayTintaSuave) }
                    items(integrantes, key = { it.first.id }) { (tienda, miembro) ->
                        TarjetaDirectorio(
                            titulo = miembro.nombre.ifBlank { tienda.nombre },
                            detalle = listOfNotNull(tipos[tienda.tipoId], sectores[tienda.sectorId]).joinToString(" · "),
                            accion = "Ver tienda",
                            alAbrir = { alAbrir(tienda.id) }
                        )
                    }
                } else {
                    item {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(AmancaySuperficie).padding(5.dp)) {
                            BotonPestanaDirectorio("Emprendimientos", !pestana, Modifier.weight(1f)) { pestana = false }
                            BotonPestanaDirectorio("Asociaciones", pestana, Modifier.weight(1f)) { pestana = true }
                        }
                    }
                    if (!pestana) {
                        item {
                            OutlinedTextField(texto, { texto = it }, label = { Text("Buscar por nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                        }
                        item {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                FiltroDirectorio("Tipo de emprendimiento", tipos, tipo) { tipo = it }
                                FiltroDirectorio("Sector", sectores, sector) { sector = it }
                                FiltroDirectorio("Asociación", asociaciones.associate { it.id to it.nombre }, asociacionFiltro) { asociacionFiltro = it }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (tipo.isNotEmpty()) TextButton(onClick = { tipo = "" }) { Text("Tipo: ${tipos[tipo] ?: "Todos"} ×", color = AmancayBordo) }
                                if (sector.isNotEmpty()) TextButton(onClick = { sector = "" }) { Text("Sector: ${sectores[sector] ?: "Todos"} ×", color = AmancayBordo) }
                                if (asociacionFiltro.isNotEmpty()) TextButton(onClick = { asociacionFiltro = "" }) { Text("Asociación: ${asociaciones.firstOrNull { it.id == asociacionFiltro }?.nombre ?: "Todas"} ×", color = AmancayBordo) }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${visibles.size} emprendimientos encontrados", color = AmancayTintaSuave, style = MaterialTheme.typography.titleSmall)
                                Text("Relevancia  ⌄", color = AmancayBordo, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        if (visibles.isEmpty()) item { Text(error ?: if (cargando) "Cargando directorio…" else "No hay emprendimientos que coincidan.", color = AmancayTintaSuave) }
                        items(visibles, key = { it.id }) { tienda ->
                            TarjetaDirectorio(
                                titulo = tienda.nombre,
                                detalle = listOfNotNull(tipos[tienda.tipoId], sectores[tienda.sectorId]).joinToString(" · "),
                                accion = "Ver tienda",
                                alAbrir = { alAbrir(tienda.id) }
                            )
                        }
                    } else {
                        item { Text("Asociaciones", style = MaterialTheme.typography.headlineSmall, color = AmancayBordo, fontWeight = FontWeight.Bold) }
                        if (asociaciones.isEmpty()) item { Text(error ?: if (cargando) "Cargando asociaciones…" else "Todavía no hay asociaciones publicadas.", color = AmancayTintaSuave) }
                        items(asociaciones, key = { it.id }) { asociacion ->
                            TarjetaDirectorio(
                                titulo = asociacion.nombre,
                                detalle = "${asociacion.integrantes.size} ${if (asociacion.integrantes.size == 1) "integrante" else "integrantes"} · ${sectores[asociacion.sectorId].orEmpty()}".trimEnd(' ', '·'),
                                accion = "Ver integrantes",
                                alAbrir = { asociacionAbierta = asociacion.id }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(14.dp)) }
            }
        }
    }
}

@Composable
private fun TarjetaDirectorio(titulo: String, detalle: String, accion: String, alAbrir: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = AmancaySuperficie), border = BorderStroke(1.dp, AmancayBorde)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = AmancayChipDurazno) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(titulo.firstOrNull()?.uppercase() ?: "✿", color = AmancayBordo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(titulo, color = AmancayBordo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (detalle.isNotBlank()) Text(detalle, color = AmancayTintaSuave, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = alAbrir) { Text("→", color = AmancayBordo, style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun BotonPestanaDirectorio(texto: String, seleccionado: Boolean, modifier: Modifier = Modifier, alClick: () -> Unit) {
    Button(
        onClick = alClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (seleccionado) AmancayBordo else Color.Transparent,
            contentColor = if (seleccionado) Color.White else AmancayBordo
        )
    ) { Text(texto) }
}

@Composable
private fun FiltroDirectorio(etiqueta: String, opciones: Map<String, String>, seleccionado: String, alSeleccionar: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        TextButton(onClick = { abierto = true }) {
            Text("$etiqueta: ${opciones[seleccionado] ?: "Todos"}  ⌄", color = AmancayBordo)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = { abierto = false; alSeleccionar("") })
            opciones.entries.sortedBy { it.value.lowercase() }.forEach { (id, nombre) ->
                DropdownMenuItem(text = { Text(nombre) }, onClick = { abierto = false; alSeleccionar(id) })
            }
        }
    }
}
