package com.example.amancay.buyer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.amancay.data.ProductoCatalogoComprador
import com.example.amancay.data.TiendaCatalogoComprador
import com.example.amancay.data.VarianteCatalogoComprador

@Composable
internal fun FormularioContactoComprador(
    tienda: TiendaCatalogoComprador,
    producto: String?,
    fichaProducto: ProductoCatalogoComprador?,
    variante: VarianteCatalogoComprador?,
    correoSesion: String?,
    nombreSesion: String?,
    alCerrar: () -> Unit,
    alWhatsApp: () -> Unit,
    alCorreo: () -> Unit,
    alEnviar: (nombre: String, correo: String, mensaje: String) -> Unit
) {
    var nombre by remember(tienda.id, producto, nombreSesion) { mutableStateOf(nombreSesion.orEmpty()) }
    var correo by remember(tienda.id, producto, correoSesion) { mutableStateOf(correoSesion.orEmpty()) }
    var mensaje by remember(tienda.id, producto) { mutableStateOf("") }
    val correoValido = correo.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
    val habilitado = nombre.trim().length in 2..100 && correoValido && mensaje.trim().length in 5..2000
    val mostrarWhatsApp = tienda.whatsappHabilitado
    var mostrarFormulario by remember(tienda.id, producto, tienda.formularioHabilitado) {
        mutableStateOf(tienda.formularioHabilitado && !tienda.whatsappHabilitado && !tienda.correoHabilitado)
    }
    val precioVisible = tienda.mostrarPrecios
    val precio = fichaProducto?.let { ficha ->
        variante?.precio?.let(::formatoPrecio) ?: ficha.precioBase?.let(::formatoPrecio)
            ?: ficha.variantes.mapNotNull { it.precio }.minOrNull()?.let(::formatoPrecio)
    }
    val imagen = variante?.imagenUrl ?: fichaProducto?.imagenUrl

    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = AmancayFondo, contentColor = AmancayTinta) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = alCerrar) { Text("← Volver", color = AmancayBordo) }
                        Spacer(Modifier.weight(1f))
                        Text("Rincón Amancay", color = AmancayBordo, fontWeight = FontWeight.Bold)
                    }
                    Text("SOBRE ESTE PRODUCTO", color = AmancayNaranjaIcono, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Contactar a ${tienda.nombre}", color = AmancayBordo, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Haz tu consulta directamente a la emprendedora.", color = AmancayTintaSuave, style = MaterialTheme.typography.bodyLarge)
                }
                if (fichaProducto != null) item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = AmancaySuperficie), border = BorderStroke(1.dp, AmancayBorde)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)).background(AmancayChipDurazno), contentAlignment = Alignment.Center) {
                                if (!imagen.isNullOrBlank()) {
                                    AsyncImage(imagen, contentDescription = fichaProducto.nombre, modifier = Modifier.fillMaxSize())
                                } else {
                                    Text("✿", color = AmancayBordo, style = MaterialTheme.typography.headlineLarge)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(fichaProducto.nombre, color = AmancayBordo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                val varianteTexto = variante?.opciones?.values?.joinToString(" · ")
                                Text(listOfNotNull(varianteTexto, fichaProducto.categoriaNombre.takeIf(String::isNotBlank)).joinToString(" · ").ifBlank { tienda.nombre }, color = AmancayTintaSuave)
                                if (precioVisible && precio != null) Text(precio, color = AmancayBordo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                else if (!precioVisible) Text("Consultar precio", color = AmancayBordo, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                item {
                    Text("¿Cómo quieres contactar?", color = AmancayBordo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (mostrarWhatsApp) {
                            CanalContacto("WhatsApp", "Conversación directa", "◉", AmancayVerdeFondo, AmancayVerdeTexto, Modifier.fillMaxWidth(), alWhatsApp)
                        }
                        if (tienda.formularioHabilitado) {
                            CanalContacto("Formulario", "Deja tu consulta", "▤", AmancayChipRosa, AmancayBordo, Modifier.fillMaxWidth(), { mostrarFormulario = true }, seleccionado = mostrarFormulario)
                        }
                        if (tienda.correoHabilitado && tienda.correoDireccion.isNotBlank()) {
                            CanalContacto("Correo", tienda.correoDireccion, "✉", AmancayChipDurazno, AmancayBordo, Modifier.fillMaxWidth(), alCorreo)
                        }
                    }
                }
                if (mostrarFormulario && tienda.formularioHabilitado) item {
                    Text("Tu mensaje", color = AmancayBordo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(mensaje, { if (it.length <= 2000) mensaje = it }, placeholder = { Text("Hola, me interesa este producto…") }, minLines = 4, maxLines = 7, supportingText = { Text("${mensaje.length}/2000", modifier = Modifier.fillMaxWidth()) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                }
                if (mostrarFormulario && tienda.formularioHabilitado) item {
                    if (nombreSesion.isNullOrBlank()) {
                        OutlinedTextField(nombre, { if (it.length <= 100) nombre = it }, label = { Text("Tu nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (correoSesion.isNullOrBlank()) {
                        OutlinedTextField(correo, { if (it.length <= 254) correo = it }, label = { Text("Tu correo") }, singleLine = true, isError = correo.isNotBlank() && !correoValido, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text("Responderemos a ${correoSesion}", color = AmancayTintaSuave, style = MaterialTheme.typography.bodySmall)
                    }
                    if (correo.isNotBlank() && !correoValido) Text("Escribe un correo válido.", color = MaterialTheme.colorScheme.error)
                }
                if (mostrarFormulario && tienda.formularioHabilitado) item {
                    Button(onClick = { alEnviar(nombre.trim(), correo.trim(), mensaje.trim()) }, enabled = habilitado, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = AmancayBordo, contentColor = Color.White)) {
                        Text("Enviar consulta   →", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun CanalContacto(titulo: String, bajada: String, icono: String, fondo: Color, tinta: Color, modifier: Modifier, alClick: () -> Unit, seleccionado: Boolean = false) {
    Card(onClick = alClick, modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = fondo), border = BorderStroke(if (seleccionado) 2.dp else 1.dp, tinta.copy(alpha = if (seleccionado) .8f else .35f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Surface(modifier = Modifier.size(42.dp), shape = CircleShape, color = tinta) {
                Box(contentAlignment = Alignment.Center) { Text(icono, color = Color.White, style = MaterialTheme.typography.titleLarge) }
            }
            Text(titulo, color = AmancayBordo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(bajada, color = AmancayTintaSuave, style = MaterialTheme.typography.bodySmall)
        }
    }
}
