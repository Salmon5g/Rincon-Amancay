package com.example.amancay.emprendedora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
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
import androidx.compose.foundation.verticalScroll
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
import com.example.amancay.data.CampoTipoProducto
import com.example.amancay.data.CategoriaEmprendedora
import com.example.amancay.data.ColaOfflineCatalogo
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
import com.example.amancay.ui.CamaraProductoGuiada
import com.example.amancay.ui.ProductImage
import com.example.amancay.ui.StepScaffold
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.mutableIntStateOf
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ImagenSeleccionadaProducto(
    val uri: Uri?,
    val bitmap: Bitmap?,
    val archivoId: String,
    val uriOriginalParaEliminar: Uri? = null
)

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
    return try {
        context.contentResolver.openInputStream(uri)?.use { entrada -> FileOutputStream(destino).use(entrada::copyTo) }
            ?: throw IllegalStateException("No se pudo conservar la foto seleccionada.")
        Uri.fromFile(destino)
    } catch (error: Exception) {
        destino.delete()
        throw error
    }
}

private fun guardarBitmapPersistente(context: Context, bitmap: Bitmap): Uri {
    val directorio = File(context.filesDir, "borradores-producto").apply { mkdirs() }
    val destino = File(directorio, "${UUID.randomUUID()}.jpg")
    return try {
        FileOutputStream(destino).use { salida ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, salida)) { "No se pudo preparar la foto tomada." }
        }
        Uri.fromFile(destino)
    } catch (error: Exception) {
        destino.delete()
        throw error
    }
}

private suspend fun decodificarFotoSeleccionada(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val escala = minOf(1f, 1600f / max(info.size.width, info.size.height))
            if (escala < 1f) {
                decoder.setTargetSize(
                    (info.size.width * escala).roundToInt().coerceAtLeast(1),
                    (info.size.height * escala).roundToInt().coerceAtLeast(1)
                )
            }
            decoder.isMutableRequired = true
        }
    }.getOrNull()
}

private fun prepararVistaFotoProducto(original: Bitmap, recorteCuadrado: Boolean, mejorarLuz: Boolean): Bitmap {
    val encuadrada = if (recorteCuadrado) {
        val lado = minOf(original.width, original.height)
        Bitmap.createBitmap(original, (original.width - lado) / 2, (original.height - lado) / 2, lado, lado)
    } else original
    if (!mejorarLuz) return encuadrada

    val resultado = Bitmap.createBitmap(encuadrada.width, encuadrada.height, Bitmap.Config.ARGB_8888)
    val matriz = ColorMatrix(floatArrayOf(
        1.04f, 0f, 0f, 0f, 8f,
        0f, 1.04f, 0f, 0f, 8f,
        0f, 0f, 1.04f, 0f, 8f,
        0f, 0f, 0f, 1f, 0f
    ))
    AndroidCanvas(resultado).drawBitmap(encuadrada, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(matriz)
    })
    return resultado
}

private data class BorradorAlta(
    val tipoId: String, val categoriaId: String, val nombre: String, val descripcion: String,
    val modalidad: String, val manejaStock: Boolean, val alAgotarse: String,
    val precio: String, val stock: String, val paso: Int, val foto: String?,
    val archivoId: String, val atributos: Map<String, String>, val variantes: List<VarianteBorrador>,
    val conVariantes: Boolean = false,
    val operacionId: String = "",
    val productoIdNuevo: String = "",
    val firmaSolicitud: String = "",
    val recorteCuadrado: Boolean = true,
    val mejorarLuz: Boolean = false
)

private val TIPOS_PRODUCTO_APP = setOf(
    "alimento_reposteria", "artesania_manualidad", "plantas", "producto_agricola",
    "vestuario", "accesorio", "cosmetica_natural"
)

private fun firmaSolicitudProducto(tiendaId: String, productoId: String, solicitud: ProductoSolicitud): String {
    val estable = solicitud.copy(
        atributos = solicitud.atributos.toSortedMap(),
        variantes = solicitud.variantes.map { it.copy(opciones = it.opciones.toSortedMap()) }
    )
    val contenido = "$tiendaId|$productoId|$estable"
    val hash = MessageDigest.getInstance("SHA-256").digest(contenido.toByteArray(Charsets.UTF_8))
    return BigInteger(1, hash).toString(16).padStart(64, '0')
}

private data class IdentidadAltaProducto(
    val productoId: String,
    val operacionId: String,
    val firma: String,
    val avisoIntentoAnterior: Boolean = false
)

private fun identidadAltaInicial(borrador: BorradorAlta?): IdentidadAltaProducto {
    val migrar = borrador?.let { it.operacionId.isNotBlank() && it.firmaSolicitud.isBlank() } == true
    return IdentidadAltaProducto(
        productoId = if (migrar) UUID.randomUUID().toString() else borrador?.productoIdNuevo?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
        operacionId = if (migrar) "" else borrador?.operacionId.orEmpty(),
        firma = if (migrar) "" else borrador?.firmaSolicitud.orEmpty(),
        avisoIntentoAnterior = migrar
    )
}

private data class AltaPreparada(
    val identidad: IdentidadAltaProducto,
    val solicitud: ProductoSolicitud
)

private data class EntradaEditorProducto(
    val original: ProductoEmprendedora?,
    val tipo: TipoProductoEmprendedora?,
    val tipos: List<TipoProductoEmprendedora>,
    val tipoId: String,
    val categoriaId: String,
    val categorias: List<CategoriaEmprendedora>,
    val nombre: String,
    val descripcion: String,
    val modalidad: String,
    val manejaStock: Boolean,
    val alAgotarse: String,
    val precioBase: String,
    val stockInicial: String,
    val atributos: Map<String, String>,
    val campos: List<CampoTipoProducto>,
    val conVariantes: Boolean,
    val opciones: List<String>,
    val variantes: List<VarianteBorrador>,
    val fotoLista: Boolean,
    val tiendaPublicada: Boolean
)

private fun campoObligatorioFaltante(entrada: EntradaEditorProducto): CampoTipoProducto? =
    entrada.campos.firstOrNull { it.obligatorio && entrada.atributos[it.clave].isNullOrBlank() }

private fun errorDatosProducto(entrada: EntradaEditorProducto): String? = when {
    entrada.nombre.isBlank() -> "Ingresa el nombre del producto."
    entrada.descripcion.isBlank() -> "Ingresa la descripción del producto."
    else -> null
}

