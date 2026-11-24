package com.example.amancay.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.amancay.ui.theme.Wine

/** Cámara de producto con una guía de encuadre simple y visible. */
@Composable
fun CamaraProductoGuiada(
    onFoto: (Bitmap) -> Unit,
    onCancelar: () -> Unit,
    onGaleria: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var tienePermiso by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var error by remember { mutableStateOf<String?>(null) }
    var capturando by remember { mutableStateOf(false) }
    var proveedor by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        tienePermiso = concedido
        if (!concedido) error = "Puedes permitir la cámara en Ajustes o elegir una foto de tu galería."
    }

    LaunchedEffect(Unit) {
        if (!tienePermiso) pedirPermiso.launch(Manifest.permission.CAMERA)
    }

    fun tomarFoto() {
        if (capturando || !tienePermiso) return
        capturando = true
        try {
            imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toBitmap()
                        val rotacion = image.imageInfo.rotationDegrees
                        val fotoOrientada = if (rotacion == 0) bitmap else {
                            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotacion.toFloat()) }, true)
                                .also { if (it !== bitmap) bitmap.recycle() }
                        }
                        onFoto(fotoOrientada)
                    } catch (_: Exception) {
                        error = "No se pudo preparar la foto. Inténtalo de nuevo."
                        capturando = false
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    error = "No se pudo tomar la foto. Vuelve a intentarlo."
                    capturando = false
                }
            })
        } catch (_: Exception) {
            error = "No se encontró una cámara disponible en este teléfono."
            capturando = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF171312)).padding(horizontal = 18.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(62.dp)
        ) {
            androidx.compose.material3.IconButton(onClick = onCancelar, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Text("Foto de tu producto", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }

        Surface(
            color = Color(0xCC34242A),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("Centra el producto en el marco", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Busca luz de frente y deja un poco de espacio alrededor.", color = Color(0xFFF5E9E4), fontSize = 13.sp)
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (tienePermiso && error == null) {
                AndroidView(
                    factory = { viewContext ->
                        PreviewView(viewContext).also { previewView ->
                            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                            preview.setSurfaceProvider(ContextCompat.getMainExecutor(viewContext), previewView.surfaceProvider)
                            try {
                                val future = ProcessCameraProvider.getInstance(viewContext)
                                future.addListener({
                                    try {
                                        val cameraProvider = future.get()
                                        cameraProvider.unbindAll()
                                        if (activity == null) {
                                            error = "No se pudo iniciar la cámara en esta pantalla."
                                        } else {
                                            val camera = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                                CameraSelector.DEFAULT_BACK_CAMERA
                                            } else CameraSelector.DEFAULT_FRONT_CAMERA
                                            cameraProvider.bindToLifecycle(activity, camera, preview, imageCapture)
                                            proveedor = cameraProvider
                                        }
                                    } catch (_: Exception) {
                                        error = "No se pudo iniciar la cámara. Puedes elegir una foto de la galería."
                                    }
                                }, ContextCompat.getMainExecutor(viewContext))
                            } catch (_: Exception) {
                                error = "No se pudo iniciar la cámara. Puedes elegir una foto de la galería."
                            }
                        }
                    },
                    onRelease = { proveedor?.unbindAll() },
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val ancho = size.width
                        val alto = size.height
                        val linea = Color.White.copy(alpha = .78f)
                        drawRoundRect(color = Color(0xFFFFB279), style = Stroke(width = 3.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()))
                        for (parte in 1..2) {
                            val x = ancho * parte / 3f
                            val y = alto * parte / 3f
                            drawLine(linea, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, alto), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(linea, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(ancho, y), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                    Text(error ?: "Necesitamos permiso para abrir la cámara.", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("También puedes escoger una foto guardada en tu teléfono.", color = Color(0xFFF5E9E4), fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }

        Text("Consejo: usa un fondo sencillo para que tu producto destaque.", color = Color(0xFFF5E9E4), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp)
        ) {
            Button(onClick = onGaleria, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A3438)), modifier = Modifier.height(48.dp)) {
                Text("Galería", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = ::tomarFoto,
                enabled = tienePermiso && error == null && !capturando,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, disabledContainerColor = Color.Gray),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.size(72.dp)
            ) {
                Box(Modifier.size(56.dp).background(Wine, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Tomar foto", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            Button(onClick = onCancelar, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A3438)), modifier = Modifier.height(48.dp)) {
                Text("Volver", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
