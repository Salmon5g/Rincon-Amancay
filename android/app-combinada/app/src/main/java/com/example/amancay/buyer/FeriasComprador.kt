package com.example.amancay.buyer

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val fechaFin: Date?
)

class RepositorioFeriasComprador(private val db: FirebaseFirestore) {
    private val _ferias = MutableStateFlow<List<FeriaComprador>>(emptyList())
    val ferias: StateFlow<List<FeriaComprador>> = _ferias.asStateFlow()

    private val _cargando = MutableStateFlow(true)
    val cargando: StateFlow<Boolean> = _cargando.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var suscripcion: ListenerRegistration? = null

    fun iniciar() {
        if (suscripcion != null) return
        // Sin orderBy en la consulta: así no depende del índice compuesto
        // ferias(estado, fechaInicio) y el orden se aplica en el cliente.
        suscripcion = db.collection("ferias")
            .whereEqualTo("estado", "publicada")
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
                            fechaFin = documento.getTimestamp("fechaFin")?.toDate()
                        )
                    }
                    .sortedWith(Comparator { primera, segunda ->
                        compararFechas(primera.fechaInicio, segunda.fechaInicio)
                    })
                _error.value = null
                _cargando.value = false
            }
    }

    fun cerrar() {
        suscripcion?.remove()
        suscripcion = null
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
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        TituloPantalla(
            titulo = "Ferias",
            bajada = "Ferias publicadas de los emprendimientos Amancay."
        )
        Spacer(Modifier.height(12.dp))
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
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(feria.nombre, fontWeight = FontWeight.Bold, color = AmancayTinta) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val fecha = formatoFeria(feria.fechaInicio)
                if (fecha.isNotBlank()) Text("Inicio: $fecha", color = AmancayTinta)
                val termino = formatoFeria(feria.fechaFin)
                if (termino.isNotBlank()) Text("Término: $termino", color = AmancayTinta)
                if (feria.direccion.isNotBlank()) Text(feria.direccion, color = AmancayTinta)
                if (feria.organizador.isNotBlank()) Text("Organiza: ${feria.organizador}", color = AmancayTintaSuave)
                if (feria.descripcion.isNotBlank()) Text(feria.descripcion, color = AmancayTinta)
            }
        },
        confirmButton = {
            TextButton(onClick = alCerrar) { Text("Cerrar", color = AmancayBordo) }
        },
        containerColor = AmancaySuperficie
    )
}

private fun formatoFeria(fecha: Date?): String {
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
