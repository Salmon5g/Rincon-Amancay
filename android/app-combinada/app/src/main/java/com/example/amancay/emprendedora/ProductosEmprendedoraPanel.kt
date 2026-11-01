package com.example.amancay.emprendedora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.amancay.data.ApiEmprendedoraException
import com.example.amancay.data.CategoriaEmprendedora
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
    alAviso: (String?) -> Unit
) {
    val productos by repository.productos.collectAsState()
    val tipos by repository.tipos.collectAsState()
    val categorias by repository.categorias.collectAsState()
    val errorLectura by repository.error.collectAsState()
    val scope = rememberCoroutineScope()
    var editando by remember { mutableStateOf<ProductoEmprendedora?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    var ajustando by remember { mutableStateOf<ProductoEmprendedora?>(null) }

    Text("MI CATÁLOGO", fontSize = 12.sp, color = Wine, fontWeight = FontWeight.Bold)
    Text(if (nuevo || editando != null) "Ficha de producto" else "Productos", fontSize = 28.sp, color = Dark)
    Text("Tus cambios privados se guardan en Firebase. Publicar actualiza la tienda que ve el comprador.", color = Muted)
    Spacer(Modifier.height(12.dp))
    errorLectura?.let { AvisoEmprendedora(it, true) }

    if (nuevo || editando != null) {
        EditorProductoEmprendedora(
            original = editando,
            repository = repository,
            tipos = tipos,
            categorias = categorias,
            enviando = enviando,
            onCancel = { nuevo = false; editando = null },
            onSave = { solicitud ->
                scope.launch {
                    alEnviando(true); alError(null); alAviso(null)
                    try {
                        repository.guardar(tiendaId, solicitud)
                        nuevo = false; editando = null
                        alAviso("Borrador de producto guardado en Firebase.")
                    } catch (e: Exception) {
                        alError(e.message ?: "No se pudo guardar el producto.")
                    } finally { alEnviando(false) }
                }
            }
        )
        return
    }

    Button(
        onClick = { nuevo = true },
        enabled = !enviando && tipos.any { it.activo } && categorias.any { it.activo },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Wine),
        shape = RoundedCornerShape(15.dp)
    ) { Text("＋ Agregar producto", fontWeight = FontWeight.Bold) }
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
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(producto.nombre, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Dark)
                    Text(estadoProducto(producto.estadoPublicacion), color = if (producto.estadoPublicacion == "publicado") Wine else Muted, fontWeight = FontWeight.SemiBold)
                }
                Text(if (producto.tieneVariantes) "Variantes" else producto.precioBase?.let(::pesos) ?: "Sin precio", color = Dark, fontWeight = FontWeight.Bold)
            }
            if (producto.descripcion.isNotBlank()) Text(producto.descripcion, color = Muted, modifier = Modifier.padding(top = 6.dp))
            val inventario = if (producto.tieneVariantes) {
                producto.variantes.joinToString(" · ") { variante -> "${resumenOpciones(variante)}: ${variante.stock?.toString() ?: "sin stock"}" }
            } else if (producto.manejaStock) "Stock: ${producto.stock ?: 0}" else "Sin control de stock"
            Text(inventario, color = Muted, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, enabled = !enviando, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Editar", color = Wine) }
                if (producto.manejaStock) OutlinedButton(onClick = onAdjustStock, enabled = !enviando, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Ajustar stock", color = Wine) }
            }
            if (producto.estadoPublicacion == "publicado") {
                OutlinedButton(onClick = onRetire, enabled = !enviando, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Retirar del catálogo", color = Wine) }
            } else {
                val puedePublicar = tiendaPublicada && producto.imagenes.isNotEmpty()
                Button(onClick = onPublish, enabled = !enviando && puedePublicar, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = Wine), shape = RoundedCornerShape(12.dp)) { Text("Publicar producto") }
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

    TarjetaEmprendedora(if (original == null) "Crea un borrador" else "Edita el borrador", "El stock se cambia por separado para mantener su historial.")
    Spacer(Modifier.height(10.dp))
    CampoProducto("Nombre", nombre, { nombre = it }, 120)
    Spacer(Modifier.height(10.dp))
    CampoProducto("Descripción", descripcion, { descripcion = it }, 2000, multiline = true)
    Spacer(Modifier.height(10.dp))
    SelectorProducto("Tipo de producto", (if (original == null) tiposNuevos else tipos.filter { it.activo || it.id == original.tipoProductoId }).map { it.id to it.nombre }, tipoId) { nuevoId ->
        tipoId = nuevoId
        error = null
    }
    if (tipo == null) AvisoEmprendedora(falloTipoHistorico ?: "Cargando la definición del tipo de producto…", falloTipoHistorico != null)
    Spacer(Modifier.height(10.dp))
    SelectorProducto("Categoría", categoriasDisponibles.map { it.id to it.nombre }, categoriaId) { categoriaId = it }
    if (campos.isNotEmpty()) {
        Text("Detalles del producto", color = Dark, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        campos.forEach { field ->
            Spacer(Modifier.height(8.dp))
            CampoProducto(field.etiqueta + if (field.obligatorio) " *" else "", atributos[field.clave].orEmpty(), { atributos = atributos + (field.clave to it) }, 500)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Modalidad de venta", color = Dark, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("regular" to "Regular", "a_pedido" to "A pedido", "pieza_unica" to "Pieza única").forEach { (value, label) ->
            OutlinedButton(onClick = { modalidad = value; if (value == "pieza_unica") { manejaStock = true; stockInicial = "1" } }, enabled = original == null && (!conVariantes || value != "pieza_unica"), shape = RoundedCornerShape(12.dp)) {
                Text(label, color = if (modalidad == value) Wine else Muted)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = manejaStock, onCheckedChange = { if (original == null && modalidad != "pieza_unica") manejaStock = it }, enabled = original == null && modalidad != "pieza_unica")
        Text("Controlar unidades disponibles", color = Dark)
    }
    SelectorProducto("Al agotarse", listOf("mostrar_agotado" to "Mostrar agotado", "ocultar" to "Ocultar", "pasar_a_pedido" to "Pasar a pedido"), alAgotarse) { alAgotarse = it }
    if (!conVariantes) {
        Spacer(Modifier.height(10.dp))
        CampoProducto("Precio en pesos chilenos", precioBase, { precioBase = it.filter(Char::isDigit).take(12) }, 12)
        if (manejaStock && original == null) {
            Spacer(Modifier.height(8.dp))
            CampoProducto("Stock inicial", stockInicial, { stockInicial = it.filter(Char::isDigit).take(9) }, 9)
        } else if (original != null && manejaStock) {
            Text("Stock actual: ${original.stock ?: 0}. Usa “Ajustar stock” desde la lista.", color = Muted, fontSize = 12.sp)
        }
    } else {
        Text("Variantes", color = Dark, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text("Completa ${opciones.joinToString(", ")}. Las variantes guardadas no se eliminan; puedes desactivarlas.", color = Muted, fontSize = 12.sp)
        variantes.forEachIndexed { index, variante ->
            Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Line)) {
                Column(Modifier.padding(12.dp)) {
                    opciones.forEach { key ->
                        Spacer(Modifier.height(6.dp))
                        CampoProducto(key.replaceFirstChar { it.uppercase() }, variante.opciones[key].orEmpty(), { value ->
                            variantes = variantes.mapIndexed { i, old -> if (i == index && !old.existente) old.copy(opciones = old.opciones + (key to value)) else old }
                        }, 100, readOnly = variante.existente)
                    }
                    Spacer(Modifier.height(6.dp))
                    CampoProducto("Precio (CLP)", variante.precio, { value -> variantes = variantes.actualizar(index) { it.copy(precio = value.filter(Char::isDigit).take(12)) } }, 12)
                    if (manejaStock && !variante.existente) {
                        Spacer(Modifier.height(6.dp))
                        CampoProducto("Stock inicial", variante.stock, { value -> variantes = variantes.actualizar(index) { it.copy(stock = value.filter(Char::isDigit).take(9)) } }, 9)
                    } else if (variante.existente && manejaStock) {
                        Text("Stock: ${original?.variantes?.firstOrNull { it.id == variante.id }?.stock ?: 0}. Ajusta desde la lista.", color = Muted, fontSize = 12.sp)
                    }
                    if (variante.existente) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = variante.activa, onCheckedChange = { active -> variantes = variantes.actualizar(index) { it.copy(activa = active) } })
                        Text("Variante activa", color = Dark)
                    }
                    if (!variante.existente) TextButton(onClick = { variantes = variantes.filterIndexed { i, _ -> i != index } }) { Text("Quitar variante nueva", color = Wine) }
                }
            }
        }
        if (variantes.size < 20 && original == null) {
            OutlinedButton(onClick = { variantes = variantes + nuevaVariante(opciones) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp)) { Text("＋ Agregar variante", color = Wine) }
        }
    }
    AvisoEmprendedora("Las imágenes quedan como extensión preparada. La API actual solo publica referencias de Firebase Storage verificadas; la integración de Supabase requiere acordar política y cambiar el validador antes de activar carga.", false)
    error?.let { AvisoEmprendedora(it, true) }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCancel, enabled = !enviando, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Cancelar", color = Wine) }
        Button(
            onClick = {
                val invalidField = campos.firstOrNull { it.obligatorio && atributos[it.clave].isNullOrBlank() }
                val invalidVariant = variantes.any { row -> opciones.any { row.opciones[it].isNullOrBlank() } || row.precio.toLongOrNull() == null || (manejaStock && !row.existente && row.stock.toIntOrNull() == null) }
                val stockValue = stockInicial.toIntOrNull()
                error = when {
                    tipo == null -> "Espera a que se carguen los tipos de producto."
                    categoriasDisponibles.none { it.id == categoriaId } -> "Selecciona una categoría."
                    nombre.isBlank() || (original == null && tipos.none { it.id == tipoId && it.activo }) -> "Completa el nombre y selecciona un tipo habilitado."
                    invalidField != null -> "Completa el campo ${invalidField.etiqueta}."
                    conVariantes && (variantes.isEmpty() || invalidVariant) -> "Completa las opciones, precios y stock inicial de cada variante."
                    !conVariantes && precioBase.toLongOrNull() == null -> "Ingresa un precio válido."
                    !conVariantes && manejaStock && original == null && stockValue == null -> "Ingresa el stock inicial."
                    modalidad == "pieza_unica" && stockValue != 1 && original == null -> "Una pieza única comienza con stock 1."
                    else -> null
                }
                if (error == null && tipo != null) {
                    val variantesSolicitud = variantes.map { row ->
                        VarianteProductoSolicitud(row.id, row.opciones, row.precio.toLong(), if (row.existente || !manejaStock) null else row.stock.toInt(), row.activa)
                    }
                    onSave(ProductoSolicitud(
                        id = original?.id,
                        versionEsperada = original?.versionEsperada,
                        operacionId = operacionId,
                        productoIdNuevo = productoIdNuevo,
                        nombre = nombre.trim(),
                        descripcion = descripcion.trim(),
                        tipo = tipo,
                        categoriaId = categoriaId,
                        atributos = atributos.filterValues { it.isNotBlank() },
                        modalidad = modalidad,
                        manejaStock = manejaStock,
                        alAgotarse = alAgotarse,
                        precioBase = if (conVariantes) null else precioBase.toLong(),
                        stockInicial = if (!conVariantes && manejaStock && original == null) stockValue else null,
                        variantes = variantesSolicitud
                    ))
                }
            },
            enabled = !enviando && nombre.isNotBlank() && tipo != null,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Wine), shape = RoundedCornerShape(14.dp)
        ) { Text(if (enviando) "Guardando…" else "Guardar borrador") }
    }
}

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
private fun DialogoAjusteStock(
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
    OutlinedTextField(value = value, onValueChange = { if (!readOnly) onValue(it.take(max)) }, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, minLines = if (multiline) 3 else 1, singleLine = !multiline, readOnly = readOnly, shape = RoundedCornerShape(14.dp))
}

