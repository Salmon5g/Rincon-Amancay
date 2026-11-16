package com.example.amancay.ui

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.amancay.BuildConfig
import com.example.amancay.ui.theme.Dark
import com.example.amancay.ui.theme.Line
import com.example.amancay.ui.theme.Muted
import com.example.amancay.ui.theme.Wine
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object EnlacesPublicos {
    private val base get() = BuildConfig.PUBLIC_WEB_URL.trimEnd('/')
    fun inicio() = "$base/comprador"
    fun tienda(id: String) = "$base/comprador?tienda=${Uri.encode(id)}"
    fun asociacion(id: String) = "$base/comprador/directorio?vista=asociaciones&asociacion=${Uri.encode(id)}"
    fun feria(id: String) = "$base/comprador/ferias?feria=${Uri.encode(id)}"
}

fun imagenCodigoQr(titulo: String, enlace: String): Bitmap {
    val lado = 720
    val margen = 80
    val ancho = lado + margen * 2
    val alto = lado + margen * 2 + 150
    val bits = QRCodeWriter().encode(enlace, BarcodeFormat.QR_CODE, lado, lado, mapOf(EncodeHintType.MARGIN to 1))
    val pixeles = IntArray(lado * lado) { i -> if (bits.get(i % lado, i / lado)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    val qr = Bitmap.createBitmap(pixeles, lado, lado, Bitmap.Config.ARGB_8888)
    val imagen = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(imagen)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.drawBitmap(qr, margen.toFloat(), margen.toFloat(), null)
    val pinturaTitulo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF75264F.toInt()
        textSize = 46f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val tituloCorto = TextUtils.ellipsize(titulo, pinturaTitulo, (ancho - margen).toFloat(), TextUtils.TruncateAt.END).toString()
    canvas.drawText(tituloCorto, ancho / 2f, (lado + margen + 70).toFloat(), pinturaTitulo)
    val pinturaPie = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF816976.toInt()
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("Escanea para abrirlo en Rincón Amancay", ancho / 2f, (lado + margen + 125).toFloat(), pinturaPie)
    qr.recycle()
    return imagen
}

private fun nombreArchivoQr(titulo: String): String {
    val base = titulo.lowercase().replace(Regex("[^a-z0-9áéíóúñü]+"), "-").trim('-').take(40).ifBlank { "rincon-amancay" }
    return "qr-$base.png"
}

fun compartirCodigoQr(context: Context, titulo: String, enlace: String, imagen: Bitmap) {
    val carpeta = File(context.cacheDir, "qr").apply { mkdirs() }
    carpeta.listFiles()?.forEach { it.delete() }
    val archivo = File(carpeta, nombreArchivoQr(titulo))
    archivo.outputStream().use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "$titulo en Rincón Amancay: $enlace")
        clipData = ClipData.newRawUri(titulo, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(envio, "Compartir código QR"))
}

fun puedeGuardarQrEnGaleria(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

fun guardarCodigoQrEnGaleria(context: Context, titulo: String, imagen: Bitmap) {
    check(puedeGuardarQrEnGaleria())
    val resolver = context.contentResolver
    val valores = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, nombreArchivoQr(titulo))
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Rincon Amancay")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valores)
        ?: error("No se pudo crear la imagen en la galería.")
    try {
        resolver.openOutputStream(uri)?.use { imagen.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ?: error("No se pudo escribir la imagen en la galería.")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
}

@Composable
fun BotonCodigoQr(texto: String, titulo: String, enlace: String, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    OutlineButton(texto, { abierto = true }, modifier)
    if (abierto) DialogoCodigoQr(titulo, enlace) { abierto = false }
}

@Composable
fun DialogoCodigoQr(titulo: String, enlace: String, alCerrar: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagen by produceState<Bitmap?>(null, titulo, enlace) {
        value = withContext(Dispatchers.Default) { imagenCodigoQr(titulo, enlace) }
    }
    var mensaje by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = alCerrar,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Código QR", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Dark)
                Box(Modifier.size(240.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, Line, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    val actual = imagen
                    if (actual == null) CircularProgressIndicator(color = Wine)
                    else Image(actual.asImageBitmap(), contentDescription = "Código QR de $titulo", modifier = Modifier.size(240.dp))
                }
                Text(enlace, fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.heightIn(max = 60.dp))
                mensaje?.let { Text(it, fontSize = 13.sp, color = Wine, textAlign = TextAlign.Center) }
                PrimaryButton("Compartir imagen", {
                    val actual = imagen ?: return@PrimaryButton
                    runCatching { compartirCodigoQr(context, titulo, enlace, actual) }
                        .onFailure { mensaje = "No se pudo compartir el código QR." }
                }, enabled = imagen != null)
                if (puedeGuardarQrEnGaleria()) {
                    OutlineButton("Guardar en Fotos", {
                        val actual = imagen ?: return@OutlineButton
                        scope.launch {
                            mensaje = runCatching { withContext(Dispatchers.IO) { guardarCodigoQrEnGaleria(context, titulo, actual) } }
                                .fold({ "Código QR guardado en Fotos › Rincon Amancay." }, { "No se pudo guardar el código QR." })
                        }
                    }, enabled = imagen != null)
                }
                GhostButton("Cerrar", alCerrar)
            }
        },
        confirmButton = {}
    )
}
