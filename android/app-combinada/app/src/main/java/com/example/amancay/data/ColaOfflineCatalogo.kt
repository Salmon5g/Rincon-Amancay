package com.example.amancay.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ColaOfflineCatalogo {
    private const val PREFS = "cola_offline_catalogo"
    private const val KEY = "operaciones"
    private const val STOCK_KEY = "ajustes_stock"
    private const val DELETE_KEY = "eliminaciones"
    private const val EDIT_KEY = "ediciones"
    private val mutex = Mutex()

    private fun esConflictoDeVersion(error: ApiEmprendedoraException?): Boolean {
        if (error?.codigo != "conflicto") return false
        val mensaje = error.message.orEmpty().lowercase()
        return listOf(
            "cambió; volver a leer",
            "cambió; vuelve a cargar",
            "cambió; actualiza el catálogo",
            "ficha cambió; volver a leer"
        ).any(mensaje::contains)
    }

    data class OperacionDisponibilidad(
        val operacionId: String,
        val tiendaId: String,
        val productoIds: List<String>,
        val versiones: List<String>,
        val disponible: Boolean,
        val ultimoError: String? = null
    )

    data class ResultadoSincronizacion(
        val aplicadas: Int,
        val pendientes: Int,
        val omitidas: Int
    )

    data class OperacionStock(
        val operacionId: String,
        val tiendaId: String,
        val productoId: String,
        val varianteId: String?,
        val version: String,
        val cantidad: Int,
        val motivo: String,
        val nota: String?,
        val ultimoError: String? = null
    )

    data class OperacionEliminar(
        val operacionId: String,
        val tiendaId: String,
        val productoId: String,
        val version: String,
        val ultimoError: String? = null
    )

    data class OperacionEdicion(
        val operacionId: String,
        val tiendaId: String,
        val solicitud: ProductoSolicitud,
        val publicar: Boolean,
        val ultimoError: String? = null
    )

    fun hayRed(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun observarReconexiones(context: Context, alConectar: () -> Unit): () -> Unit {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return {}
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                ) alConectar()
            }
        }
        return try {
            cm.registerDefaultNetworkCallback(callback)
            if (hayRed(context)) alConectar()
            val liberar: () -> Unit = { runCatching { cm.unregisterNetworkCallback(callback) } }
            liberar
        } catch (_: Exception) {
            {}
        }
    }

    fun encolarDisponibilidad(
        context: Context,
        tiendaId: String,
        productoIds: List<String>,
        versiones: List<String>,
        disponible: Boolean
    ): Int {
        require(tiendaId.isNotBlank() && productoIds.isNotEmpty() && productoIds.size == versiones.size)
        val nuevos = productoIds.zip(versiones).distinctBy { it.first }.chunked(20).map { grupo ->
            OperacionDisponibilidad(
                operacionId = UUID.randomUUID().toString(),
                tiendaId = tiendaId,
                productoIds = grupo.map { it.first },
                versiones = grupo.map { it.second },
                disponible = disponible
            )
        }
        synchronized(this) {
            val actuales = leer(context)
            guardar(context, actuales + nuevos)
        }
        return nuevos.size
    }

    fun pendientes(context: Context, tiendaId: String? = null): List<OperacionDisponibilidad> =
        synchronized(this) { leer(context).filter { tiendaId == null || it.tiendaId == tiendaId } }

    fun aplicarCambiosLocales(
        context: Context,
        tiendaId: String,
        productos: List<ProductoEmprendedora>
    ): List<ProductoEmprendedora> {
        val cambios = pendientes(context, tiendaId)
            .flatMap { op -> op.productoIds.map { it to op.disponible } }
            .toMap()
        val eliminados = pendientesEliminar(context, tiendaId).map { it.productoId }.toSet()
        val ediciones = pendientesEdicion(context, tiendaId).associateBy { it.solicitud.id }
        val stockPendiente = pendientesStock(context, tiendaId)
        return productos.filterNot { it.id in eliminados }.map { producto ->
            val solicitud = ediciones[producto.id]?.solicitud
            val editadoLocal = solicitud?.let { pendiente ->
                val variantesActuales = producto.variantes.associateBy { it.id }
                producto.copy(
                    nombre = pendiente.nombre,
                    descripcion = pendiente.descripcion,
                    tipoProductoId = pendiente.tipo.id,
                    versionTipoProducto = pendiente.tipo.version,
                    categoriaId = pendiente.categoriaId,
                    atributosEspecificos = pendiente.atributos,
                    tieneVariantes = pendiente.tieneVariantes,
                    modalidad = pendiente.modalidad,
                    manejaStock = pendiente.manejaStock,
                    alAgotarse = pendiente.alAgotarse,
                    precioBase = if (pendiente.tieneVariantes) null else pendiente.precioBase ?: producto.precioBase,
                    stock = if (pendiente.tieneVariantes) null else pendiente.stockInicial ?: producto.stock,
                    variantes = if (!pendiente.tieneVariantes) emptyList() else pendiente.variantes.map { variante ->
                        val anterior = variantesActuales[variante.id]
                        VarianteProductoEmprendedora(
                            variante.id, variante.opciones, variante.precio,
                            variante.stockInicial ?: anterior?.stock, variante.activa, anterior?.imagen
                        )
                    }
                )
            } ?: producto
            val conDisponibilidad = cambios[editadoLocal.id]?.let { editadoLocal.copy(disponible = it) } ?: editadoLocal
            val ajustes = stockPendiente.filter { it.productoId == producto.id && it.version == producto.versionEsperada }
            if (ajustes.isEmpty()) conDisponibilidad else {
                var stock = conDisponibilidad.stock
                var variantes = conDisponibilidad.variantes
                ajustes.forEach { ajuste ->
                    if (ajuste.varianteId == null) {
                        stock = ((stock ?: 0) + ajuste.cantidad).coerceAtLeast(0)
                    } else {
                        variantes = variantes.map { variante ->
                            if (variante.id == ajuste.varianteId) variante.copy(stock = ((variante.stock ?: 0) + ajuste.cantidad).coerceAtLeast(0))
                            else variante
                        }
                    }
                }
                conDisponibilidad.copy(stock = stock, variantes = variantes)
            }
        }
    }

    fun encolarAjusteStock(
        context: Context,
        tiendaId: String,
        producto: ProductoEmprendedora,
        varianteId: String?,
        cantidad: Int,
        motivo: String,
        nota: String?,
        operacionId: String
    ) {
        require(tiendaId.isNotBlank() && producto.id.isNotBlank() && producto.versionEsperada.isNotBlank())
        require(cantidad != 0 && operacionId.isNotBlank())
        synchronized(this) {
            val nuevos = leerStock(context) + OperacionStock(
                operacionId, tiendaId, producto.id, varianteId, producto.versionEsperada,
                cantidad, motivo, nota
            )
            guardarStock(context, nuevos)
        }
    }

    fun encolarEliminacion(context: Context, tiendaId: String, producto: ProductoEmprendedora): Int {
        require(tiendaId.isNotBlank() && producto.id.isNotBlank() && producto.versionEsperada.isNotBlank())
        synchronized(this) {
            val actuales = leerEliminaciones(context)
            if (actuales.any { it.tiendaId == tiendaId && it.productoId == producto.id }) return 0
            guardarEliminaciones(context, actuales + OperacionEliminar(UUID.randomUUID().toString(), tiendaId, producto.id, producto.versionEsperada))
        }
        return 1
    }

    fun encolarEdicion(context: Context, tiendaId: String, solicitud: ProductoSolicitud, publicar: Boolean) {
        require(!solicitud.id.isNullOrBlank() && !solicitud.versionEsperada.isNullOrBlank())
        val estable = solicitud.copy(operacionId = solicitud.operacionId ?: UUID.randomUUID().toString())
        synchronized(this) {
            val actuales = leerEdiciones(context).filterNot { it.tiendaId == tiendaId && it.solicitud.id == estable.id }
            guardarEdiciones(context, actuales + OperacionEdicion(estable.operacionId!!, tiendaId, estable, publicar))
        }
    }

    suspend fun sincronizar(
        context: Context,
        repository: ProductosEmprendedoraRepository,
        tiendaId: String
    ): ResultadoSincronizacion = mutex.withLock {
        if (!hayRed(context)) return@withLock ResultadoSincronizacion(0, cantidadPendiente(context, tiendaId), 0)
        var aplicadas = 0
        var omitidas = 0
        for (op in pendientesEdicion(context, tiendaId)) {
            if (!hayRed(context)) break
            try {
                aplicarEdicion(repository, op)
                quitarEdicion(context, op.operacionId)
                aplicadas++
            } catch (error: Exception) {
                val apiError = error as? ApiEmprendedoraException
                if (!esConflictoDeVersion(apiError)) {
                    guardarErrorEdicion(context, op.operacionId, apiError?.message ?: "No se pudo sincronizar la edición.")
                    continue
                }
                try {
                    val productoId = op.solicitud.id.orEmpty()
                    val version = repository.leerVersionesDisponibilidad(op.tiendaId, listOf(productoId))[productoId]
                    if (version == null) {
                        quitarEdicion(context, op.operacionId)
                        omitidas++
                        continue
                    }
                    val solicitud = op.solicitud.copy(versionEsperada = version, operacionId = UUID.randomUUID().toString())
                    val rebasada = op.copy(operacionId = solicitud.operacionId!!, solicitud = solicitud, ultimoError = null)
                    reemplazarEdicion(context, op.operacionId, rebasada)
                    try {
                        aplicarEdicion(repository, rebasada)
                        quitarEdicion(context, rebasada.operacionId)
                        aplicadas++
                    } catch (reintentoError: Exception) {
                        guardarErrorEdicion(context, rebasada.operacionId, reintentoError.message ?: "No se pudo aplicar la edición actualizada.")
                    }
                } catch (rebaseError: Exception) {
                    guardarErrorEdicion(context, op.operacionId, rebaseError.message ?: "No se pudo resolver la edición concurrente.")
                }
            }
        }
        val ops = pendientes(context, tiendaId)
        for (op in ops) {
            if (!hayRed(context)) break
            try {
                aplicar(repository, tiendaId, op)
                quitar(context, op.operacionId)
                aplicadas += op.productoIds.size
            } catch (error: Exception) {
                val apiError = error as? ApiEmprendedoraException
                if (!esConflictoDeVersion(apiError)) {
                    guardarError(context, op.operacionId, apiError?.message ?: "No se pudo sincronizar el cambio.")
                    continue
                }

                try {
                    val versionesActuales = repository.leerVersionesDisponibilidad(tiendaId, op.productoIds)
                    val vigentes = op.productoIds.mapIndexedNotNull { index, productoId ->
                        versionesActuales[productoId]?.let { productoId to it }
                    }
                    omitidas += op.productoIds.size - vigentes.size
                    if (vigentes.isEmpty()) {
                        quitar(context, op.operacionId)
                        continue
                    }
                    val rebasada = op.copy(
                        operacionId = UUID.randomUUID().toString(),
                        productoIds = vigentes.map { it.first },
                        versiones = vigentes.map { it.second },
                        ultimoError = null
                    )
                    reemplazar(context, op.operacionId, rebasada)
                    try {
                        aplicar(repository, tiendaId, rebasada)
                        quitar(context, rebasada.operacionId)
                        aplicadas += vigentes.size
                    } catch (reintentoError: Exception) {
                        guardarError(context, rebasada.operacionId, reintentoError.message ?: "No se pudo aplicar el cambio actualizado.")
                    }
                } catch (rebaseError: Exception) {
                    guardarError(context, op.operacionId, rebaseError.message ?: "No se pudo resolver un cambio concurrente.")
                }
            }
        }
        for (op in pendientesStock(context, tiendaId)) {
            if (!hayRed(context)) break
            try {
                aplicarStock(repository, tiendaId, op)
                quitarStock(context, op.operacionId)
                aplicadas++
            } catch (error: Exception) {
                val apiError = error as? ApiEmprendedoraException
                if (!esConflictoDeVersion(apiError)) {
                    guardarErrorStock(context, op.operacionId, apiError?.message ?: "No se pudo sincronizar el ajuste de stock.")
                    continue
                }
                try {
                    val version = repository.leerVersionesDisponibilidad(tiendaId, listOf(op.productoId))[op.productoId]
                    if (version == null) {
                        quitarStock(context, op.operacionId)
                        omitidas++
                        continue
                    }
                    val rebasada = op.copy(operacionId = UUID.randomUUID().toString(), version = version, ultimoError = null)
                    reemplazarStock(context, op.operacionId, rebasada)
                    try {
                        aplicarStock(repository, tiendaId, rebasada)
                        quitarStock(context, rebasada.operacionId)
                        aplicadas++
                    } catch (reintentoError: Exception) {
                        guardarErrorStock(context, rebasada.operacionId, reintentoError.message ?: "No se pudo aplicar el ajuste actualizado.")
                    }
                } catch (rebaseError: Exception) {
                    guardarErrorStock(context, op.operacionId, rebaseError.message ?: "No se pudo resolver el ajuste de stock concurrente.")
                }
            }
        }
        for (op in pendientesEliminar(context, tiendaId)) {
            if (!hayRed(context)) break
            try {
                aplicarEliminacion(repository, tiendaId, op)
                quitarEliminacion(context, op.operacionId)
                aplicadas++
            } catch (error: Exception) {
                val apiError = error as? ApiEmprendedoraException
                if (apiError?.codigo == "no-encontrado") {
                    quitarEliminacion(context, op.operacionId)
                    aplicadas++
                    continue
                }
                if (!esConflictoDeVersion(apiError)) {
                    guardarErrorEliminacion(context, op.operacionId, apiError?.message ?: "No se pudo sincronizar la eliminación.")
                    continue
                }
                try {
                    val version = repository.leerVersionesDisponibilidad(tiendaId, listOf(op.productoId))[op.productoId]
                    if (version == null) {
                        quitarEliminacion(context, op.operacionId)
                        aplicadas++
                        continue
                    }
                    val rebasada = op.copy(operacionId = UUID.randomUUID().toString(), version = version, ultimoError = null)
                    reemplazarEliminacion(context, op.operacionId, rebasada)
                    try {
                        aplicarEliminacion(repository, tiendaId, rebasada)
                        quitarEliminacion(context, rebasada.operacionId)
                        aplicadas++
                    } catch (reintentoError: Exception) {
                        guardarErrorEliminacion(context, rebasada.operacionId, reintentoError.message ?: "No se pudo aplicar la eliminación actualizada.")
                    }
                } catch (rebaseError: Exception) {
                    guardarErrorEliminacion(context, op.operacionId, rebaseError.message ?: "No se pudo resolver la eliminación concurrente.")
                }
            }
        }
        ResultadoSincronizacion(aplicadas, cantidadPendiente(context, tiendaId), omitidas)
    }

    fun cantidadPendiente(context: Context, tiendaId: String): Int =
        pendientes(context, tiendaId).sumOf { it.productoIds.size } +
            pendientesStock(context, tiendaId).size + pendientesEliminar(context, tiendaId).size + pendientesEdicion(context, tiendaId).size

    fun primerErrorPendiente(context: Context, tiendaId: String): String? =
        pendientesEdicion(context, tiendaId).firstNotNullOfOrNull { it.ultimoError }
            ?: pendientes(context, tiendaId).firstNotNullOfOrNull { it.ultimoError }
            ?: pendientesStock(context, tiendaId).firstNotNullOfOrNull { it.ultimoError }
            ?: pendientesEliminar(context, tiendaId).firstNotNullOfOrNull { it.ultimoError }

    private suspend fun aplicarEdicion(repository: ProductosEmprendedoraRepository, op: OperacionEdicion) {
        val productoId = repository.guardar(op.tiendaId, op.solicitud)
        if (op.publicar) repository.publicar(op.tiendaId, repository.leerCompleto(op.tiendaId, productoId))
    }

    private suspend fun aplicar(
        repository: ProductosEmprendedoraRepository,
        tiendaId: String,
        op: OperacionDisponibilidad
    ) {
        val productos = op.productoIds.mapIndexed { index, id ->
            ProductoEmprendedora(
                id = id,
                nombre = id,
                descripcion = "",
                tipoProductoId = "",
                versionTipoProducto = 1,
                categoriaId = "",
                atributosEspecificos = emptyMap(),
                tieneVariantes = false,
                modalidad = "regular",
                manejaStock = false,
                alAgotarse = "mostrar_agotado",
                precioBase = null,
                stock = null,
                variantes = emptyList(),
                imagenes = emptyList(),
                estadoPublicacion = "borrador",
                versionEsperada = op.versiones[index],
                disponible = op.disponible
            )
        }
        repository.cambiarDisponibilidad(tiendaId, productos, op.disponible, listOf(op.operacionId))
    }

    private suspend fun aplicarStock(
        repository: ProductosEmprendedoraRepository,
        tiendaId: String,
        op: OperacionStock
    ) {
        val producto = ProductoEmprendedora(
            id = op.productoId,
            nombre = op.productoId,
            descripcion = "",
            tipoProductoId = "",
            versionTipoProducto = 1,
            categoriaId = "",
            atributosEspecificos = emptyMap(),
            tieneVariantes = op.varianteId != null,
            modalidad = "regular",
            manejaStock = true,
            alAgotarse = "mostrar_agotado",
            precioBase = null,
            stock = null,
            variantes = emptyList(),
            imagenes = emptyList(),
            estadoPublicacion = "borrador",
            versionEsperada = op.version,
            disponible = true
        )
        repository.ajustarStock(tiendaId, producto, op.varianteId, op.cantidad, op.motivo, op.nota, op.operacionId)
    }

    private suspend fun aplicarEliminacion(
        repository: ProductosEmprendedoraRepository,
        tiendaId: String,
        op: OperacionEliminar
    ) {
        val producto = ProductoEmprendedora(
            id = op.productoId,
            nombre = op.productoId,
            descripcion = "",
            tipoProductoId = "",
            versionTipoProducto = 1,
            categoriaId = "",
            atributosEspecificos = emptyMap(),
            tieneVariantes = false,
            modalidad = "regular",
            manejaStock = false,
            alAgotarse = "mostrar_agotado",
            precioBase = null,
            stock = null,
            variantes = emptyList(),
            imagenes = emptyList(),
            estadoPublicacion = "borrador",
            versionEsperada = op.version,
            disponible = false
        )
        repository.eliminar(tiendaId, producto, op.operacionId)
    }

    private fun leer(context: Context): List<OperacionDisponibilidad> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY, "[]"))
        val resultado = mutableListOf<OperacionDisponibilidad>()
        for (i in 0 until array.length()) {
            val op = array.optJSONObject(i) ?: continue
            if (op.optString("tipo") != "disponibilidad") continue
            val ids = mutableListOf<String>()
            val idArray = op.optJSONArray("productoIds")
            for (j in 0 until (idArray?.length() ?: 0)) idArray?.optString(j)?.let { if (it.isNotBlank()) ids.add(it) }
            val versiones = mutableListOf<String>()
            val versionArray = op.optJSONArray("versiones")
            for (j in 0 until (versionArray?.length() ?: 0)) versionArray?.optString(j)?.let { versiones.add(it) }
            val tiendaId = op.optString("tiendaId")
            if (tiendaId.isBlank() || ids.isEmpty() || ids.size != versiones.size) continue
            val disponible = op.optBoolean("disponible", true)
            val operacionId = op.optString("operacionId").takeIf(String::isNotBlank)
                ?: UUID.nameUUIDFromBytes("$tiendaId/${ids.joinToString(",")}/${versiones.joinToString(",")}/$disponible".toByteArray())
            resultado.add(
                OperacionDisponibilidad(
                    operacionId = operacionId,
                    tiendaId = tiendaId,
                    productoIds = ids,
                    versiones = versiones,
                    disponible = disponible,
                    ultimoError = op.optString("ultimoError").takeIf(String::isNotBlank)
                )
            )
        }
        return resultado
    }

    private fun guardar(context: Context, ops: List<OperacionDisponibilidad>) {
        val array = JSONArray()
        ops.forEach { op ->
            array.put(JSONObject().apply {
                put("tipo", "disponibilidad")
                put("operacionId", op.operacionId)
                put("tiendaId", op.tiendaId)
                put("productoIds", JSONArray(op.productoIds))
                put("versiones", JSONArray(op.versiones))
                put("disponible", op.disponible)
                op.ultimoError?.let { put("ultimoError", it) }
            })
        }
        val guardado = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, array.toString()).commit()
        check(guardado) { "No se pudo guardar el cambio pendiente en este dispositivo." }
    }

    private fun quitar(context: Context, operacionId: String) = synchronized(this) {
        guardar(context, leer(context).filterNot { it.operacionId == operacionId })
    }

    private fun reemplazar(context: Context, operacionId: String, nueva: OperacionDisponibilidad) = synchronized(this) {
        guardar(context, leer(context).map { if (it.operacionId == operacionId) nueva else it })
    }

    private fun guardarError(context: Context, operacionId: String, mensaje: String) = synchronized(this) {
        guardar(context, leer(context).map {
            if (it.operacionId == operacionId) it.copy(ultimoError = mensaje.take(180)) else it
        })
    }

    fun pendientesStock(context: Context, tiendaId: String? = null): List<OperacionStock> =
        synchronized(this) { leerStock(context).filter { tiendaId == null || it.tiendaId == tiendaId } }

    private fun leerStock(context: Context): List<OperacionStock> {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(STOCK_KEY, "[]"))
        return (0 until array.length()).mapNotNull { index ->
            val op = array.optJSONObject(index) ?: return@mapNotNull null
            val tiendaId = op.optString("tiendaId")
            val productoId = op.optString("productoId")
            val operacionId = op.optString("operacionId")
            val version = op.optString("version")
            val cantidad = op.optInt("cantidad")
            val motivo = op.optString("motivo")
            if (tiendaId.isBlank() || productoId.isBlank() || operacionId.isBlank() || version.isBlank() || cantidad == 0 || motivo.isBlank()) return@mapNotNull null
            OperacionStock(
                operacionId, tiendaId, productoId,
                op.optString("varianteId").takeIf(String::isNotBlank), version, cantidad, motivo,
                op.optString("nota").takeIf(String::isNotBlank), op.optString("ultimoError").takeIf(String::isNotBlank)
            )
        }
    }

    private fun guardarStock(context: Context, ops: List<OperacionStock>) {
        val array = JSONArray()
        ops.forEach { op -> array.put(JSONObject().apply {
            put("operacionId", op.operacionId)
            put("tiendaId", op.tiendaId)
            put("productoId", op.productoId)
            op.varianteId?.let { put("varianteId", it) }
            put("version", op.version)
            put("cantidad", op.cantidad)
            put("motivo", op.motivo)
            op.nota?.let { put("nota", it) }
            op.ultimoError?.let { put("ultimoError", it) }
        }) }
        val guardado = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(STOCK_KEY, array.toString()).commit()
        check(guardado) { "No se pudo guardar el ajuste de stock pendiente en este dispositivo." }
    }

    private fun quitarStock(context: Context, operacionId: String) = synchronized(this) {
        guardarStock(context, leerStock(context).filterNot { it.operacionId == operacionId })
    }

    private fun reemplazarStock(context: Context, operacionId: String, nueva: OperacionStock) = synchronized(this) {
        guardarStock(context, leerStock(context).map { if (it.operacionId == operacionId) nueva else it })
    }

    private fun guardarErrorStock(context: Context, operacionId: String, mensaje: String) = synchronized(this) {
        guardarStock(context, leerStock(context).map {
            if (it.operacionId == operacionId) it.copy(ultimoError = mensaje.take(180)) else it
        })
    }

    fun pendientesEliminar(context: Context, tiendaId: String? = null): List<OperacionEliminar> =
        synchronized(this) { leerEliminaciones(context).filter { tiendaId == null || it.tiendaId == tiendaId } }

    private fun leerEliminaciones(context: Context): List<OperacionEliminar> {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DELETE_KEY, "[]"))
        return (0 until array.length()).mapNotNull { index ->
            val op = array.optJSONObject(index) ?: return@mapNotNull null
            val tiendaId = op.optString("tiendaId")
            val productoId = op.optString("productoId")
            val operacionId = op.optString("operacionId")
            val version = op.optString("version")
            if (tiendaId.isBlank() || productoId.isBlank() || operacionId.isBlank() || version.isBlank()) return@mapNotNull null
            OperacionEliminar(operacionId, tiendaId, productoId, version, op.optString("ultimoError").takeIf(String::isNotBlank))
        }
    }

    private fun guardarEliminaciones(context: Context, ops: List<OperacionEliminar>) {
        val array = JSONArray()
        ops.forEach { op -> array.put(JSONObject().apply {
            put("operacionId", op.operacionId)
            put("tiendaId", op.tiendaId)
            put("productoId", op.productoId)
            put("version", op.version)
            op.ultimoError?.let { put("ultimoError", it) }
        }) }
        val guardado = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(DELETE_KEY, array.toString()).commit()
        check(guardado) { "No se pudo guardar la eliminación pendiente en este dispositivo." }
    }

    private fun quitarEliminacion(context: Context, operacionId: String) = synchronized(this) {
        guardarEliminaciones(context, leerEliminaciones(context).filterNot { it.operacionId == operacionId })
    }

    private fun reemplazarEliminacion(context: Context, operacionId: String, nueva: OperacionEliminar) = synchronized(this) {
        guardarEliminaciones(context, leerEliminaciones(context).map { if (it.operacionId == operacionId) nueva else it })
    }

    private fun guardarErrorEliminacion(context: Context, operacionId: String, mensaje: String) = synchronized(this) {
        guardarEliminaciones(context, leerEliminaciones(context).map {
            if (it.operacionId == operacionId) it.copy(ultimoError = mensaje.take(180)) else it
        })
    }

    fun pendientesEdicion(context: Context, tiendaId: String? = null): List<OperacionEdicion> =
        synchronized(this) { leerEdiciones(context).filter { tiendaId == null || it.tiendaId == tiendaId } }

    private fun leerEdiciones(context: Context): List<OperacionEdicion> {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(EDIT_KEY, "[]"))
        return (0 until array.length()).mapNotNull { index ->
            val op = array.optJSONObject(index) ?: return@mapNotNull null
            val tiendaId = op.optString("tiendaId")
            val operacionId = op.optString("operacionId")
            val solicitud = op.optJSONObject("solicitud")?.let(::solicitudDesdeJson)
            if (tiendaId.isBlank() || operacionId.isBlank() || solicitud?.id.isNullOrBlank() || solicitud?.versionEsperada.isNullOrBlank()) return@mapNotNull null
            OperacionEdicion(
                operacionId, tiendaId, solicitud!!, op.optBoolean("publicar"),
                op.optString("ultimoError").takeIf(String::isNotBlank)
            )
        }
    }

    private fun guardarEdiciones(context: Context, ops: List<OperacionEdicion>) {
        val array = JSONArray()
        ops.forEach { op -> array.put(JSONObject().apply {
            put("operacionId", op.operacionId)
            put("tiendaId", op.tiendaId)
            put("solicitud", solicitudAJson(op.solicitud))
            put("publicar", op.publicar)
            op.ultimoError?.let { put("ultimoError", it) }
        }) }
        val guardado = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(EDIT_KEY, array.toString()).commit()
        check(guardado) { "No se pudo guardar la edición pendiente en este dispositivo." }
    }

    private fun quitarEdicion(context: Context, operacionId: String) = synchronized(this) {
        guardarEdiciones(context, leerEdiciones(context).filterNot { it.operacionId == operacionId })
    }

    private fun reemplazarEdicion(context: Context, operacionId: String, nueva: OperacionEdicion) = synchronized(this) {
        guardarEdiciones(context, leerEdiciones(context).map { if (it.operacionId == operacionId) nueva else it })
    }

    private fun guardarErrorEdicion(context: Context, operacionId: String, mensaje: String) = synchronized(this) {
        guardarEdiciones(context, leerEdiciones(context).map {
            if (it.operacionId == operacionId) it.copy(ultimoError = mensaje.take(180)) else it
        })
    }

    private fun solicitudAJson(solicitud: ProductoSolicitud): JSONObject = JSONObject().apply {
        solicitud.id?.let { put("id", it) }
        solicitud.versionEsperada?.let { put("versionEsperada", it) }
        solicitud.operacionId?.let { put("solicitudOperacionId", it) }
        solicitud.productoIdNuevo?.let { put("productoIdNuevo", it) }
        put("nombre", solicitud.nombre)
        put("descripcion", solicitud.descripcion)
        put("tipo", JSONObject().apply {
            put("id", solicitud.tipo.id)
            put("nombre", solicitud.tipo.nombre)
            put("version", solicitud.tipo.version)
            put("activo", solicitud.tipo.activo)
            put("campos", JSONArray().apply {
                solicitud.tipo.campos.forEach { campo -> put(JSONObject().put("clave", campo.clave).put("etiqueta", campo.etiqueta).put("obligatorio", campo.obligatorio)) }
            })
            put("opcionesVariante", JSONArray(solicitud.tipo.opcionesVariante))
        })
        put("tieneVariantes", solicitud.tieneVariantes)
        put("categoriaId", solicitud.categoriaId)
        put("atributos", JSONObject().apply { solicitud.atributos.forEach { (clave, valor) -> put(clave, valor) } })
        put("modalidad", solicitud.modalidad)
        put("manejaStock", solicitud.manejaStock)
        put("alAgotarse", solicitud.alAgotarse)
        solicitud.precioBase?.let { put("precioBase", it) }
        solicitud.stockInicial?.let { put("stockInicial", it) }
        put("variantes", JSONArray().apply {
            solicitud.variantes.forEach { variante ->
                put(JSONObject().apply {
                    put("id", variante.id)
                    put("opciones", JSONObject().apply { variante.opciones.forEach { (clave, valor) -> put(clave, valor) } })
                    put("precio", variante.precio)
                    put("activa", variante.activa)
                    variante.stockInicial?.let { put("stockInicial", it) }
                })
            }
        })
    }

    private fun solicitudDesdeJson(json: JSONObject): ProductoSolicitud? {
        val tipoJson = json.optJSONObject("tipo") ?: return null
        val tipoId = tipoJson.optString("id")
        val tipoVersion = tipoJson.optInt("version")
        if (tipoId.isBlank() || tipoVersion <= 0) return null
        val camposJson = tipoJson.optJSONArray("campos") ?: JSONArray()
        val campos = (0 until camposJson.length()).mapNotNull { index ->
            val campo = camposJson.optJSONObject(index) ?: return@mapNotNull null
            val clave = campo.optString("clave")
            if (clave.isBlank()) null else CampoTipoProducto(clave, campo.optString("etiqueta", clave), campo.optBoolean("obligatorio"))
        }
        val opcionesJson = tipoJson.optJSONArray("opcionesVariante") ?: JSONArray()
        val opcionesTipo = (0 until opcionesJson.length()).mapNotNull { opcionesJson.optString(it).takeIf(String::isNotBlank) }
        val tipo = TipoProductoEmprendedora(tipoId, tipoJson.optString("nombre", tipoId), tipoVersion, campos, opcionesTipo, tipoJson.optBoolean("activo", true))
        val atributosJson = json.optJSONObject("atributos") ?: JSONObject()
        val atributos = atributosJson.keys().asSequence().associateWith { atributosJson.optString(it) }
        val variantesJson = json.optJSONArray("variantes") ?: JSONArray()
        val variantes = (0 until variantesJson.length()).mapNotNull { index ->
            val variante = variantesJson.optJSONObject(index) ?: return@mapNotNull null
            val id = variante.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val opcionesJson = variante.optJSONObject("opciones") ?: JSONObject()
            val opciones = opcionesJson.keys().asSequence().associateWith { opcionesJson.optString(it) }
            VarianteProductoSolicitud(
                id, opciones, variante.optLong("precio"),
                if (variante.has("stockInicial")) variante.optInt("stockInicial") else null,
                variante.optBoolean("activa", true)
            )
        }
        return ProductoSolicitud(
            id = json.optString("id").takeIf(String::isNotBlank),
            versionEsperada = json.optString("versionEsperada").takeIf(String::isNotBlank),
            operacionId = json.optString("solicitudOperacionId").takeIf(String::isNotBlank),
            productoIdNuevo = json.optString("productoIdNuevo").takeIf(String::isNotBlank),
            nombre = json.optString("nombre"),
            descripcion = json.optString("descripcion"),
            tipo = tipo,
            tieneVariantes = json.optBoolean("tieneVariantes"),
            categoriaId = json.optString("categoriaId"),
            atributos = atributos,
            modalidad = json.optString("modalidad", "regular"),
            manejaStock = json.optBoolean("manejaStock", true),
            alAgotarse = json.optString("alAgotarse", "mostrar_agotado"),
            precioBase = if (json.has("precioBase")) json.optLong("precioBase") else null,
            stockInicial = if (json.has("stockInicial")) json.optInt("stockInicial") else null,
            variantes = variantes
        )
    }
}
