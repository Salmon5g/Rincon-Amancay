package com.example.amancay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** Guía por pasos que deja iluminado el control sobre el que debe actuar la persona. */
@Composable
fun GuiaInterfazSpotlight(
    objetivo: Rect,
    nombreObjetivo: String,
    paso: Int,
    total: Int,
    titulo: String,
    explicacion: String,
    alTocarObjetivo: () -> Unit,
    alSaltar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val densidad = LocalDensity.current
    val interaccion = remember { MutableInteractionSource() }
    val ancho = with(densidad) { objetivo.width.toDp() }
    val alto = with(densidad) { objetivo.height.toDp() }
    val posicionX = objetivo.left.roundToInt()
    val posicionY = objetivo.top.roundToInt()
    val centroPantallaDp = LocalConfiguration.current.screenHeightDp / 2
    val objetivoEstaArriba = with(densidad) { objetivo.center.y.toDp().value < centroPantallaDp }

    Box(modifier.fillMaxSize().zIndex(10f)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = interaccion, indication = null) {}
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            ) {
                drawRect(Color(0xB8000000))
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = androidx.compose.ui.geometry.Offset(objetivo.left - 7.dp.toPx(), objetivo.top - 7.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(objetivo.width + 14.dp.toPx(), objetivo.height + 14.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(
                    color = Color(0xFFFFC84A),
                    topLeft = androidx.compose.ui.geometry.Offset(objetivo.left - 7.dp.toPx(), objetivo.top - 7.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(objetivo.width + 14.dp.toPx(), objetivo.height + 14.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(posicionX, posicionY) }
                .size(ancho, alto)
                .clickable(onClick = alTocarObjetivo)
                .semantics {
                    role = Role.Button
                    contentDescription = "Paso $paso de $total. $explicacion. Toca $nombreObjetivo para continuar."
                }
        )
        Card(
            modifier = Modifier
                .align(if (objetivoEstaArriba) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(horizontal = 18.dp)
                .then(if (objetivoEstaArriba) Modifier.padding(bottom = 104.dp) else Modifier.padding(top = 82.dp))
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF4))
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Paso $paso de $total", color = Color(0xFF75264F), fontSize = 14.sp)
                Text(titulo, color = Color(0xFF501A37), fontSize = 22.sp, lineHeight = 27.sp)
                Text(explicacion, color = Color(0xFF3A2731), fontSize = 17.sp, lineHeight = 23.sp)
                Text("Toca «$nombreObjetivo» para continuar.", color = Color(0xFF501A37), fontSize = 16.sp)
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = alSaltar, modifier = Modifier.height(48.dp)) {
                        Text("Saltar guía", color = Color(0xFF75264F), fontSize = 16.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Toca lo iluminado", color = Color(0xFF501A37), fontSize = 14.sp)
                }
            }
        }
    }
}
