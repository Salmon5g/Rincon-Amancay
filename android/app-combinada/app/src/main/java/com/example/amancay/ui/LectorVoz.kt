package com.example.amancay.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

enum class EstadoLectura { INACTIVO, PREPARANDO, LEYENDO, PAUSADO, SIN_VOZ }

/**
 * Lectura en voz alta bajo demanda. El motor se prepara recién al primer toque,
 * nunca habla solo y permite pausar y reanudar desde la frase interrumpida.
 */
class LectorVoz(context: Context) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val principal = Handler(Looper.getMainLooper())
    private val atributos = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val solicitudFoco = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(atributos)
        .setOnAudioFocusChangeListener({ cambio ->
            if (cambio == AudioManager.AUDIOFOCUS_LOSS || cambio == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pausar()
        }, principal)
        .build()

    private var motor: TextToSpeech? = null
    private var motorListo = false
    private var frases: List<String> = emptyList()
    private var indice = 0
    private var generacion = 0

    var estado by mutableStateOf(EstadoLectura.INACTIVO)
        private set
    var idActual by mutableStateOf<String?>(null)
        private set
    var aviso by mutableStateOf<String?>(null)
        private set

    fun alternar(id: String, texto: String) {
        when {
            idActual == id && estado == EstadoLectura.LEYENDO -> pausar()
            idActual == id && estado == EstadoLectura.PAUSADO -> reanudar()
            estado == EstadoLectura.PREPARANDO -> Unit
            else -> leer(id, texto)
        }
    }

    fun leer(id: String, texto: String) {
        val partes = dividirEnFrases(texto)
        if (partes.isEmpty()) return
        detenerMotor()
        frases = partes
        indice = 0
        idActual = id
        aviso = null
        if (motorListo) hablarDesdeIndice() else prepararMotor()
    }

    fun pausar() {
        if (estado != EstadoLectura.LEYENDO) return
        detenerMotor()
        estado = EstadoLectura.PAUSADO
    }

    fun reanudar() {
        if (estado != EstadoLectura.PAUSADO || !motorListo) return
        hablarDesdeIndice()
    }

    fun detener() {
        if (estado == EstadoLectura.SIN_VOZ) return
        detenerMotor()
        frases = emptyList()
        indice = 0
        idActual = null
        estado = EstadoLectura.INACTIVO
    }

    fun liberar() {
        detenerMotor()
        motor?.shutdown()
        motor = null
        motorListo = false
        if (estado != EstadoLectura.SIN_VOZ) estado = EstadoLectura.INACTIVO
    }

    fun abrirInstalacionVoz() {
        try {
            appContext.startActivity(
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            aviso = "Busca «Texto a voz» en los ajustes del teléfono para instalar una voz en español."
        }
    }

    private fun prepararMotor() {
        estado = EstadoLectura.PREPARANDO
        if (motor != null) return
        motor = TextToSpeech(appContext) { resultado ->
            principal.post { alPrepararMotor(resultado) }
        }
    }

    private fun alPrepararMotor(resultado: Int) {
        val tts = motor ?: return
        if (resultado != TextToSpeech.SUCCESS || !elegirIdioma(tts)) {
            sinVoz("Este teléfono no tiene una voz en español disponible.")
            return
        }
        tts.setAudioAttributes(atributos)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId ?: return
                principal.post { alComenzarFrase(utteranceId) }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId ?: return
                principal.post { alTerminarFrase(utteranceId) }
            }

            @Deprecated("Requerido por la API")
            override fun onError(utteranceId: String?) {
                utteranceId ?: return
                principal.post { alFallar(utteranceId) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId ?: return
                principal.post { alFallar(utteranceId) }
            }
        })
        motorListo = true
        if (estado == EstadoLectura.PREPARANDO && frases.isNotEmpty()) hablarDesdeIndice() else estado = EstadoLectura.INACTIVO
    }

    private fun elegirIdioma(tts: TextToSpeech): Boolean = listOf(
        Locale.forLanguageTag("es-CL"),
        Locale.forLanguageTag("es-419"),
        Locale.forLanguageTag("es-US"),
        Locale.forLanguageTag("es")
    ).any { idioma ->
        runCatching { tts.setLanguage(idioma) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)
    }

    private fun hablarDesdeIndice() {
        val tts = motor ?: return
        if (audio.requestAudioFocus(solicitudFoco) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            aviso = "No se puede leer en este momento porque otro sonido está activo, como una llamada."
            estado = if (indice > 0) EstadoLectura.PAUSADO else EstadoLectura.INACTIVO
            return
        }
        generacion++
        estado = EstadoLectura.LEYENDO
        val limite = (TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(200)
        frases.drop(indice).flatMap { partirPorLongitud(it, limite) }.let { pendientes ->
            frases = frases.take(indice) + pendientes
        }
        for (posicion in indice until frases.size) {
            val modo = if (posicion == indice) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val enviado = tts.speak(frases[posicion], modo, null, "$generacion:$posicion")
            if (enviado != TextToSpeech.SUCCESS) {
                alFallar("$generacion:$posicion")
                return
            }
        }
    }

    private fun alComenzarFrase(id: String) {
        val (gen, posicion) = leerId(id) ?: return
        if (gen == generacion && estado == EstadoLectura.LEYENDO) indice = posicion
    }

    private fun alTerminarFrase(id: String) {
        val (gen, posicion) = leerId(id) ?: return
        if (gen != generacion || estado != EstadoLectura.LEYENDO) return
        if (posicion >= frases.lastIndex) {
            audio.abandonAudioFocusRequest(solicitudFoco)
            frases = emptyList()
            indice = 0
            idActual = null
            estado = EstadoLectura.INACTIVO
        }
    }

    private fun alFallar(id: String) {
        val (gen, _) = leerId(id) ?: return
        if (gen != generacion) return
        detener()
        aviso = "No se pudo completar la lectura. Puedes intentarlo otra vez."
    }

    private fun sinVoz(mensaje: String) {
        motor?.shutdown()
        motor = null
        motorListo = false
        frases = emptyList()
        idActual = null
        aviso = mensaje
        estado = EstadoLectura.SIN_VOZ
    }

    private fun detenerMotor() {
        generacion++
        if (motorListo) motor?.stop()
        audio.abandonAudioFocusRequest(solicitudFoco)
    }

    private fun leerId(id: String): Pair<Int, Int>? {
        val partes = id.split(':')
        val gen = partes.getOrNull(0)?.toIntOrNull() ?: return null
        val posicion = partes.getOrNull(1)?.toIntOrNull() ?: return null
        return gen to posicion
    }
}

