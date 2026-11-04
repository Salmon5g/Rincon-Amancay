// Mapa SOLO VISUAL de la zona elegida en el combo "Zona de la tienda (sector)".
// No guarda nada ni modifica el contrato del backend: es un ayudante visual para
// confirmar la selección (pensado para usuarios de mayor edad).
// Descarga 4 baldosas de OpenStreetMap con HttpURLConnection (sin dependencias nuevas)
// y superpone el pin de la zona. Sin gestos: es una imagen.
package com.example.amancay.emprendedora

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.ui.theme.Line
import com.example.amancay.ui.theme.Muted
import com.example.amancay.ui.theme.Wine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

// Los servidores de OpenStreetMap bloquean clientes sin User-Agent identificable:
// la app se presenta con su nombre y propósito (proyecto educativo).
private const val AGENTE_MAPA = "AmancayApp/1.0 (proyecto educativo Rincon Amancay)"

// Coordenadas de referencia de las zonas activas del catálogo (centro aproximado de cada una)
// y el nivel de detalle del mapa de referencia. Solo alimentan esta vista previa; la zona real
// de la tienda sigue siendo el sectorId de Firestore. Si se crea una zona nueva en el catálogo
// y no está aquí, el mapa simplemente no se muestra.
private data class ZonaReferencia(val lat: Double, val lon: Double, val zoom: Int)

private val coordenadasZona = mapOf(
    "puerto_montt_centro" to ZonaReferencia(-41.4693, -72.9416, 15),
    "puerto_montt_alerce" to ZonaReferencia(-41.4236, -72.9203, 15),
    "puerto_montt_pelluco" to ZonaReferencia(-41.4575, -72.9010, 15),
    "puerto_montt_angelmo" to ZonaReferencia(-41.4792, -72.9550, 15),
    "puerto_montt_mirasol" to ZonaReferencia(-41.4995, -72.9380, 15),
    "puerto_montt_maillen" to ZonaReferencia(-41.5230, -72.9510, 15),
    "puerto_varas" to ZonaReferencia(-41.3210, -72.9780, 13),
    "llanquihue" to ZonaReferencia(-41.2460, -73.0030, 13),
    "frutillar" to ZonaReferencia(-41.1360, -73.0640, 13),
    "calbuco" to ZonaReferencia(-41.7660, -73.1310, 13)
)

private fun urlBaldosa(zoom: Int, x: Int, y: Int) = "https://tile.openstreetmap.org/$zoom/$x/$y.png"

@Composable
fun MapaZonaVisual(zonaId: String?, modifier: Modifier = Modifier) {
    val zona = zonaId?.let { coordenadasZona[it] } ?: return

    // Posición de la zona en coordenadas de baldosa (fracción de mundo * 2^zoom).
    val n = 1 shl zona.zoom
    val latRad = Math.toRadians(zona.lat)
    val xf = (zona.lon + 180.0) / 360.0 * n
    val yf = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n

    // Bloque 2x2 de baldosas cuyo centro queda a <= 1/2 baldosa del punto,
    // así el pin SIEMPRE queda visible dentro de la vista.
    val x0 = xf.roundToInt() - 1
    val y0 = yf.roundToInt() - 1
    val posiciones = remember(x0, y0) { listOf(x0 to y0, x0 + 1 to y0, x0 to y0 + 1, x0 + 1 to y0 + 1) }
    val baldosas = remember(posiciones) { List(4) { mutableStateOf<ImageBitmap?>(null) } }

    LaunchedEffect(posiciones) {
        baldosas.forEach { it.value = null }
        posiciones.forEachIndexed { indice, (tx, ty) ->
            launch(Dispatchers.IO) {
                try {
                    val conexion = URL(urlBaldosa(zona.zoom, tx, ty)).openConnection() as HttpURLConnection
                    conexion.connectTimeout = 10000
                    conexion.readTimeout = 10000
                    conexion.setRequestProperty("User-Agent", AGENTE_MAPA)
                    if (conexion.responseCode == 200) {
                        conexion.inputStream.use { entrada ->
                            BitmapFactory.decodeStream(entrada)?.let { baldosas[indice].value = it.asImageBitmap() }
                        }
                    }
                    conexion.disconnect()
                } catch (_: Exception) {
                    // Sin red: la vista queda vacía, sin romper la ficha.
                }
            }
        }
    }

    Column(modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Line, RoundedCornerShape(16.dp))
        ) {
            val ancho = maxWidth
            val baldosa = ancho / 2
            val altoRecorte = (ancho - 170.dp) / 2

            // requiredHeight: el grid mide 2x2 baldosas (~412dp) y DEBE medir eso aunque el
            // contenedor limita a 170dp (el Box recorta con .clip lo que sobra por arriba).
            Column(
                Modifier
                    .requiredHeight(baldosa * 2)
                    .offset(y = -altoRecorte)
            ) {
                Row {
                    baldosas[0].value?.let { Image(it, null, Modifier.size(baldosa)) }
                    baldosas[1].value?.let { Image(it, null, Modifier.size(baldosa)) }
                }
                Row {
                    baldosas[2].value?.let { Image(it, null, Modifier.size(baldosa)) }
                    baldosas[3].value?.let { Image(it, null, Modifier.size(baldosa)) }
                }
            }

            // Pin de la zona, centrado en su posición exacta dentro del bloque,
            // acotado a la vista para que nunca quede fuera del recuadro.
            val pinX = (((xf - x0) * (ancho.value / 2)).dp).coerceIn(12.dp, ancho - 12.dp) - 9.dp
            val pinY = (((yf - y0) * (ancho.value / 2)).dp - altoRecorte).coerceIn(12.dp, 158.dp) - 9.dp
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = pinX, y = pinY)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Wine, CircleShape)
                    .border(3.dp, Color.White, CircleShape)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Vista de la zona seleccionada (solo referencia) · © OpenStreetMap",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = Muted,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
