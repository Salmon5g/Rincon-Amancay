package com.example.amancay.emprendedora

import android.Manifest
import android.net.Uri
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.amancay.buyer.AmancayBordo
import com.example.amancay.buyer.AmancayTinta
import com.example.amancay.buyer.AmancayTintaSuave
import com.example.amancay.buyer.configurarOsmdroid
import com.example.amancay.buyer.iconoPunto
import com.example.amancay.buyer.obtenerUbicacionActual
import com.example.amancay.buyer.tienePermisoUbicacion
import com.example.amancay.data.PuntoRetiroTienda
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/**
 * Mapa para marcar el punto de venta o retiro de la tienda (Entrega 6, A18).
 * Un toque fija el punto; volver a tocar lo mueve. Se puede autocompletar la
 * dirección con el geocodificador gratuito de OpenStreetMap (Nominatim) o usar
 * la ubicación actual del teléfono. Usa OpenStreetMap vía osmdroid, sin
 * llaves ni cuentas, igual que el mapa de ferias del comprador.
 */

private const val AGENTE_GEOCODIFICADOR =
    "RinconAmancay/1.0 (app estudiantil; busqueda de direccion del punto de retiro)"

/**
 * Posición y zoom que comparten el mapa pequeño de la ficha y el de pantalla
 * completa: así ambos ven exactamente el mismo lugar.
 */
class CamaraMapa {
    var centro: GeoPoint = GeoPoint(-41.4693, -72.9416)
    var zoom: Double = 13.0
}

data class DireccionEncontrada(
    val nombre: String,
    val latitud: Double,
    val longitud: Double
) {
    val nombreCorto: String get() = nombre.substringBefore(",").trim()
}

/** Busca direcciones con Nominatim (OpenStreetMap). Gratuito y sin llave. */
suspend fun buscarDirecciones(consulta: String): List<DireccionEncontrada> =
    withContext(Dispatchers.IO) {
        val texto = consulta.trim()
        if (texto.length < 3) return@withContext emptyList()
        val url = URL(
            "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=" +
                Uri.encode(texto)
        )
        val conexion = url.openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 10_000
            conexion.readTimeout = 10_000
            conexion.setRequestProperty("User-Agent", AGENTE_GEOCODIFICADOR)
            if (conexion.responseCode != 200) return@withContext emptyList()
            val cuerpo = conexion.inputStream.bufferedReader().use { it.readText() }
            JSONArray(cuerpo).let { arreglo ->
                (0 until arreglo.length()).mapNotNull { indice ->
                    val item = arreglo.optJSONObject(indice) ?: return@mapNotNull null
                    val lat = item.optDouble("lat", Double.NaN)
                    val lon = item.optDouble("lon", Double.NaN)
                    val nombre = item.optString("display_name")
                    if (lat.isNaN() || lon.isNaN() || nombre.isBlank()) return@mapNotNull null
                    DireccionEncontrada(nombre = nombre, latitud = lat, longitud = lon)
                }
            }
        } finally {
            conexion.disconnect()
        }
    }

internal suspend fun zonasDelPunto(latitud: Double, longitud: Double): Set<String> =
    withContext(Dispatchers.IO) {
        val url = URL(
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1" +
                "&layer=address&lat=$latitud&lon=$longitud"
        )
        val conexion = url.openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 4_000
            conexion.readTimeout = 4_000
            conexion.setRequestProperty("User-Agent", AGENTE_GEOCODIFICADOR)
            if (conexion.responseCode != 200) return@withContext emptySet()
            val cuerpo = conexion.inputStream.bufferedReader().use { it.readText() }
            val direccion = JSONObject(cuerpo).optJSONObject("address") ?: return@withContext emptySet()
            if (direccion.optString("country_code") != "cl") return@withContext emptySet()
            listOf("neighbourhood", "quarter", "suburb", "borough", "hamlet", "village", "town", "city", "municipality", "city_district", "locality", "island")
                .map { normalizarZona(direccion.optString(it)) }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (_: IOException) {
            emptySet()
        } catch (_: org.json.JSONException) {
            emptySet()
        } finally {
            conexion.disconnect()
        }
    }

