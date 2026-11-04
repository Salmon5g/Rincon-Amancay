// Mapa SOLO VISUAL de la zona elegida en el combo "Zona de la tienda (sector)".
// No guarda nada ni modifica el contrato del backend: es un ayudante visual para
// confirmar la selección (pensado para usuarios de mayor edad).
// Usa OpenStreetMap vía osmdroid (gratuito, sin API key). Sin gestos: ni zoom ni arrastre.
package com.example.amancay.emprendedora

import android.preference.PreferenceManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.amancay.ui.theme.Line
import com.example.amancay.ui.theme.Muted
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// Coordenadas de referencia de las zonas activas del catálogo (centro aproximado de cada una).
// Solo alimentan esta vista previa; la zona real de la tienda sigue siendo el sectorId de Firestore.
// Si se crea una zona nueva en el catálogo y no está aquí, el mapa simplemente no se muestra.
private val coordenadasZona = mapOf(
    "puerto_montt_centro" to Triple(-41.4693, -72.9416, 14.5),
    "puerto_montt_alerce" to Triple(-41.4236, -72.9203, 14.5),
    "puerto_montt_pelluco" to Triple(-41.4575, -72.9010, 14.5),
    "puerto_montt_angelmo" to Triple(-41.4792, -72.9550, 14.5),
    "puerto_montt_mirasol" to Triple(-41.4995, -72.9380, 14.5),
    "puerto_montt_maillen" to Triple(-41.5230, -72.9510, 14.5),
    "puerto_montt_pargua" to Triple(-41.7756, -73.1660, 13.0),
    "puerto_varas" to Triple(-41.3210, -72.9780, 13.5),
    "llanquihue" to Triple(-41.2460, -73.0030, 13.5),
    "frutillar" to Triple(-41.1360, -73.0640, 13.5),
    "calbuco" to Triple(-41.7660, -73.1310, 13.5)
)

@Composable
fun MapaZonaVisual(zonaId: String?, modifier: Modifier = Modifier) {
    val destino = zonaId?.let { coordenadasZona[it] } ?: return
    val context = LocalContext.current
    val punto = GeoPoint(destino.first, destino.second)

    val mapa = remember {
        Configuration.getInstance().apply {
            load(context, PreferenceManager.getDefaultSharedPreferences(context))
            userAgentValue = context.packageName
        }
        // Mapa congelado: onTouchEvent en false evita el arrastre; el zoom por gestos y
        // botones también quedan desactivados. Solo se ve el pin de la zona.
        object : MapView(context) {
            override fun onTouchEvent(event: android.view.MotionEvent?): Boolean = false
        }.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            overlays.add(Marker(this))
        }
    }

    DisposableEffect(mapa) {
        mapa.onResume()
        onDispose { mapa.onDetach() }
    }

    Column(modifier = modifier) {
        AndroidView(
            factory = { mapa },
            update = { vista ->
                vista.controller.setZoom(destino.third)
                vista.controller.setCenter(punto)
                (vista.overlays.firstOrNull { it is Marker } as? Marker)?.position = punto
                vista.invalidate()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Line, RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Vista de la zona seleccionada (solo referencia)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = Muted,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
