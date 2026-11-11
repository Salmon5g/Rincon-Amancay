package com.example.amancay.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONArray
import org.json.JSONObject

object ColaOfflineCatalogo {
    private const val PREFS = "cola_offline_catalogo"
    private const val KEY = "operaciones"

    data class OperacionDisponibilidad(
        val tiendaId: String,
        val productoIds: List<String>,
        val versiones: List<String>,
        val disponible: Boolean
    )

    fun hayRed(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun encolarDisponibilidad(context: Context, tiendaId: String, productoIds: List<String>, versiones: List<String>, disponible: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY, "[]"))
        val op = JSONObject()
        op.put("tipo", "disponibilidad")
        op.put("tiendaId", tiendaId)
        op.put("productoIds", JSONArray(productoIds))
        op.put("versiones", JSONArray(versiones))
        op.put("disponible", disponible)
        array.put(op)
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun pendientes(context: Context): List<OperacionDisponibilidad> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY, "[]"))
        val resultado = mutableListOf<OperacionDisponibilidad>()
        for (i in 0 until array.length()) {
            val op = array.optJSONObject(i) ?: continue
            if (op.optString("tipo") != "disponibilidad") continue
            val ids = mutableListOf<String>()
            val vs = op.optJSONArray("productoIds")
            for (j in 0 until (vs?.length() ?: 0)) vs?.optString(j)?.let { if (it.isNotBlank()) ids.add(it) }
            val vers = mutableListOf<String>()
            val vj = op.optJSONArray("versiones")
            for (j in 0 until (vj?.length() ?: 0)) vj?.optString(j)?.let { vers.add(it) }
            resultado.add(OperacionDisponibilidad(op.optString("tiendaId"), ids, vers, op.optBoolean("disponible", true)))
        }
        return resultado
    }

    fun limpiar(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    suspend fun sincronizar(context: Context, repository: ProductosEmprendedoraRepository, tiendaId: String) {
        if (!hayRed(context)) return
        val ops = pendientes(context)
        if (ops.isEmpty()) return
        var exito = true
        ops.forEach { op ->
            try {
                val productos = op.productoIds.mapIndexed { index, id ->
                    // rebuild minimal ProductoEmprendedora with version
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
                        versionEsperada = op.versiones.getOrElse(index) { "" },
                        disponible = op.disponible
                    )
                }
                repository.cambiarDisponibilidad(tiendaId, productos, op.disponible)
            } catch (_: Exception) {
                exito = false
            }
        }
        if (exito) limpiar(context)
    }
}
