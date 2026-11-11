package com.example.amancay.data

import android.content.Context

data class ValoresDefectoProducto(
    val modalidad: String = "regular",
    val manejaStock: Boolean = true,
    val alAgotarse: String = "mostrar_agotado"
)

object PreferenciasCatalogoEmprendedora {
    private const val PREFS = "catalogo_emprendedora"
    private fun clave(tiendaId: String, campo: String) = "$tiendaId:$campo"

    fun leerValoresDefecto(context: Context, tiendaId: String): ValoresDefectoProducto {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ValoresDefectoProducto(
            modalidad = prefs.getString(clave(tiendaId, "modalidad"), "regular") ?: "regular",
            manejaStock = prefs.getBoolean(clave(tiendaId, "manejaStock"), true),
            alAgotarse = prefs.getString(clave(tiendaId, "alAgotarse"), "mostrar_agotado") ?: "mostrar_agotado"
        )
    }

    fun guardarValoresDefecto(context: Context, tiendaId: String, valores: ValoresDefectoProducto) {
        val guardados = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(clave(tiendaId, "modalidad"), valores.modalidad)
            .putBoolean(clave(tiendaId, "manejaStock"), valores.manejaStock)
            .putString(clave(tiendaId, "alAgotarse"), valores.alAgotarse)
            .putBoolean(clave(tiendaId, "valoresDefectoSincronizados"), true)
            .commit()
        check(guardados) { "No se pudo guardar la configuración local del catálogo." }
    }

    fun hayValoresDefectoSincronizados(context: Context, tiendaId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(clave(tiendaId, "valoresDefectoSincronizados"), false)

    fun catalogoSinConexion(context: Context, tiendaId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(clave(tiendaId, "catalogoOffline"), true)

    fun guardarCatalogoSinConexion(context: Context, tiendaId: String, activo: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(clave(tiendaId, "catalogoOffline"), activo)
            .apply()
    }
}
