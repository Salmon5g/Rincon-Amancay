package com.example.amancay.emprendedora

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.amancay.data.ApiEmprendedoraException
import com.example.amancay.data.CategoriaEmprendedora
import com.example.amancay.data.DraftState
import com.example.amancay.data.ProductoEmprendedora
import com.example.amancay.data.ProductoSolicitud
import com.example.amancay.data.ProductosEmprendedoraRepository
import com.example.amancay.data.decodificarVistaPreviaProducto
import com.example.amancay.data.prepararImagenProducto
import com.example.amancay.data.PreferenciasCatalogoEmprendedora
import com.example.amancay.data.TipoProductoEmprendedora
import com.example.amancay.data.VarianteProductoEmprendedora
import com.example.amancay.data.VarianteProductoSolicitud
import com.example.amancay.ui.theme.CardPink
import com.example.amancay.ui.theme.Dark
import com.example.amancay.ui.theme.Line
import com.example.amancay.ui.theme.Muted
import com.example.amancay.ui.theme.Wine
import com.example.amancay.ui.OutlineButton
import com.example.amancay.ui.PrimaryButton
import com.example.amancay.ui.SelectableChip
import com.example.amancay.ui.AmancayField
import com.example.amancay.ui.Fig
import com.example.amancay.ui.PhotoStep
import com.example.amancay.ui.ProductImage
import com.example.amancay.ui.StepScaffold
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.mutableIntStateOf
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.launch

private data class ImagenSeleccionadaProducto(val uri: Uri?, val bitmap: Bitmap?, val archivoId: String)

private fun crearUriFotoTemporalProducto(context: Context): Uri {
    val directorio = File(context.cacheDir, "product-photos")
    check(directorio.exists() || directorio.mkdirs()) { "No se pudo preparar el almacenamiento temporal de la cámara." }
    val archivo = File.createTempFile("amancay-product-", ".jpg", directorio)
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
    } catch (error: Exception) {
        archivo.delete()
        throw error
    }
}

private fun eliminarFotoTemporalProducto(context: Context, uri: Uri?) {
    if (uri?.scheme == "file") {
        val archivo = uri.path?.let(::File) ?: return
        val directorio = File(context.filesDir, "borradores-producto").canonicalFile
        if (archivo.canonicalFile.parentFile == directorio) archivo.delete()
        return
    }
    if (uri?.authority != "${context.packageName}.fileprovider") return
    val nombre = uri.lastPathSegment ?: return
    if (!nombre.matches(Regex("amancay-product-[A-Za-z0-9_-]+\\.jpg"))) return
    File(File(context.cacheDir, "product-photos"), nombre).delete()
}

