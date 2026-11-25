package com.example.amancay.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.roundToInt

/** Permite elegir qué parte de una imagen se conserva en el formato cuadrado del catálogo. */
@Composable
fun AjusteEncuadreFotoDialog(
    imagen: Bitmap,
    onCancelar: () -> Unit,
    onConfirmar: (Bitmap) -> Unit
) {
    var zoom by remember(imagen) { mutableFloatStateOf(1f) }
    var desplazamiento by remember(imagen) { mutableStateOf(Offset.Zero) }
    var ladoVistaPx by remember(imagen) { mutableFloatStateOf(0f) }
    val imagenCompose = remember(imagen) { imagen.asImageBitmap() }

    Dialog(
        onDismissRequest = onCancelar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).widthIn(max = 460.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F2)),
            shape = RoundedCornerShape(26.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Ajusta el encuadre", color = Color(0xFF702447), fontSize = 22.sp)
                Text(
                    "Mueve la foto para centrar el producto. Pellizca para acercar o alejar.",
                    color = Color(0xFF66545D), fontSize = 14.sp
                )
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF241B20))
                        .onSizeChanged { ladoVistaPx = minOf(it.width, it.height).toFloat() }
                        .pointerInput(imagen) {
                            detectTransformGestures { centro, movimiento, cambioZoom, _ ->
                                val lado = minOf(size.width, size.height).toFloat()
                                ladoVistaPx = lado
                                if (lado <= 0f) return@detectTransformGestures
                                val puntoCentro = Offset(centro.x - lado / 2f, centro.y - lado / 2f)
                                val nuevoZoom = (zoom * cambioZoom).coerceIn(1f, 4f)
                                val relacionZoom = nuevoZoom / zoom
                                val nuevoDesplazamiento = Offset(
                                    puntoCentro.x + (desplazamiento.x - puntoCentro.x) * relacionZoom + movimiento.x,
                                    puntoCentro.y + (desplazamiento.y - puntoCentro.y) * relacionZoom + movimiento.y
                                )
                                val escalaBase = max(lado / imagen.width, lado / imagen.height)
                                val ancho = imagen.width * escalaBase * nuevoZoom
                                val alto = imagen.height * escalaBase * nuevoZoom
                                desplazamiento = Offset(
                                    nuevoDesplazamiento.x.coerceIn(-((ancho - lado) / 2f).coerceAtLeast(0f), ((ancho - lado) / 2f).coerceAtLeast(0f)),
                                    nuevoDesplazamiento.y.coerceIn(-((alto - lado) / 2f).coerceAtLeast(0f), ((alto - lado) / 2f).coerceAtLeast(0f))
                                )
                                zoom = nuevoZoom
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val lado = size.minDimension
                        val escalaBase = max(lado / imagen.width, lado / imagen.height)
                        val escala = escalaBase * zoom
                        val ancho = imagen.width * escala
                        val alto = imagen.height * escala
                        drawImage(
                            image = imagenCompose,
                            dstOffset = IntOffset(
                                ((lado - ancho) / 2f + desplazamiento.x).roundToInt(),
                                ((lado - alto) / 2f + desplazamiento.y).roundToInt()
                            ),
                            dstSize = IntSize(ancho.roundToInt().coerceAtLeast(1), alto.roundToInt().coerceAtLeast(1))
                        )
                        val linea = Color.White.copy(alpha = 0.68f)
                        for (division in 1..2) {
                            val posicion = lado * division / 3f
                            drawLine(linea, Offset(posicion, 0f), Offset(posicion, lado), strokeWidth = 1.dp.toPx())
                            drawLine(linea, Offset(0f, posicion), Offset(lado, posicion), strokeWidth = 1.dp.toPx())
                        }
                        drawRect(Color.White.copy(alpha = 0.85f), style = Stroke(width = 2.dp.toPx()))
                    }
                }
                Text("El cuadro conserva el mismo formato para todas las fotos.", color = Color(0xFF66545D), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onCancelar,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Cancelar", color = Color(0xFF702447)) }
                    Button(
                        onClick = {
                            val lado = ladoVistaPx
                            if (lado <= 0f) return@Button
                            val escalaBase = max(lado / imagen.width, lado / imagen.height)
                            val escala = escalaBase * zoom
                            val ladoOrigen = (lado / escala).roundToInt().coerceIn(1, minOf(imagen.width, imagen.height))
                            val centroX = imagen.width / 2f - desplazamiento.x / escala
                            val centroY = imagen.height / 2f - desplazamiento.y / escala
                            val izquierda = (centroX - ladoOrigen / 2f).roundToInt().coerceIn(0, imagen.width - ladoOrigen)
                            val arriba = (centroY - ladoOrigen / 2f).roundToInt().coerceIn(0, imagen.height - ladoOrigen)
                            onConfirmar(Bitmap.createBitmap(imagen, izquierda, arriba, ladoOrigen, ladoOrigen))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF702447)),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Usar encuadre", color = Color.White) }
                }
            }
        }
    }
}
