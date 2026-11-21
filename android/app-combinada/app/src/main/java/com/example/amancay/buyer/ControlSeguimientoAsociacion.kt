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
import com.example.amancay.data.SeguimientoAsociacionesComprador
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private enum class EstadoSeguimientoAsociacion {
    SIN_SESION,
    CONSULTANDO,
    DISPONIBLE,
    GUARDANDO,
    DEJANDO_DE_SEGUIR,
    SIGUIENDO,
    ERROR
}

@Composable
internal fun ControlSeguimientoAsociacion(
    asociacionId: String,
    nombreAsociacion: String,
    usuarioId: String?,
    firestore: FirebaseFirestore,
    alPedirInicioSesion: () -> Unit
) {
    val repositorio = remember(firestore) { SeguimientoAsociacionesComprador(firestore) }
    var intentoConsulta by remember(usuarioId, asociacionId) { mutableIntStateOf(0) }
    var estado by remember(usuarioId, asociacionId) {
        mutableStateOf(
            if (usuarioId.isNullOrBlank()) EstadoSeguimientoAsociacion.SIN_SESION
            else EstadoSeguimientoAsociacion.CONSULTANDO
        )
    }
    var aviso by remember(usuarioId, asociacionId) { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()

    LaunchedEffect(usuarioId, asociacionId, intentoConsulta) {
        if (usuarioId.isNullOrBlank()) {
            estado = EstadoSeguimientoAsociacion.SIN_SESION
            return@LaunchedEffect
        }
        estado = EstadoSeguimientoAsociacion.CONSULTANDO
        aviso = null
        try {
            estado = if (withTimeout(10_000) {
                    repositorio.estaSiguiendo(usuarioId, asociacionId)
                }
            ) EstadoSeguimientoAsociacion.SIGUIENDO else EstadoSeguimientoAsociacion.DISPONIBLE
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            estado = EstadoSeguimientoAsociacion.ERROR
            aviso = mensajeErrorSeguimientoAsociacion(error)
        }
    }

    fun dejarDeSeguir() {
        val uid = usuarioId ?: return
        estado = EstadoSeguimientoAsociacion.DEJANDO_DE_SEGUIR
        aviso = null
        alcance.launch {
            try {
                withTimeout(15_000) {
                    repositorio.dejarDeSeguir(uid, asociacionId)
                }
                runCatching { PreferenciasAvisosComprador.actualizarTema(uid, "asociacion", asociacionId, false) }
                estado = EstadoSeguimientoAsociacion.DISPONIBLE
                aviso = "Dejaste de seguir a $nombreAsociacion."
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                estado = EstadoSeguimientoAsociacion.ERROR
                aviso = mensajeErrorSeguimientoAsociacion(error)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = {
                when (estado) {
                    EstadoSeguimientoAsociacion.SIN_SESION -> alPedirInicioSesion()
                    EstadoSeguimientoAsociacion.ERROR -> intentoConsulta += 1
                    EstadoSeguimientoAsociacion.DISPONIBLE -> {
                        val uid = usuarioId ?: return@Button
                        estado = EstadoSeguimientoAsociacion.GUARDANDO
                        aviso = null
                        alcance.launch {
                            try {
                                withTimeout(15_000) {
                                    repositorio.seguir(uid, asociacionId)
                                }
                                if (PreferenciasAvisosComprador.consentimiento(firestore, uid)) {
                                    PreferenciasAvisosComprador.actualizarTema(uid, "asociacion", asociacionId, true)
                                }
                                estado = EstadoSeguimientoAsociacion.SIGUIENDO
                                aviso = "Ahora sigues a $nombreAsociacion."
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                estado = EstadoSeguimientoAsociacion.ERROR
                                aviso = mensajeErrorSeguimientoAsociacion(error)
                            }
                        }
                    }
                    else -> Unit
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = estado != EstadoSeguimientoAsociacion.CONSULTANDO &&
                estado != EstadoSeguimientoAsociacion.GUARDANDO &&
                estado != EstadoSeguimientoAsociacion.DEJANDO_DE_SEGUIR &&
                estado != EstadoSeguimientoAsociacion.SIGUIENDO,
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (estado == EstadoSeguimientoAsociacion.CONSULTANDO ||
                    estado == EstadoSeguimientoAsociacion.GUARDANDO ||
                    estado == EstadoSeguimientoAsociacion.DEJANDO_DE_SEGUIR
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when (estado) {
                        EstadoSeguimientoAsociacion.SIN_SESION -> "Iniciar sesión para seguir"
                        EstadoSeguimientoAsociacion.CONSULTANDO -> "Consultando…"
                        EstadoSeguimientoAsociacion.DISPONIBLE -> "Seguir asociación"
                        EstadoSeguimientoAsociacion.GUARDANDO -> "Guardando…"
                        EstadoSeguimientoAsociacion.DEJANDO_DE_SEGUIR -> "Actualizando…"
                        EstadoSeguimientoAsociacion.SIGUIENDO -> "Siguiendo"
                        EstadoSeguimientoAsociacion.ERROR -> "Reintentar"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (estado == EstadoSeguimientoAsociacion.SIGUIENDO) {
            TextButton(onClick = ::dejarDeSeguir, modifier = Modifier.fillMaxWidth()) {
                Text("Dejar de seguir", color = AmancayBordo)
            }
        }
        if (usuarioId.isNullOrBlank() && estado == EstadoSeguimientoAsociacion.SIN_SESION) {
            Text(
                "Inicia sesión para seguir esta asociación.",
                style = MaterialTheme.typography.bodySmall,
                color = AmancayTintaSuave
            )
        }
        aviso?.let { mensaje ->
            Text(
                mensaje,
                style = MaterialTheme.typography.bodySmall,
                color = if (estado == EstadoSeguimientoAsociacion.ERROR) MaterialTheme.colorScheme.error
                    else AmancayTintaSuave
            )
        }
    }
}

private fun mensajeErrorSeguimientoAsociacion(error: Exception): String =
    when ((error as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Firestore no permite actualizar este seguimiento. Pide que publiquen las reglas actualizadas."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "No hay conexión con Firestore. Reintenta cuando vuelva la conexión."
        else -> "No se pudo actualizar el seguimiento. Reintenta."
    }
