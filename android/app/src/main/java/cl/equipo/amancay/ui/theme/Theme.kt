package cl.equipo.amancay.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AmancayMostaza,
    onPrimary = AmancayTinta,
    secondary = AmancayBordoSuave,
    tertiary = AmancayMostazaSuave,
    background = Color(0xFF241318),
    surface = Color(0xFF33202A),
    onBackground = Color(0xFFFBEEDF),
    onSurface = Color(0xFFFBEEDF)
)

private val LightColorScheme = lightColorScheme(
    primary = AmancayBordo,
    onPrimary = Color.White,
    secondary = AmancayMostaza,
    onSecondary = AmancayTinta,
    tertiary = AmancayNaranjaIcono,
    background = AmancayFondo,
    onBackground = AmancayTinta,
    surface = AmancaySuperficie,
    onSurface = AmancayTinta,
    surfaceVariant = AmancaySuperficieVariante,
    onSurfaceVariant = AmancayTintaSuave,
    primaryContainer = AmancayBordoSuave,
    onPrimaryContainer = AmancayBordoOscuro,
    secondaryContainer = AmancayMostazaSuave,
    onSecondaryContainer = AmancayTinta,
    outline = AmancayBorde,
    outlineVariant = AmancayBorde
)

@Composable
fun RinconAmancayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Se desactiva el color dinámico por defecto para conservar la marca
    // crema / bordó / mostaza del diseño Figma en todos los dispositivos.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
