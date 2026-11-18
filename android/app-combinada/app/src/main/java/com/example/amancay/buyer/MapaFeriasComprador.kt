package com.example.amancay.buyer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Mapas del modo comprador (Entrega 6, A11–A15): mapa de próximas ferias,
 * ficha de feria con ubicación y «Cómo llegar» hacia la aplicación de mapas.
 *
 * Se usa OpenStreetMap a través de osmdroid, sin llaves ni cuentas: el mapa
 * descarga baldosas públicas y la ubicación del usuario se lee del servicio
 * de ubicación del sistema con permiso del visitante.
 */

private const val AGENTE_OSM = "RinconAmancay/1.0 (app estudiantil; mapa de ferias y tiendas)"

fun configurarOsmdroid(contexto: Context) {
    val configuracion = Configuration.getInstance()
    configuracion.userAgentValue = AGENTE_OSM
    configuracion.osmdroidBasePath = File(contexto.cacheDir, "osmdroid")
    configuracion.osmdroidTileCache = File(contexto.cacheDir, "osmdroid/tiles")
}

fun tienePermisoUbicacion(contexto: Context): Boolean =
    ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

fun feriaConUbicacion(feria: FeriaComprador): Boolean =
    feria.latitud?.let { it.isFinite() && it in -90.0..90.0 } == true &&
        feria.longitud?.let { it.isFinite() && it in -180.0..180.0 } == true

/** Última posición conocida del sistema; no enciende el GPS por sí sola. */
fun ultimaUbicacionConocida(contexto: Context): Pair<Double, Double>? {
    if (!tienePermisoUbicacion(contexto)) return null
    val servicio = contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val proveedores = listOf(
        LocationManager.FUSED_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).filter { nombre ->
        runCatching { servicio.isProviderEnabled(nombre) }.getOrDefault(false)
    }
    val posiciones = proveedores.mapNotNull { proveedor ->
        runCatching { servicio.getLastKnownLocation(proveedor) }.getOrNull()
    }.sortedByDescending { it?.time ?: 0L }
    val mejor = posiciones.firstOrNull() ?: return null
    return mejor.latitude to mejor.longitude
}

/**
 * Posición actual para el botón «Mi ubicación»: si el sistema ya conoce una
 * posición la usa; si no, pide una lectura nueva al GPS y espera con límite
 * de tiempo para no dejar al usuario colgado.
 */
suspend fun obtenerUbicacionActual(contexto: Context, esperaMs: Long = 15_000): Pair<Double, Double>? =
    ultimaUbicacionConocida(contexto) ?: withContext(Dispatchers.Main) {
        val servicio = contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
        val proveedor = listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).firstOrNull { nombre ->
            runCatching { servicio.isProviderEnabled(nombre) }.getOrDefault(false)
        } ?: return@withContext null
        withTimeoutOrNull(esperaMs) {
            suspendCancellableCoroutine { continuacion ->
                val oyente = LocationListener { ubicacion ->
                    if (continuacion.isActive) {
                        continuacion.resume(ubicacion.latitude to ubicacion.longitude)
                    }
                }
                runCatching {
                    servicio.requestSingleUpdate(proveedor, oyente, Looper.getMainLooper())
                }.onFailure {
                    if (continuacion.isActive) continuacion.resume(null)
                }
                continuacion.invokeOnCancellation {
                    runCatching { servicio.removeUpdates(oyente) }
                }
            }
        }
    }

fun distanciaMetros(desde: Pair<Double, Double>, latitud: Double, longitud: Double): Float {
    val resultado = FloatArray(1)
    android.location.Location.distanceBetween(
        desde.first, desde.second, latitud, longitud, resultado
    )
    return resultado[0]
}

fun formatoDistancia(metros: Float): String = when {
    metros < 1000f -> "${"%.0f".format(metros)} m"
    metros < 10_000f -> "${"%.1f".format(metros / 1000f)} km"
    else -> "${"%.0f".format(metros / 1000f)} km"
}

private fun destinoFeria(feria: FeriaComprador): String? = when {
    feriaConUbicacion(feria) -> String.format(Locale.US, "%.6f,%.6f", feria.latitud, feria.longitud)
    feria.direccion.isNotBlank() -> feria.direccion.trim()
    else -> null
}

private fun uriMapaFeria(feria: FeriaComprador): Uri? {
    val destino = destinoFeria(feria) ?: return null
    val etiqueta = feria.nombre.trim()
    val consulta = if (etiqueta.isNotEmpty() && feriaConUbicacion(feria)) "$destino($etiqueta)" else destino
    return Uri.parse("geo:0,0?q=${Uri.encode(consulta)}")
}

private fun uriDireccionesGoogle(feria: FeriaComprador): Uri? {
    val destino = destinoFeria(feria) ?: return null
    return Uri.parse("https://www.google.com/maps/dir/").buildUpon()
        .appendQueryParameter("api", "1")
        .appendQueryParameter("destination", destino)
        .build()
}

