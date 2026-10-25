package com.example.amancay.buyer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sistema visual compartido del comprador, derivado del diseño Figma de la
 * emprendedora (pantallas 01-11): misma barra superior, tarjetas crema con
 * borde suave, chips redondos y acento bordó + mostaza.
 *
 * La parte compradora reutiliza la "vista pública" (pantalla 09):
 * foto grande arriba, categoría en mayúsculas, título serif, precio grande
 * y badge "Disponible para encargo".
 */

// ---------------------------------------------------------------------------
// Barra superior
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperiorAmancay(
    titulo: String = "Amancay",
    subtitulo: String = "Rincón",
    alAyuda: (() -> Unit)? = null,
    alSalir: (() -> Unit)? = null
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AmancaySuperficie,
            titleContentColor = AmancayTinta
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Logo flor simplificado: círculo durazno con inicial.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AmancayNaranjaFondo),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✿", color = AmancayBordo, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        subtitulo,
                        style = MaterialTheme.typography.bodySmall,
                        color = AmancayTintaSuave
                    )
                    Text(
                        titulo,
                        style = MaterialTheme.typography.titleMedium,
                        color = AmancayBordo
                    )
                }
            }
        },
        actions = {
            if (alSalir != null) {
                androidx.compose.material3.TextButton(onClick = alSalir) {
                    Text("Salir", color = AmancayBordo, fontWeight = FontWeight.SemiBold)
                }
            }
            if (alAyuda != null) {
                androidx.compose.material3.TextButton(onClick = alAyuda) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AmancayAyudaFondo),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?", color = AmancayBordo, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Encabezados y etiquetas
// ---------------------------------------------------------------------------

@Composable
fun EtiquetaSeccion(texto: String) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
        color = AmancayBordo
    )
}

@Composable
fun TituloPantalla(titulo: String, bajada: String = "") {
    Column {
        Text(titulo, style = MaterialTheme.typography.headlineSmall, color = AmancayTinta)
        if (bajada.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(bajada, style = MaterialTheme.typography.bodyMedium, color = AmancayTintaSuave)
        }
    }
}

// ---------------------------------------------------------------------------
// Chips y badges
// ---------------------------------------------------------------------------

@Composable
fun ChipFiltroAmancay(
    texto: String,
    seleccionado: Boolean,
    alSeleccionar: () -> Unit
) {
    FilterChip(
        selected = seleccionado,
        onClick = alSeleccionar,
        label = { Text(texto) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AmancayBordo,
            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
            containerColor = AmancaySuperficie,
            labelColor = AmancayTinta
        ),
        shape = RoundedCornerShape(50.dp)
    )
}

@Composable
fun BadgeDisponibilidad(texto: String) {
    if (texto.isBlank()) return
    val esAgotado = texto.equals("Agotado", ignoreCase = true) ||
        texto.equals("No disponible", ignoreCase = true)
    val fondo = if (esAgotado) AmancayBordoSuave else AmancayVerdeFondo
    val color = if (esAgotado) AmancayBordoOscuro else AmancayVerdeTexto
    Surface(
        color = fondo,
        shape = RoundedCornerShape(50.dp)
    ) {
        Text(
            texto,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
fun EtiquetaCategoria(texto: String) {
    if (texto.isBlank()) return
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = AmancayBordo
    )
}

// ---------------------------------------------------------------------------
// Tarjetas y botones
// ---------------------------------------------------------------------------

@Composable
fun TarjetaAmancay(
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AmancaySuperficie),
        border = androidx.compose.foundation.BorderStroke(1.dp, AmancayBorde),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) { contenido() }
    }
}

@Composable
fun BotonPrimarioAmancay(texto: String, alClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = alClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AmancayBordo,
            contentColor = androidx.compose.ui.graphics.Color.White
        )
    ) {
        Text(texto, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
fun BotonMostazaAmancay(texto: String, alClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = alClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AmancayMostaza,
            contentColor = AmancayTinta
        )
    ) {
        Text(texto, modifier = Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------
// Placeholder de foto (foto grande estilo pantalla 09)
// ---------------------------------------------------------------------------

@Composable
fun FotoProductoAmancay(
    letra: String = "✿",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AmancayNaranjaFondo),
        contentAlignment = Alignment.Center
    ) {
        // Cuando haya imágenes (RF-CMP-03 completo) reemplazar por AsyncImage/Coil.
        // Por ahora se muestra un jarrón genérico como en Figma.
        Text("🏺", style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
fun MiniaturaAmancay(letra: String = "🏺") {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AmancayNaranjaFondo),
        contentAlignment = Alignment.Center
    ) {
        Text(letra, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun IconoCategoriaAmancay(emoji: String = "◧") {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AmancayNaranjaIcono.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium, color = AmancayBordo)
    }
}

@Composable
fun ChipInfoAmancay(texto: String) {
    AssistChip(
        onClick = {},
        label = { Text(texto, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = AmancayMostazaSuave,
            labelColor = AmancayTinta
        ),
        border = null
    )
}
