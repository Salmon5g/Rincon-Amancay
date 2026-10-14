package com.example.amancay.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.data.*
import com.example.amancay.ui.theme.*

// ---------- Scaffold de pasos ----------

@Composable
fun StepScaffold(
    paso: String, titulo: String, subtitulo: String,
    onNext: () -> Unit, onBack: () -> Unit, onSaveExit: () -> Unit,
    nextLabel: String = "Continuar", nextEnabled: Boolean = true, pasoActual: Int = 1,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        StepHeader(paso, titulo, subtitulo)
        Spacer(Modifier.height(12.dp))
        StepProgressBar(actual = pasoActual)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Atrás", onBack, modifier = Modifier.weight(1f), fontSize = 14.sp)
            PrimaryButton(nextLabel, onNext, modifier = Modifier.weight(1f), enabled = nextEnabled, fontSize = 14.sp)
        }
        GhostButton("Salir y guardar", onSaveExit)
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- HOME ----------

@Composable
fun HomeScreen(
    borrador: Product?,
    publicados: Int,
    ventasTotal: Long,
    ventasCount: Int,
    linkCompartido: Boolean,
    onAgregar: () -> Unit,
    onContinuarBorrador: () -> Unit,
    onMisProductos: () -> Unit,
    onRegistrarVenta: () -> Unit,
    onVerCatalogo: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("HOLA, ANA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Wine)
        Text("Tu tienda, paso a paso", fontSize = 32.sp, color = Dark)
        Text(
            "Administra productos, ventas y visibilidad desde un solo lugar.",
            fontSize = 15.sp, color = Muted
        )
        Spacer(Modifier.height(14.dp))
        if (borrador != null) {
            DraftBanner(onContinue = onContinuarBorrador)
            Spacer(Modifier.height(14.dp))
        }
        Text("HOY, 10 DE OCTUBRE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentRed)
        Spacer(Modifier.height(6.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Wine),
            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("¿Qué quieres hacer hoy?", fontSize = 28.sp, color = Color.White)
                Text(
                    "Elige una acción. Te acompañamos en cada paso.",
                    fontSize = 15.sp, color = Color.White
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAgregar, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 18.dp)
                ) {
                    Text("+ Agregar producto", color = Dark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.weight(1f).clickable { onMisProductos() }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Image(
                        painter = painterResource(Fig.CARD_GRID), contentDescription = null,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Mis productos", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dark)
                    Text("8 publicados · 2 borradores", fontSize = 13.sp, color = Muted)
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.weight(1f).clickable { onRegistrarVenta() }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Image(
                        painter = painterResource(Fig.CARD_PLUS), contentDescription = null,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Registrar venta", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dark)
                    Text("Descuenta stock automáticamente", fontSize = 13.sp, color = Muted)
                }
            }
        }
        ProgressChecklist(
            tieneProducto = publicados > 0,
            tieneVenta = ventasCount > 0,
            compartido = linkCompartido,
            onVerCatalogo = onVerCatalogo
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ProgressChecklist(
    tieneProducto: Boolean,
    tieneVenta: Boolean,
    compartido: Boolean,
    onVerCatalogo: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Tu progreso", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Dark)
            Spacer(Modifier.height(8.dp))
            CheckRow("Agrega tu primer producto", tieneProducto)
            CheckRow("Registra tu primera venta", tieneVenta)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                CheckDot(compartido)
                Spacer(Modifier.width(10.dp))
                Text("Comparte tu catálogo", fontSize = 14.sp, color = Dark, modifier = Modifier.weight(1f))
                if (!compartido) {
                    TextButton(onClick = onVerCatalogo, contentPadding = PaddingValues(0.dp)) {
                        Text("Ver", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Wine)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(text: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        CheckDot(done)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = Dark)
    }
}

@Composable
private fun CheckDot(done: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(if (done) GreenBg else Color.Transparent, CircleShape)
            .border(1.5.dp, if (done) GreenText else Line, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            Icon(Icons.Default.Check, contentDescription = null, tint = GreenText, modifier = Modifier.size(14.dp))
        }
    }
}

// ---------- PASO 1 FOTO ----------

@Composable
fun PhotoStep(
    draft: DraftState, bump: () -> Unit,
    onTakePhoto: () -> Unit, onPickPhoto: () -> Unit,
    onNext: () -> Unit, onBack: () -> Unit, onSaveExit: () -> Unit
) {
    StepScaffold("Paso 1 de 5", "Primero, una foto", "Muéstrale a tus clientes lo que vendes.", onNext, onBack, onSaveExit, pasoActual = 1) {
        Crossfade(targetState = draft.fotoLista, label = "foto") { lista ->
        if (lista) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ProductImage(
                    fallback = Fig.VASE_BIG, size = 170.dp,
                    uri = draft.photoUri, bitmap = draft.photoBitmap, hasPhoto = true
                )
                Spacer(Modifier.height(10.dp))
                Surface(color = GreenBg, shape = RoundedCornerShape(20.dp)) {
                    Text(
                        "Foto lista", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GreenText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("La imagen está preparada para subir.", fontSize = 13.sp, color = Muted)
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                color = Color.White,
                modifier = Modifier.fillMaxWidth().clickable { onPickPhoto() }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 28.dp, horizontal = 20.dp)
                ) {
                    Image(
                        painter = painterResource(Fig.CAMERA), contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Agrega una foto", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Dark)
                    Text(
                        "La comprimiremos antes de subirla para ahorrar datos.",
                        fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center
                    )
                }
            }
        }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Usar cámara", onTakePhoto)
        Spacer(Modifier.height(10.dp))
        OutlineButton("Elegir galería", onPickPhoto)
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- PASO 2 BASICO ----------

@Composable
fun BasicsStep(draft: DraftState, bump: () -> Unit, onNext: () -> Unit, onBack: () -> Unit, onSaveExit: () -> Unit) {
    StepScaffold("Paso 2 de 5", "Cuéntanos lo básico", "Usaremos estos datos para mostrar tu producto.", onNext, onBack, onSaveExit, nextEnabled = draft.nombre.isNotBlank(), pasoActual = 2) {
        AmancayField(draft.nombre, { draft.nombre = it; bump() }, "Nombre del producto", "")
        Spacer(Modifier.height(16.dp))
        Text("¿Qué tipo de producto es?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dark)
        Spacer(Modifier.height(10.dp))
        val tipos = ProductType.values().toList()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tipos.take(2).forEach { t ->
                    SelectableChip(t.label, draft.tipo == t) { draft.tipo = t; bump() }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tipos.drop(2).forEach { t ->
                    SelectableChip(t.label, draft.tipo == t) { draft.tipo = t; bump() }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Elegir un tipo adapta las preguntas del siguiente paso.", fontSize = 13.sp, color = Muted)
    }
}

// ---------- PASO 3 DETALLES ----------

@Composable
fun DetailsStep(draft: DraftState, bump: () -> Unit, onNext: () -> Unit, onBack: () -> Unit, onSaveExit: () -> Unit) {
    val titulo = when (draft.tipo) {
        ProductType.ALIMENTOS -> "Detalles de alimentos"
        ProductType.DECORACION -> "Detalles de decoración"
        ProductType.BIENESTAR -> "Detalles de bienestar"
        ProductType.PAPELERIA -> "Detalles de papelería"
        ProductType.TEXTIL -> "Detalles de textil"
        null -> "Detalles de decoración"
    }
    StepScaffold("Paso 3 de 5", titulo, "Solo te pedimos lo que ayuda a decidir un encargo.", onNext, onBack, onSaveExit, pasoActual = 3) {
        AmancayField(draft.material, { draft.material = it; bump() }, "Material o ingredientes", "")
        Spacer(Modifier.height(14.dp))
        AmancayField(draft.tamano, { draft.tamano = it; bump() }, "Tamaño o formato", "")
        Spacer(Modifier.height(14.dp))
        AmancayField(draft.cuidados, { draft.cuidados = it; bump() }, "Cuidados o información útil", "", singleLine = false, minLines = 3)
        Spacer(Modifier.height(10.dp))
        Text("No necesitas escribir mucho. Puedes completar o editar después.", fontSize = 13.sp, color = Muted)
    }
}

// ---------- PASO 4 PRECIO ----------

@Composable
fun PriceStep(draft: DraftState, bump: () -> Unit, onNext: () -> Unit, onBack: () -> Unit, onSaveExit: () -> Unit) {
    StepScaffold("Paso 4 de 5", "Precio, variantes y stock", "Cada variante puede tener su propio precio, foto y cantidad.", onNext, onBack, onSaveExit, nextEnabled = draft.variantes.any { it.precioLong() > 0L }, pasoActual = 4) {
        draft.variantes.forEachIndexed { i, v ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Variante ${i + 1}", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = Wine, modifier = Modifier.weight(1f)
                        )
                        if (draft.variantes.size > 1) {
                            IconButton(onClick = { draft.variantes.removeAt(i); bump() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Quitar", tint = Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    AmancayField(v.nombre, { v.nombre = it; bump() }, "Nombre", "")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { AmancayField(v.precio, { v.precio = it.filter { c -> c.isDigit() }; bump() }, "Precio", "", keyboardType = KeyboardType.Number) }
                        Box(Modifier.weight(1f)) { AmancayField(v.stock, { v.stock = it.filter { c -> c.isDigit() }; bump() }, "Stock", "", keyboardType = KeyboardType.Number) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedButton(
            onClick = { draft.variantes.add(Variant(nombre = "Variante ${draft.variantes.size + 1}")); bump() },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, Wine)
        ) {
            Text("+ Añadir variante", color = Wine, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        if (draft.variantes.none { it.precioLong() > 0L }) {
            Spacer(Modifier.height(6.dp))
            Text("Agrega al menos un precio para continuar.", fontSize = 13.sp, color = Muted)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- PASO 5 REVISA ----------

@Composable
fun ReviewStep(draft: DraftState, bump: () -> Unit, onPublish: () -> Unit, onBack: () -> Unit, onSaveExit: () -> Unit) {
    StepScaffold("Paso 5 de 5", "Revisa y publica", "Tú decides cuándo se muestra y cómo se descuenta.", onPublish, onBack, onSaveExit, nextLabel = "Publicar producto", pasoActual = 5) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductImage(
                    fallback = Fig.VASE_REVIEW, size = 92.dp,
                    uri = draft.photoUri, bitmap = draft.photoBitmap, hasPhoto = draft.fotoLista
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("DECORACIÓN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Wine)
                    Text(draft.nombre.ifBlank { "Jarrón Tierra" }, fontSize = 24.sp, color = Dark)
                    Text(
                        draft.material.ifBlank { "Pieza de gres hecha a mano." },
                        fontSize = 13.sp, color = Muted
                    )
                    Text(
                        formatPrice(draft.variantes.firstOrNull()?.precioLong() ?: 28500),
                        fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Dark
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        ToggleRow("Disponible para encargo", "Si lo apagas, se oculta del catálogo.", draft.disponibleEncargo) { draft.disponibleEncargo = it; bump() }
        ToggleRow("Publicar ahora", "El producto aparecerá en tu vitrina pública.", draft.publicarAhora) { draft.publicarAhora = it; bump() }
        Spacer(Modifier.height(4.dp))
        Text("Manejo de stock", fontSize = 14.sp, color = Muted)
        Text("Por encargo", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Wine)
        Spacer(Modifier.height(8.dp))
    }
}

// ---------- CATALOGO / MIS PRODUCTOS ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    productos: List<Product>,
    onEdit: (Product) -> Unit,
    onDelete: (Product) -> Unit,
    onCreate: () -> Unit,
    onPreview: (Product) -> Unit,
    settings: ShopSettings,
    onSettings: (ShopSettings) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var toDelete by remember { mutableStateOf<Product?>(null) }
    val filtrados = when (tab) {
        1 -> productos.filter { it.publicado }
        2 -> productos.filter { it.esBorrador }
        else -> productos
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("MI CATÁLOGO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Wine)
            Text("Mis productos", fontSize = 30.sp, color = Dark)
            Text("Edita, oculta o elimina sin perder el control.", fontSize = 15.sp, color = Muted)
            Spacer(Modifier.height(12.dp))
            ToggleRow("Visibilidad masiva", "", settings.visibilidadMaxima) { onSettings(settings.copy(visibilidadMaxima = it)) }
            ToggleRow("Valores por defecto", "", settings.valoresPorDefecto) { onSettings(settings.copy(valoresPorDefecto = it)) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectableChip("Todos · ${productos.size}", tab == 0) { tab = 0 }
                SelectableChip("Publicados · ${productos.count { it.publicado }}", tab == 1) { tab = 1 }
                SelectableChip("Borradores · ${productos.count { it.esBorrador }}", tab == 2) { tab = 2 }
            }
            Spacer(Modifier.height(14.dp))
        }
        items(filtrados, key = { it.id }) { p ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).animateItem().clickable { onPreview(p) }
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProductImage(
                        fallback = Fig.VASE_THUMB, size = 68.dp,
                        uri = p.photoUri, bitmap = p.photoBitmap, hasPhoto = p.tieneFoto
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dark)
                        Text(
                            "${formatPrice(p.precioPrincipal())} · ${p.stockTotal()} ${if (p.stockTotal() == 1) "unidad" else "unidades"}",
                            fontSize = 13.sp, color = Muted
                        )
                        if (!p.esBorrador) {
                            Text("Visible", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GreenText)
                        } else {
                            Text("Borrador", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Editar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Wine,
                            modifier = Modifier.clickable { onEdit(p) }.padding(4.dp)
                        )
                        Text(
                            "Eliminar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentRed,
                            modifier = Modifier.clickable { toDelete = p }.padding(4.dp)
                        )
                    }
                }
            }
        }
        item {
            PrimaryButton("+ Crear otro producto", onCreate)
            Spacer(Modifier.height(16.dp))
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            text = {
                PopIn {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("¿Eliminar producto?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Dark)
                    Text(
                        "Se eliminará \"${p.nombre}\" de tu tienda. Esta acción no se puede deshacer.",
                        fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onDelete(p); toDelete = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) { Text("Sí, eliminar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    Spacer(Modifier.height(8.dp))
                    OutlineButton("Cancelar", { toDelete = null })
                }
                }
            },
            confirmButton = {}
        )
    }
}

// ---------- VISTA PUBLICA ----------

@Composable
fun PreviewCatalogScreen(producto: Product?, onShare: () -> Unit, onContact: () -> Unit) {
    val p = producto
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text("VISTA PÚBLICA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Wine, modifier = Modifier.fillMaxWidth())
        Text("Así ven tu catálogo", fontSize = 30.sp, color = Dark, modifier = Modifier.fillMaxWidth())
        Text("Revisa cómo se muestra tu tienda antes de compartirla.", fontSize = 15.sp, color = Muted, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        if (p == null) {
            Text("Aún no tienes productos publicados.", color = Muted)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardPink)
            ) {
                if (p.tieneFoto && (p.photoBitmap != null || p.photoUri != null)) {
                    ProductImage(
                        fallback = Fig.VASE_HERO, size = 210.dp,
                        uri = p.photoUri, bitmap = p.photoBitmap, hasPhoto = true,
                        modifier = Modifier.fillMaxWidth(), corner = 24.dp
                    )
                } else {
                    Image(
                        painter = painterResource(Fig.VASE_HERO), contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "${p.tipo?.label?.uppercase() ?: "DECORACIÓN"} · CASA NATIVA",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Wine,
                modifier = Modifier.fillMaxWidth()
            )
            Text(p.nombre, fontSize = 28.sp, color = Dark, modifier = Modifier.fillMaxWidth())
            Text(
                p.material.ifBlank { "Pieza de gres hecha a mano, con textura mate y tonos inspirados en la tierra húmeda." },
                fontSize = 15.sp, color = Muted, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(formatPrice(p.precioPrincipal()), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Dark, modifier = Modifier.fillMaxWidth())
            Text("Disponible para encargo", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GreenText, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Contactar a Ana", onContact)
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tu tienda está visible", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Dark)
                    Text("Comparte el enlace cuando quieras.", fontSize = 13.sp, color = Muted)
                    Spacer(Modifier.height(10.dp))
                    OutlineButton("Compartir", onShare)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- REGISTRAR VENTA ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterSaleScreen(
    productos: List<Product>,
    onConfirm: (Product, Variant, Int) -> Unit,
    onVerHistorial: () -> Unit
) {
    val publicados = productos.filter { it.publicado }
    var prodIdx by remember(productos) { mutableStateOf(0) }
    var cantidad by remember { mutableStateOf(1) }
    var varIdx by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    val prod = publicados.getOrNull(prodIdx)
    val variante = prod?.variantes?.getOrNull(varIdx) ?: prod?.variantes?.firstOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("REGISTRAR VENTA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Wine, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("Una venta a la vez", fontSize = 30.sp, color = Dark, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("El stock se descuenta automáticamente.", fontSize = 15.sp, color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("Producto", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dark)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = prod?.nombre ?: "Sin productos", onValueChange = {},
                readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Line, focusedBorderColor = Wine,
                    unfocusedContainerColor = Color.White, focusedContainerColor = Color.White,
                    unfocusedTextColor = Dark, focusedTextColor = Dark
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                publicados.forEachIndexed { i, p ->
                    DropdownMenuItem(text = { Text(p.nombre) }, onClick = { prodIdx = i; varIdx = 0; cantidad = 1; expanded = false })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (prod != null) {
            Text("Variante", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dark)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                prod.variantes.forEachIndexed { i, v ->
                    SelectableChip(v.nombre.ifBlank { "Única" }, i == (prod.variantes.indexOf(variante))) {
                        varIdx = i
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Cantidad", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dark)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { if (cantidad > 1) cantidad-- }, shape = CircleShape,
                    modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Wine)
                ) { Text("−", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Wine) }
                SteppingNumber(value = cantidad, modifier = Modifier.padding(horizontal = 28.dp)) { n ->
                    Text("$n", fontSize = 48.sp, color = Dark)
                }
                Button(
                    onClick = { cantidad++ }, shape = CircleShape, modifier = Modifier.size(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Wine), contentPadding = PaddingValues(0.dp)
                ) { Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }
            val stock = variante?.stockInt() ?: 0
            Text("Stock después de la venta", fontSize = 14.sp, color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(
                "${(stock - cantidad).coerceAtLeast(0)} unidades",
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Wine,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            val totalAnim by animateIntAsState((((variante?.precioLong() ?: 0L) * cantidad).coerceAtLeast(0L)).toInt(), tween(250), label = "total")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Precio final", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dark)
                Text(formatPrice(totalAnim.toLong()), fontWeight = FontWeight.Bold, color = Dark, fontSize = 17.sp)
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Confirmar venta y descontar stock", { if (variante != null) onConfirm(prod, variante, cantidad) })
            Spacer(Modifier.height(4.dp))
            GhostButton("Ver historial de ventas", onVerHistorial)
        } else {
            Text("Publica un producto para registrar ventas.", color = Muted)
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- HISTORIAL ----------

@Composable
fun SalesHistoryScreen(ventas: List<Sale>) {
    var filtro by remember { mutableStateOf("Todos") }
    val lista = when (filtro) {
        "Feria" -> ventas.filter { it.canal == "Feria" }
        "Producto" -> ventas.filter { it.canal == "Producto" }
        else -> ventas
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("HISTORIAL DE VENTAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Wine)
        Text("Tus ventas guardadas", fontSize = 30.sp, color = Dark)
        Text("Filtra para encontrar una venta cuando la necesites.", fontSize = 15.sp, color = Muted)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Todos", "Último mes", "Feria", "Producto").forEach { f ->
                SelectableChip(f, filtro == f) { filtro = f }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(lista) { s ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth().animateItem()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                            Text(s.productoNombre, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Dark)
                            Text("${s.canal} · ${formatFecha(s.fecha)}", fontSize = 13.sp, color = Muted)
                        }
                        Text(formatPrice(s.total), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Wine)
                    }
                }
            }
            item {
                Text("Puedes filtrar por fecha, feria o producto.", fontSize = 13.sp, color = Muted)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ---------- CONFIGURACION ----------

@Composable
fun SettingsScreen(settings: ShopSettings, onChange: (ShopSettings) -> Unit) {
    var s = settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("CONFIGURACIÓN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Wine)
        Text("Tu forma de trabajar", fontSize = 30.sp, color = Dark)
        Text("Ajusta la app una sola vez y úsala con tranquilidad.", fontSize = 15.sp, color = Muted)
        Spacer(Modifier.height(16.dp))
        Text("VENTAS E HISTORIAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Wine)
        ToggleRow("Guardar historial de ventas", "Precio, fecha, lugar, producto y variante.", true) {}
        Spacer(Modifier.height(8.dp))
        Text("VALORES PARA PRODUCTOS NUEVOS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Wine)
        Spacer(Modifier.height(4.dp))
        Text("Manejo de stock", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dark)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Piezas físicas", "Sin stock", "Por encargo").forEach { m ->
                SelectableChip(m, s.manejoStock == m) { s = s.copy(manejoStock = m); onChange(s) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Al agotarse", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Dark)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectableChip("Ocultar automáticamente", s.ocultarAutomatico) { s = s.copy(ocultarAutomatico = true); onChange(s) }
            SelectableChip("Mostrar “agotado”", !s.ocultarAutomatico) { s = s.copy(ocultarAutomatico = false); onChange(s) }
        }
        ToggleRow("Aceptar pedidos", "", s.aceptarPedidos) { s = s.copy(aceptarPedidos = it); onChange(s) }
        Spacer(Modifier.height(16.dp))
    }
}
