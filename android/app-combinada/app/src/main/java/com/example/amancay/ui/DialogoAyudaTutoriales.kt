package com.example.amancay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.amancay.ui.theme.Dark
import com.example.amancay.ui.theme.Muted
import com.example.amancay.ui.theme.Wine

data class OpcionTutorialAyuda(
    val id: String,
    val titulo: String,
    val icono: ImageVector
)

/** Selector visual de tutoriales con accesos grandes y un recorrido completo destacado. */
@Composable
fun DialogoAyudaTutoriales(
    titulo: String,
    descripcion: String,
    opciones: List<OpcionTutorialAyuda>,
    alElegir: (String) -> Unit,
    alRepetirTodo: () -> Unit,
    alCerrar: () -> Unit,
    mostrarRecorridoCompleto: Boolean = true,
    modifier: Modifier = Modifier
) {
    val alturaMaxima = (LocalConfiguration.current.screenHeightDp.dp - 32.dp).coerceAtLeast(360.dp)
    val bordeOpcion = Color(0xFFFF9968)
    val fondoIcono = Color(0xFFFFE7D7)
    val vinoOscuro = Color(0xFF74264F)
    val coral = Color(0xFFEA666B)
    val lector = rememberLectorVoz()

    Dialog(
        onDismissRequest = alCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(max = alturaMaxima),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F1)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        titulo,
                        color = vinoOscuro,
                        fontSize = 26.sp,
                        lineHeight = 31.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                    )
                    Text(descripcion, color = Muted, fontSize = 15.sp, lineHeight = 21.sp)
                    BotonEscuchar(
                        lector = lector,
                        id = "ayuda:$titulo",
                        texto = { textoParaEscucharAyuda(titulo, descripcion, opciones.map { it.titulo }, mostrarRecorridoCompleto) },
                        etiqueta = "Escuchar instrucciones",
                        color = vinoOscuro
                    )
                    Spacer(Modifier.height(2.dp))
                    if (opciones.isNotEmpty()) {
                        Text("Elige qué tutorial quieres repetir", color = vinoOscuro, fontSize = 15.sp)
                    }

                    opciones.forEach { opcion ->
                        Card(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable(
                                role = Role.Button,
                                onClick = { alElegir(opcion.id) }
                            ),
                            shape = RoundedCornerShape(17.dp),
                            border = BorderStroke(1.dp, bordeOpcion),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(42.dp).clip(CircleShape).background(fondoIcono),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(opcion.icono, contentDescription = null, tint = vinoOscuro, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(opcion.titulo, color = Dark, fontSize = 16.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = vinoOscuro, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    if (mostrarRecorridoCompleto) {
                        Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp).background(Color(0xFFEEDBCF)))

                        Card(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable(
                                role = Role.Button,
                                onClick = alRepetirTodo
                            ),
                            shape = RoundedCornerShape(17.dp),
                            colors = CardDefaults.cardColors(containerColor = vinoOscuro)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(42.dp).clip(CircleShape).background(coral),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Text("Recorrido completo", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = alCerrar, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Cerrar", color = vinoOscuro, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Texto del diálogo de Ayuda para «Escuchar instrucciones». */
fun textoParaEscucharAyuda(titulo: String, descripcion: String, tutoriales: List<String>, recorridoCompleto: Boolean): String = unirFrases(
    titulo,
    descripcion,
    tutoriales.takeIf { it.isNotEmpty() }?.let { "Puedes repetir estos tutoriales: ${it.joinToString(", ")}" },
    if (recorridoCompleto) "También puedes elegir Recorrido completo para ver toda la guía otra vez" else null
)