/** Abre la aplicación de mapas del celular directamente en el destino de la feria (A11). */
fun abrirComoLlegar(contexto: Context, feria: FeriaComprador): Boolean {
    val geo = uriMapaFeria(feria) ?: return false
    try {
        contexto.startActivity(Intent(Intent.ACTION_VIEW, geo))
        return true
    } catch (_: Exception) {
        // Si no hay una app que entienda geo:, abre la ruta web con el mismo destino.
    }
    val direcciones = uriDireccionesGoogle(feria) ?: return false
    return try {
        contexto.startActivity(Intent(Intent.ACTION_VIEW, direcciones))
        true
    } catch (_: Exception) {
        false
    }
}

private fun ordenPorFecha(): Comparator<FeriaComprador> = Comparator { primera, segunda ->
    when {
        primera.fechaInicio == null && segunda.fechaInicio == null -> 0
        primera.fechaInicio == null -> 1
        segunda.fechaInicio == null -> -1
        else -> primera.fechaInicio.compareTo(segunda.fechaInicio)
    }
}

// ---------------------------------------------------------------------------
// Mapa osmdroid
// ---------------------------------------------------------------------------

internal fun iconoPunto(color: Int, borde: Int, tamanoPx: Int): Bitmap {
    val imagen = Bitmap.createBitmap(tamanoPx, tamanoPx, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(imagen)
    val radio = tamanoPx / 2f
    val centro = radio
    val pintarBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = borde
        style = Paint.Style.FILL
    }
    val pintarPunto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    lienzo.drawCircle(centro, centro, radio, pintarBorde)
    lienzo.drawCircle(centro, centro, radio * 0.62f, pintarPunto)
    return imagen
}

@Composable
fun MapaOsmdroid(
    ferias: List<FeriaComprador>,
    ubicacionUsuario: Pair<Double, Double>?,
    modifier: Modifier = Modifier,
    feriaResaltada: FeriaComprador? = null,
    alResaltar: (FeriaComprador?) -> Unit = {}
) {
    val contexto = LocalContext.current
    var centrado by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { vista ->
            configurarOsmdroid(vista)
            MapView(vista).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(12.0)
                // Mientras el dedo está sobre el mapa, el contenedor con scroll
                // no roba el gesto: el arrastre queda dentro del mapa.
                setOnTouchListener { vistaMapa, _ ->
                    vistaMapa.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
            }
        },
        onRelease = { mapa -> mapa.onDetach() },
        update = { mapa ->
            val centro: Pair<Double, Double>? = when {
                !centrado && ubicacionUsuario != null -> ubicacionUsuario
                !centrado -> ferias.firstOrNull { feriaConUbicacion(it) }
                    ?.let { it.latitud!! to it.longitud!! }
                else -> null
            }
            centro?.let { (latitud, longitud) ->
                mapa.controller.setCenter(org.osmdroid.util.GeoPoint(latitud, longitud))
                centrado = true
            }
            feriaResaltada?.let { feria ->
                feria.latitud?.let { latitud ->
                    feria.longitud?.let { longitud ->
                        mapa.controller.animateTo(org.osmdroid.util.GeoPoint(latitud, longitud))
                    }
                }
            }

            mapa.overlays.removeAll { overlay -> overlay is Marker }
            ferias.filter { feriaConUbicacion(it) }.forEach { feria ->
                val marcador = Marker(mapa)
                marcador.position = org.osmdroid.util.GeoPoint(feria.latitud!!, feria.longitud!!)
                marcador.title = feria.nombre
                marcador.snippet = feria.direccion
                marcador.icon = android.graphics.drawable.BitmapDrawable(
                    mapa.context.resources,
                    iconoPunto(
                        color = 0xFF7B1E3B.toInt(),
                        borde = if (feria.id == feriaResaltada?.id) 0xFFE8A33D.toInt() else 0xFFFFFFFF.toInt(),
                        tamanoPx = 46
                    )
                )
                marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                marcador.setOnMarkerClickListener { _, _ ->
                    alResaltar(feria)
                    true
                }
                mapa.overlays.add(marcador)
            }
            ubicacionUsuario?.let { (latitud, longitud) ->
                val marcaUsuario = Marker(mapa)
                marcaUsuario.position = org.osmdroid.util.GeoPoint(latitud, longitud)
                marcaUsuario.title = "Tu ubicación"
                marcaUsuario.icon = android.graphics.drawable.BitmapDrawable(
                    mapa.context.resources,
                    iconoPunto(
                        color = 0xFFE8A33D.toInt(),
                        borde = 0xFFFFFFFF.toInt(),
                        tamanoPx = 40
                    )
                )
                marcaUsuario.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                marcaUsuario.setOnMarkerClickListener { _, _ -> true }
                mapa.overlays.add(marcaUsuario)
            }
            mapa.invalidate()
        }
    )
}

// ---------------------------------------------------------------------------
// Pantalla: mapa de próximas ferias (A12–A15)
// ---------------------------------------------------------------------------

