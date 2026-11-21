package com.example.amancay.buyer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.amancay.data.SeguimientoTiendasComprador
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private enum class EstadoSeguimientoTienda {
    SIN_SESION,
    CONSULTANDO,
    DISPONIBLE,
    GUARDANDO,
    DEJANDO_DE_SEGUIR,
    SIGUIENDO,
    ERROR
}

@Composable
internal fun ControlSeguimientoTienda(
    tiendaId: String,
    nombreTienda: String,
    usuarioId: String?,
    firestore: FirebaseFirestore,
    alPedirInicioSesion: () -> Unit
) {
    val repositorio = remember(firestore) { SeguimientoTiendasComprador(firestore) }
    var intentoConsulta by remember(usuarioId, tiendaId) { mutableIntStateOf(0) }
    var estado by remember(usuarioId, tiendaId) {
        mutableStateOf(
            if (usuarioId.isNullOrBlank()) EstadoSeguimientoTienda.SIN_SESION
            else EstadoSeguimientoTienda.CONSULTANDO
        )
    }
    var aviso by remember(usuarioId, tiendaId) { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()

    LaunchedEffect(usuarioId, tiendaId, intentoConsulta) {
        if (usuarioId.isNullOrBlank()) {
            estado = EstadoSeguimientoTienda.SIN_SESION
            return@LaunchedEffect
        }
        estado = EstadoSeguimientoTienda.CONSULTANDO
        aviso = null
        try {
            estado = if (withTimeout(10_000) {
                    repositorio.estaSiguiendo(usuarioId, tiendaId)
                }
            ) EstadoSeguimientoTienda.SIGUIENDO else EstadoSeguimientoTienda.DISPONIBLE
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            estado = EstadoSeguimientoTienda.ERROR
            aviso = mensajeErrorSeguimiento(error)
        }
    }

    fun dejarDeSeguir() {
        val uid = usuarioId ?: return
        estado = EstadoSeguimientoTienda.DEJANDO_DE_SEGUIR
        aviso = null
        alcance.launch {
            try {
                withTimeout(15_000) {
                    repositorio.dejarDeSeguir(uid, tiendaId)
                }
                runCatching { PreferenciasAvisosComprador.actualizarTema(uid, "tienda", tiendaId, false) }
                estado = EstadoSeguimientoTienda.DISPONIBLE
                aviso = "Dejaste de seguir a $nombreTienda."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                estado = EstadoSeguimientoTienda.ERROR
                aviso = mensajeErrorSeguimiento(error)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = {
                when (estado) {
                    EstadoSeguimientoTienda.SIN_SESION -> alPedirInicioSesion()
                    EstadoSeguimientoTienda.ERROR -> intentoConsulta += 1
                    EstadoSeguimientoTienda.DISPONIBLE -> {
                        val uid = usuarioId ?: return@Button
                        estado = EstadoSeguimientoTienda.GUARDANDO
                        aviso = null
                        alcance.launch {
                            try {
                                withTimeout(15_000) {
                                    repositorio.seguir(uid, tiendaId)
                                }
                                if (PreferenciasAvisosComprador.consentimiento(firestore, uid)) {
                                    PreferenciasAvisosComprador.actualizarTema(uid, "tienda", tiendaId, true)
                                }
                                estado = EstadoSeguimientoTienda.SIGUIENDO
                                aviso = "Ahora sigues a $nombreTienda."
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                estado = EstadoSeguimientoTienda.ERROR
                                aviso = mensajeErrorSeguimiento(error)
                            }
                        }
                    }
                    else -> Unit
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = estado != EstadoSeguimientoTienda.CONSULTANDO &&
                estado != EstadoSeguimientoTienda.GUARDANDO &&
                estado != EstadoSeguimientoTienda.DEJANDO_DE_SEGUIR &&
                estado != EstadoSeguimientoTienda.SIGUIENDO,
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (estado == EstadoSeguimientoTienda.CONSULTANDO ||
                    estado == EstadoSeguimientoTienda.GUARDANDO ||
                    estado == EstadoSeguimientoTienda.DEJANDO_DE_SEGUIR
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when (estado) {
                        EstadoSeguimientoTienda.SIN_SESION -> "Iniciar sesión para seguir"
                        EstadoSeguimientoTienda.CONSULTANDO -> "Consultando…"
                        EstadoSeguimientoTienda.DISPONIBLE -> "Seguir tienda"
                        EstadoSeguimientoTienda.GUARDANDO -> "Guardando…"
                        EstadoSeguimientoTienda.DEJANDO_DE_SEGUIR -> "Actualizando…"
                        EstadoSeguimientoTienda.SIGUIENDO -> "Siguiendo"
                        EstadoSeguimientoTienda.ERROR -> "Reintentar"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (estado == EstadoSeguimientoTienda.SIGUIENDO) {
            TextButton(onClick = ::dejarDeSeguir, modifier = Modifier.fillMaxWidth()) {
                Text("Dejar de seguir", color = AmancayBordo)
            }
        }
        if (usuarioId.isNullOrBlank() && estado == EstadoSeguimientoTienda.SIN_SESION) {
            Text(
                "Inicia sesión con Google para seguir esta tienda.",
                style = MaterialTheme.typography.bodySmall,
                color = AmancayTintaSuave
            )
        }
        aviso?.let { mensaje ->
            Text(
                mensaje,
                style = MaterialTheme.typography.bodySmall,
                color = if (estado == EstadoSeguimientoTienda.ERROR) MaterialTheme.colorScheme.error
                    else AmancayTintaSuave
            )
        }
    }
}

private fun mensajeErrorSeguimiento(error: Exception): String =
    when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Firestore no permite actualizar este seguimiento. Pide que publiquen las reglas actualizadas."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "No hay conexión con Firestore. Reintenta cuando vuelva la conexión."
        else -> "No se pudo actualizar el seguimiento. Reintenta."
    }
