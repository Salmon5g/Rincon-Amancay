package cl.equipo.amancay

/**
 * Interruptor de destino de datos del modo comprador (variante debug).
 *
 * - `true`  -> emuladores locales de Firebase (127.0.0.1 + `adb reverse`). Requiere
 *   `npm run emulators` y `npm run seed` en la carpeta `firebase/`.
 * - `false` -> proyecto Firebase real en la nube. Requiere colocar `google-services.json`
 *   en `android/app/` y publicar las reglas y los datos. Ver `docs/firebase-nube.md`.
 */
object DestinoConfig {
    const val USAR_EMULADORES = true
}