private fun copiarFotoPersistente(context: Context, uri: Uri): Uri {
    val directorio = File(context.filesDir, "borradores-producto").apply { mkdirs() }
    val destino = File(directorio, "${UUID.randomUUID()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { entrada -> FileOutputStream(destino).use(entrada::copyTo) }
        ?: throw IllegalStateException("No se pudo conservar la foto seleccionada.")
    return Uri.fromFile(destino)
}

private data class BorradorAlta(
    val tipoId: String, val categoriaId: String, val nombre: String, val descripcion: String,
    val modalidad: String, val manejaStock: Boolean, val alAgotarse: String,
    val precio: String, val stock: String, val paso: Int, val foto: String?,
    val archivoId: String, val atributos: Map<String, String>, val variantes: List<VarianteBorrador>,
    val conVariantes: Boolean = false
)

private fun leerBorradorAlta(context: Context, tiendaId: String): BorradorAlta? = runCatching {
    val raw = context.getSharedPreferences("borradores_productos", Context.MODE_PRIVATE).getString(tiendaId, null) ?: return null
    val json = JSONObject(raw)
    val atributosJson = json.optJSONObject("atributos") ?: JSONObject()
    val variantesJson = json.optJSONArray("variantes") ?: JSONArray()
    BorradorAlta(
        json.optString("tipoId"), json.optString("categoriaId"), json.optString("nombre"), json.optString("descripcion"),
        json.optString("modalidad", "regular"), json.optBoolean("manejaStock", true), json.optString("alAgotarse", "mostrar_agotado"),
        json.optString("precio"), json.optString("stock"), json.optInt("paso", 1), json.optString("foto").takeIf(String::isNotBlank),
        json.optString("archivoId", UUID.randomUUID().toString()), atributosJson.keys().asSequence().associateWith { atributosJson.optString(it) },
        (0 until variantesJson.length()).map { i ->
            val v = variantesJson.getJSONObject(i)
            val op = v.optJSONObject("opciones") ?: JSONObject()
            VarianteBorrador(v.optString("id"), op.keys().asSequence().associateWith { op.optString(it) }, v.optString("precio"), v.optString("stock"), v.optBoolean("activa", true), false,
                fotoUri = v.optString("foto").takeIf(String::isNotBlank)?.let(Uri::parse), archivoId = v.optString("archivoId", UUID.randomUUID().toString()), fotoNueva = v.optBoolean("fotoNueva"))
        },
        json.optBoolean("conVariantes", variantesJson.length() > 0)
    )
}.getOrNull()

private fun guardarBorradorAlta(context: Context, tiendaId: String, borrador: BorradorAlta) {
    val atributos = JSONObject().apply { borrador.atributos.forEach { (k, v) -> put(k, v) } }
    val variantes = JSONArray().apply { borrador.variantes.forEach { v ->
        put(JSONObject().apply {
            put("id", v.id); put("opciones", JSONObject().apply { v.opciones.forEach { (k, value) -> put(k, value) } })
            put("precio", v.precio); put("stock", v.stock); put("activa", v.activa); put("foto", v.fotoUri?.toString().orEmpty())
            put("archivoId", v.archivoId); put("fotoNueva", v.fotoNueva)
        })
    } }
    val json = JSONObject().apply {
        put("tipoId", borrador.tipoId); put("categoriaId", borrador.categoriaId); put("nombre", borrador.nombre); put("descripcion", borrador.descripcion)
        put("modalidad", borrador.modalidad); put("manejaStock", borrador.manejaStock); put("alAgotarse", borrador.alAgotarse)
        put("precio", borrador.precio); put("stock", borrador.stock); put("paso", borrador.paso); put("foto", borrador.foto.orEmpty())
        put("archivoId", borrador.archivoId); put("atributos", atributos); put("variantes", variantes)
        put("conVariantes", borrador.conVariantes)
    }
    context.getSharedPreferences("borradores_productos", Context.MODE_PRIVATE).edit().putString(tiendaId, json.toString()).apply()
}

@Composable
internal fun ProductosEmprendedoraPanel(
    tiendaId: String,
    tiendaPublicada: Boolean,
    repository: ProductosEmprendedoraRepository,
    enviando: Boolean,
    alEnviando: (Boolean) -> Unit,
    alError: (String?) -> Unit,
    alAviso: (String?) -> Unit,
    abrirNuevoAlIniciar: Boolean = false,
    soloEditor: Boolean = false,
    productoInicialId: String? = null,
    alFinalizarEdicion: () -> Unit = {}
) {
    val productos by repository.productos.collectAsState()
    val tipos by repository.tipos.collectAsState()
    val categorias by repository.categorias.collectAsState()
    val errorLectura by repository.error.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editando by remember { mutableStateOf<ProductoEmprendedora?>(null) }
    var viendo by remember { mutableStateOf<ProductoEmprendedora?>(null) }
    var nuevo by remember(tiendaId, abrirNuevoAlIniciar) { mutableStateOf(abrirNuevoAlIniciar) }
    var ajustando by remember { mutableStateOf<ProductoEmprendedora?>(null) }
    var cargandoProductoInicial by remember(productoInicialId, tiendaId) { mutableStateOf(productoInicialId != null) }
    var imagenDetalle by remember(viendo?.id) { mutableStateOf<Bitmap?>(null) }
    var cargandoImagenDetalle by remember(viendo?.id) { mutableStateOf(false) }
    var errorImagenDetalle by remember(viendo?.id) { mutableStateOf<String?>(null) }
    var intentoImagenDetalle by remember(viendo?.id) { mutableIntStateOf(0) }

    LaunchedEffect(viendo?.id, viendo?.imagenes, intentoImagenDetalle) {
        val producto = viendo ?: return@LaunchedEffect
        imagenDetalle = null
        errorImagenDetalle = null
        if (producto.imagenes.isEmpty()) return@LaunchedEffect
        cargandoImagenDetalle = true
        try {
            imagenDetalle = decodificarVistaPreviaProducto(
                repository.leerImagenPrivada(tiendaId, producto.id)
            )
            if (imagenDetalle == null) errorImagenDetalle = "No se pudo mostrar la foto guardada."
        } catch (e: Exception) {
            errorImagenDetalle = e.message ?: "No se pudo cargar la foto privada."
        } finally {
            cargandoImagenDetalle = false
        }
    }

    LaunchedEffect(productoInicialId, tiendaId) {
        if (productoInicialId == null) return@LaunchedEffect
        alEnviando(true)
        alError(null)
        try {
            editando = repository.leerCompleto(tiendaId, productoInicialId)
        } catch (e: Exception) {
            alError(e.message ?: "No se pudo abrir el producto.")
        } finally {
            cargandoProductoInicial = false
            alEnviando(false)
        }
    }

    if (nuevo || editando != null) {
        errorLectura?.let { AvisoEmprendedora(it, true) }
        EditorProductoEmprendedora(
            tiendaId = tiendaId,
            original = editando,
            repository = repository,
            tipos = tipos,
            categorias = categorias,
            enviando = enviando,
            onCancel = {
                nuevo = false
                editando = null
                alFinalizarEdicion()
            },
            tiendaPublicada = tiendaPublicada,
            onSave = { solicitud, imagen, imagenesVariantes, publicar ->
                scope.launch {
                    alEnviando(true); alError(null); alAviso(null)
                    try {
                        val productoId = repository.guardar(tiendaId, solicitud)
                        if (imagen != null) {
                            val preparada = prepararImagenProducto(context.contentResolver, imagen.uri, imagen.bitmap)
                            repository.subirImagen(tiendaId, productoId, imagen.archivoId, preparada)
                            eliminarFotoTemporalProducto(context, imagen.uri)
                        }
                        imagenesVariantes.forEach { (varianteId, fotoVariante) ->
                            val preparada = prepararImagenProducto(context.contentResolver, fotoVariante.uri, fotoVariante.bitmap)
                            val ruta = repository.subirImagen(tiendaId, productoId, fotoVariante.archivoId, preparada)
                            repository.asignarImagenVariante(tiendaId, productoId, varianteId, ruta)
                            eliminarFotoTemporalProducto(context, fotoVariante.uri)
                        }
                        if (publicar) {
                            val publicado = repository.leerCompleto(tiendaId, productoId)
                            repository.publicar(tiendaId, publicado)
                        }
                        nuevo = false; editando = null
                        alAviso(
                            when {
                                publicar -> "Producto publicado. Tus clientes lo verán según stock y disponibilidad."
                                imagen == null && imagenesVariantes.isEmpty() -> "Borrador guardado en Firebase."
                                else -> "Borrador e imágenes guardados. Las fotos quedaron protegidas hasta publicar el producto."
                            }
                        )
                        alFinalizarEdicion()
                    } catch (e: Exception) {
                        alError(e.message ?: "No se pudo guardar el producto.")
                    } finally { alEnviando(false) }
                }
            }
        )
        return
    }

    viendo?.let { producto ->
        VistaPreviaProductoEmprendedora(
            producto = producto,
            tipo = tipos.firstOrNull { it.id == producto.tipoProductoId }?.nombre,
            categoria = categorias.firstOrNull { it.id == producto.categoriaId }?.nombre,
            imagen = imagenDetalle,
            cargandoImagen = cargandoImagenDetalle,
            errorImagen = errorImagenDetalle,
            alReintentarImagen = { intentoImagenDetalle++ },
            alVolver = { viendo = null },
            alEditar = {
                scope.launch {
                    alEnviando(true); alError(null)
                    try {
                        editando = repository.leerCompleto(tiendaId, producto.id)
                        viendo = null
                    } catch (e: Exception) {
                        alError(e.message ?: "No se pudo abrir el producto para editarlo.")
                    } finally { alEnviando(false) }
                }
            },
            enviando = enviando
        )
        return
    }

    Text("MI CATÁLOGO", fontSize = 12.sp, color = Wine, fontWeight = FontWeight.Bold)
    Text("Mis productos", fontSize = 30.sp, color = Dark, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
    Text("Tus cambios se guardan en Firebase. Publicar actualiza la tienda que ve el comprador.", fontSize = 15.sp, color = Muted)
    Spacer(Modifier.height(12.dp))
    errorLectura?.let { AvisoEmprendedora(it, true) }

    if (soloEditor && cargandoProductoInicial) {
        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = Wine)
        }
        return
    }

    if (soloEditor && productoInicialId != null) {
        AvisoEmprendedora("No se pudo abrir este producto. Vuelve al catálogo e inténtalo de nuevo.", true)
        return
    }

    if (soloEditor) {
        return
    }

    PrimaryButton(
        text = "＋ Agregar producto",
        onClick = { nuevo = true },
        enabled = !enviando && tipos.any { it.activo } && categorias.any { it.activo }
    )
    if (tipos.none { it.activo } || categorias.none { it.activo }) {
        AvisoEmprendedora("Para crear productos, administración debe habilitar al menos un tipo de producto y una categoría.", false)
    }
    if (!tiendaPublicada) AvisoEmprendedora("Publica primero tu tienda desde Inicio para que los productos puedan aparecer en la web comprador.", false)
    if (productos.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        TarjetaEmprendedora("Aún no hay productos", "Agrega un borrador para comenzar tu catálogo.")
    }
    productos.forEach { producto ->
        TarjetaProductoEmprendedora(
            producto = producto,
            enviando = enviando,
            tiendaPublicada = tiendaPublicada,
            onPreview = { viendo = producto },
            onEdit = {
                scope.launch {
                    alEnviando(true); alError(null)
                    try { editando = repository.leerCompleto(tiendaId, producto.id) }
                    catch (e: Exception) { alError(e.message ?: "No se pudo abrir el producto.") }
                    finally { alEnviando(false) }
                }
            },
            onPublish = {
                scope.launch {
                    alEnviando(true); alError(null); alAviso(null)
                    try {
                        repository.publicar(tiendaId, producto)
                        alAviso("Producto publicado en el catálogo compartido.")
                    } catch (e: Exception) { alError(mensajeProducto(e)) }
                    finally { alEnviando(false) }
                }
            },
            onRetire = {
                scope.launch {
                    alEnviando(true); alError(null); alAviso(null)
                    try {
                        repository.retirar(tiendaId, producto)
                        alAviso("Producto retirado del catálogo público. El borrador sigue en tu lista.")
                    } catch (e: Exception) { alError(mensajeProducto(e)) }
                    finally { alEnviando(false) }
                }
            },
            onAdjustStock = { ajustando = producto }
        )
        Spacer(Modifier.height(10.dp))
    }

    ajustando?.let { producto ->
        DialogoAjusteStock(
            producto = producto,
            enviando = enviando,
            onDismiss = { ajustando = null },
            onConfirm = { varianteId, cantidad, motivo, nota, operacionId ->
                scope.launch {
                    alEnviando(true); alError(null); alAviso(null)
                    try {
                        repository.ajustarStock(tiendaId, producto, varianteId, cantidad, motivo, nota, operacionId)
                        ajustando = null
                        alAviso("Ajuste de stock guardado con su historial.")
                    } catch (e: Exception) { alError(mensajeProducto(e)) }
                    finally { alEnviando(false) }
                }
            }
        )
    }
}