/** Lector ligado a la pantalla: se pausa al salir de la app y se libera al cerrarla. */
@Composable
fun rememberLectorVoz(): LectorVoz {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val lector = remember { LectorVoz(contexto) }
    DisposableEffect(ciclo, lector) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_STOP) lector.pausar()
        }
        ciclo.lifecycle.addObserver(observador)
        onDispose {
            ciclo.lifecycle.removeObserver(observador)
            lector.liberar()
        }
    }
    return lector
}

/** Botón voluntario «Escuchar»: alterna entre escuchar, pausar y reanudar, con opción de detener. */
@Composable
fun BotonEscuchar(
    lector: LectorVoz,
    id: String,
    texto: () -> String,
    modifier: Modifier = Modifier,
    etiqueta: String = "Escuchar ficha",
    color: Color = Color(0xFF75264F)
) {
    val propio = lector.idActual == id
    val estado = if (propio || lector.estado == EstadoLectura.SIN_VOZ) lector.estado else EstadoLectura.INACTIVO
    val (icono, rotulo) = when (estado) {
        EstadoLectura.LEYENDO -> Icons.Filled.Pause to "Pausar"
        EstadoLectura.PAUSADO -> Icons.Filled.PlayArrow to "Reanudar"
        EstadoLectura.PREPARANDO -> Icons.AutoMirrored.Filled.VolumeUp to "Preparando voz…"
        EstadoLectura.SIN_VOZ -> Icons.AutoMirrored.Filled.VolumeUp to "Probar voz otra vez"
        EstadoLectura.INACTIVO -> Icons.AutoMirrored.Filled.VolumeUp to etiqueta
    }
    val descripcion = when (estado) {
        EstadoLectura.LEYENDO -> "Pausar lectura en voz alta"
        EstadoLectura.PAUSADO -> "Reanudar lectura en voz alta"
        EstadoLectura.PREPARANDO -> "Preparando la voz"
        EstadoLectura.SIN_VOZ -> "Volver a probar la lectura en voz alta"
        EstadoLectura.INACTIVO -> "$etiqueta en voz alta"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { lector.alternar(id, texto()) },
                enabled = estado != EstadoLectura.PREPARANDO,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = descripcion },
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, color),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
            ) {
                Icon(icono, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(rotulo, fontSize = 16.sp)
            }
            if (estado == EstadoLectura.LEYENDO || estado == EstadoLectura.PAUSADO) {
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = lector::detener,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Detener lectura en voz alta" }
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Detener", color = color, fontSize = 16.sp)
                }
            }
            if (estado == EstadoLectura.SIN_VOZ) {
                TextButton(onClick = lector::abrirInstalacionVoz, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Instalar voz", color = color, fontSize = 16.sp)
                }
            }
        }
        lector.aviso?.let { Text(it, color = Color(0xFF5E4A53), fontSize = 14.sp, lineHeight = 19.sp) }
    }
}
