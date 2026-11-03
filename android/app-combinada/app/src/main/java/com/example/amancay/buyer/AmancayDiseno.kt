package com.example.amancay.buyer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.ui.Fig
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sistema visual del modo comprador (paleta propia de `buyer/`): barra
 * superior con la marca, chips con ícono, hero de Inicio, barra de
 * navegación inferior y banner de la próxima feria.
 *
 * El modo emprendedora no usa este archivo: todo lo que vive aquí es
 * exclusivo de la vista compradora.
 */

// ---------------------------------------------------------------------------
// Barra superior
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperiorAmancay(
    alVolver: (() -> Unit)? = null,
    contenidoSesion: (@Composable RowScope.() -> Unit)? = null
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = AmancayFondo),
        navigationIcon = {
            if (alVolver != null) {
                IconButton(onClick = alVolver) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver al inicio de la aplicación",
                        tint = AmancayBordo
                    )
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(Fig.LOGO),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Rincón Amancay",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    color = AmancayBordo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        },
        actions = {
            if (contenidoSesion != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = contenidoSesion)
                Spacer(Modifier.width(10.dp))
            }
        }
    )
}

/** Píldora "Iniciar sesión" de la barra superior. */
@Composable
fun BotonIniciarSesionAmancay(alClick: () -> Unit, habilitado: Boolean = true) {
    Button(
        onClick = alClick,
        enabled = habilitado,
        shape = RoundedCornerShape(50.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AmancayBordo,
            contentColor = Color.White,
            disabledContainerColor = AmancayBordo.copy(alpha = 0.45f),
            disabledContentColor = Color.White
        )
    ) {
        Text("Iniciar sesión", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------------------------------------------------------------------------
// Encabezados y etiquetas
// ---------------------------------------------------------------------------

@Composable
fun EtiquetaSeccion(texto: String) {
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
        color = AmancayBordo
    )
}

@Composable
fun TituloPantalla(titulo: String, bajada: String = "") {
    Column {
        Text(
            titulo,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            color = AmancayTinta
        )
        if (bajada.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(bajada, style = MaterialTheme.typography.bodyMedium, color = AmancayTintaSuave)
        }
    }
}

/** Título de sección con enlace «Ver todo» a la derecha (imagen de referencia). */
@Composable
fun EncabezadoSeccion(texto: String, alVerTodo: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            color = AmancayTinta,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (alVerTodo != null) {
            TextButton(onClick = alVerTodo, contentPadding = PaddingValues(start = 8.dp)) {
                Text(
                    "Ver todo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = AmancayBordo
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = AmancayBordo
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Buscador y chips de categoría
// ---------------------------------------------------------------------------

@Composable
fun BuscadorAmancay(
    valor: String,
    alCambiar: (String) -> Unit,
    placeholder: String = "¿Qué estás buscando?"
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = AmancayTintaSuave) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = AmancayTintaSuave)
        },
        trailingIcon = {
            if (valor.isNotEmpty()) {
                IconButton(onClick = { alCambiar("") }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Limpiar búsqueda",
                        tint = AmancayTintaSuave
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = AmancaySuperficie,
            unfocusedContainerColor = AmancaySuperficie,
            focusedBorderColor = AmancayBordo,
            unfocusedBorderColor = AmancayBorde,
            focusedTextColor = AmancayTinta,
            unfocusedTextColor = AmancayTinta,
            cursorColor = AmancayBordo
        )
    )
}

/** Ícono de Material asociado al nombre de la categoría (dinámica desde Firestore). */
fun iconoCategoriaAmancay(texto: String): ImageVector {
    val nombre = texto.lowercase()
    return when {
        "textil" in nombre -> Icons.Outlined.Checkroom
        "sabor" in nombre || "aliment" in nombre || "comest" in nombre -> Icons.Outlined.Restaurant
        "cer" in nombre -> Icons.Outlined.Coffee
        "bienestar" in nombre || "cosm" in nombre || "natural" in nombre -> Icons.Outlined.Spa
        "hogar" in nombre || "deco" in nombre || "mueble" in nombre -> Icons.Outlined.Storefront
        else -> Icons.Outlined.Storefront
    }
}

/** Fondo pastel estable por categoría; «Todos» usa la lila. */
fun colorCategoriaAmancay(texto: String): Color {
    val nombre = texto.lowercase()
    return when {
        "textil" in nombre -> AmancayChipRosa
        "sabor" in nombre || "aliment" in nombre || "comest" in nombre -> AmancayChipDurazno
        "cer" in nombre -> AmancayChipAmarillo
        "bienestar" in nombre || "cosm" in nombre || "natural" in nombre -> AmancayChipVerde
        else -> AmancayChipLila
    }
}

@Composable
fun ChipCategoriaAmancay(
    texto: String,
    icono: ImageVector,
    seleccionado: Boolean,
    alSeleccionar: () -> Unit
) {
    FilterChip(
        selected = seleccionado,
        onClick = alSeleccionar,
        label = { Text(texto, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = {
            Icon(icono, contentDescription = null, modifier = Modifier.size(17.dp))
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colorCategoriaAmancay(texto),
            labelColor = AmancayTinta,
            iconColor = AmancayTinta,
            selectedContainerColor = AmancayBordo,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        ),
        border = null,
        shape = RoundedCornerShape(50.dp)
    )
}

// ---------------------------------------------------------------------------
// Hero de Inicio
// ---------------------------------------------------------------------------

@Composable
fun HeroBienvenida(alExplorar: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1.2f)) {
            Text(
                "Hecho cerca,\nelegido con cariño.",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    lineHeight = 27.sp
                ),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = AmancayTinta
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Descubre productos creados por emprendedoras de Puerto Montt.",
                style = MaterialTheme.typography.bodyMedium,
                color = AmancayTintaSuave
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = alExplorar,
                shape = RoundedCornerShape(50.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmancayBordo,
                    contentColor = Color.White
                )
            ) {
                Text("Explorar productos", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        IlustracionHero(
            Modifier
                .weight(0.8f)
                .height(172.dp)
                .padding(top = 2.dp)
        )
    }
}

/**
 * Decoración del hero dibujada con Canvas (sin assets binarios): forma
 * orgánica durazno con flor, panel de texto y silueta de montañas.
 */
@Composable
private fun IlustracionHero(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxSize()) {
            val ancho = size.width
            val alto = size.height

            // Forma orgánica de fondo.
            val burbuja = Path().apply {
                moveTo(ancho * 0.40f, alto * 0.05f)
                cubicTo(ancho * 0.86f, -alto * 0.05f, ancho * 1.08f, alto * 0.24f, ancho * 0.97f, alto * 0.54f)
                cubicTo(ancho * 0.87f, alto * 0.82f, ancho * 0.48f, alto * 0.84f, ancho * 0.32f, alto * 0.60f)
                cubicTo(ancho * 0.20f, alto * 0.42f, ancho * 0.20f, alto * 0.12f, ancho * 0.40f, alto * 0.05f)
                close()
            }
            drawPath(burbuja, AmancayHeroDurazno)

            // Flor de seis pétalos sobre la forma.
            val centroX = ancho * 0.78f
            val centroY = alto * 0.24f
            val radio = ancho * 0.068f
            repeat(6) { indice ->
                val angulo = Math.PI / 3 * indice
                drawCircle(
                    color = AmancayMostaza,
                    radius = radio,
                    center = Offset(
                        centroX + (cos(angulo) * radio * 1.7).toFloat(),
                        centroY + (sin(angulo) * radio * 1.7).toFloat()
                    )
                )
            }
            drawCircle(AmancayBordo, radius = radio * 0.85f, center = Offset(centroX, centroY))

            // Montañas al pie.
            val montanas = Path().apply {
                moveTo(0f, alto)
                lineTo(0f, alto * 0.80f)
                lineTo(ancho * 0.24f, alto * 0.56f)
                lineTo(ancho * 0.44f, alto * 0.78f)
                lineTo(ancho * 0.64f, alto * 0.52f)
                lineTo(ancho * 0.84f, alto * 0.76f)
                lineTo(ancho, alto * 0.64f)
                lineTo(ancho, alto)
                close()
            }
            drawPath(montanas, AmancayHeroVerde)

            val montanasClaras = Path().apply {
                moveTo(0f, alto)
                lineTo(0f, alto * 0.90f)
                lineTo(ancho * 0.30f, alto * 0.72f)
                lineTo(ancho * 0.56f, alto * 0.90f)
                lineTo(ancho * 0.80f, alto * 0.76f)
                lineTo(ancho, alto * 0.88f)
                lineTo(ancho, alto)
                close()
            }
            drawPath(montanasClaras, AmancayHeroVerdeClaro)
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AmancayHeroRosa)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                "Productos locales\nHistorias reales",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                color = AmancayBordo,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Navegación inferior
// ---------------------------------------------------------------------------

enum class PestanaComprador(val etiqueta: String) {
    INICIO("Inicio"),
    EXPLORAR("Explorar"),
    FERIAS("Ferias"),
    CUENTA("Mi sesión")
}

@Composable
fun BarraInferiorComprador(
    actual: PestanaComprador,
    alSeleccionar: (PestanaComprador) -> Unit
) {
    NavigationBar(containerColor = AmancaySuperficie) {
        PestanaComprador.entries.forEach { pestana ->
            NavigationBarItem(
                selected = pestana == actual,
                onClick = { alSeleccionar(pestana) },
                icon = {
                    Icon(
                        iconoPestanaComprador(pestana),
                        contentDescription = pestana.etiqueta
                    )
                },
                label = {
                    Text(pestana.etiqueta, style = MaterialTheme.typography.labelMedium)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AmancayBordo,
                    selectedTextColor = AmancayBordo,
                    unselectedIconColor = AmancayTintaSuave,
                    unselectedTextColor = AmancayTintaSuave,
                    indicatorColor = AmancayBordoSuave
                )
            )
        }
    }
}

private fun iconoPestanaComprador(pestana: PestanaComprador): ImageVector = when (pestana) {
    PestanaComprador.INICIO -> Icons.Outlined.Home
    PestanaComprador.EXPLORAR -> Icons.Outlined.Search
    PestanaComprador.FERIAS -> Icons.Outlined.Event
    PestanaComprador.CUENTA -> Icons.Outlined.Person
}

// ---------------------------------------------------------------------------
// Banner de la próxima feria
// ---------------------------------------------------------------------------

@Composable
fun TarjetaFeriaProxima(
    nombre: String,
    lugar: String,
    alAbrir: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = alAbrir,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AmancayChipDurazno),
        border = BorderStroke(1.dp, AmancayBorde),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AmancaySuperficie),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Event, contentDescription = null, tint = AmancayBordo, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Próxima feria",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.6.sp,
                    color = AmancayBordoOscuro
                )
                Text(
                    nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AmancayTinta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lugar.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Place,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = AmancayTintaSuave
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            lugar,
                            style = MaterialTheme.typography.bodySmall,
                            color = AmancayTintaSuave,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(width = 66.dp, height = 52.dp)) {
                    val ancho = size.width
                    val alto = size.height
                    val montanas = Path().apply {
                        moveTo(0f, alto)
                        lineTo(0f, alto * 0.72f)
                        lineTo(ancho * 0.26f, alto * 0.34f)
                        lineTo(ancho * 0.50f, alto * 0.70f)
                        lineTo(ancho * 0.72f, alto * 0.28f)
                        lineTo(ancho, alto * 0.66f)
                        lineTo(ancho, alto)
                        close()
                    }
                    drawPath(montanas, AmancayHeroVerde)
                    val claro = Path().apply {
                        moveTo(0f, alto)
                        lineTo(0f, alto * 0.86f)
                        lineTo(ancho * 0.32f, alto * 0.60f)
                        lineTo(ancho * 0.58f, alto * 0.86f)
                        lineTo(ancho * 0.82f, alto * 0.66f)
                        lineTo(ancho, alto * 0.84f)
                        lineTo(ancho, alto)
                        close()
                    }
                    drawPath(claro, AmancayHeroVerdeClaro)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AmancayBordo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chips y badges
// ---------------------------------------------------------------------------

@Composable
fun ChipFiltroAmancay(
    texto: String,
    seleccionado: Boolean,
    alSeleccionar: () -> Unit
) {
    FilterChip(
        selected = seleccionado,
        onClick = alSeleccionar,
        label = { Text(texto) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AmancayBordo,
            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
            containerColor = AmancaySuperficie,
            labelColor = AmancayTinta
        ),
        shape = RoundedCornerShape(50.dp)
    )
}

@Composable
fun BadgeDisponibilidad(texto: String) {
    if (texto.isBlank()) return
    val esAgotado = texto.equals("Agotado", ignoreCase = true) ||
        texto.equals("No disponible", ignoreCase = true)
    val fondo = if (esAgotado) AmancayBordoSuave else AmancayVerdeFondo
    val color = if (esAgotado) AmancayBordoOscuro else AmancayVerdeTexto
    Surface(
        color = fondo,
        shape = RoundedCornerShape(50.dp)
    ) {
        Text(
            texto,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
fun EtiquetaCategoria(texto: String) {
    if (texto.isBlank()) return
    Text(
        texto.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = AmancayBordo
    )
}

// ---------------------------------------------------------------------------
// Tarjetas y botones
// ---------------------------------------------------------------------------

@Composable
fun TarjetaAmancay(
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AmancaySuperficie),
        border = BorderStroke(1.dp, AmancayBorde),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) { contenido() }
    }
}

@Composable
fun BotonPrimarioAmancay(texto: String, alClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = alClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AmancayBordo,
            contentColor = androidx.compose.ui.graphics.Color.White
        )
    ) {
        Text(texto, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
fun BotonMostazaAmancay(texto: String, alClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = alClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AmancayMostaza,
            contentColor = AmancayTinta
        )
    ) {
        Text(texto, modifier = Modifier.padding(vertical = 6.dp), fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------
// Placeholder de foto (foto grande estilo pantalla 09)
// ---------------------------------------------------------------------------

@Composable
fun FotoProductoAmancay(
    letra: String = "✿",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AmancayNaranjaFondo),
        contentAlignment = Alignment.Center
    ) {
        // Cuando haya imágenes (RF-CMP-03 completo) reemplazar por AsyncImage/Coil.
        // Por ahora se muestra un jarrón genérico como en Figma.
        Text("🏺", style = MaterialTheme.typography.displaySmall)
    }
}

@Composable
fun MiniaturaAmancay(letra: String = "🏺") {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AmancayNaranjaFondo),
        contentAlignment = Alignment.Center
    ) {
        Text(letra, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun IconoCategoriaAmancay(emoji: String = "◧") {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AmancayNaranjaIcono.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium, color = AmancayBordo)
    }
}

@Composable
fun ChipInfoAmancay(texto: String) {
    AssistChip(
        onClick = {},
        label = { Text(texto, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = AmancayMostazaSuave,
            labelColor = AmancayTinta
        ),
        border = null
    )
}