@Composable
private fun TarjetaProductoEmprendedora(
    producto: ProductoEmprendedora,
    enviando: Boolean,
    tiendaPublicada: Boolean,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onPublish: () -> Unit,
    onRetire: () -> Unit,
    onAdjustStock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable(enabled = !enviando, onClick = onPreview),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(producto.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dark)
                    Text(estadoProducto(producto.estadoPublicacion), color = if (producto.estadoPublicacion == "publicado") Wine else Muted, fontWeight = FontWeight.SemiBold)
                    Text("Ver vista previa", fontSize = 13.sp, color = Wine, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                }
                Text(if (producto.tieneVariantes) "Variantes" else producto.precioBase?.let(::pesos) ?: "Sin precio", color = Dark, fontWeight = FontWeight.Bold)
            }
            if (producto.descripcion.isNotBlank()) Text(producto.descripcion, color = Muted, modifier = Modifier.padding(top = 6.dp))
            val inventario = if (producto.tieneVariantes) {
                producto.variantes.joinToString(" · ") { variante -> "${resumenOpciones(variante)}: ${variante.stock?.toString() ?: "sin stock"}" }
            } else if (producto.manejaStock) "Stock: ${producto.stock ?: 0}" else "Sin control de stock"
            Text(inventario, color = Muted, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onEdit,
                    enabled = !enviando,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) { Text("Editar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Wine) }
                if (producto.manejaStock) TextButton(
                    onClick = onAdjustStock,
                    enabled = !enviando,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) { Text("Stock", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Wine) }
                Spacer(Modifier.weight(1f))
                if (producto.estadoPublicacion == "publicado") {
                    TextButton(
                        onClick = onRetire,
                        enabled = !enviando,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) { Text("Retirar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Wine) }
                } else {
                    val puedePublicar = tiendaPublicada && producto.imagenes.isNotEmpty()
                    TextButton(
                        onClick = onPublish,
                        enabled = !enviando && puedePublicar,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) { Text("Publicar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (puedePublicar) Wine else Muted) }
                }
            }
            if (producto.estadoPublicacion == "publicado") {
                Text("El producto está visible en el catálogo comprador.", color = Muted, fontSize = 12.sp)
            } else {
                val puedePublicar = tiendaPublicada && producto.imagenes.isNotEmpty()
                if (!tiendaPublicada) Text("La tienda debe estar publicada antes.", color = Muted, fontSize = 12.sp)
                if (producto.imagenes.isEmpty()) Text("Agrega una foto y guarda el borrador para habilitar la publicación.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun VistaPreviaProductoEmprendedora(
    producto: ProductoEmprendedora,
    tipo: String?,
    categoria: String?,
    imagen: Bitmap?,
    cargandoImagen: Boolean,
    errorImagen: String?,
    alReintentarImagen: () -> Unit,
    alVolver: () -> Unit,
    alEditar: () -> Unit,
    enviando: Boolean
) {
    val precio = if (producto.tieneVariantes) {
        producto.variantes.filter { it.activa }.minOfOrNull { it.precio }?.let { "Desde ${pesos(it)}" }
    } else producto.precioBase?.let(::pesos)
    val modalidad = when (producto.modalidad) {
        "a_pedido", "por_encargo" -> "Disponible para encargo"
        else -> if (producto.manejaStock) {
            val unidades = if (producto.tieneVariantes) producto.variantes.filter { it.activa }.sumOf { it.stock ?: 0 }
            else producto.stock ?: 0
            if (unidades > 0) "Disponible · $unidades unidades" else "Agotado"
        } else "Disponible"
    }

    Column(Modifier.fillMaxWidth()) {
        Text("VISTA PREVIA", fontSize = 12.sp, color = Wine, fontWeight = FontWeight.Bold)
        Text("Así vería tu producto un comprador", fontSize = 28.sp, color = Dark)
        Text("Esta ficha te permite revisar la foto y los datos antes de publicar.", fontSize = 15.sp, color = Muted)
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(250.dp)
                        .background(CardPink, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        imagen != null -> ProductImage(
                            fallback = Fig.CAMERA, size = 250.dp, bitmap = imagen,
                            hasPhoto = true, modifier = Modifier.fillMaxWidth(), corner = 20.dp,
                            contentScale = ContentScale.Fit
                        )
                        cargandoImagen -> androidx.compose.material3.CircularProgressIndicator(color = Wine)
                        producto.imagenes.isNotEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📷", fontSize = 34.sp)
                            Text("No se pudo mostrar la foto privada", color = Muted, fontSize = 13.sp)
                            if (errorImagen != null) TextButton(onClick = alReintentarImagen) { Text("Reintentar", color = Wine) }
                        }
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📷", fontSize = 38.sp)
                            Text("Este producto aún no tiene una foto", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    listOfNotNull(tipo?.takeIf(String::isNotBlank), categoria?.takeIf(String::isNotBlank))
                        .joinToString(" · ").ifBlank { "PRODUCTO" }.uppercase(),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Wine
                )
                Spacer(Modifier.height(4.dp))
                Text(producto.nombre, fontSize = 26.sp, color = Dark)
                if (producto.descripcion.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(producto.descripcion, fontSize = 15.sp, color = Muted)
                }
                if (producto.atributosEspecificos.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        producto.atributosEspecificos.entries.joinToString(" · ") { (clave, valor) ->
                            "${clave.replaceFirstChar { it.uppercase() }}: $valor"
                        },
                        fontSize = 13.sp, color = Muted
                    )
                }
                precio?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Dark)
                }
                Spacer(Modifier.height(4.dp))
                Text(modalidad, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = com.example.amancay.ui.theme.GreenText)
                Spacer(Modifier.height(12.dp))
                if (producto.estadoPublicacion == "publicado") {
                    Text("Este producto ya está publicado en el catálogo.", fontSize = 13.sp, color = Muted)
                } else {
                    Text(
                        "Solo tú puedes ver esta vista previa. El producto aparecerá para compradores cuando lo publiques.",
                        fontSize = 13.sp, color = Muted
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Contactar a la vendedora", onClick = {}, enabled = false)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlineButton("Volver a mis productos", alVolver)
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Editar producto", alEditar, enabled = !enviando)
    }
}

@Composable
private fun EditorProductoEmprendedora(
    tiendaId: String,
    original: ProductoEmprendedora?,
    repository: ProductosEmprendedoraRepository,
    tipos: List<TipoProductoEmprendedora>,
    categorias: List<CategoriaEmprendedora>,
    enviando: Boolean,
    tiendaPublicada: Boolean,
    onCancel: () -> Unit,
    onSave: (ProductoSolicitud, ImagenSeleccionadaProducto?, Map<String, ImagenSeleccionadaProducto>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val borradorGuardado = remember(context, tiendaId, original?.id) { if (original == null) leerBorradorAlta(context, tiendaId) else null }
    val valoresDefecto = remember(context, tiendaId, original?.id) { PreferenciasCatalogoEmprendedora.leerValoresDefecto(context, tiendaId) }
    val tiposNuevos = tipos.filter { it.activo }
    val tiposDisponibles = if (original == null) tiposNuevos else tipos.filter { it.activo || it.id == original.tipoProductoId }
    val initialTipo = original?.tipoProductoId ?: tiposNuevos.firstOrNull()?.id.orEmpty()
    val initialCategory = original?.categoriaId ?: categorias.firstOrNull { it.activo }?.id.orEmpty()
    var tipoId by remember(original?.id) { mutableStateOf(borradorGuardado?.tipoId?.takeIf(String::isNotBlank) ?: initialTipo) }
    var categoriaId by remember(original?.id) { mutableStateOf(borradorGuardado?.categoriaId?.takeIf(String::isNotBlank) ?: initialCategory) }
    var nombre by remember(original?.id, original?.versionEsperada) { mutableStateOf(borradorGuardado?.nombre ?: original?.nombre.orEmpty()) }
    var descripcion by remember(original?.id, original?.versionEsperada) { mutableStateOf(borradorGuardado?.descripcion ?: original?.descripcion.orEmpty()) }
    var modalidad by remember(original?.id) { mutableStateOf(borradorGuardado?.modalidad ?: original?.modalidad ?: valoresDefecto.modalidad) }
    var manejaStock by remember(original?.id) { mutableStateOf(borradorGuardado?.manejaStock ?: original?.manejaStock ?: if (valoresDefecto.modalidad == "pieza_unica") true else valoresDefecto.manejaStock) }
    var alAgotarse by remember(original?.id, original?.versionEsperada) { mutableStateOf(borradorGuardado?.alAgotarse ?: original?.alAgotarse ?: valoresDefecto.alAgotarse) }
    var precioBase by remember(original?.id, original?.versionEsperada) { mutableStateOf(borradorGuardado?.precio ?: original?.precioBase?.toString().orEmpty()) }
    var stockInicial by remember(original?.id) { mutableStateOf(borradorGuardado?.stock ?: original?.stock?.toString().orEmpty()) }
    var paso by remember(original?.id) { mutableIntStateOf(borradorGuardado?.paso ?: 1) }
    val fotoLocal = remember(original?.id) { DraftState().apply { photoUri = borradorGuardado?.foto?.let(Uri::parse); fotoLista = photoUri != null } }
    var capturaPendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var varianteConFotoTemporal by remember { mutableStateOf<String?>(null) }
    var varianteCapturaPendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var imagenPrivada by remember(original?.id, original?.imagenes) { mutableStateOf<Bitmap?>(null) }
    var cargandoVistaPrevia by remember(original?.id, original?.imagenes) { mutableStateOf(false) }
    var errorVistaPrevia by remember(original?.id, original?.imagenes) { mutableStateOf<String?>(null) }
    var archivoId by remember(original?.id) { mutableStateOf(borradorGuardado?.archivoId ?: UUID.randomUUID().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var variantes by remember(original?.id, original?.versionEsperada) {
        mutableStateOf(original?.variantes?.map { v ->
            VarianteBorrador(v.id, v.opciones, v.precio.toString(), v.stock?.toString().orEmpty(), v.activa, true, imagen = v.imagen)
        } ?: borradorGuardado?.variantes.orEmpty())
    }
    val selectorGaleria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val idVariante = varianteConFotoTemporal
            if (idVariante != null) {
                val persistida = runCatching { copiarFotoPersistente(context, uri) }.getOrElse { error = it.message; uri }
                variantes = variantes.map { row -> if (row.id == idVariante) row.copy(fotoUri = persistida, fotoBitmap = null, archivoId = UUID.randomUUID().toString(), imagen = null, fotoNueva = true) else row }
                varianteConFotoTemporal = null
            } else {
                eliminarFotoTemporalProducto(context, fotoLocal.photoUri)
                fotoLocal.photoUri = runCatching { copiarFotoPersistente(context, uri) }.getOrElse { error = it.message; uri }
                fotoLocal.photoBitmap = null
                fotoLocal.fotoLista = true
                archivoId = UUID.randomUUID().toString()
            }
        } else varianteConFotoTemporal = null
    }
    val selectorCamara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { guardada ->
        val uri = capturaPendiente?.let(Uri::parse)
        capturaPendiente = null
        val idVariante = varianteCapturaPendiente
        varianteCapturaPendiente = null
        if (guardada && uri != null && idVariante != null) {
            val persistida = runCatching { copiarFotoPersistente(context, uri) }.getOrElse { error = it.message; uri }
            variantes = variantes.map { row -> if (row.id == idVariante) row.copy(fotoUri = persistida, fotoBitmap = null, archivoId = UUID.randomUUID().toString(), imagen = null, fotoNueva = true) else row }
        } else if (guardada && uri != null) {
            eliminarFotoTemporalProducto(context, fotoLocal.photoUri)
            fotoLocal.photoBitmap = null
            fotoLocal.photoUri = runCatching { copiarFotoPersistente(context, uri) }.getOrElse { error = it.message; uri }
            fotoLocal.fotoLista = true
            archivoId = UUID.randomUUID().toString()
        } else {
            eliminarFotoTemporalProducto(context, uri)
        }
    }
    fun cancelarEditor() {
        eliminarFotoTemporalProducto(context, fotoLocal.photoUri)
        variantes.forEach { eliminarFotoTemporalProducto(context, it.fotoUri) }
        capturaPendiente?.let { eliminarFotoTemporalProducto(context, Uri.parse(it)) }
        capturaPendiente = null
        if (original == null) context.getSharedPreferences("borradores_productos", Context.MODE_PRIVATE).edit().remove(tiendaId).apply()
        onCancel()
    }
    var atributos by remember(original?.id, original?.versionEsperada) { mutableStateOf(borradorGuardado?.atributos ?: original?.atributosEspecificos.orEmpty()) }
    var tipoHistorico by remember(original?.id, original?.versionEsperada) { mutableStateOf<TipoProductoEmprendedora?>(null) }
    var falloTipoHistorico by remember(original?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(tiendaId, original?.id, original?.imagenes) {
        val producto = original?.takeIf { it.imagenes.isNotEmpty() } ?: return@LaunchedEffect
        cargandoVistaPrevia = true
        errorVistaPrevia = null
        try {
            val bytes = repository.leerImagenPrivada(tiendaId, producto.id)
            imagenPrivada = decodificarVistaPreviaProducto(bytes)
            if (imagenPrivada == null) errorVistaPrevia = "No se pudo decodificar la foto guardada."
        } catch (e: Exception) {
            errorVistaPrevia = e.message ?: "No se pudo cargar la vista previa privada."
        } finally {
            cargandoVistaPrevia = false
        }
    }
    LaunchedEffect(tiendaId, original?.id, original?.imagenes, variantes.map { it.id to it.imagen }) {
        val producto = original ?: return@LaunchedEffect
        variantes.forEachIndexed { index, row ->
            if (row.imagen != null && row.fotoBitmap == null && row.fotoUri == null) {
                val imageIndex = producto.imagenes.indexOf(row.imagen)
                if (imageIndex >= 0) runCatching {
                    decodificarVistaPreviaProducto(repository.leerImagenPrivada(tiendaId, producto.id, imageIndex))
                }.getOrNull()?.let { bitmap ->
                    variantes = variantes.mapIndexed { i, variant -> if (i == index) variant.copy(fotoBitmap = bitmap) else variant }
                }
            }
        }
    }
    LaunchedEffect(original?.tipoProductoId, original?.versionTipoProducto) {
        if (original != null) {
            try { tipoHistorico = repository.leerTipo(original.tipoProductoId, original.versionTipoProducto) }
            catch (e: Exception) { falloTipoHistorico = e.message ?: "No se pudo leer la versión de tipo usada por este producto." }
        }
    }
    val tipoActual = tipos.firstOrNull { it.id == tipoId }
    val tipo = if (original != null && original.tipoProductoId == tipoId) {
        tipoHistorico?.takeIf { it.version == original.versionTipoProducto }
            ?: tipoActual?.takeIf { it.version == original.versionTipoProducto }
    } else tipoActual
    val campos = tipo?.campos.orEmpty()
    val opciones = tipo?.opcionesVariante.orEmpty()
    var conVariantes by remember(original?.id) {
        mutableStateOf(borradorGuardado?.conVariantes ?: original?.tieneVariantes ?: (tipos.firstOrNull { it.id == tipoId }?.opcionesVariante?.isNotEmpty() ?: false))
    }
    fun guardarLocalYSalir() {
        if (original == null) {
            guardarBorradorAlta(
                context,
                tiendaId,
                BorradorAlta(tipoId, categoriaId, nombre, descripcion, modalidad, manejaStock, alAgotarse, precioBase, stockInicial, paso, fotoLocal.photoUri?.toString(), archivoId, atributos, variantes, conVariantes)
            )
            onCancel()
        } else onCancel()
    }
    val categoriasDisponibles = categorias.filter { it.activo || it.id == original?.categoriaId }
    val productoIdNuevo = remember(original?.id) { if (original == null) UUID.randomUUID().toString() else null }
    val operacionId = remember(original?.id, nombre, descripcion, tipoId, categoriaId, modalidad, manejaStock, alAgotarse, precioBase, stockInicial, atributos, variantes) { UUID.randomUUID().toString() }

    LaunchedEffect(original?.id, tipoId, categoriaId, nombre, descripcion, modalidad, manejaStock, alAgotarse, precioBase, stockInicial, paso, archivoId, fotoLocal.photoUri, atributos, variantes, conVariantes) {
        if (original == null) guardarBorradorAlta(context, tiendaId, BorradorAlta(tipoId, categoriaId, nombre, descripcion, modalidad, manejaStock, alAgotarse, precioBase, stockInicial, paso, fotoLocal.photoUri?.toString(), archivoId, atributos, variantes, conVariantes))
    }

    LaunchedEffect(tipoId, original?.id) {
        if (original == null && (borradorGuardado == null || tipoId != borradorGuardado.tipoId)) {
            atributos = emptyMap()
            conVariantes = opciones.isNotEmpty()
            variantes = if (opciones.isNotEmpty()) listOf(nuevaVariante(opciones)) else emptyList()
        }
    }

    val invalidField = campos.firstOrNull { it.obligatorio && atributos[it.clave].isNullOrBlank() }
    val invalidVariant = variantes.any { row ->
        opciones.any { row.opciones[it].isNullOrBlank() } || (row.precio.toLongOrNull() ?: 0L) <= 0L ||
            (manejaStock && !row.existente && row.stock.toIntOrNull() == null)
    }
    val stockValue = stockInicial.toIntOrNull()
    fun guardarBorrador(publicar: Boolean = false) {
        error = when {
            tipo == null -> "Espera a que se carguen los tipos de producto."
            categoriasDisponibles.none { it.id == categoriaId } -> "Selecciona una categoría."
            nombre.isBlank() || (original == null && tipos.none { it.id == tipoId && it.activo }) -> "Completa el nombre y selecciona un tipo habilitado."
            invalidField != null -> "Completa el campo " + invalidField.etiqueta + "."
            conVariantes && (variantes.isEmpty() || invalidVariant) -> "Completa las opciones, precios y stock inicial de cada variante."
            !conVariantes && (precioBase.toLongOrNull() ?: 0L) <= 0L -> "Ingresa un precio válido."
            !conVariantes && manejaStock && original == null && (stockValue == null || stockValue < 0) -> "Ingresa un stock inicial válido."
            modalidad == "pieza_unica" && stockValue != 1 && original == null -> "Una pieza única comienza con stock 1."
            publicar && fotoLocal.fotoLista.not() && original?.imagenes.isNullOrEmpty() -> "Agrega una foto antes de publicar."
            publicar && !tiendaPublicada -> "Publica primero tu tienda desde Inicio."
            else -> null
        }
        if (error == null && tipo != null) {
            val solicitudes = variantes.map { v -> VarianteProductoSolicitud(v.id, v.opciones, v.precio.toLong(), if (v.existente || !manejaStock) null else v.stock.toInt(), v.activa) }
            val solicitud = ProductoSolicitud(
                id=original?.id, versionEsperada=original?.versionEsperada, operacionId=operacionId,
                productoIdNuevo=productoIdNuevo, nombre=nombre.trim(), descripcion=descripcion.trim(),
                tipo=tipo, categoriaId=categoriaId, atributos=atributos.filterValues { it.isNotBlank() },
                modalidad=modalidad, manejaStock=manejaStock, alAgotarse=alAgotarse,
                precioBase=if (conVariantes) null else precioBase.toLong(),
                stockInicial=if (!conVariantes && manejaStock && original == null) stockValue else null,
                variantes=solicitudes
            )
            val imagen = if (fotoLocal.fotoLista && (fotoLocal.photoUri != null || fotoLocal.photoBitmap != null)) {
                ImagenSeleccionadaProducto(fotoLocal.photoUri, fotoLocal.photoBitmap, archivoId)
            } else null
            val fotosVariantes = variantes.mapNotNull { row ->
                if (row.fotoNueva) {
                    row.fotoUri?.let { row.id to ImagenSeleccionadaProducto(it, row.fotoBitmap, row.archivoId) }
                        ?: row.fotoBitmap?.let { bitmap -> row.id to ImagenSeleccionadaProducto(null, bitmap, row.archivoId) }
                } else null
            }.toMap()
            if (original == null) context.getSharedPreferences("borradores_productos", Context.MODE_PRIVATE).edit().remove(tiendaId).apply()
            onSave(solicitud, imagen, fotosVariantes, publicar)
        }
    }
    if (paso == 1) {
        PhotoStep(draft=fotoLocal, bump={}, onTakePhoto={
            runCatching { crearUriFotoTemporalProducto(context) }
                .onSuccess { uri -> capturaPendiente = uri.toString(); selectorCamara.launch(uri) }
                .onFailure { error = it.message ?: "No se pudo preparar la cámara." }
        },
            onPickPhoto={ selectorGaleria.launch("image/*") }, onNext={ paso=2 },
            onBack=::cancelarEditor, onSaveExit=::guardarLocalYSalir, showSaveExit=true,
            imagenExistente=original?.imagenes?.isNotEmpty() == true,
            imagenPrevia=imagenPrivada,
            mensajeImagenExistente=when {
                imagenPrivada != null -> "Vista previa de tu imagen privada. Solo tú puedes verla hasta publicar el producto."
                cargandoVistaPrevia -> "Cargando la vista previa segura…"
                errorVistaPrevia != null -> errorVistaPrevia
                else -> "La imagen está guardada de forma privada. Puedes agregar otra foto aquí."
            })
    } else if (paso == 2) {
        StepScaffold("Paso 2 de 5", "Datos del producto", "Completa la información para que más personas encuentren tu producto.",
            onNext={ paso=3 }, onBack={ paso=1 }, onSaveExit=::cancelarEditor,
            nextEnabled=nombre.isNotBlank() && categoriasDisponibles.any { it.id == categoriaId } && (precioBase.toLongOrNull() ?: 0L) > 0L, pasoActual=2, showSaveExit=false, onExit=::guardarLocalYSalir) {
            Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(22.dp), border=BorderStroke(1.dp,Line)) {
            Column(Modifier.padding(16.dp)) {
            AmancayField(nombre, { nombre=it.take(120) }, "Nombre del producto *", "Ej.: Taza flores silvestres")
            Spacer(Modifier.height(16.dp))
            AmancayField(descripcion, { descripcion=it.take(500) }, "Descripción *", "Cuenta sus características, materiales y medidas.", singleLine=false, minLines=4)
            Text("${descripcion.length}/500", modifier=Modifier.fillMaxWidth(), textAlign=TextAlign.End, color=Muted, fontSize=12.sp)
            Spacer(Modifier.height(16.dp))
            SelectorProducto("Categoría", categoriasDisponibles.map { it.id to it.nombre }, categoriaId) { categoriaId=it }
            Spacer(Modifier.height(14.dp))
            AmancayField(precioBase, { precioBase=it.filter(Char::isDigit).take(12) }, "Precio base *", "Ej.: 6.990", keyboardType=KeyboardType.Number)
            Text("Ingresa el precio en pesos chilenos.", fontSize=13.sp, color=Muted, modifier=Modifier.padding(top=6.dp))
            }
            }
            Spacer(Modifier.height(10.dp))
            AvisoEmprendedora("Guardaremos lo que ya escribiste. Puedes continuar más tarde desde donde quedaste.", false)
        }
    } else if (paso == 3) {
        StepScaffold("Paso 3 de 5", "Tipo de producto",
            "Elige el tipo para mostrar los campos que necesitas.",
            onNext={ paso=4 }, onBack={ paso=2 }, onSaveExit=::cancelarEditor,
            nextEnabled=invalidField == null && tipo != null, pasoActual=3, showSaveExit=false, onExit=::guardarLocalYSalir) {
            tiposDisponibles.forEach { item ->
                val seleccionado = tipoId == item.id
                Card(
                    modifier=Modifier.fillMaxWidth().padding(bottom=8.dp).clickable {
                        tipoId=item.id
                        conVariantes=item.opcionesVariante.isNotEmpty()
                        error=null
                    },
                    colors=CardDefaults.cardColors(containerColor=if (seleccionado) Color(0xFFFFF0EB) else Color.White),
                    shape=RoundedCornerShape(18.dp),
                    border=BorderStroke(if (seleccionado) 2.dp else 1.dp, if (seleccionado) Wine else Line)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).background(Color(0xFFFFE5D9), RoundedCornerShape(50)), contentAlignment=Alignment.Center) {
                            val icono = when {
                                item.nombre.contains("ropa", true) || item.nombre.contains("textil", true) -> Icons.Filled.Checkroom
                                item.nombre.contains("alimento", true) -> Icons.Filled.Restaurant
                                item.nombre.contains("artesanía", true) || item.nombre.contains("artesania", true) -> Icons.Filled.Palette
                                else -> Icons.Filled.MoreHoriz
                            }
                            Icon(icono, contentDescription=null, tint=Color(0xFFEA7459), modifier=Modifier.size(24.dp))
                        }
                        Text(item.nombre, modifier=Modifier.weight(1f).padding(start=12.dp), color=Wine, fontSize=17.sp, fontWeight=FontWeight.Bold)
                        RadioButton(selected=seleccionado, onClick={
                            tipoId=item.id
                            conVariantes=item.opcionesVariante.isNotEmpty()
                            error=null
                        })
                    }
                }
            }
            if (tipo == null) AvisoEmprendedora(falloTipoHistorico ?: "Cargando la definición de este tipo…", falloTipoHistorico != null)
            if (campos.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Detalles de ${tipo?.nombre.orEmpty()}", fontWeight=FontWeight.Bold, color=Wine, fontSize=19.sp)
                Text("Completa los campos específicos para este producto.", color=Muted, fontSize=14.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (campos.isEmpty()) Text("Este tipo no requiere detalles adicionales. Puedes continuar.", fontSize=15.sp, color=Muted)
            campos.forEach { f ->
                AmancayField(atributos[f.clave].orEmpty(),
                    { v -> atributos=atributos + (f.clave to v.take(500)) },
                    f.etiqueta + if (f.obligatorio) " *" else "", "Ingresa ${f.etiqueta.lowercase()}", singleLine=false, minLines=2)
                Spacer(Modifier.height(12.dp))
            }
            error?.let { AvisoEmprendedora(it, true) }
            Text("No necesitas escribir mucho. Puedes completar o editar estos datos más adelante.", fontSize=13.sp, color=Muted)
        }
    } else if (paso == 4) {
        StepScaffold("Paso 4 de 5", "Variantes", "Cada variante puede tener su propio precio, foto y stock.",
            onNext={ paso=5 }, onBack={ paso=3 }, onSaveExit=::cancelarEditor,
            nextEnabled=if (conVariantes) variantes.isNotEmpty() && !invalidVariant else (precioBase.toLongOrNull() ?: 0L) > 0L && (!manejaStock || stockValue != null || original != null),
            pasoActual=4, showSaveExit=false, onExit=::guardarLocalYSalir) {
            Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(18.dp), border=BorderStroke(1.dp,Line), modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=!conVariantes, enabled=original==null, onCheckedChange={ sinVariantes ->
                        conVariantes=!sinVariantes
                        variantes=if (sinVariantes) emptyList() else listOf(nuevaVariante(opciones))
                    })
                    Text("Este producto no tiene variantes", color=Dark, fontSize=14.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!conVariantes) {
                AmancayField(precioBase, { precioBase=it.filter(Char::isDigit).take(12) }, "Precio", "Ingresa el precio", keyboardType=KeyboardType.Number)
                Spacer(Modifier.height(12.dp))
                if (manejaStock && original == null) AmancayField(stockInicial, { stockInicial=it.filter(Char::isDigit).take(9) }, "Stock inicial", "Cantidad disponible", keyboardType=KeyboardType.Number)
                else if (original != null && manejaStock) Text("Stock actual: ${original.stock ?: 0}. Ajusta las unidades desde el catálogo.", color=Muted, fontSize=13.sp)
            } else {
                Text("Variantes", color=Dark, fontWeight=FontWeight.Bold)
                Text("Cada variante guarda su foto, precio y stock por separado.", color=Muted, fontSize=13.sp)
                variantes.forEachIndexed { index,v ->
                    Card(Modifier.fillMaxWidth().padding(top=10.dp), colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(22.dp), border=BorderStroke(1.dp,Line)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                ProductImage(fallback=Fig.VASE_REVIEW, size=82.dp, uri=v.fotoUri, bitmap=v.fotoBitmap, hasPhoto=v.fotoUri != null || v.fotoBitmap != null || v.imagen != null)
                                Column(Modifier.weight(1f)) {
                                    Text("Foto de esta variante", color=Dark, fontWeight=FontWeight.Bold)
                                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick={ varianteConFotoTemporal=v.id; selectorGaleria.launch("image/*") }, modifier=Modifier.heightIn(min=48.dp)) { Text("Galería", color=Wine) }
                                        TextButton(onClick={
                                            runCatching { crearUriFotoTemporalProducto(context) }.onSuccess { uri ->
                                                varianteCapturaPendiente=v.id
                                                capturaPendiente=uri.toString()
                                                selectorCamara.launch(uri)
                                            }.onFailure { error=it.message ?: "No se pudo abrir la cámara." }
                                        }, modifier=Modifier.heightIn(min=48.dp)) { Text("Cámara", color=Wine) }
                                    }
                                    if (v.imagen != null && v.fotoUri == null) Text("Foto guardada", color=Muted, fontSize=12.sp)
                                }
                            }
                            opciones.forEach { key -> CampoProducto(key.replaceFirstChar { it.uppercase() }, v.opciones[key].orEmpty(),
                                { value -> variantes=variantes.mapIndexed { i,old -> if (i == index && !old.existente) old.copy(opciones=old.opciones + (key to value)) else old } },
                                100, readOnly=v.existente, placeholder=ejemploOpcionVariante(key)) }
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) { AmancayField(v.precio, { value -> variantes=variantes.actualizar(index) { it.copy(precio=value.filter(Char::isDigit).take(12)) } }, "Precio (CLP)", "Ej.: 18.000", keyboardType=KeyboardType.Number) }
                                if (manejaStock && !v.existente) {
                                    Box(Modifier.weight(1f)) { AmancayField(v.stock, { value -> variantes=variantes.actualizar(index) { it.copy(stock=value.filter(Char::isDigit).take(9)) } }, "Stock", "Ej.: 3", keyboardType=KeyboardType.Number) }
                                } else if (v.existente && manejaStock) {
                                    Text("Stock: " + (original?.variantes?.firstOrNull { it.id == v.id }?.stock ?: 0) + ". Ajusta desde la lista.", color=Muted, fontSize=12.sp)
                                }
                            }
                            if (v.existente) Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(checked=v.activa, onCheckedChange={ active -> variantes=variantes.actualizar(index) { it.copy(activa=active) } }, modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp))
                                Text("Variante activa", color=Dark)
                            } else TextButton(onClick={ variantes=variantes.filterIndexed { i,_ -> i != index } }, modifier=Modifier.heightIn(min=48.dp)) { Text("Quitar variante nueva", color=Wine) }
                        }
                    }
                }
                if (variantes.size < 20 && original == null) OutlineButton("＋ Agregar variante", { variantes=variantes + nuevaVariante(opciones) })
            }
            error?.let { AvisoEmprendedora(it, true) }
            if (original != null && manejaStock) Text("El stock existente se modifica desde el catálogo para conservar el historial.", fontSize=13.sp, color=Muted)
        }
    } else if (paso == 5) {
        StepScaffold("Paso 5 de 5", "Disponibilidad", "Cuéntanos cómo quieres vender este producto.",
            onNext={ paso=6 }, onBack={ paso=4 }, onSaveExit=::cancelarEditor,
            nextEnabled=!enviando, pasoActual=5, showSaveExit=false, onExit=::guardarLocalYSalir) {
            Text("Tipo de disponibilidad", color=Dark, fontWeight=FontWeight.Bold, fontSize=18.sp)
            Text("Selecciona cómo estará disponible este producto.", color=Muted, fontSize=14.sp)
            Spacer(Modifier.height(10.dp))
            OpcionDisponibilidad("Regular", "Producto siempre disponible en forma habitual.", Icons.Filled.Inventory2, modalidad=="regular", original==null) { modalidad="regular" }
            OpcionDisponibilidad("A pedido", "Se realiza especialmente tras la compra.", Icons.Filled.Schedule, modalidad=="a_pedido", original==null) { modalidad="a_pedido" }
            OpcionDisponibilidad("Pieza única", "Producto único, no se repite.", Icons.Filled.Star, modalidad=="pieza_unica", original==null && !conVariantes) {
                modalidad="pieza_unica"; manejaStock=true; stockInicial="1"
            }
            Spacer(Modifier.height(14.dp))
            Text("¿Maneja stock?", color=Dark, fontWeight=FontWeight.Bold, fontSize=18.sp)
            Text("Indica si llevas control de la cantidad disponible.", color=Muted, fontSize=14.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF6E8E6)).border(1.dp, Wine, RoundedCornerShape(16.dp))) {
                listOf(true to "Sí", false to "No").forEach { (valor, texto) ->
                    Box(Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(15.dp)).background(if (manejaStock==valor) Wine else Color.Transparent).clickable(enabled=original==null && modalidad!="pieza_unica") { manejaStock=valor }, contentAlignment=Alignment.Center) {
                        Text(texto, color=if (manejaStock==valor) Color.White else Wine, fontWeight=FontWeight.Bold, fontSize=16.sp)
                    }
                }
            }
            if (manejaStock) {
                Spacer(Modifier.height(14.dp))
                Text("Cuando se agote", color=Dark, fontWeight=FontWeight.Bold, fontSize=18.sp)
                Text("Elige qué quieres que pase cuando no haya más stock.", color=Muted, fontSize=14.sp)
                Spacer(Modifier.height(8.dp))
                OpcionDisponibilidad("Mostrar agotado", "El producto sigue visible, pero se muestra como agotado.", Icons.Filled.Visibility, alAgotarse=="mostrar_agotado", original==null) { alAgotarse="mostrar_agotado" }
                OpcionDisponibilidad("Ocultar producto", "No se mostrará hasta que vuelva a tener stock.", Icons.Filled.VisibilityOff, alAgotarse=="ocultar", original==null) { alAgotarse="ocultar" }
                OpcionDisponibilidad("Pasar a pedido", "Al agotarse, cambiará automáticamente a pedido.", Icons.Filled.SwapHoriz, alAgotarse=="pasar_a_pedido", original==null) { alAgotarse="pasar_a_pedido" }
            }
            if (manejaStock && conVariantes && original==null) Text("El stock inicial se define por separado para cada variante.", color=Muted, fontSize=13.sp, modifier=Modifier.padding(top=8.dp))
            error?.let { AvisoEmprendedora(it, true) }
        }
    } else {
        StepScaffold("Último paso", "Revisa tu producto", "Verifica que toda la información esté correcta antes de publicarlo.",
            onNext={ guardarBorrador(publicar = original == null && tiendaPublicada) }, onBack={ paso=5 }, onSaveExit={ guardarBorrador(false) },
            nextLabel=when { original != null -> "Guardar cambios"; tiendaPublicada -> "Publicar producto"; else -> "Guardar borrador" }, nextEnabled=!enviando,
            pasoActual=5, showSaveExit=original==null, onExit=::guardarLocalYSalir) {
            Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp,Line)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        ProductImage(fallback=Fig.VASE_REVIEW, size=92.dp, uri=fotoLocal.photoUri, bitmap=fotoLocal.photoBitmap ?: imagenPrivada, hasPhoto=true)
                        Spacer(Modifier.size(14.dp))
                        Column {
                            Text(tipo?.nombre?.uppercase() ?: "PRODUCTO", fontSize=12.sp, fontWeight=FontWeight.Bold, color=Wine)
                            Text(nombre.ifBlank { "Nombre del producto" }, fontSize=22.sp, color=Dark)
                            Text(categoriasDisponibles.firstOrNull { it.id == categoriaId }?.nombre.orEmpty(), fontSize=14.sp, color=Muted)
                        }
                    }
                    Text(descripcion.ifBlank { "Sin descripción." }, color=Dark, modifier=Modifier.padding(top=10.dp))
                    Text(if (conVariantes) "${variantes.size} ${if (variantes.size == 1) "variante" else "variantes"}" else "Precio: " + (precioBase.toLongOrNull()?.let(::pesos) ?: "Pendiente"), color=Muted, modifier=Modifier.padding(top=8.dp))
                    Text("Modalidad: " + modalidad.replace('_',' '), color=Muted)
                    campos.filter { !atributos[it.clave].isNullOrBlank() }.forEach { field ->
                        Text(field.etiqueta + ": " + atributos[field.clave], color=Muted, modifier=Modifier.padding(top=4.dp))
                    }
                    if (conVariantes) variantes.forEach { variante ->
                        val nombreVariante = opciones.mapNotNull { variante.opciones[it]?.takeIf(String::isNotBlank) }.joinToString(" / ")
                        Text(
                            (nombreVariante.ifBlank { "Variante" }) + " · " + (variante.precio.toLongOrNull()?.let(::pesos) ?: "Precio pendiente") +
                                if (manejaStock) " · stock inicial " + (variante.stock.ifBlank { "pendiente" }) else "",
                            color=Muted, modifier=Modifier.padding(top=4.dp)
                        )
                    } else if (manejaStock) {
                        Text("Stock inicial: " + (stockInicial.ifBlank { original?.stock?.toString() ?: "pendiente" }), color=Muted)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val mensajeImagen = when {
                fotoLocal.fotoLista -> "Al guardar, primero se conservará el borrador y luego se subirá la foto. Si falla la carga, podrás reintentar sin perder el producto."
                original?.imagenes?.isNotEmpty() == true -> "La imagen guardada se conserva de forma privada. Puedes agregar otra foto; la tienda debe estar publicada para publicar el producto."
                else -> "Puedes guardar el borrador sin foto, pero tendrás que agregar una imagen antes de publicarlo."
            }
            AvisoEmprendedora(mensajeImagen, false)
            listOf(
                "Información general" to "Nombre, descripción y categoría" to 2,
                "Fotos del producto" to "Imagen principal y galería" to 1,
                "Precio y variantes" to "Precio, opciones y modalidad" to 4,
                "Stock y disponibilidad" to "Comportamiento del stock y visibilidad" to 5
            ).forEach { (par, destino) ->
                val (titulo, detalle) = par
                Card(
                    modifier=Modifier.fillMaxWidth().padding(top=8.dp).clickable { paso=destino },
                    colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(18.dp), border=BorderStroke(1.dp,Line)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
                        val icono = when (titulo) {
                            "Información general" -> Icons.Filled.Storefront
                            "Fotos del producto" -> Icons.Filled.PhotoLibrary
                            "Precio y variantes" -> Icons.Filled.Sell
                            "Stock y disponibilidad" -> Icons.Filled.Inventory2
                            else -> Icons.Filled.Person
                        }
                        Box(Modifier.size(42.dp).background(Color(0xFFFFE9E0), RoundedCornerShape(50)), contentAlignment=Alignment.Center) { Icon(icono, contentDescription=null, tint=Wine, modifier=Modifier.size(22.dp)) }
                        Column(Modifier.weight(1f).padding(start=10.dp)) {
                            Text(titulo, color=Wine, fontWeight=FontWeight.Bold, fontSize=15.sp)
                            Text(detalle, color=Muted, fontSize=12.sp)
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("Editar", color=Wine, fontWeight=FontWeight.Bold, fontSize=13.sp)
                            Icon(Icons.Filled.ArrowForward, contentDescription=null, tint=Wine, modifier=Modifier.size(18.dp))
                        }
                    }
                }
            }
            error?.let { AvisoEmprendedora(it, true) }
        }
    }}