private fun errorCategoriaProducto(entrada: EntradaEditorProducto): String? = when {
    entrada.categorias.isEmpty() -> "No hay categorías activas para este tipo de producto."
    entrada.categorias.none {
        it.id == entrada.categoriaId && (it.tipoProductoId == entrada.tipoId || it.id == entrada.original?.categoriaId)
    } -> "Selecciona una categoría para este tipo de producto."
    else -> null
}

private fun errorPrecioStockProducto(entrada: EntradaEditorProducto): String? {
    val stock = entrada.stockInicial.toIntOrNull()
    if (!entrada.conVariantes) {
        return when {
            (entrada.precioBase.toLongOrNull() ?: 0L) <= 0L -> "Ingresa un precio entero mayor que cero."
            entrada.manejaStock && entrada.original == null && (stock ?: -1) < 0 -> "Ingresa un stock inicial entero de cero o más."
            else -> null
        }
    }
    var incompleta = -1
    for (index in entrada.variantes.indices) {
        val variante = entrada.variantes[index]
        val opcionesIncompletas = entrada.opciones.any { variante.opciones[it].isNullOrBlank() }
        val precioInvalido = (variante.precio.toLongOrNull() ?: 0L) <= 0L
        val stockInvalido = entrada.manejaStock && !variante.existente && (variante.stock.toIntOrNull() ?: -1) < 0
        if (opcionesIncompletas || precioInvalido || stockInvalido) {
            incompleta = index
            break
        }
    }
    if (entrada.variantes.isEmpty()) return "Agrega al menos una variante."
    if (incompleta >= 0) return "Variante ${incompleta + 1}: completa sus opciones, ingresa un precio mayor que cero y, si corresponde, un stock de cero o más."
    val combinaciones = entrada.variantes.map { variante ->
        entrada.opciones.map { variante.opciones[it].orEmpty().trim().lowercase() }
    }
    return if (combinaciones.distinct().size != combinaciones.size) "Cada variante debe tener una combinación de opciones distinta." else null
}

private fun validarEntradaEditorProducto(entrada: EntradaEditorProducto, publicar: Boolean): String? {
    val tipo = entrada.tipo
    val invalidField = campoObligatorioFaltante(entrada)
    val stock = entrada.stockInicial.toIntOrNull()
    return when {
        tipo == null -> "Espera a que se carguen los tipos de producto."
        errorDatosProducto(entrada) != null -> errorDatosProducto(entrada)
        errorCategoriaProducto(entrada) != null -> errorCategoriaProducto(entrada)
        entrada.original == null && entrada.tipos.none { it.id == entrada.tipoId && it.activo } -> "Selecciona un tipo habilitado."
        invalidField != null -> "Completa el campo ${invalidField.etiqueta}."
        errorPrecioStockProducto(entrada) != null -> errorPrecioStockProducto(entrada)
        entrada.modalidad == "pieza_unica" && stock != 1 && entrada.original == null -> "Una pieza única comienza con stock 1."
        publicar && !entrada.fotoLista && entrada.original?.imagenes.isNullOrEmpty() -> "Agrega una foto antes de publicar."
        publicar && !entrada.tiendaPublicada -> "Publica primero tu tienda desde Inicio."
        else -> null
    }
}

private fun crearSolicitudEditorProducto(entrada: EntradaEditorProducto, productoIdNuevo: String?): ProductoSolicitud {
    val tipo = checkNotNull(entrada.tipo)
    val stock = entrada.stockInicial.toIntOrNull()
    val solicitudes = if (entrada.conVariantes) entrada.variantes.map { variante ->
        VarianteProductoSolicitud(
            variante.id,
            variante.opciones,
            variante.precio.toLong(),
            if (variante.existente || !entrada.manejaStock) null else variante.stock.toInt(),
            variante.activa
        )
    } else emptyList()
    return ProductoSolicitud(
        id = entrada.original?.id,
        versionEsperada = entrada.original?.versionEsperada,
        productoIdNuevo = productoIdNuevo,
        nombre = entrada.nombre.trim(),
        descripcion = entrada.descripcion.trim(),
        tipo = tipo,
        tieneVariantes = entrada.conVariantes,
        categoriaId = entrada.categoriaId,
        atributos = entrada.atributos.filterValues { it.isNotBlank() },
        modalidad = entrada.modalidad,
        manejaStock = entrada.manejaStock,
        alAgotarse = entrada.alAgotarse,
        precioBase = if (entrada.conVariantes) null else entrada.precioBase.toLong(),
        stockInicial = if (!entrada.conVariantes && entrada.manejaStock && entrada.original == null) stock else null,
        variantes = solicitudes
    )
}

private fun prepararSolicitudAlta(
    tiendaId: String,
    identidadActual: IdentidadAltaProducto,
    solicitud: ProductoSolicitud
): AltaPreparada {
    val firmaActual = firmaSolicitudProducto(tiendaId, identidadActual.productoId, solicitud)
    val separar = identidadActual.firma.isNotBlank() && identidadActual.firma != firmaActual
    val productoId = if (separar) UUID.randomUUID().toString() else identidadActual.productoId
    val operacionId = if (separar || identidadActual.operacionId.isBlank()) UUID.randomUUID().toString() else identidadActual.operacionId
    val firma = if (separar) firmaSolicitudProducto(tiendaId, productoId, solicitud) else firmaActual
    val identidad = IdentidadAltaProducto(productoId, operacionId, firma, identidadActual.avisoIntentoAnterior || separar)
    return AltaPreparada(identidad, solicitud.copy(productoIdNuevo = productoId, operacionId = operacionId))
}

private suspend fun guardarConVersionVigente(
    repository: ProductosEmprendedoraRepository,
    tiendaId: String,
    solicitud: ProductoSolicitud
): String = try {
    repository.guardar(tiendaId, solicitud)
} catch (e: ApiEmprendedoraException) {
    val id = solicitud.id
    if (id == null || !ColaOfflineCatalogo.esConflictoDeVersion(e)) throw e
    val version = repository.leerVersionesDisponibilidad(tiendaId, listOf(id))[id] ?: throw e
    repository.guardar(tiendaId, solicitud.copy(versionEsperada = version, operacionId = UUID.randomUUID().toString()))
}

