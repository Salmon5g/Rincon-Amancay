package com.example.amancay.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.amancay.ui.theme.Wine

/**
 * Micrófono para dictar el contenido de un campo. Abre el dictado del sistema solo al tocarlo
 * y entrega el texto reconocido; si el teléfono no tiene reconocimiento de voz, no se muestra.
 */
@Composable
fun BotonDictado(
    nombreCampo: String,
    alReconocer: (String) -> Unit,
    color: Color = Wine
) {
    val contexto = LocalContext.current
    val intencion = remember(nombreCampo) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CL")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla para completar: ${nombreCampo.trimEnd('*', ' ')}")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    val disponible = remember(contexto) {
        intencion.resolveActivity(contexto.packageManager) != null
    }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        if (resultado.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val texto = resultado.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (texto.isNotEmpty()) alReconocer(texto)
    }
    if (!disponible) return
    IconButton(
        onClick = {
            try {
                lanzador.launch(intencion)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(contexto, "Este teléfono no tiene dictado por voz disponible.", Toast.LENGTH_LONG).show()
            }
        },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Dictar ${nombreCampo.trimEnd('*', ' ').lowercase()} por voz",
            tint = color
        )
    }
}