@Composable
private fun OpcionDisponibilidad(
    titulo: String,
    detalle: String,
    icono: ImageVector,
    seleccionada: Boolean,
    habilitada: Boolean = true,
    alSeleccionar: () -> Unit
) {
    Card(
        modifier=Modifier.fillMaxWidth().padding(vertical=4.dp).clickable(enabled=habilitada, onClick=alSeleccionar),
        colors=CardDefaults.cardColors(containerColor=if (seleccionada) Color(0xFFFFF5F0) else Color.White),
        shape=RoundedCornerShape(18.dp),
        border=BorderStroke(if (seleccionada) 1.5.dp else 1.dp, if (seleccionada) Wine else Line)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(Color(0xFFFFE9E0), RoundedCornerShape(50)), contentAlignment=Alignment.Center) {
                Icon(icono, contentDescription=null, tint=Wine, modifier=Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal=10.dp)) {
                Text(titulo, color=Wine, fontWeight=FontWeight.Bold, fontSize=15.sp)
                Text(detalle, color=Muted, fontSize=12.sp, lineHeight=16.sp)
            }
            Box(Modifier.size(22.dp).border(2.dp, if (seleccionada) Wine else Muted, RoundedCornerShape(50)), contentAlignment=Alignment.Center) {
                if (seleccionada) Box(Modifier.size(12.dp).background(Wine, RoundedCornerShape(50)))
            }
        }
    }
}