private fun leerBorradorAlta(context: Context, tiendaId: String): BorradorAlta? = runCatching {
    val raw = context.getSharedPreferences("borradores_productos", Context.MODE_PRIVATE).getString(tiendaId, null) ?: return null
    val json = JSONObject(raw)
    val versionFlujo = json.optInt("versionFlujo", 1)
    val pasoGuardado = json.optInt("paso", 1)
    val pasoMigrado = if (versionFlujo < 2) when (pasoGuardado) {
        3 -> 4 // El paso 3 anterior también contenía los detalles del tipo.
        in 4..5 -> pasoGuardado + 1
        6 -> 7
        else -> pasoGuardado
    } else pasoGuardado
    val atributosJson = json.optJSONObject("atributos") ?: JSONObject()
    val variantesJson = json.optJSONArray("variantes") ?: JSONArray()
    BorradorAlta(
        json.optString("tipoId"), json.optString("categoriaId"), json.optString("nombre"), json.optString("descripcion"),
        json.optString("modalidad", "regular"), json.optBoolean("manejaStock", true), json.optString("alAgotarse", "mostrar_agotado"),
        json.optString("precio"), json.optString("stock"), pasoMigrado, json.optString("foto").takeIf(String::isNotBlank),
        json.optString("archivoId", UUID.randomUUID().toString()), atributosJson.keys().asSequence().associateWith { atributosJson.optString(it) },
        (0 until variantesJson.length()).map { i ->
            val v = variantesJson.getJSONObject(i)
            val op = v.optJSONObject("opciones") ?: JSONObject()
            VarianteBorrador(v.optString("id"), op.keys().asSequence().associateWith { op.optString(it) }, v.optString("precio"), v.optString("stock"), v.optBoolean("activa", true), false,
                fotoUri = v.optString("foto").takeIf(String::isNotBlank)?.let(Uri::parse), archivoId = v.optString("archivoId", UUID.randomUUID().toString()), fotoNueva = v.optBoolean("fotoNueva"))
        },
        json.optBoolean("conVariantes", variantesJson.length() > 0),
        json.optString("operacionId"), json.optString("productoIdNuevo"), json.optString("firmaSolicitud"),
        json.optBoolean("recorteCuadrado", true), json.optBoolean("mejorarLuz", false)
    )
}.getOrNull()

private fun guardarBorradorAlta(context: Context, tiendaId: String, borrador: BorradorAlta, confirmar: Boolean = false) {
    val atributos = JSONObject().apply { borrador.atributos.forEach { (k, v) -> put(k, v) } }
    val variantes = JSONArray().apply { borrador.variantes.forEach { v ->
        put(JSONObject().apply {
            put("id", v.id); put("opciones", JSONObject().apply { v.opciones.forEach { (k, value) -> put(k, value) } })
            put("precio", v.precio); put("stock", v.stock); put("activa", v.activa); put("foto", v.fotoUri?.toString().orEmpty())
            put("archivoId", v.archivoId); put("fotoNueva", v.fotoNueva)
        })
    } }
    val json = JSONObject().apply {
        put("versionFlujo", 3)
        put("tipoId", borrador.tipoId); put("categoriaId", borrador.categoriaId); put("nombre", borrador.nombre); put("descripcion", borrador.descripcion)
        put("modalidad", borrador.modalidad); put("manejaStock", borrador.manejaStock); put("alAgotarse", borrador.alAgotarse)
        put("precio", borrador.precio); put("stock", borrador.stock); put("paso", borrador.paso); put("foto", borrador.foto.orEmpty())
        put("archivoId", borrador.archivoId); put("atributos", atributos); put("variantes", variantes)
        put("conVariantes", borrador.conVariantes)
        put("operacionId", borrador.operacionId); put("productoIdNuevo", borrador.productoIdNuevo)
        put("firmaSolicitud", borrador.firmaSolicitud)
        put("recorteCuadrado", borrador.recorteCuadrado); put("mejorarLuz", borrador.mejorarLuz)
    }
    val editor = context.getSharedPreferences("borradores_productos", Context.MODE_PRIVATE).edit().putString(tiendaId, json.toString())
    if (confirmar) editor.commit() else editor.apply()
}

