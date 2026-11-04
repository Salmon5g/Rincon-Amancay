package com.example.amancay.emprendedora

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    if (uri?.authority != "${context.packageName}.fileprovider") return
    val nombre = uri.lastPathSegment ?: return
    if (!nombre.matches(Regex("amancay-product-[A-Za-z0-9_-]+\\.jpg"))) return
    File(File(context.cacheDir, "product-photos"), nombre).delete()
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
            onSave = { solicitud, imagen ->
                scope.launch {
                    alEnviando(true); alError(null); alAviso(null)
                    try {
                        val productoId = repository.guardar(tiendaId, solicitud)
                        if (imagen != null) {
                            val preparada = prepararImagenProducto(context.contentResolver, imagen.uri, imagen.bitmap)
                            repository.subirImagen(tiendaId, productoId, imagen.archivoId, preparada)
                            eliminarFotoTemporalProducto(context, imagen.uri)
                        }
                        nuevo = false; editando = null
                        alAviso(if (imagen == null) "Borrador guardado en Firebase." else "Borrador e imagen guardados. La foto quedó protegida hasta publicar el producto.")
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
    Text("Productos", fontSize = 30.sp, color = Dark)
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
    onCancel: () -> Unit,
    onSave: (ProductoSolicitud, ImagenSeleccionadaProducto?) -> Unit
) {
    val context = LocalContext.current
    val tiposNuevos = tipos.filter { it.activo }
    val tiposDisponibles = if (original == null) tiposNuevos else tipos.filter { it.activo || it.id == original.tipoProductoId }
    val initialTipo = original?.tipoProductoId ?: tiposNuevos.firstOrNull()?.id.orEmpty()
    val initialCategory = original?.categoriaId ?: categorias.firstOrNull { it.activo }?.id.orEmpty()
    var tipoId by remember(original?.id, tipos) { mutableStateOf(initialTipo) }
    var categoriaId by remember(original?.id, categorias) { mutableStateOf(initialCategory) }
    var nombre by remember(original?.id, original?.versionEsperada) { mutableStateOf(original?.nombre.orEmpty()) }
    var descripcion by remember(original?.id, original?.versionEsperada) { mutableStateOf(original?.descripcion.orEmpty()) }
    var modalidad by remember(original?.id) { mutableStateOf(original?.modalidad ?: "regular") }
    var manejaStock by remember(original?.id) { mutableStateOf(original?.manejaStock ?: true) }
    var alAgotarse by remember(original?.id, original?.versionEsperada) { mutableStateOf(original?.alAgotarse ?: "mostrar_agotado") }
    var precioBase by remember(original?.id, original?.versionEsperada) { mutableStateOf(original?.precioBase?.toString().orEmpty()) }
    var stockInicial by remember(original?.id) { mutableStateOf(original?.stock?.toString().orEmpty()) }
    var paso by remember(original?.id) { mutableIntStateOf(1) }
    val fotoLocal = remember(original?.id) { DraftState() }
    var capturaPendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var imagenPrivada by remember(original?.id, original?.imagenes) { mutableStateOf<Bitmap?>(null) }
    var cargandoVistaPrevia by remember(original?.id, original?.imagenes) { mutableStateOf(false) }
    var errorVistaPrevia by remember(original?.id, original?.imagenes) { mutableStateOf<String?>(null) }
    var archivoId by remember(original?.id) { mutableStateOf(UUID.randomUUID().toString()) }
    val selectorGaleria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            eliminarFotoTemporalProducto(context, fotoLocal.photoUri)
            fotoLocal.photoUri = uri
            fotoLocal.photoBitmap = null
            fotoLocal.fotoLista = true
            archivoId = UUID.randomUUID().toString()
        }
    }
    val selectorCamara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { guardada ->
        val uri = capturaPendiente?.let(Uri::parse)
        capturaPendiente = null
        if (guardada && uri != null) {
            eliminarFotoTemporalProducto(context, fotoLocal.photoUri)
            fotoLocal.photoBitmap = null
            fotoLocal.photoUri = uri
            fotoLocal.fotoLista = true
            archivoId = UUID.randomUUID().toString()
        } else {
            eliminarFotoTemporalProducto(context, uri)
        }
    }
    fun cancelarEditor() {
        eliminarFotoTemporalProducto(context, fotoLocal.photoUri)
        capturaPendiente?.let { eliminarFotoTemporalProducto(context, Uri.parse(it)) }
        capturaPendiente = null
        onCancel()
    }
    var atributos by remember(original?.id, original?.versionEsperada) { mutableStateOf(original?.atributosEspecificos.orEmpty()) }
    var variantes by remember(original?.id, original?.versionEsperada) {
        mutableStateOf(original?.variantes?.map { v ->
            VarianteBorrador(v.id, v.opciones, v.precio.toString(), v.stock?.toString().orEmpty(), v.activa, true)
        } ?: emptyList())
    }
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
    val conVariantes = original?.tieneVariantes ?: opciones.isNotEmpty()
    val categoriasDisponibles = categorias.filter { it.activo || it.id == original?.categoriaId }
    var error by remember { mutableStateOf<String?>(null) }
    val productoIdNuevo = remember(original?.id) { if (original == null) UUID.randomUUID().toString() else null }
    val operacionId = remember(original?.id, nombre, descripcion, tipoId, categoriaId, modalidad, manejaStock, alAgotarse, precioBase, stockInicial, atributos, variantes) { UUID.randomUUID().toString() }

    LaunchedEffect(tipoId, original?.id) {
        if (original == null) {
            atributos = emptyMap()
            variantes = if (opciones.isNotEmpty()) listOf(nuevaVariante(opciones)) else emptyList()
        }
    }

    val invalidField = campos.firstOrNull { it.obligatorio && atributos[it.clave].isNullOrBlank() }
    val invalidVariant = variantes.any { row ->
        opciones.any { row.opciones[it].isNullOrBlank() } || (row.precio.toLongOrNull() ?: 0L) <= 0L ||
            (manejaStock && !row.existente && row.stock.toIntOrNull() == null)
    }
    val stockValue = stockInicial.toIntOrNull()
    fun guardarBorrador() {
        error = when {
            tipo == null -> "Espera a que se carguen los tipos de producto."
            categoriasDisponibles.none { it.id == categoriaId } -> "Selecciona una categoría."
            nombre.isBlank() || (original == null && tipos.none { it.id == tipoId && it.activo }) -> "Completa el nombre y selecciona un tipo habilitado."
            invalidField != null -> "Completa el campo " + invalidField.etiqueta + "."
            conVariantes && (variantes.isEmpty() || invalidVariant) -> "Completa las opciones, precios y stock inicial de cada variante."
            !conVariantes && (precioBase.toLongOrNull() ?: 0L) <= 0L -> "Ingresa un precio válido."
            !conVariantes && manejaStock && original == null && (stockValue == null || stockValue < 0) -> "Ingresa un stock inicial válido."
            modalidad == "pieza_unica" && stockValue != 1 && original == null -> "Una pieza única comienza con stock 1."
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
            onSave(solicitud, imagen)
        }
    }
    if (paso == 1) {
        PhotoStep(draft=fotoLocal, bump={}, onTakePhoto={
            runCatching { crearUriFotoTemporalProducto(context) }
                .onSuccess { uri -> capturaPendiente = uri.toString(); selectorCamara.launch(uri) }
                .onFailure { error = it.message ?: "No se pudo preparar la cámara." }
        },
            onPickPhoto={ selectorGaleria.launch("image/*") }, onNext={ paso=2 },
            onBack=::cancelarEditor, onSaveExit=::cancelarEditor, showSaveExit=false,
            imagenExistente=original?.imagenes?.isNotEmpty() == true,
            imagenPrevia=imagenPrivada,
            mensajeImagenExistente=when {
                imagenPrivada != null -> "Vista previa de tu imagen privada. Solo tú puedes verla hasta publicar el producto."
                cargandoVistaPrevia -> "Cargando la vista previa segura…"
                errorVistaPrevia != null -> errorVistaPrevia
                else -> "La imagen está guardada de forma privada. Puedes agregar otra foto aquí."
            })
    } else if (paso == 2) {
        StepScaffold("Paso 2 de 5", "Cuéntanos lo básico", "Usaremos estos datos para mostrar tu producto.",
            onNext={ paso=3 }, onBack={ paso=1 }, onSaveExit=::cancelarEditor,
            nextEnabled=nombre.isNotBlank() && tipo != null, pasoActual=2, showSaveExit=false) {
            AmancayField(nombre, { nombre=it.take(120) }, "Nombre del producto", "")
            Spacer(Modifier.height(16.dp))
            AmancayField(descripcion, { descripcion=it.take(2000) }, "Descripción", "", singleLine=false, minLines=3)
            Spacer(Modifier.height(16.dp))
            Text("¿Qué tipo de producto es?", fontSize=15.sp, fontWeight=FontWeight.Bold, color=Dark, modifier=Modifier.padding(top=12.dp))
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                tiposDisponibles.chunked(2).forEach { fila ->
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        fila.forEach { item -> SelectableChip(item.nombre, tipoId == item.id) { tipoId=item.id; error=null } }
                    }
                }
            }
            if (tipo == null) AvisoEmprendedora(falloTipoHistorico ?: "Cargando la definición del tipo de producto…", falloTipoHistorico != null)
            Spacer(Modifier.height(12.dp))
            SelectorProducto("Categoría", categoriasDisponibles.map { it.id to it.nombre }, categoriaId) { categoriaId=it }
            Text("Elegir un tipo adapta las preguntas del siguiente paso.", fontSize=13.sp, color=Muted, modifier=Modifier.padding(top=10.dp))
        }
    } else if (paso == 3) {
        StepScaffold("Paso 3 de 5", if (tipo == null) "Detalles del producto" else "Detalles de " + tipo.nombre,
            "Solo te pedimos lo que ayuda a decidir un encargo.",
            onNext={ paso=4 }, onBack={ paso=2 }, onSaveExit=::cancelarEditor,
            nextEnabled=invalidField == null && tipo != null, pasoActual=3, showSaveExit=false) {
            if (campos.isEmpty()) Text("Este tipo no requiere detalles adicionales. Puedes continuar.", fontSize=15.sp, color=Muted)
            campos.forEach { f ->
                AmancayField(atributos[f.clave].orEmpty(),
                    { v -> atributos=atributos + (f.clave to v.take(500)) },
                    f.etiqueta + if (f.obligatorio) " *" else "", "", singleLine=false, minLines=2)
                Spacer(Modifier.height(12.dp))
            }
            error?.let { AvisoEmprendedora(it, true) }
            Text("No necesitas escribir mucho. Puedes completar o editar estos datos más adelante.", fontSize=13.sp, color=Muted)
        }
    } else if (paso == 4) {
        StepScaffold("Paso 4 de 5", "Precio, variantes y stock", "Cada variante puede tener su propio precio, foto y cantidad.",
            onNext={ paso=5 }, onBack={ paso=3 }, onSaveExit=::cancelarEditor,
            nextEnabled=if (conVariantes) variantes.isNotEmpty() else precioBase.isNotBlank(),
            pasoActual=4, showSaveExit=false) {
            Text("Modalidad de venta", color=Dark, fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("regular" to "Regular", "a_pedido" to "A pedido", "pieza_unica" to "Pieza única").forEach { (value,label) ->
                    SelectableChip(text=label, selected=modalidad == value, enabled=original == null && (!conVariantes || value != "pieza_unica")) {
                        modalidad=value
                        if (value == "pieza_unica") { manejaStock=true; stockInicial="1" }
                    }
                }
            }
            if (!conVariantes) {
                AmancayField(precioBase, { precioBase=it.filter(Char::isDigit).take(12) }, "Precio en pesos chilenos", "", keyboardType=KeyboardType.Number)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=manejaStock, onCheckedChange={ if (original == null && modalidad != "pieza_unica") manejaStock=it }, enabled=original == null && modalidad != "pieza_unica", modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp))
                    Text("Controlar unidades disponibles", color=Dark)
                }
                if (manejaStock && original == null) AmancayField(stockInicial, { stockInicial=it.filter(Char::isDigit).take(9) }, "Stock inicial", "", keyboardType=KeyboardType.Number)
                else if (original != null && manejaStock) Text("Stock actual: " + (original.stock ?: 0) + ". Usa “Ajustar stock” desde la lista.", color=Muted, fontSize=12.sp)
            } else {
                Text("Variantes", color=Dark, fontWeight=FontWeight.Bold)
                Text("Completa " + opciones.joinToString(", ") + ". Las variantes guardadas se conservan; puedes desactivarlas.", color=Muted, fontSize=12.sp)
                variantes.forEachIndexed { index,v ->
                    Card(Modifier.fillMaxWidth().padding(top=8.dp), colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp,Line)) {
                        Column(Modifier.padding(12.dp)) {
                            opciones.forEach { key -> CampoProducto(key.replaceFirstChar { it.uppercase() }, v.opciones[key].orEmpty(),
                                { value -> variantes=variantes.mapIndexed { i,old -> if (i == index && !old.existente) old.copy(opciones=old.opciones + (key to value)) else old } },
                                100, readOnly=v.existente) }
                            AmancayField(v.precio, { value -> variantes=variantes.actualizar(index) { it.copy(precio=value.filter(Char::isDigit).take(12)) } }, "Precio (CLP)", "", keyboardType=KeyboardType.Number)
                            if (manejaStock && !v.existente) AmancayField(v.stock, { value -> variantes=variantes.actualizar(index) { it.copy(stock=value.filter(Char::isDigit).take(9)) } }, "Stock inicial", "", keyboardType=KeyboardType.Number)
                            else if (v.existente && manejaStock) Text("Stock: " + (original?.variantes?.firstOrNull { it.id == v.id }?.stock ?: 0) + ". Ajusta desde la lista.", color=Muted, fontSize=12.sp)
                            if (v.existente) Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(checked=v.activa, onCheckedChange={ active -> variantes=variantes.actualizar(index) { it.copy(activa=active) } }, modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp))
                                Text("Variante activa", color=Dark)
                            } else TextButton(onClick={ variantes=variantes.filterIndexed { i,_ -> i != index } }, modifier=Modifier.heightIn(min=48.dp)) { Text("Quitar variante nueva", color=Wine) }
                        }
                    }
                }
                if (variantes.size < 20 && original == null) OutlineButton("＋ Agregar variante", { variantes=variantes + nuevaVariante(opciones) })
            }
            if (original == null || !manejaStock) SelectorProducto("Al agotarse",
                listOf("mostrar_agotado" to "Mostrar agotado", "ocultar" to "Ocultar", "pasar_a_pedido" to "Pasar a pedido"), alAgotarse) { alAgotarse=it }
            error?.let { AvisoEmprendedora(it, true) }
            Text("El stock existente se modifica desde “Ajustar stock” en el catálogo para conservar su historial.", fontSize=13.sp, color=Muted)
        }
    } else {
        StepScaffold("Paso 5 de 5", "Revisa y publica", "Tú decides cuándo se muestra y cómo se descuenta.",
            onNext={ guardarBorrador() }, onBack={ paso=4 }, onSaveExit=::cancelarEditor,
            nextLabel=if (original == null) "Guardar borrador" else "Guardar cambios", nextEnabled=!enviando,
            pasoActual=5, showSaveExit=false) {
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
                    Text(if (conVariantes) variantes.size.toString() + " variante(s)" else "Precio: " + (precioBase.toLongOrNull()?.let(::pesos) ?: "Pendiente"), color=Muted, modifier=Modifier.padding(top=8.dp))
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
            error?.let { AvisoEmprendedora(it, true) }
        }
    }}

private data class VarianteBorrador(
    val id: String,
    val opciones: Map<String, String>,
    val precio: String,
    val stock: String,
    val activa: Boolean,
    val existente: Boolean
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
                CampoProducto("Cantidad (+ suma, − resta)", cantidad, { cantidad = it.filter { c -> c.isDigit() || c == '-' || c == '+' }.take(10) }, 10)
                SelectorProducto("Motivo", listOf("reposicion" to "Reposición", "perdida" to "Pérdida", "correccion" to "Corrección"), motivo) { motivo = it }
                Spacer(Modifier.height(8.dp))
                CampoProducto("Nota (opcional)", nota, { nota = it }, 500)
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
private fun CampoProducto(label: String, value: String, onValue: (String) -> Unit, max: Int, multiline: Boolean = false, readOnly: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (!readOnly) onValue(it.take(max)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
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