private data class VarianteBorrador(
    val id: String,
    val opciones: Map<String, String>,
    val precio: String,
    val stock: String,
    val activa: Boolean,
    val existente: Boolean,
    val fotoUri: Uri? = null,
    val fotoBitmap: Bitmap? = null,
    val archivoId: String = UUID.randomUUID().toString(),
    val imagen: String? = null,
    val fotoNueva: Boolean = false
)

private fun nuevaVariante(opciones: List<String>) = VarianteBorrador(UUID.randomUUID().toString(), opciones.associateWith { "" }, "", "", true, false)
private inline fun List<VarianteBorrador>.actualizar(index: Int, transform: (VarianteBorrador) -> VarianteBorrador) = mapIndexed { i, row -> if (i == index) transform(row) else row }

@Composable
internal fun DialogoAjusteStock(
    producto: ProductoEmprendedora,
    enviando: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?, Int, String, String?, String) -> Unit
) {
    var varianteId by remember(producto.id) { mutableStateOf(producto.variantes.firstOrNull { it.activa }?.id.orEmpty()) }
    var cantidad by remember(producto.id) { mutableStateOf("") }
    var motivo by remember(producto.id) { mutableStateOf("reposicion") }
    var nota by remember(producto.id) { mutableStateOf("") }
    var error by remember(producto.id) { mutableStateOf<String?>(null) }
    val operacionId = remember(producto.id, varianteId, cantidad, motivo, nota) { UUID.randomUUID().toString() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar stock", color = Dark, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(producto.nombre, color = Muted)
                if (producto.tieneVariantes) SelectorProducto("Variante", producto.variantes.map { it.id to resumenOpciones(it) }, varianteId) { varianteId = it }
                Spacer(Modifier.height(8.dp))
                CampoProducto("Cantidad (usa + para sumar y − para restar)", cantidad, { cantidad = it.filter { c -> c.isDigit() || c == '-' || c == '+' }.take(10) }, 10, placeholder="Ej.: +5 o -2")
                SelectorProducto("Motivo", listOf("reposicion" to "Reposición", "perdida" to "Pérdida", "correccion" to "Corrección"), motivo) { motivo = it }
                Spacer(Modifier.height(8.dp))
                CampoProducto("Nota (opcional)", nota, { nota = it }, 500, placeholder="Ej.: Reposición de materiales")
                error?.let { AvisoEmprendedora(it, true) }
            }
        },
        confirmButton = {
            Button(enabled = !enviando, onClick = {
                val value = cantidad.toIntOrNull()
                error = when {
                    value == null || value == 0 -> "Ingresa una cantidad distinta de cero."
                    motivo == "reposicion" && value < 0 -> "La reposición debe sumar unidades."
                    motivo == "perdida" && value > 0 -> "La pérdida debe restar unidades."
                    producto.tieneVariantes && varianteId.isBlank() -> "Selecciona una variante."
                    else -> null
                }
                if (error == null && value != null) onConfirm(if (producto.tieneVariantes) varianteId else null, value, motivo, nota.trim().takeIf(String::isNotEmpty), operacionId)
            }, colors = ButtonDefaults.buttonColors(containerColor = Wine)) { Text("Guardar ajuste") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !enviando, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar", color = Wine) } },
        containerColor = androidx.compose.ui.graphics.Color.White,
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
private fun CampoProducto(label: String, value: String, onValue: (String) -> Unit, max: Int, multiline: Boolean = false, readOnly: Boolean = false, placeholder: String = "") {
    OutlinedTextField(
        value = value,
        onValueChange = { if (!readOnly) onValue(it.take(max)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder, color = Muted) },
        minLines = if (multiline) 3 else 1,
        singleLine = !multiline,
        readOnly = readOnly,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Wine,
            unfocusedBorderColor = Line,
            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.White,
            cursorColor = Wine,
            focusedTextColor = Dark,
            unfocusedTextColor = Dark
        )
    )
}

private fun ejemploOpcionVariante(clave: String): String = when {
    clave.contains("color", ignoreCase = true) -> "Ej.: Mostaza"
    clave.contains("talla", ignoreCase = true) || clave.contains("talle", ignoreCase = true) -> "Ej.: M"
    clave.contains("material", ignoreCase = true) -> "Ej.: Lana"
    else -> "Ingresa ${clave.replace('_', ' ').lowercase()}"
}

@Composable
private fun SelectorProducto(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember(label, options) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Seleccionar"
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Dark, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = options.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, Wine),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = androidx.compose.ui.graphics.Color.White, contentColor = Wine),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) { Text(selectedLabel, color = Wine, fontWeight = FontWeight.Bold) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) }
            }
        }
    }
}

