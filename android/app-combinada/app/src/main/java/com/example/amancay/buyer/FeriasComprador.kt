package com.example.amancay.buyer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.amancay.data.ApiFeriasComprador
import com.example.amancay.data.ParticipanteFeriaPublico
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ferias públicas del modo comprador: lectura de solo lectura de la
 * colección `ferias` (las reglas publicadas permiten leer las ferias con
 * `estado == "publicada"`). No se escribe nada desde esta pantalla.
 */

data class FeriaComprador(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val organizador: String,
    val direccion: String,
    val fechaInicio: Date?,
    val fechaFin: Date?,
    val latitud: Double?,
    val longitud: Double?
)

class RepositorioFeriasComprador(private val db: FirebaseFirestore) {
    private val _ferias = MutableStateFlow<List<FeriaComprador>>(emptyList())
    val ferias: StateFlow<List<FeriaComprador>> = _ferias.asStateFlow()

    private val _cargando = MutableStateFlow(true)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var suscripcion: ListenerRegistration? = null
    private val alcanceApi = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun iniciar() {
        if (suscripcion != null) return
        // Sin orderBy en la consulta: así no depende del índice compuesto
        // ferias(estado, fechaInicio) y el orden se aplica en el cliente.
        suscripcion = db.collection("ferias")
            .whereEqualTo("estado", "publicada")
            .limit(100)
            .addSnapshotListener { snapshot, excepcion ->
                if (excepcion != null) {
                    _error.value = "No se pudieron leer las ferias publicadas."
                    _cargando.value = false
                    return@addSnapshotListener
                }
                _ferias.value = snapshot?.documents.orEmpty()
                    .mapNotNull { documento ->
                        val nombre = documento.getString("nombre")
                            ?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                        FeriaComprador(
                            id = documento.id,
                            nombre = nombre,
                            descripcion = documento.getString("descripcion").orEmpty(),
                            organizador = documento.getString("organizador").orEmpty(),
                            direccion = documento.getString("direccion").orEmpty(),
                            fechaInicio = documento.getTimestamp("fechaInicio")?.toDate(),
                            fechaFin = documento.getTimestamp("fechaFin")?.toDate(),
                            latitud = documento.getDouble("latitud"),
                            longitud = documento.getDouble("longitud")
                        )
                    }
                    .sortedWith(Comparator { primera, segunda ->
                        compararFechas(primera.fechaInicio, segunda.fechaInicio)
                    })
                _error.value = null
                _cargando.value = false
            }
        alcanceApi.launch {
            delay(2_000)
            while (isActive) {
                try {
                    val feriasApi = ApiFeriasComprador.leerFerias()
                        .map { feria ->
                            FeriaComprador(
                                id = feria.id,
                                nombre = feria.nombre,
                                descripcion = feria.descripcion,
                                organizador = feria.organizador,
                                direccion = feria.direccion,
                                fechaInicio = feria.fechaInicio,
                                fechaFin = feria.fechaFin,
                                latitud = feria.latitud,
                                longitud = feria.longitud
                            )
                        }
                        .sortedWith(Comparator { primera, segunda ->
                            compararFechas(primera.fechaInicio, segunda.fechaInicio)
                        })
                    if (feriasApi.isNotEmpty() || _ferias.value.isEmpty()) {
                        _ferias.value = feriasApi
                        _error.value = null
                        _cargando.value = false
                    }
                    break
                } catch (error: Exception) {
                    if (_ferias.value.isEmpty()) {
                        _error.value = error.message ?: "No se pudieron cargar las ferias."
                        _cargando.value = false
                    }
                    delay(5_000)
                }
            }
        }
    }

    fun cerrar() {
        suscripcion?.remove()
        suscripcion = null
        alcanceApi.cancel()
    }

    private fun compararFechas(primera: Date?, segunda: Date?): Int = when {
        primera == null && segunda == null -> 0
        primera == null -> 1
        segunda == null -> -1
        else -> primera.compareTo(segunda)
    }
}

/** La feria más próxima que aún no comenzó (para el banner de Inicio). */
fun proximaFeria(ferias: List<FeriaComprador>): FeriaComprador? {
    val ahora = Date()
    return ferias.firstOrNull { fecha -> fecha.fechaInicio?.after(ahora) != false }
}

// ---------------------------------------------------------------------------
// Pantalla de ferias
// ---------------------------------------------------------------------------