private suspend fun cargarFotosVariantesPrivadas(
    repository: ProductosEmprendedoraRepository,
    tiendaId: String,
    producto: ProductoEmprendedora,
    variantes: List<VarianteBorrador>
): List<VarianteBorrador> {
    val resultado = variantes.toMutableList()
    for (index in variantes.indices) {
        val variante = variantes[index]
        val imagen = variante.imagen ?: continue
        if (variante.fotoBitmap != null || variante.fotoUri != null) continue
        val indiceImagen = producto.imagenes.indexOf(imagen)
        if (indiceImagen < 0) continue
        val bitmap = try {
            decodificarVistaPreviaProducto(repository.leerImagenPrivada(tiendaId, producto.id, indiceImagen))
        } catch (_: Exception) {
            null
        }
        if (bitmap != null) resultado[index] = variante.copy(fotoBitmap = bitmap)
    }
    return resultado
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
                        if (!ColaOfflineCatalogo.hayRed(context)) {
                            if (solicitud.id == null) {
                                nuevo = false; editando = null
                                alAviso("El producto quedó guardado como borrador local. Podrás continuar y sincronizarlo al volver internet.")
                                alFinalizarEdicion()
                                return@launch
                            }
                            if (imagen != null || imagenesVariantes.isNotEmpty()) {
                                throw ApiEmprendedoraException("sin-conexion", "Los cambios de fotos necesitan conexión. Conservamos el formulario para que puedas guardarlo al volver internet.")
                            }
                            if (!PreferenciasCatalogoEmprendedora.catalogoSinConexion(context, tiendaId)) {
                                throw ApiEmprendedoraException("sin-conexion", "Activa «Guardar catálogo sin conexión» para conservar esta edición.")
                            }
                            ColaOfflineCatalogo.encolarEdicion(context, tiendaId, solicitud, publicar)
                            nuevo = false; editando = null
                            alAviso("Sin conexión: los cambios del producto quedaron guardados y se sincronizarán al volver internet.")
                            alFinalizarEdicion()
                            return@launch
                        }
                        val productoId = guardarConVersionVigente(repository, tiendaId, solicitud)
                        if (imagen != null) {
                            val preparada = prepararImagenProducto(context.contentResolver, imagen.uri, imagen.bitmap)
                            val indiceAReemplazar = editando?.imagenes?.takeIf { it.isNotEmpty() }?.let { 0 }
                            repository.subirImagen(tiendaId, productoId, imagen.archivoId, preparada, indiceAReemplazar)
                        }
                        imagenesVariantes.forEach { (varianteId, fotoVariante) ->
                            val preparada = prepararImagenProducto(context.contentResolver, fotoVariante.uri, fotoVariante.bitmap)
                            val ruta = repository.subirImagen(tiendaId, productoId, fotoVariante.archivoId, preparada)
                            repository.asignarImagenVariante(tiendaId, productoId, varianteId, ruta)
                        }
                        if (publicar) {
                            val publicado = repository.leerCompleto(tiendaId, productoId)
                            repository.publicar(tiendaId, publicado)
                        }
                        if (solicitud.id == null) {
                            context.getSharedPreferences("borradores_productos", Context.MODE_PRIVATE).edit().remove(tiendaId).apply()
                        }
                        eliminarFotoTemporalProducto(context, imagen?.uri)
                        eliminarFotoTemporalProducto(context, imagen?.uriOriginalParaEliminar)
                        imagenesVariantes.values.forEach { eliminarFotoTemporalProducto(context, it.uri) }
                        val imagenPublicadaActualizada = editando?.estadoPublicacion == "publicado" && imagen != null
                        nuevo = false; editando = null
                        alAviso(
                            when {
                                publicar -> "Producto publicado. Tus clientes lo verán según stock y disponibilidad."
                                imagenPublicadaActualizada -> "Imagen actualizada. El cambio ya está disponible en el catálogo público."
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
    val ordenTiposApp = listOf(
        "alimento_reposteria", "artesania_manualidad", "plantas", "producto_agricola",
        "vestuario", "accesorio", "cosmetica_natural"
    )
    val tiposNuevos = tipos.filter { it.activo && it.id in TIPOS_PRODUCTO_APP }
        .sortedBy { ordenTiposApp.indexOf(it.id) }
    val tiposDisponibles = if (original == null) tiposNuevos else tipos.filter { it.activo || it.id == original.tipoProductoId }
    val initialTipo = original?.tipoProductoId
        ?: borradorGuardado?.tipoId?.takeIf { id -> tiposNuevos.any { it.id == id } }
        ?: tiposNuevos.firstOrNull()?.id.orEmpty()
    val categoriaInicialPorTipo = categorias.firstOrNull { it.activo && it.tipoProductoId == initialTipo }?.id.orEmpty()
    val initialCategory = original?.categoriaId
        ?: borradorGuardado?.categoriaId?.takeIf { id -> categorias.any { it.id == id && it.activo && it.tipoProductoId == initialTipo } }
        ?: categoriaInicialPorTipo
    var tipoId by remember(original?.id) { mutableStateOf(initialTipo) }
    var categoriaId by remember(original?.id) { mutableStateOf(initialCategory) }
    var nombre by remember(original?.id, original?.versionEsperada) { mutableStateOf(borradorGuardado?.nombre ?: original?.nombre.orEmpty()) }
    var descripcion by remember(original?.id, original?.versionEsperada) { mutableStateOf(borradorGuardado?.descripcion ?: original?.descripcion.orEmpty()) }
    var modalidad by remember(original?.id) { mutableStateOf(borradorGuardado?.modalidad ?: original?.modalidad ?: valoresDefecto.modalidad) }
    var manejaStock by remember(original?.id) { mutableStateOf(borradorGuardado?.manejaStock ?: original?.manejaStock ?: if (valoresDefecto.modalidad == "pieza_unica") true else valoresDefecto.manejaStock) }
    var alAgotarse by remember(original?.id, original?.versionEsperada) { mutableStateOf(borradorGuardado?.alAgotarse ?: original?.alAgotarse ?: valoresDefecto.alAgotarse) }
    var precioBase by remember(original?.id, original?.versionEsperada) { mutableStateOf(borradorGuardado?.precio ?: original?.precioBase?.toString().orEmpty()) }
    var stockInicial by remember(original?.id) { mutableStateOf(borradorGuardado?.stock ?: original?.stock?.toString().orEmpty()) }
    var paso by remember(original?.id) { mutableIntStateOf(borradorGuardado?.paso ?: 1) }
    val fotoLocal = remember(original?.id) { DraftState().apply { photoUri = borradorGuardado?.foto?.let(Uri::parse); fotoLista = photoUri != null } }
    var recorteCuadrado by remember(original?.id) { mutableStateOf(borradorGuardado?.recorteCuadrado ?: true) }
    var mejorarLuz by remember(original?.id) { mutableStateOf(borradorGuardado?.mejorarLuz ?: false) }
    var compararOriginal by remember { mutableStateOf(false) }
    var imagenOriginalSeleccionada by remember(original?.id) { mutableStateOf<Bitmap?>(null) }
    var imagenPreparada by remember(original?.id) { mutableStateOf<Bitmap?>(null) }
    var cargandoPreviaSeleccionada by remember(original?.id) { mutableStateOf(false) }
    var procesandoPreviaSeleccionada by remember(original?.id) { mutableStateOf(false) }
    var camaraProductoVisible by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
            val persistida = runCatching { copiarFotoPersistente(context, uri) }.getOrElse {
                error = it.message ?: "No se pudo conservar la foto elegida. Inténtalo otra vez."
                null
            }
            if (persistida != null && idVariante != null) {
                variantes = variantes.map { row -> if (row.id == idVariante) row.copy(fotoUri = persistida, fotoBitmap = null, archivoId = UUID.randomUUID().toString(), imagen = null, fotoNueva = true) else row }
            } else if (persistida != null) {
                val fotoAnterior = fotoLocal.photoUri
                imagenOriginalSeleccionada = null
                imagenPreparada = null
                fotoLocal.photoUri = persistida
                fotoLocal.photoBitmap = null
                fotoLocal.fotoLista = true
                recorteCuadrado = true
                mejorarLuz = false
                compararOriginal = false
                archivoId = UUID.randomUUID().toString()
                eliminarFotoTemporalProducto(context, fotoAnterior)
            }
        }
        varianteConFotoTemporal = null
    }
    fun abrirGaleriaParaFoto(idVariante: String? = null) {
        varianteConFotoTemporal = idVariante
        try {
            selectorGaleria.launch("image/*")
        } catch (e: Exception) {
            varianteConFotoTemporal = null
            error = e.message ?: "No se pudo abrir la galería de fotos."
        }
    }
    val selectorCamara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { guardada ->
        val uri = capturaPendiente?.let(Uri::parse)
        capturaPendiente = null
        val idVariante = varianteCapturaPendiente
        varianteCapturaPendiente = null
        if (guardada && uri != null) {
            val persistida = runCatching { copiarFotoPersistente(context, uri) }.getOrElse {
                error = it.message ?: "No se pudo conservar la foto tomada. Inténtalo otra vez."
                null
            }
            if (persistida != null && idVariante != null) {
                variantes = variantes.map { row -> if (row.id == idVariante) row.copy(fotoUri = persistida, fotoBitmap = null, archivoId = UUID.randomUUID().toString(), imagen = null, fotoNueva = true) else row }
            } else if (persistida != null) {
                val fotoAnterior = fotoLocal.photoUri
                fotoLocal.photoBitmap = null
                fotoLocal.photoUri = persistida
                fotoLocal.fotoLista = true
                archivoId = UUID.randomUUID().toString()
                eliminarFotoTemporalProducto(context, fotoAnterior)
            }
        }
        eliminarFotoTemporalProducto(context, uri)
    }
    fun abrirCamaraParaFoto(idVariante: String? = null) {
        if (idVariante == null) {
            camaraProductoVisible = true
            error = null
            return
        }
        val uri = try {
            crearUriFotoTemporalProducto(context)
        } catch (e: Exception) {
            error = e.message ?: "No se pudo preparar la cámara."
            return
        }
        capturaPendiente = uri.toString()
        varianteCapturaPendiente = idVariante
        try {
            selectorCamara.launch(uri)
        } catch (e: Exception) {
            capturaPendiente = null
            varianteCapturaPendiente = null
            eliminarFotoTemporalProducto(context, uri)
            error = e.message ?: "No se encontró una aplicación de cámara disponible."
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
        variantes = cargarFotosVariantesPrivadas(repository, tiendaId, producto, variantes)
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
    val categoriasDisponibles = categorias.filter {
        (it.activo && it.tipoProductoId == tipoId) || it.id == original?.categoriaId
    }
    LaunchedEffect(tipoId, categorias, original?.categoriaId) {
        if (original == null && categoriasDisponibles.none { it.id == categoriaId }) {
            categoriaId = categoriasDisponibles.firstOrNull()?.id.orEmpty()
        }
    }
    var identidadAlta by remember(original?.id) { mutableStateOf(identidadAltaInicial(borradorGuardado)) }
    val operacionIdEdicion = remember(original?.id, original?.versionEsperada, nombre, descripcion, tipoId, categoriaId, modalidad, manejaStock, alAgotarse, precioBase, stockInicial, atributos, variantes) {
        UUID.randomUUID().toString()
    }
    val operacionId = if (original == null) identidadAlta.operacionId else operacionIdEdicion
    var confirmarDescarte by rememberSaveable(original?.id) { mutableStateOf(false) }
    fun guardarLocalYSalir() {
        if (original == null) {
            guardarBorradorAlta(
                context,
                tiendaId,
                BorradorAlta(tipoId, categoriaId, nombre, descripcion, modalidad, manejaStock, alAgotarse, precioBase, stockInicial, paso, fotoLocal.photoUri?.toString(), archivoId, atributos, variantes, conVariantes, operacionId.takeIf { identidadAlta.firma.isNotBlank() }.orEmpty(), identidadAlta.productoId, identidadAlta.firma, recorteCuadrado, mejorarLuz)
            )
            onCancel()
        } else onCancel()
    }
    LaunchedEffect(fotoLocal.photoUri, fotoLocal.fotoLista) {
        imagenOriginalSeleccionada = null
        imagenPreparada = null
        val uri = fotoLocal.photoUri?.takeIf { fotoLocal.fotoLista } ?: return@LaunchedEffect
        cargandoPreviaSeleccionada = true
        try {
            imagenOriginalSeleccionada = decodificarFotoSeleccionada(context, uri)
        } finally {
            cargandoPreviaSeleccionada = false
        }
    }
    LaunchedEffect(imagenOriginalSeleccionada, recorteCuadrado, mejorarLuz) {
        val originalPrevia = imagenOriginalSeleccionada
        if (originalPrevia == null) {
            imagenPreparada = null
            return@LaunchedEffect
        }
        procesandoPreviaSeleccionada = true
        try {
            imagenPreparada = withContext(Dispatchers.Default) {
                prepararVistaFotoProducto(originalPrevia, recorteCuadrado, mejorarLuz)
            }
        } finally {
            procesandoPreviaSeleccionada = false
        }
    }
    LaunchedEffect(original?.id, tipoId, categoriaId, nombre, descripcion, modalidad, manejaStock, alAgotarse, precioBase, stockInicial, paso, archivoId, fotoLocal.photoUri, atributos, variantes, conVariantes, operacionId, identidadAlta, recorteCuadrado, mejorarLuz) {
        if (original == null) guardarBorradorAlta(context, tiendaId, BorradorAlta(tipoId, categoriaId, nombre, descripcion, modalidad, manejaStock, alAgotarse, precioBase, stockInicial, paso, fotoLocal.photoUri?.toString(), archivoId, atributos, variantes, conVariantes, operacionId.takeIf { identidadAlta.firma.isNotBlank() }.orEmpty(), identidadAlta.productoId, identidadAlta.firma, recorteCuadrado, mejorarLuz))
    }

    LaunchedEffect(tipoId, original?.id) {
        if (original == null && (borradorGuardado == null || tipoId != borradorGuardado.tipoId)) {
            atributos = emptyMap()
            conVariantes = opciones.isNotEmpty()
            variantes = if (opciones.isNotEmpty()) listOf(nuevaVariante(opciones)) else emptyList()
        }
    }

    val onDiscardDraft: (() -> Unit)? = if (original == null && !enviando) ({ confirmarDescarte = true }) else null
    if (confirmarDescarte && original == null) {
        AlertDialog(
            onDismissRequest = { confirmarDescarte = false },
            title = { Text("¿Descartar este borrador?", color = Dark, fontWeight = FontWeight.Bold) },
            text = { Text("Se borrarán los datos y las fotos guardadas en este teléfono. Si algún intento anterior alcanzó Firebase, ese producto seguirá en Mis productos.", color = Muted) },
            confirmButton = {
                TextButton(onClick = ::cancelarEditor) { Text("Descartar", color = Wine) }
            },
            dismissButton = {
                TextButton(onClick = { confirmarDescarte = false }) { Text("Seguir editando", color = Muted) }
            }
        )
    }

    val entradaEditor = EntradaEditorProducto(
        original, tipo, tipos, tipoId, categoriaId, categoriasDisponibles, nombre, descripcion,
        modalidad, manejaStock, alAgotarse, precioBase, stockInicial, atributos, campos,
        conVariantes, opciones, variantes, fotoLocal.fotoLista, tiendaPublicada
    )
    val invalidField = campoObligatorioFaltante(entradaEditor)
    val errorDatos = errorDatosProducto(entradaEditor)
    val errorCategoria = errorCategoriaProducto(entradaEditor)
    val errorPreciosYStock = errorPrecioStockProducto(entradaEditor)
    fun guardarBorrador(publicar: Boolean = false) {
        val mensajeError = validarEntradaEditorProducto(entradaEditor, publicar)
        error = mensajeError
        if (mensajeError != null) return
        val solicitud = crearSolicitudEditorProducto(entradaEditor, identidadAlta.productoId.takeIf { original == null })
        val solicitudFinal = if (original == null) {
            val preparada = prepararSolicitudAlta(tiendaId, identidadAlta, solicitud)
            identidadAlta = preparada.identidad
            guardarBorradorAlta(
                context,
                tiendaId,
                BorradorAlta(tipoId, categoriaId, nombre, descripcion, modalidad, manejaStock, alAgotarse, precioBase, stockInicial, paso, fotoLocal.photoUri?.toString(), archivoId, atributos, variantes, conVariantes, preparada.identidad.operacionId, preparada.identidad.productoId, preparada.identidad.firma, recorteCuadrado, mejorarLuz),
                confirmar = true
            )
            preparada.solicitud
        } else solicitud.copy(operacionId = operacionId)
        val imagen = if (fotoLocal.fotoLista && (fotoLocal.photoUri != null || fotoLocal.photoBitmap != null)) {
            val fotoProcesada = imagenPreparada
            val seAplicaAjuste = fotoProcesada != null && (recorteCuadrado || mejorarLuz)
            ImagenSeleccionadaProducto(
                uri = if (seAplicaAjuste) null else fotoLocal.photoUri,
                bitmap = if (seAplicaAjuste) fotoProcesada else fotoLocal.photoBitmap,
                archivoId = archivoId,
                uriOriginalParaEliminar = fotoLocal.photoUri.takeIf { seAplicaAjuste }
            )
        } else null
        val fotosVariantes = variantes.mapNotNull { row ->
            if (row.fotoNueva) {
                row.fotoUri?.let { row.id to ImagenSeleccionadaProducto(it, row.fotoBitmap, row.archivoId) }
                    ?: row.fotoBitmap?.let { bitmap -> row.id to ImagenSeleccionadaProducto(null, bitmap, row.archivoId) }
            } else null
        }.toMap()
        onSave(solicitudFinal, imagen, fotosVariantes, publicar)
    }
    fun elegirTipoProducto(item: TipoProductoEmprendedora) {
        tipoId = item.id
        categoriaId = categorias.firstOrNull { it.activo && it.tipoProductoId == item.id }?.id.orEmpty()
        conVariantes = item.opcionesVariante.isNotEmpty()
        error = null
    }
    if (camaraProductoVisible) {
        CamaraProductoGuiada(
            onFoto = { bitmap ->
                scope.launch {
                    val persistida = runCatching { withContext(Dispatchers.IO) { guardarBitmapPersistente(context, bitmap) } }.getOrElse {
                        error = it.message ?: "No se pudo guardar la foto tomada. Inténtalo de nuevo."
                        null
                    }
                    if (persistida != null) {
                        val fotoAnterior = fotoLocal.photoUri
                        imagenOriginalSeleccionada = null
                        imagenPreparada = null
                        fotoLocal.photoUri = persistida
                        fotoLocal.photoBitmap = null
                        fotoLocal.fotoLista = true
                        recorteCuadrado = true
                        mejorarLuz = false
                        compararOriginal = false
                        archivoId = UUID.randomUUID().toString()
                        eliminarFotoTemporalProducto(context, fotoAnterior)
                    }
                    camaraProductoVisible = false
                }
            },
            onCancelar = { camaraProductoVisible = false },
            onGaleria = {
                camaraProductoVisible = false
                abrirGaleriaParaFoto()
            }
        )
    } else if (paso == 1) {
        PhotoStep(draft=fotoLocal, bump={}, onTakePhoto={ abrirCamaraParaFoto() },
            onPickPhoto={ abrirGaleriaParaFoto() }, onNext={ paso=2 },
            onBack=::cancelarEditor, onSaveExit=::guardarLocalYSalir, showSaveExit=true, onDiscardDraft=onDiscardDraft,
            totalPasos=6, dibujarBajoBarraSistema=true, continuarHabilitado=!(cargandoPreviaSeleccionada || procesandoPreviaSeleccionada),
            imagenOriginalSeleccionada=imagenOriginalSeleccionada,
            imagenPreparada=imagenPreparada,
            recorteCuadrado=recorteCuadrado,
            onRecorteCuadrado={ recorteCuadrado = it; compararOriginal = false },
            mejorarLuz=mejorarLuz,
            onMejorarLuz={ mejorarLuz = it; compararOriginal = false },
            compararOriginal=compararOriginal,
            onCompararOriginal={ compararOriginal = it },
            procesandoImagen=cargandoPreviaSeleccionada || procesandoPreviaSeleccionada,
            imagenExistente=original?.imagenes?.isNotEmpty() == true,
            imagenPrevia=imagenPrivada,
            mensajeImagenExistente=when {
                imagenPrivada != null -> "Vista previa de tu imagen privada. Solo tú puedes verla hasta publicar el producto."
                cargandoVistaPrevia -> "Cargando la vista previa segura…"
                errorVistaPrevia != null -> errorVistaPrevia
                else -> "La foto actual está guardada de forma privada. Si eliges otra, reemplazará la imagen principal y conservará las demás."
            })
    } else if (paso == 2) {
        StepScaffold("Paso 2 de 6", "Datos del producto", "Completa la información para que más personas encuentren tu producto.",
            onNext={ paso=3 }, onBack={ paso=1 }, onSaveExit=::cancelarEditor,
            nextEnabled=errorDatos == null, pasoActual=2, showSaveExit=false, onExit=::guardarLocalYSalir, onDiscardDraft=onDiscardDraft, totalPasos=6, dibujarBajoBarraSistema=true) {
            Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(22.dp), border=BorderStroke(1.dp,Line)) {
            Column(Modifier.padding(16.dp)) {
            AmancayField(nombre, { nombre=it.take(120) }, "Nombre del producto *", "Ej.: Taza flores silvestres")
            Spacer(Modifier.height(16.dp))
            AmancayField(descripcion, { descripcion=it.take(500) }, "Descripción *", "Cuenta sus características, materiales y medidas.", singleLine=false, minLines=4)
            Text("${descripcion.length}/500", modifier=Modifier.fillMaxWidth(), textAlign=TextAlign.End, color=Muted, fontSize=12.sp)
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.height(14.dp))
            AmancayField(precioBase, { precioBase=it.filter(Char::isDigit).take(12) }, "Precio base", "Ej.: 6.990", keyboardType=KeyboardType.Number)
            Text("Si agregas variantes, el precio se define para cada una. Si no, completa aquí el precio del producto.", fontSize=13.sp, color=Muted, modifier=Modifier.padding(top=6.dp))
            errorDatos?.let { AvisoEmprendedora(it, true) }
            }
            }
            Spacer(Modifier.height(10.dp))
            AvisoEmprendedora("Guardaremos lo que ya escribiste. Puedes continuar más tarde desde donde quedaste.", false)
        }
    } else if (paso == 3) {
        StepScaffold("Paso 3 de 6", "Tipo de producto",
            "Elige el tipo para mostrar los campos que necesitas.",
            onNext={ paso=4 }, onBack={ paso=2 }, onSaveExit=::cancelarEditor,
            nextEnabled=tipo != null, pasoActual=3, showSaveExit=false, onExit=::guardarLocalYSalir, onDiscardDraft=onDiscardDraft, totalPasos=6, dibujarBajoBarraSistema=true) {
            if (tiposDisponibles.isEmpty()) {
                AvisoEmprendedora("No hay tipos de producto activos. Pide a administración que habilite uno para continuar.", true)
            }
            tiposDisponibles.forEach { item ->
                val seleccionado = tipoId == item.id
                Card(
                    modifier=Modifier.fillMaxWidth().padding(bottom=8.dp).clickable { elegirTipoProducto(item) },
                    colors=CardDefaults.cardColors(containerColor=if (seleccionado) Color(0xFFFFF0EB) else Color.White),
                    shape=RoundedCornerShape(18.dp),
                    border=BorderStroke(if (seleccionado) 2.dp else 1.dp, if (seleccionado) Wine else Line)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).background(Color(0xFFFFE5D9), RoundedCornerShape(50)), contentAlignment=Alignment.Center) {
                            val icono = when {
                                item.id == "vestuario" -> Icons.Filled.Checkroom
                                item.id == "alimento_reposteria" || item.id == "producto_agricola" -> Icons.Filled.Restaurant
                                item.id == "artesania_manualidad" -> Icons.Filled.Palette
                                item.id == "plantas" -> Icons.Filled.Star
                                item.id == "cosmetica_natural" -> Icons.Filled.Star
                                item.id == "accesorio" -> Icons.Filled.Star
                                else -> Icons.Filled.MoreHoriz
                            }
                            Icon(icono, contentDescription=null, tint=Color(0xFFEA7459), modifier=Modifier.size(24.dp))
                        }
                        Text(item.nombre, modifier=Modifier.weight(1f).padding(start=12.dp), color=Wine, fontSize=17.sp, fontWeight=FontWeight.Bold)
                        RadioButton(selected=seleccionado, onClick={
                            elegirTipoProducto(item)
                        })
                    }
                }
            }
            if (tipo == null && tiposDisponibles.isNotEmpty()) AvisoEmprendedora(falloTipoHistorico ?: "Cargando la definición de este tipo…", falloTipoHistorico != null)
            error?.let { AvisoEmprendedora(it, true) }
        }
    } else if (paso == 4) {
        StepScaffold("Paso 4 de 6", "Categoría y detalles", "Elige una categoría de ${tipo?.nombre ?: "este tipo"} y completa sus detalles.",
            onNext={ paso=5 }, onBack={ paso=3 }, onSaveExit=::cancelarEditor,
            nextEnabled=errorCategoria == null && invalidField == null && tipo != null,
            pasoActual=4, showSaveExit=false, onExit=::guardarLocalYSalir, onDiscardDraft=onDiscardDraft, totalPasos=6, dibujarBajoBarraSistema=true) {
            if (categoriasDisponibles.isEmpty()) {
                AvisoEmprendedora("No hay categorías activas para este tipo de producto. Pide a administración que revise su vínculo en Firebase.", true)
            } else {
                SelectorProducto("Categoría *", categoriasDisponibles.map { it.id to it.nombre }, categoriaId, estiloPanelInferior = true) { categoriaId = it; error = null }
            }
            errorCategoria?.let { AvisoEmprendedora(it, true) }
            Spacer(Modifier.height(14.dp))
            if (campos.isNotEmpty()) {
                Text("Detalles de ${tipo?.nombre.orEmpty()}", fontWeight=FontWeight.Bold, color=Wine, fontSize=19.sp)
                Text("Completa los campos específicos para este producto.", color=Muted, fontSize=14.sp)
                Spacer(Modifier.height(8.dp))
                campos.forEach { f ->
                    AmancayField(atributos[f.clave].orEmpty(),
                        { v -> atributos=atributos + (f.clave to v.take(500)) },
                        f.etiqueta + if (f.obligatorio) " *" else "", "Ingresa ${f.etiqueta.lowercase()}", singleLine=false, minLines=2)
                    Spacer(Modifier.height(12.dp))
                }
            } else Text("Este tipo no requiere detalles adicionales. Puedes continuar.", fontSize=15.sp, color=Muted)
            if (invalidField != null) AvisoEmprendedora("Completa el campo ${invalidField.etiqueta}.", true)
            Text("Puedes completar o editar estos datos más adelante.", fontSize=13.sp, color=Muted)
        }
    } else if (paso == 5) {
        StepScaffold("Paso 5 de 6", "Variantes", "Cada variante puede tener su propio precio, foto y stock.",
            onNext={ paso=6 }, onBack={ paso=4 }, onSaveExit=::cancelarEditor,
            nextEnabled=errorPreciosYStock == null,
            pasoActual=5, showSaveExit=false, onExit=::guardarLocalYSalir, onDiscardDraft=onDiscardDraft, totalPasos=6, dibujarBajoBarraSistema=true) {
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
                                        TextButton(onClick={ abrirGaleriaParaFoto(v.id) }, modifier=Modifier.heightIn(min=48.dp)) { Text("Galería", color=Wine) }
                                        TextButton(onClick={ abrirCamaraParaFoto(v.id) }, modifier=Modifier.heightIn(min=48.dp)) { Text("Cámara", color=Wine) }
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
            (errorPreciosYStock ?: error)?.let { AvisoEmprendedora(it, true) }
            if (original != null && manejaStock) Text("El stock existente se modifica desde el catálogo para conservar el historial.", fontSize=13.sp, color=Muted)
        }
    } else if (paso == 6) {
        StepScaffold("Paso 6 de 6", "Disponibilidad", "Cuéntanos cómo quieres vender este producto.",
            onNext={ paso=7 }, onBack={ paso=5 }, onSaveExit=::cancelarEditor,
            nextEnabled=!enviando, pasoActual=6, showSaveExit=false, onExit=::guardarLocalYSalir, onDiscardDraft=onDiscardDraft, totalPasos=6, dibujarBajoBarraSistema=true) {
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
            onNext={ guardarBorrador(publicar = original == null && tiendaPublicada) }, onBack={ paso=6 }, onSaveExit={ guardarBorrador(false) },
            nextLabel=when { original != null -> "Guardar cambios"; tiendaPublicada -> "Publicar producto"; else -> "Guardar borrador" }, nextEnabled=!enviando,
            pasoActual=6, showSaveExit=original==null, onExit=::guardarLocalYSalir, onDiscardDraft=onDiscardDraft, totalPasos=6, dibujarBajoBarraSistema=true) {
            Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp,Line)) {
                Column(Modifier.padding(16.dp)) {
                    if (identidadAlta.avisoIntentoAnterior) {
                        AvisoEmprendedora("Al cambiar los datos después de un intento de guardado, se usará un producto nuevo para no mezclar solicitudes. Si el intento anterior llegó a Firebase, quedará como borrador separado en Mis productos.", false)
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        ProductImage(
                            fallback=Fig.VASE_REVIEW,
                            size=92.dp,
                            uri=fotoLocal.photoUri,
                            bitmap=if (fotoLocal.fotoLista) fotoLocal.photoBitmap else imagenPrivada,
                            hasPhoto=true
                        )
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
                    Text("Control de stock: " + if (manejaStock) "Activado" else "No se controla", color=Muted)
                    if (manejaStock) Text("Al agotarse: " + when (alAgotarse) {
                        "ocultar" -> "ocultar el producto"
                        "pasar_a_pedido" -> "pasar a pedido"
                        else -> "mostrarlo como agotado"
                    }, color=Muted)
                    Text(
                        "Disponibilidad inicial: " + if (original?.disponible == false) "No disponible" else "Disponible",
                        color=Muted
                    )
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
                original?.imagenes?.isNotEmpty() == true -> "Al elegir otra foto, reemplazará la imagen principal y conservará las demás imágenes. La nueva foto se verá en la tienda al publicar los cambios."
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
                val stockActual = if (producto.tieneVariantes) {
                    producto.variantes.firstOrNull { it.id == varianteId }?.stock
                } else producto.stock
                error = when {
                    value == null || value == 0 -> "Ingresa una cantidad distinta de cero."
                    motivo == "reposicion" && value < 0 -> "La reposición debe sumar unidades."
                    motivo == "perdida" && value > 0 -> "La pérdida debe restar unidades."
                    producto.tieneVariantes && varianteId.isBlank() -> "Selecciona una variante."
                    producto.manejaStock && stockActual == null -> "No se pudo consultar el stock actual. Vuelve a cargar el producto."
                    producto.manejaStock && stockActual != null && stockActual + value < 0 -> "El stock no puede quedar en negativo. Hay $stockActual unidades para este producto o variante."
                    producto.modalidad == "pieza_unica" && stockActual != null && stockActual + value > 1 -> "Una pieza única no puede tener más de una unidad disponible."
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
private fun SelectorProducto(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    estiloPanelInferior: Boolean = false,
    onSelect: (String) -> Unit
) {
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
                border = BorderStroke(if (estiloPanelInferior) 1.dp else 2.dp, if (estiloPanelInferior) Line else Wine),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = androidx.compose.ui.graphics.Color.White, contentColor = if (estiloPanelInferior) Dark else Wine),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (estiloPanelInferior) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedLabel, modifier = Modifier.weight(1f), color = Dark, textAlign = TextAlign.Start, fontWeight = FontWeight.Medium)
                        Text("⌄", color = Wine, fontSize = 22.sp)
                    }
                } else Text(selectedLabel, color = Wine, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(),
                shape = if (estiloPanelInferior) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 18.dp, bottomEnd = 18.dp) else RoundedCornerShape(8.dp),
                containerColor = androidx.compose.ui.graphics.Color.White,
                tonalElevation = if (estiloPanelInferior) 2.dp else 8.dp
            ) {
                if (estiloPanelInferior) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp).width(38.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Line))
                        Text("Elige una categoría", color = Wine, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
                        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                            options.forEachIndexed { index, (id, name) ->
                                val marcado = id == selected
                                Box(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .border(if (marcado) 1.dp else 0.dp, if (marcado) Wine else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
                                        .background(if (marcado) Color(0xFFF7F1F8) else androidx.compose.ui.graphics.Color.White)
                                        .clickable { onSelect(id) }
                                        .padding(horizontal = 10.dp, vertical = 9.dp)
                                ) {
                                    Text(name, color = Dark, fontSize = 13.sp)
                                }
                                if (index < options.lastIndex) Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(Line.copy(alpha = 0.55f)))
                            }
                        }
                        Button(
                            onClick = { expanded = false },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Wine, contentColor = Color.White),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) { Text("Listo", fontWeight = FontWeight.Bold) }
                    }
                } else {
                    options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) }
                }
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
