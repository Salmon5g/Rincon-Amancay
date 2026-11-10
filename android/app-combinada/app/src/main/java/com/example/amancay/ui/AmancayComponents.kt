package com.example.amancay.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.amancay.R
import com.example.amancay.ui.theme.*

// Assets exactos del diseño Figma (vals normales para evitar desfases de const entre builds)
object Fig {
    @DrawableRes val LOGO: Int = R.drawable.amancay_logo_color
    @DrawableRes val FLORAL_DERECHA: Int = R.drawable.amancay_floral_derecha
    @DrawableRes val HOJAS_INFERIORES: Int = R.drawable.amancay_hojas_inferiores
    @DrawableRes val FLORES_INFERIORES: Int = R.drawable.amancay_flores_inferiores
    @DrawableRes val NAV_INICIO: Int = R.drawable.fig_05
    @DrawableRes val NAV_PRODUCTOS: Int = R.drawable.fig_06
    @DrawableRes val NAV_VENDER: Int = R.drawable.fig_07
    @DrawableRes val NAV_MAS: Int = R.drawable.fig_08
    @DrawableRes val CARD_GRID: Int = R.drawable.fig_03
    @DrawableRes val CARD_PLUS: Int = R.drawable.fig_04
    @DrawableRes val CAMERA: Int = R.drawable.fig_10
    @DrawableRes val EDIT: Int = R.drawable.fig_62
    @DrawableRes val VASE_BIG: Int = R.drawable.fig_17
    @DrawableRes val VASE_REVIEW: Int = R.drawable.fig_47
    @DrawableRes val VASE_THUMB: Int = R.drawable.fig_61
    @DrawableRes val VASE_THUMB2: Int = R.drawable.fig_63
    @DrawableRes val VASE_HERO: Int = R.drawable.fig_72
}

@Composable
fun ProductWizardTopBar(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 18.dp)) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart).heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Wine, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(8.dp))
            Text("Volver", color = Wine, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFFFFAF5))
                    .border(1.dp, Color(0xFFF7DED0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(Fig.LOGO), contentDescription = null, modifier = Modifier.size(39.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Rincón", fontSize = 13.sp, color = Muted)
                Text("Amancay", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Wine, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            }
        }
        Spacer(Modifier.width(76.dp).align(Alignment.CenterEnd))
    }
}

@Composable
fun BotanicalBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFFFFFAF5))) {
        Image(
            painter = painterResource(Fig.FLORAL_DERECHA),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).width(230.dp).height(390.dp),
            contentScale = ContentScale.Fit,
            alpha = 0.74f
        )
        Image(
            painter = painterResource(Fig.HOJAS_INFERIORES),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomStart).width(150.dp).height(100.dp),
            contentScale = ContentScale.Fit,
            alpha = 0.82f
        )
        Image(
            painter = painterResource(Fig.FLORES_INFERIORES),
            contentDescription = null,
            modifier = Modifier.align(Alignment.BottomEnd).width(160.dp).height(95.dp),
            contentScale = ContentScale.Fit,
            alpha = 0.78f
        )
    }
}

@Composable
fun AmancayTopBar(onHelp: () -> Unit, onExit: (() -> Unit)? = null, etiquetaEspacio: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (etiquetaEspacio) 50.dp else 44.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFF8F2))
                .border(1.dp, Color(0xFFF7DED0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Fig.LOGO),
                contentDescription = "Rincón Amancay",
                modifier = Modifier.size(if (etiquetaEspacio) 42.dp else 38.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Rincón", fontSize = if (etiquetaEspacio) 14.sp else 13.sp, color = Muted)
            Text("Amancay", fontSize = if (etiquetaEspacio) 22.sp else 19.sp, fontWeight = FontWeight.Bold, color = Wine, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
        }
        Spacer(Modifier.weight(1f))
        if (onExit != null) {
            TextButton(
                onClick = onExit,
                modifier = Modifier.heightIn(min = 48.dp).background(if (etiquetaEspacio) Color(0xFFF8E9EA) else Color.Transparent, RoundedCornerShape(24.dp)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("Salir", fontWeight = FontWeight.Bold, color = Wine)
            }
        }
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(CircleShape)
                .background(Color(0xFFF8E9EA))
                .clickable { onHelp() },
            contentAlignment = Alignment.Center
        ) {
            Text("?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Wine)
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, fontSize: TextUnit = 16.sp) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Wine,
            disabledContainerColor = Wine.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 18.dp)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, fontSize: TextUnit = 16.sp, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Wine),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Wine),
        contentPadding = PaddingValues(vertical = 18.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = fontSize)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(text, color = Wine, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmancayField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    // Estado local: la letra aparece al instante y el foco no se pierde
    // aunque la pantalla se recomponga; se sincroniza hacia el borrador.
    var text by remember(value) { mutableStateOf(value) }
    Column {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dark)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            placeholder = { Text(placeholder, color = Muted, fontSize = 15.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Wine, unfocusedBorderColor = Line,
                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                cursorColor = Wine,
                focusedTextColor = Dark, unfocusedTextColor = Dark
            )
        )
    }
}

