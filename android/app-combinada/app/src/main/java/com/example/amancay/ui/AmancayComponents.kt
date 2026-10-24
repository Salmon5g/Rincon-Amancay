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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.amancay.R
import com.example.amancay.ui.theme.*

// Assets exactos del diseño Figma (vals normales para evitar desfases de const entre builds)
object Fig {
    @DrawableRes val LOGO: Int = R.drawable.fig_01
    @DrawableRes val NAV_INICIO: Int = R.drawable.fig_05
    @DrawableRes val NAV_PRODUCTOS: Int = R.drawable.fig_06
    @DrawableRes val NAV_VENDER: Int = R.drawable.fig_07
    @DrawableRes val NAV_MAS: Int = R.drawable.fig_08
    @DrawableRes val CARD_GRID: Int = R.drawable.fig_03
    @DrawableRes val CARD_PLUS: Int = R.drawable.fig_04
    @DrawableRes val CAMERA: Int = R.drawable.fig_10
    @DrawableRes val VASE_BIG: Int = R.drawable.fig_17
    @DrawableRes val VASE_REVIEW: Int = R.drawable.fig_47
    @DrawableRes val VASE_THUMB: Int = R.drawable.fig_61
    @DrawableRes val VASE_THUMB2: Int = R.drawable.fig_63
    @DrawableRes val VASE_HERO: Int = R.drawable.fig_72
}

@Composable
fun AmancayTopBar(onHelp: () -> Unit, onExit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(Fig.LOGO),
            contentDescription = "Rincón Amancay",
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Rincón", fontSize = 13.sp, color = Muted)
            Text("Amancay", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Wine)
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onExit,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("Salir", fontWeight = FontWeight.Bold, color = Wine)
        }
        Text(
            "?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Wine,
            modifier = Modifier
                .clickable { onHelp() }
                .padding(8.dp)
        )
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
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, fontSize: TextUnit = 16.sp) {
    OutlinedButton(
        onClick = onClick,
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
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
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
        Text(paso, fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Bold)
        Text("NUEVO PRODUCTO", fontSize = 12.sp, color = Wine, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(titulo, fontSize = 30.sp, color = Dark)
        Text(subtitulo, fontSize = 15.sp, color = Muted)
    }
}

@Composable
fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) Wine else Color.White
    val fg = if (selected) Color.White else Dark
    Surface(
        onClick = onClick,
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..total) {
            val f by animateFloatAsState(
                if (i <= actual) 1f else 0f, tween(400), label = "paso$i"
            )
            Box(
                Modifier.weight(1f).height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Line)
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(f)
                        .clip(RoundedCornerShape(3.dp)).background(Wine)
                )
            }
        }
    }
}

/** Imagen de producto: foto real si existe, si no el jarrón del diseño. */
@Composable
fun ProductImage(
    @DrawableRes fallback: Int,
    size: Dp,
    uri: Uri? = null,
    bitmap: Bitmap? = null,
    hasPhoto: Boolean = false,
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(CardPink),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = hasPhoto, label = "foto") { foto ->
        if (foto && bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        } else if (foto && uri != null) {
            AsyncImage(
                model = uri, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(fallback), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
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