@Composable
fun PantallaMapaFerias(
    ferias: List<FeriaComprador>,
    cargando: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    var permisoConcedido by remember { mutableStateOf(tienePermisoUbicacion(contexto)) }
    var ubicacion by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var resaltada by remember { mutableStateOf<FeriaComprador?>(null) }

    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permisoConcedido = tienePermisoUbicacion(contexto)
    }

    // A15: el permiso se pide al abrir el mapa, no al iniciar la aplicación.
    LaunchedEffect(Unit) {
        if (!permisoConcedido) {
            pedirPermiso.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(permisoConcedido) {
        if (permisoConcedido) {
            ubicacion = ultimaUbicacionConocida(contexto)
        } else {
            ubicacion = null
        }
    }

    val conUbicacion = ferias.filter { feriaConUbicacion(it) }
    val ordenadas = remember(ferias, ubicacion) {
        when {
            ferias.isEmpty() -> ferias
            ubicacion != null && conUbicacion.isNotEmpty() -> ferias.sortedWith(
                Comparator { primera, segunda ->
                    val sinUbicacion1 = !feriaConUbicacion(primera)
                    val sinUbicacion2 = !feriaConUbicacion(segunda)
                    if (sinUbicacion1 != sinUbicacion2) {
                        return@Comparator if (sinUbicacion1) 1 else -1
                    }
                    if (sinUbicacion1) {
                        val fecha1 = primera.fechaInicio
                        val fecha2 = segunda.fechaInicio
                        if (fecha1 == null && fecha2 == null) 0
                        else if (fecha1 == null) 1
                        else if (fecha2 == null) -1
                        else fecha1.compareTo(fecha2)
                    } else {
                        distanciaMetros(ubicacion!!, primera.latitud!!, primera.longitud!!)
                            .compareTo(distanciaMetros(ubicacion!!, segunda.latitud!!, segunda.longitud!!))
                    }
                }
            )
            else -> ferias.sortedWith(ordenPorFecha())
        }
    }

    Column(modifier) {
        TituloPantalla(
            titulo = "Mapa de ferias",
            bajada = if (ubicacion != null) {
                "Ordenadas de la más cercana a la más lejana."
            } else {
                "Ferias próximas en el mapa. Sin tu ubicación, la lista sigue el orden por fecha."
            }
        )
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(18.dp))
        ) {
            MapaOsmdroid(
                ferias = conUbicacion,
                ubicacionUsuario = ubicacion,
                feriaResaltada = resaltada,
                alResaltar = { resaltada = it },
                modifier = Modifier.fillMaxSize()
            )
            Text(
                "© OpenStreetMap",
                style = MaterialTheme.typography.labelSmall,
                color = AmancayTintaSuave,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
            if (cargando && ferias.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AmancayFondo.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AmancayBordo)
                        Spacer(Modifier.height(10.dp))
                        Text("Cargando ferias…", color = AmancayTintaSuave)
                    }
                }
            }
        }
        if (error != null && ferias.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            MensajeCatalogo(error)
        }
        Spacer(Modifier.height(12.dp))

        val sinUbicacion = conUbicacion.isEmpty()
        if (sinUbicacion) {
            MensajeCatalogo(
                "Ninguna feria publicada tiene ubicación en el mapa todavía. " +
                    "La lista sigue por fecha."
            )
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(ordenadas, key = { it.id }) { feria ->
                TarjetaFeriaEnMapa(
                    feria = feria,
                    distancia = ubicacion?.let { centro ->
                        feria.latitud?.let { latitud ->
                            feria.longitud?.let { longitud ->
                                formatoDistancia(distanciaMetros(centro, latitud, longitud))
                            }
                        }
                    },
                    resaltada = resaltada?.id == feria.id,
                    alAbrir = { resaltada = feria },
                    alComoLlegar = {
                        if (!abrirComoLlegar(contexto, feria)) {
                            resaltada = feria
                        }
                    }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun TarjetaFeriaEnMapa(
    feria: FeriaComprador,
    distancia: String?,
    resaltada: Boolean,
    alAbrir: () -> Unit,
    alComoLlegar: () -> Unit
) {
    Card(
        onClick = alAbrir,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (resaltada) AmancayBordoSuave else AmancaySuperficie
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (resaltada) AmancayBordo else AmancayBorde
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    feria.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AmancayTinta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val fecha = formatoFeria(feria.fechaInicio)
                if (fecha.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Event,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = AmancayBordo
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            fecha,
                            style = MaterialTheme.typography.bodySmall,
                            color = AmancayTintaSuave,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            distancia?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AmancayBordo
                )
            }
            if (feriaConUbicacion(feria) || feria.direccion.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                androidx.compose.material3.FilledTonalButton(
                    onClick = alComoLlegar,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 4.dp
                    ),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = AmancayBordo,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Icon(
                        Icons.Outlined.NearMe,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Cómo llegar")
                }
            }
        }
    }
}