@Composable
fun PantallaFeriasComprador(
    ferias: List<FeriaComprador>,
    cargando: Boolean,
    error: String?,
    alDetalle: (FeriaComprador) -> Unit,
    alVerMapa: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        TituloPantalla(
            titulo = "Ferias",
            bajada = "Ferias publicadas de los emprendimientos Amancay."
        )
        Spacer(Modifier.height(12.dp))
        if (ferias.isNotEmpty() || cargando) {
            BotonPrimarioAmancay(texto = "Ver ferias en el mapa", alClick = alVerMapa)
            Spacer(Modifier.height(12.dp))
        }
        when {
            cargando && ferias.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = AmancayBordo)
                Spacer(Modifier.height(12.dp))
                Text("Cargando ferias…", color = AmancayTintaSuave)
            }

            ferias.isEmpty() && error != null -> MensajeCatalogo(error)

            ferias.isEmpty() -> MensajeCatalogo("Aún no hay ferias publicadas. Vuelve pronto.")

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(ferias, key = { it.id }) { feria ->
                    TarjetaFeria(feria) { alDetalle(feria) }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun TarjetaFeria(feria: FeriaComprador, alAbrir: () -> Unit) {
    Card(
        onClick = alAbrir,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AmancaySuperficie),
        border = androidx.compose.foundation.BorderStroke(1.dp, AmancayBorde),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                feria.nombre,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AmancayTinta,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val fecha = formatoFeria(feria.fechaInicio)
            if (fecha.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Event,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = AmancayBordo
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(fecha, style = MaterialTheme.typography.bodySmall, color = AmancayTintaSuave)
                }
            }
            if (feria.direccion.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Place,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = AmancayTintaSuave
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        feria.direccion,
                        style = MaterialTheme.typography.bodySmall,
                        color = AmancayTintaSuave,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (feria.organizador.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Organiza: ${feria.organizador}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmancayTintaSuave,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DialogoDetalleFeria(feria: FeriaComprador, alCerrar: () -> Unit) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var intentoCarga by remember(feria.id) { mutableIntStateOf(0) }
    var participantes by remember(feria.id) { mutableStateOf<List<ParticipanteFeriaPublico>?>(null) }
    var cargandoParticipantes by remember(feria.id) { mutableStateOf(true) }
    var errorParticipantes by remember(feria.id) { mutableStateOf<String?>(null) }
    val lector = com.example.amancay.ui.rememberLectorVoz()

    LaunchedEffect(feria.id, intentoCarga) {
        cargandoParticipantes = true
        errorParticipantes = null
        try {
            participantes = ApiFeriasComprador.leerParticipantes(feria.id)
        } catch (error: Exception) {
            participantes = null
            errorParticipantes = error.message ?: "No se pudo cargar la muestra del catálogo."
        } finally {
            cargandoParticipantes = false
        }
    }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(feria.nombre, fontWeight = FontWeight.Bold, color = AmancayTinta) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.example.amancay.ui.BotonEscuchar(
                    lector = lector,
                    id = "feria:${feria.id}",
                    texto = { textoParaEscucharFeria(feria, participantes?.map { it.nombre }) },
                    color = AmancayBordo
                )
                val fecha = formatoFeria(feria.fechaInicio)
                if (fecha.isNotBlank()) Text("Inicio: $fecha", color = AmancayTinta)
                val termino = formatoFeria(feria.fechaFin)
                if (termino.isNotBlank()) Text("Término: $termino", color = AmancayTinta)
                if (feria.direccion.isNotBlank()) Text(feria.direccion, color = AmancayTinta)
                if (feria.organizador.isNotBlank()) Text("Organiza: ${feria.organizador}", color = AmancayTintaSuave)
                if (feria.descripcion.isNotBlank()) Text(feria.descripcion, color = AmancayTinta)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Participantes confirmadas y catálogo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AmancayTinta
                )
                when {
                    cargandoParticipantes -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AmancayBordo,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Cargando participantes…", color = AmancayTintaSuave)
                    }
                    errorParticipantes != null -> Column {
                        Text(errorParticipantes.orEmpty(), color = AmancayTintaSuave)
                        TextButton(onClick = { intentoCarga++ }) {
                            Text("Reintentar", color = AmancayBordo)
                        }
                    }
                    participantes.isNullOrEmpty() -> Text(
                        "Esta feria todavía no tiene participantes confirmadas.",
                        color = AmancayTintaSuave
                    )
                    else -> participantes.orEmpty().forEach { participante ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = AmancaySuperficie)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    participante.nombre,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AmancayTinta
                                )
                                Text(
                                    if (participante.tipo == "asociacion") "Asociación confirmada" else "Emprendimiento confirmado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmancayTintaSuave
                                )
                                if (participante.catalogo.isEmpty()) {
                                    Text("Todavía no hay catálogo público disponible.", color = AmancayTintaSuave)
                                } else {
                                    participante.catalogo.forEach { tienda ->
                                        val nombres = tienda.productos.joinToString(" · ")
                                        Text(
                                            if (participante.tipo == "asociacion") {
                                                if (nombres.isBlank()) tienda.nombreTienda else tienda.nombreTienda + ": " + nombres
                                            } else {
                                                if (nombres.isBlank()) "Sin productos públicos para mostrar." else nombres
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AmancayTintaSuave
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (feriaConUbicacion(feria)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    ) {
                        MapaOsmdroid(
                            ferias = listOf(feria),
                            ubicacionUsuario = null,
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
                    }
                    BotonPrimarioAmancay(texto = "Cómo llegar", alClick = {
                        abrirComoLlegar(contexto, feria)
                    })
                }
                com.example.amancay.ui.BotonCodigoQr(
                    "Código QR de la feria",
                    feria.nombre,
                    com.example.amancay.ui.EnlacesPublicos.feria(feria.id)
                )
                com.example.amancay.ui.BotonCompartirEnlace(
                    "Compartir feria",
                    feria.nombre,
                    com.example.amancay.ui.EnlacesPublicos.feria(feria.id)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = alCerrar) { Text("Cerrar", color = AmancayBordo) }
        },
        containerColor = AmancaySuperficie
    )
}

internal fun formatoFeria(fecha: Date?): String {
    fecha ?: return ""
    val dia = SimpleDateFormat("EEEE d 'de' MMMM", Locale.forLanguageTag("es")).format(fecha)
    val hora = SimpleDateFormat("HH:mm", Locale.forLanguageTag("es")).format(fecha)
    return dia.replaceFirstChar { letra -> letra.uppercase() } + ", $hora"
}

/** Inicia la escucha de ferias publicadas y la detiene al salir de la pantalla. */
@Composable
fun rememberRepositorioFerias(): RepositorioFeriasComprador {
    val repositorio = remember {
        RepositorioFeriasComprador(FirebaseFirestore.getInstance())
    }
    androidx.compose.runtime.DisposableEffect(repositorio) {
        repositorio.iniciar()
        onDispose { repositorio.cerrar() }
    }
    return repositorio
}
