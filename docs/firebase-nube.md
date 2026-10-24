# Migración a Firebase real (nube)

Esta guía explica cómo dejar la app comprador funcionando contra un proyecto Firebase real, sin emuladores ni `adb reverse`.

## 1. Crear el proyecto

1. Ir a la consola de Firebase: https://console.firebase.google.com.
2. Crear un proyecto (por ejemplo, `rincon-amancay-real`).
3. Activar **Firestore** → Modo nativo.
4. Activar **Storage**.

## 2. Agregar la app Android

1. En Firebase → Configuración del proyecto → General → Agregar app → Android.
2. Paquete: `cl.equipo.amancay` (exactamente este valor).
3. Descargar `google-services.json`.
4. Colocarlo en `android/app/google-services.json`. Ese archivo **sí se versiona** (no contiene secretos reales). Al estar presente, el plugin `com.google.gms.google-services` se aplica automáticamente y `processDebugGoogleServices` genera los recursos.

## 3. Cambiar el destino de la app

En la variante **debug** (la que usa el modo comprador), editar:

`android/app/src/debug/java/cl/equipo/amancay/DestinoConfig.kt`

```kotlin
object DestinoConfig {
    // true = emuladores locales | false = proyecto Firebase real
    const val USAR_EMULADORES = false
}
```

Ya no necesitás `npm run emulators` ni `adb reverse`. Si alguna vez querés volver a emuladores, poné `USAR_EMULADORES = true`.

## 4. Desplegar reglas

Desde la raíz o desde `firebase/`:

```powershell
npm --prefix firebase ci
firebase deploy --only firestore:rules,storage
```

(Usando las `firestore.rules` y `storage.rules` existentes.)

## 5. Cargar los datos ficticios a la nube

Necesitás una cuenta de servicio con permiso para escribir en Firestore:

1. Firebase → Configuración del proyecto → Cuentas de servicio.
2. Generar una nueva clave privada → descargar `serviceAccount.json` (¡Este archivo **NO** va a Git!).
3. Asignar `GOOGLE_APPLICATION_CREDENTIALS` y cargar:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\ruta\serviceAccount.json"
npm --prefix firebase run seed:cloud
```

El script carga todos los documentos de `datos-prueba/catalogo-local.json` en tu proyecto real (merge/set, preservando la estructura). También podés volver a ejecutar `seed:cloud` si necesitás refrescar.

## 6. Compilar y probar

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
cd android
.\gradlew.bat :app:assembleDebug
```

Luego Run desde Android Studio (variante debug) contra tu emulador/dispositivo. El comprador verá el mismo directorio/detalles pero desde la nube.

## Notas

- `ConexionLocal.API` apunta a `http://127.0.0.1:8787/api/v1/`. El comprador **no usa** ese endpoint (lee directamente de Firestore). Solo lo usaría si se agregan operaciones que escriban y requieran el backend.
- El `seed.mjs` queda para los emuladores. El `seed-cloud.mjs` usa `firebase-admin`.
- `google-services.json` puede subirse a Git porque no contiene secretos.
- Te quedan suscriptos a las cuotas gratuitas de Firebase (Spark). Revisá cuotas si cargás mucho.