@Composable
private fun SelectorProducto(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember(label, options) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Seleccionar"
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Dark, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = options.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(selectedLabel, color = Wine) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false }) }
            }
        }
    }
}

@Composable
private fun TarjetaEmprendedora(titulo: String, detalle: String) {
    Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(14.dp)) { Text(titulo, color = Dark, fontWeight = FontWeight.Bold); Text(detalle, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
    }
}

private fun estadoProducto(estado: String) = when (estado) { "publicado" -> "Publicado"; "archivado" -> "Retirado"; else -> "Borrador" }
private fun pesos(valor: Long) = "$" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es-CL")).format(valor)
private fun resumenOpciones(variante: VarianteProductoEmprendedora) = variante.opciones.values.joinToString(" / ").ifBlank { variante.id }
private fun mensajeProducto(error: Exception): String = when ((error as? ApiEmprendedoraException)?.codigo) {
    "imagen-requerida", "imagen-no-autorizada" -> "La publicación requiere imágenes guardadas y verificadas. La carga de imágenes aún no está habilitada."
    "tienda-no-publicada" -> "Publica primero tu tienda."
    "conflicto" -> "Los datos cambiaron desde que abriste la ficha. Vuelve a cargarla e inténtalo otra vez."
    "sin-permiso" -> "La sesión ya no tiene permiso para esta tienda. Inicia sesión de nuevo o consulta a administración."
    "no-publicable" -> "El producto no se puede publicar con su disponibilidad actual. Revisa stock y modalidad."
    else -> error.message ?: "La API no pudo completar la operación."
}