internal fun normalizarZona(nombre: String): String =
    Normalizer.normalize(nombre.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()

/** Botones de zoom del mapa, con blancos táctiles generosos. */
@Composable
internal fun ControlesZoomMapa(
    alAcercar: () -> Unit,
    alAlejar: () -> Unit,
    modifier: Modifier = Modifier,
    tamanoBoton: Dp = 48.dp
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = alAcercar,
            modifier = Modifier
                .size(tamanoBoton)
                .background(Color.White.copy(alpha = 0.96f), CircleShape)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Acercar el mapa", tint = AmancayBordo)
        }
        Spacer(Modifier.height(8.dp))
        IconButton(
            onClick = alAlejar,
            modifier = Modifier
                .size(tamanoBoton)
                .background(Color.White.copy(alpha = 0.96f), CircleShape)
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "Alejar el mapa", tint = AmancayBordo)
        }
    }
}

/**
 * Panel flotante que vive sobre el mapa: campo de búsqueda con
 * autocompletado mientras se escribe y acceso a la ubicación actual.
 */
@Composable
internal fun BuscadorDireccionPunto(
    alElegir: (DireccionEncontrada) -> Unit,
    alUbicacionActual: (latitud: Double, longitud: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var consulta by remember { mutableStateOf("") }
    var buscando by remember { mutableStateOf(false) }
    var obteniendoUbicacion by remember { mutableStateOf(false) }
    var sugerencias by remember { mutableStateOf<List<DireccionEncontrada>>(emptyList()) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var ultimaConsulta by remember { mutableStateOf("") }

    fun usarUbicacionActual() {
        alcance.launch {
            obteniendoUbicacion = true
            try {
                val posicion = obtenerUbicacionActual(contexto)
                if (posicion == null) {
                    mensaje =
                        "No pudimos obtener tu ubicación. Revisa que el GPS esté activado e intenta de nuevo."
                } else {
                    mensaje = null
                    sugerencias = emptyList()
                    alUbicacionActual(posicion.first, posicion.second)
                }
            } finally {
                obteniendoUbicacion = false
            }
        }
    }

    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (tienePermisoUbicacion(contexto)) usarUbicacionActual()
        else mensaje = "Sin permiso de ubicación no podemos marcar tu posición."
    }

    // Autocompletado: consulta cuando el texto se detiene un momento y no
    // repite la misma búsqueda. Nominatim pide a lo más una consulta por
    // segundo, por eso el texto se deja asentar antes de buscar.
    LaunchedEffect(consulta) {
        val texto = consulta.trim()
        mensaje = null
        if (texto.length < 3) {
            sugerencias = emptyList()
            return@LaunchedEffect
        }
        if (texto == ultimaConsulta) return@LaunchedEffect
        delay(800)
        ultimaConsulta = texto
        buscando = true
        try {
            val encontradas = buscarDirecciones(texto)
            sugerencias = encontradas
            mensaje = if (encontradas.isEmpty()) {
                "Sin resultados. Prueba con calle y ciudad."
            } else null
        } catch (_: Exception) {
            mensaje = "No se pudo buscar ahora. Inténtalo nuevamente."
        } finally {
            buscando = false
        }
    }

    Column(
        modifier.background(
            Color.White.copy(alpha = 0.96f),
            RoundedCornerShape(12.dp)
        ).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = consulta,
                onValueChange = { consulta = it },
                modifier = Modifier.weight(1f),
                label = { Text("Buscar dirección") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    if (tienePermisoUbicacion(contexto)) usarUbicacionActual()
                    else {
                        pedirPermiso.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }) {
                    Icon(
                        Icons.Outlined.MyLocation,
                        contentDescription = "Usar mi ubicación actual",
                        tint = AmancayBordo
                    )
                }
                Text(
                    "Mi ubicación",
                    fontSize = 9.sp,
                    color = AmancayBordo,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        if (buscando || obteniendoUbicacion) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, start = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.height(14.dp).width(14.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (obteniendoUbicacion) "Buscando tu ubicación…" else "Buscando direcciones…",
                    fontSize = 11.sp,
                    color = AmancayTintaSuave
                )
            }
        }
        sugerencias.forEach { sugerencia ->
            TextButton(
                onClick = {
                    alElegir(sugerencia)
                    sugerencias = emptyList()
                    consulta = ""
                    mensaje = null
                    ultimaConsulta = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    sugerencia.nombre,
                    color = AmancayTinta,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        mensaje?.let { aviso ->
            Text(
                aviso,
                fontSize = 11.sp,
                color = AmancayTintaSuave,
                modifier = Modifier.padding(top = 2.dp, start = 8.dp, bottom = 4.dp)
            )
        }
    }

    // Los avisos (sin posición, sin resultados) se retiran solos para no
    // tapar el mapa más de lo necesario.
    LaunchedEffect(mensaje) {
        if (mensaje != null) {
            delay(5_000)
            mensaje = null
        }
    }
}

@Composable
fun MapaPuntoRetiro(
    punto: PuntoRetiroTienda?,
    peticionCentrado: Int,
    peticionAcercar: Int,
    peticionAlejar: Int,
    camara: CamaraMapa,
    etiqueta: String,
    alMarcar: (latitud: Double, longitud: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var centradoInicial by remember { mutableStateOf(false) }
    var ultimaPeticion by remember { mutableStateOf(-1) }
    var ultimaPeticionAcercar by remember { mutableStateOf(0) }
    var ultimaPeticionAlejar by remember { mutableStateOf(0) }

    AndroidView(
        modifier = modifier,
        factory = { vista ->
            configurarOsmdroid(vista)
            MapView(vista).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(camara.zoom)
                controller.setCenter(camara.centro)
                // Comparte la cámara con las otras vistas del mismo mapa.
                addMapListener(object : org.osmdroid.events.MapListener {
                    override fun onScroll(evento: org.osmdroid.events.ScrollEvent?): Boolean {
                        camara.centro = GeoPoint(mapCenter.latitude, mapCenter.longitude)
                        camara.zoom = zoomLevelDouble
                        return false
                    }

                    override fun onZoom(evento: org.osmdroid.events.ZoomEvent?): Boolean {
                        camara.centro = GeoPoint(mapCenter.latitude, mapCenter.longitude)
                        camara.zoom = zoomLevelDouble
                        return false
                    }
                })
                // Mientras el dedo está sobre el mapa, el formulario no roba
                // el gesto para desplazarse: el arrastre queda dentro del mapa.
                setOnTouchListener { vistaMapa, _ ->
                    vistaMapa.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
                overlays.add(object : Overlay() {
                    override fun onSingleTapConfirmed(
                        evento: MotionEvent?,
                        mapa: MapView?
                    ): Boolean {
                        val toque = evento ?: return false
                        val vistaMapa = mapa ?: return false
                        val puntoToque = vistaMapa.projection.fromPixels(toque.x.toInt(), toque.y.toInt())
                        alMarcar(puntoToque.latitude, puntoToque.longitude)
                        return true
                    }
                })
            }
        },
        onRelease = { mapa -> mapa.onDetach() },
        update = { mapa ->
            if (peticionAcercar != ultimaPeticionAcercar) {
                ultimaPeticionAcercar = peticionAcercar
                mapa.controller.zoomIn()
            }
            if (peticionAlejar != ultimaPeticionAlejar) {
                ultimaPeticionAlejar = peticionAlejar
                mapa.controller.zoomOut()
            }
            val recentrar = peticionCentrado != ultimaPeticion
            if (recentrar) ultimaPeticion = peticionCentrado
            when {
                recentrar && punto != null -> {
                    mapa.controller.setCenter(GeoPoint(punto.latitud, punto.longitud))
                    mapa.controller.setZoom(15.5)
                    centradoInicial = true
                }
                !centradoInicial && punto != null -> {
                    mapa.controller.setCenter(GeoPoint(punto.latitud, punto.longitud))
                    mapa.controller.setZoom(15.5)
                    centradoInicial = true
                }
            }
            mapa.overlays.removeAll { overlay -> overlay is Marker }
            punto?.let { destino ->
                val marcador = Marker(mapa)
                marcador.position = GeoPoint(destino.latitud, destino.longitud)
                marcador.title = etiqueta
                marcador.icon = android.graphics.drawable.BitmapDrawable(
                    mapa.context.resources,
                    iconoPunto(
                        color = 0xFF7B1E3B.toInt(),
                        borde = 0xFFFFFFFF.toInt(),
                        tamanoPx = 46
                    )
                )
                marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                mapa.overlays.add(marcador)
            }
            mapa.invalidate()
        }
    )
}

@Composable
fun PieMapaOsmdroid(modifier: Modifier = Modifier) {
    Box(modifier) {
        Text(
            "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall,
            color = AmancayTintaSuave,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
        )
    }
}