@Composable
fun StepHeader(paso: String, titulo: String, subtitulo: String) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text("NUEVO PRODUCTO", fontSize = 13.sp, color = Color(0xFFEF5452), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            if (titulo == "Agrega una foto") "Agrega\nuna foto" else titulo,
            modifier = Modifier.widthIn(max = if (titulo == "Agrega una foto") 250.dp else 380.dp),
            fontSize = if (titulo == "Agrega una foto") 43.sp else 38.sp,
            lineHeight = if (titulo == "Agrega una foto") 42.sp else 42.sp,
            color = Wine,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            fontWeight = FontWeight.Bold
        )
        Text(subtitulo, fontSize = 16.sp, lineHeight = 21.sp, color = Muted)
        Spacer(Modifier.height(8.dp))
        Text(paso, fontSize = 14.sp, color = Wine, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SelectableChip(text: String, selected: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) Wine else Color.White
    val fg = if (selected) Color.White else Dark
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Wine else Line)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            fontSize = 15.sp, color = fg,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dark)
            Text(subtitle, fontSize = 13.sp, color = Muted)
        }
        Text(
            if (checked) "Activado" else "Apagado",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Wine,
            modifier = Modifier.padding(end = 8.dp)
        )
        Switch(
            checked = checked, onCheckedChange = onChange,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Wine,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = Line
            )
        )
    }
}

/** Barra de progreso del asistente: se colorea por cada paso superado. */
@Composable
fun StepProgressBar(actual: Int, total: Int = 5) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..total) {
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .background(if (i <= actual) Wine else Color(0xFFF9E9E3))
                    .border(1.dp, if (i <= actual) Wine else Line, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(if (i < actual) "✓" else i.toString(), color = if (i <= actual) Color.White else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (i < total) Spacer(Modifier.weight(1f).height(3.dp).background(if (i < actual) Wine else Line))
        }
    }
}

/** Foto real si está disponible. Una ruta privada sin vista previa se marca con la cámara, no con una foto ficticia. */
@Composable
fun ProductImage(
    @DrawableRes fallback: Int,
    size: Dp,
    uri: Uri? = null,
    bitmap: Bitmap? = null,
    hasPhoto: Boolean = false,
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    contentScale: ContentScale = ContentScale.Fit
) {
    val puedeAmpliar = hasPhoto && (bitmap != null || uri != null)
    var mostrarAmpliada by remember(uri, bitmap, hasPhoto) { mutableStateOf(false) }
    var escala by remember(uri, bitmap) { mutableFloatStateOf(1f) }
    var desplazamiento by remember(uri, bitmap) { mutableStateOf(Offset.Zero) }
    fun abrirAmpliada() {
        escala = 1f
        desplazamiento = Offset.Zero
        mostrarAmpliada = true
    }
    val transformacion = rememberTransformableState { cambioEscala, cambioPosicion, _ ->
        escala = (escala * cambioEscala).coerceIn(1f, 5f)
        desplazamiento = if (escala > 1f) desplazamiento + cambioPosicion else Offset.Zero
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(CardPink)
            .clickable(enabled = puedeAmpliar, onClick = ::abrirAmpliada),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = hasPhoto, label = "foto") { foto ->
        if (foto && bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = if (puedeAmpliar) "Ampliar foto del producto" else null,
                modifier = Modifier.fillMaxSize(), contentScale = contentScale
            )
        } else if (foto && uri != null) {
            AsyncImage(
                model = uri, contentDescription = if (puedeAmpliar) "Ampliar foto del producto" else null,
                modifier = Modifier.fillMaxSize(), contentScale = contentScale
            )
        } else if (foto) {
            Image(
                painter = painterResource(Fig.CAMERA), contentDescription = "Imagen guardada",
                modifier = Modifier.padding(size * .28f), contentScale = ContentScale.Fit
            )
        } else {
            Image(
                painter = painterResource(fallback), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        }
        }
    }

    if (mostrarAmpliada && puedeAmpliar) {
        Dialog(
            onDismissRequest = { mostrarAmpliada = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0xF20D0A0C)),
                contentAlignment = Alignment.Center
            ) {
                val gestoZoom = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .graphicsLayer {
                        scaleX = escala
                        scaleY = escala
                        translationX = desplazamiento.x
                        translationY = desplazamiento.y
                    }
                    .pointerInput(uri, bitmap) {
                        detectTapGestures(onDoubleTap = {
                            escala = if (escala > 1f) 1f else 2f
                            desplazamiento = Offset.Zero
                        })
                    }
                    .transformable(transformacion)

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Foto ampliada del producto",
                        modifier = gestoZoom,
                        contentScale = ContentScale.Fit
                    )
                } else if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Foto ampliada del producto",
                        modifier = gestoZoom,
                        contentScale = ContentScale.Fit
                    )
                }

                TextButton(
                    onClick = { mostrarAmpliada = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) { Text("Cerrar  ×", fontWeight = FontWeight.Bold) }

                Text(
                    "Pellizca o usa doble toque para ampliar",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                )
            }
        }
    }
}

@Composable
fun SuccessCheck() {
    Box(
        modifier = Modifier.size(64.dp).background(GreenBg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = GreenText, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun DraftBanner(onContinue: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BannerBg),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BannerStroke),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tu borrador está guardado", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Dark)
                Text("Puedes continuar cuando quieras.", fontSize = 13.sp, color = Muted)
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = Wine),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text("Continuar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
