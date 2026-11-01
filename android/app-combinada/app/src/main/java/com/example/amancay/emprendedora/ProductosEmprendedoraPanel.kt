package com.example.amancay.emprendedora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.data.ApiEmprendedoraException
import com.example.amancay.data.CategoriaEmprendedora
import com.example.amancay.data.DraftState
import com.example.amancay.data.ProductoEmprendedora
import com.example.amancay.data.ProductoSolicitud
import com.example.amancay.data.ProductosEmprendedoraRepository
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
import java.util.UUID
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    var editando by remember { mutableStateOf<ProductoEmprendedora?>(null) }
    var nuevo by remember(tiendaId, abrirNuevoAlIniciar) { mutableStateOf(abrirNuevoAlIniciar) }
    var ajustando by remember { mutableStateOf<ProductoEmprendedora?>(null) }
    var cargandoProductoInicial by remember(productoInicialId, tiendaId) { mutableStateOf(productoInicialId != null) }

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
            onSave = { solicitud ->
                scope.launch {
                    alEnviando(true); alError(null); alAviso(null)
                    try {
                        repository.guardar(tiendaId, solicitud)
                        nuevo = false; editando = null
                        alAviso("Borrador de producto guardado en Firebase.")
                        alFinalizarEdicion()
                    } catch (e: Exception) {
                        alError(e.message ?: "No se pudo guardar el producto.")
                    } finally { alEnviando(false) }
                }
            }
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
    onEdit: () -> Unit,
    onPublish: () -> Unit,
    onRetire: () -> Unit,
    onAdjustStock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(producto.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dark)
                    Text(estadoProducto(producto.estadoPublicacion), color = if (producto.estadoPublicacion == "publicado") Wine else Muted, fontWeight = FontWeight.SemiBold)
                }
                Text(if (producto.tieneVariantes) "Variantes" else producto.precioBase?.let(::pesos) ?: "Sin precio", color = Dark, fontWeight = FontWeight.Bold)
            }
            if (producto.descripcion.isNotBlank()) Text(producto.descripcion, color = Muted, modifier = Modifier.padding(top = 6.dp))
            val inventario = if (producto.tieneVariantes) {
                producto.variantes.joinToString(" · ") { variante -> "${resumenOpciones(variante)}: ${variante.stock?.toString() ?: "sin stock"}" }
            } else if (producto.manejaStock) "Stock: ${producto.stock ?: 0}" else "Sin control de stock"
            Text(inventario, color = Muted, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Editar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Wine, modifier = Modifier.clickable(enabled = !enviando, onClick = onEdit).padding(4.dp))
                if (producto.manejaStock) Text("Stock", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Wine, modifier = Modifier.clickable(enabled = !enviando, onClick = onAdjustStock).padding(4.dp))
                Spacer(Modifier.weight(1f))
                if (producto.estadoPublicacion == "publicado") {
                    Text("Retirar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Wine, modifier = Modifier.clickable(enabled = !enviando, onClick = onRetire).padding(4.dp))
                } else {
                    val puedePublicar = tiendaPublicada && producto.imagenes.isNotEmpty()
                    Text("Publicar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (puedePublicar) Wine else Muted, modifier = Modifier.clickable(enabled = !enviando && puedePublicar, onClick = onPublish).padding(4.dp))
                }
            }
            if (producto.estadoPublicacion == "publicado") {
                Text("El producto está visible en el catálogo comprador.", color = Muted, fontSize = 12.sp)
            } else {
                val puedePublicar = tiendaPublicada && producto.imagenes.isNotEmpty()
                if (!tiendaPublicada) Text("La tienda debe estar publicada antes.", color = Muted, fontSize = 12.sp)
                if (producto.imagenes.isEmpty()) Text("La publicación exige una imagen aprobada. La carga con Storage aún está pendiente.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EditorProductoEmprendedora(
    original: ProductoEmprendedora?,
    repository: ProductosEmprendedoraRepository,
    tipos: List<TipoProductoEmprendedora>,
    categorias: List<CategoriaEmprendedora>,
    enviando: Boolean,
    onCancel: () -> Unit,
    onSave: (ProductoSolicitud) -> Unit
) {
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
    val selectorGaleria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            fotoLocal.photoUri = uri
            fotoLocal.photoBitmap = null
            fotoLocal.fotoLista = true
        }
    }
    val selectorCamara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            fotoLocal.photoBitmap = bitmap
            fotoLocal.photoUri = null
            fotoLocal.fotoLista = true
        }
    }
    var atributos by remember(original?.id, original?.versionEsperada) { mutableStateOf(original?.atributosEspecificos.orEmpty()) }
    var variantes by remember(original?.id, original?.versionEsperada) {
        mutableStateOf(original?.variantes?.map { v ->
            VarianteBorrador(v.id, v.opciones, v.precio.toString(), v.stock?.toString().orEmpty(), v.activa, true)
        } ?: emptyList())
    }
    var tipoHistorico by remember(original?.id, original?.versionEsperada) { mutableStateOf<TipoProductoEmprendedora?>(null) }
    var falloTipoHistorico by remember(original?.id) { mutableStateOf<String?>(null) }
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
            onSave(ProductoSolicitud(
                id=original?.id, versionEsperada=original?.versionEsperada, operacionId=operacionId,
                productoIdNuevo=productoIdNuevo, nombre=nombre.trim(), descripcion=descripcion.trim(),
                tipo=tipo, categoriaId=categoriaId, atributos=atributos.filterValues { it.isNotBlank() },
                modalidad=modalidad, manejaStock=manejaStock, alAgotarse=alAgotarse,
                precioBase=if (conVariantes) null else precioBase.toLong(),
                stockInicial=if (!conVariantes && manejaStock && original == null) stockValue else null,
                variantes=solicitudes
            ))
        }
    }
    if (paso == 1) {
        PhotoStep(draft=fotoLocal, bump={}, onTakePhoto={ selectorCamara.launch(null) },
            onPickPhoto={ selectorGaleria.launch("image/*") }, onNext={ paso=2 },
            onBack=onCancel, onSaveExit=onCancel, showSaveExit=false,
            imagenExistente=original?.imagenes?.isNotEmpty() == true)
    } else if (paso == 2) {
        StepScaffold("Paso 2 de 5", "Cuéntanos lo básico", "Usaremos estos datos para mostrar tu producto.",
            onNext={ paso=3 }, onBack={ paso=1 }, onSaveExit=onCancel,
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
            onNext={ paso=4 }, onBack={ paso=2 }, onSaveExit=onCancel,
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
            onNext={ paso=5 }, onBack={ paso=3 }, onSaveExit=onCancel,
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
                    Checkbox(checked=manejaStock, onCheckedChange={ if (original == null && modalidad != "pieza_unica") manejaStock=it }, enabled=original == null && modalidad != "pieza_unica")
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
                                Checkbox(checked=v.activa, onCheckedChange={ active -> variantes=variantes.actualizar(index) { it.copy(activa=active) } })
                                Text("Variante activa", color=Dark)
                            } else TextButton(onClick={ variantes=variantes.filterIndexed { i,_ -> i != index } }) { Text("Quitar variante nueva", color=Wine) }
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
            onNext={ guardarBorrador() }, onBack={ paso=4 }, onSaveExit=onCancel,
            nextLabel=if (original == null) "Guardar borrador" else "Guardar cambios", nextEnabled=!enviando,
            pasoActual=5, showSaveExit=false) {
            Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(20.dp), border=BorderStroke(1.dp,Line)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        ProductImage(fallback=Fig.VASE_REVIEW, size=92.dp, uri=fotoLocal.photoUri, bitmap=fotoLocal.photoBitmap, hasPhoto=fotoLocal.fotoLista)
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
                fotoLocal.fotoLista -> "La foto elegida está en este teléfono y aún no se ha subido. Se guardará el producto como borrador; publicarlo requiere una imagen verificada en Storage."
                original?.imagenes?.isNotEmpty() == true -> "La imagen guardada se conservará. Al guardar se actualiza el borrador; publicar requiere que la tienda esté publicada."
                else -> "Se guardará como borrador. La publicación requiere una imagen verificada en Storage y que la tienda esté publicada."
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
        dismissButton = { TextButton(onClick = onDismiss, enabled = !enviando) { Text("Cancelar", color = Wine) } },
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
                modifier = Modifier.fillMaxWidth(),
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
    "imagen-requerida", "imagen-no-autorizada" -> "La publicación requiere imágenes guardadas y verificadas. La carga de imágenes aún no está habilitada."
    "tienda-no-publicada" -> "Publica primero tu tienda."
    "conflicto" -> "Los datos cambiaron desde que abriste la ficha. Vuelve a cargarla e inténtalo otra vez."
    "sin-permiso" -> "La sesión ya no tiene permiso para esta tienda. Inicia sesión de nuevo o consulta a administración."
    "no-publicable" -> "El producto no se puede publicar con su disponibilidad actual. Revisa stock y modalidad."
    else -> error.message ?: "La API no pudo completar la operación."
}
