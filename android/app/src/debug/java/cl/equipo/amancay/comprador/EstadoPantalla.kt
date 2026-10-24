package cl.equipo.amancay.comprador

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.equipo.amancay.ui.theme.AmancayTinta
import cl.equipo.amancay.ui.theme.AmancayTintaSuave

/**
 * Mensaje de error reutilizable, con botón primario Amancay.
 * Lo comparten el directorio y los detalles.
 */
@Composable
fun MensajeError(mensaje: String, alReintentar: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TarjetaAmancay {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Algo no salió bien", style = MaterialTheme.typography.titleMedium, color = AmancayTinta)
                Spacer(Modifier.height(4.dp))
                Text(mensaje, style = MaterialTheme.typography.bodyMedium, color = AmancayTintaSuave)
                Spacer(Modifier.height(16.dp))
                BotonPrimarioAmancay("Reintentar", alReintentar)
            }
        }
    }
}