@Composable
private fun TarjetaEmprendedora(titulo: String, detalle: String) {
    Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp)) { Text(titulo, color = Dark, fontWeight = FontWeight.Bold); Text(detalle, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
    }
}

private fun estadoProducto(estado: String) = when (estado) { "publicado" -> "Publicado"; "archivado" -> "Retirado"; else -> "Borrador" }
private fun pesos(valor: Long) = "$" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es-CL")).format(valor)
private fun resumenOpciones(variante: VarianteProductoEmprendedora) = variante.opciones.values.joinToString(" / ").ifBlank { variante.id }
internal fun mensajeProducto(error: Exception): String = when ((error as? ApiEmprendedoraException)?.codigo) {
    "imagen-requerida" -> "Agrega una foto y guarda el borrador antes de publicar."
    "imagen-no-autorizada" -> "No se pudo validar la foto. Elige otra imagen JPEG, PNG o WebP de hasta 5 MiB."
    "tienda-no-publicada" -> "Publica primero tu tienda."
    "conflicto" -> "Los datos cambiaron desde que abriste la ficha. Vuelve a cargarla e inténtalo otra vez."
    "sin-permiso" -> "La sesión ya no tiene permiso para esta tienda. Inicia sesión de nuevo o consulta a administración."
    "no-publicable" -> "El producto no se puede publicar con su disponibilidad actual. Revisa stock y modalidad."
    else -> error.message ?: "La API no pudo completar la operación."
}
