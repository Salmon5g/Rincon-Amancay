# Adaptador Kotlin de desarrollo

Ejemplos para integrar en el futuro módulo Android. No hay Gradle, applicationId ni app compilable en esta carpeta y estos archivos no se han compilado. El package ejemplo.amancay solo organiza el ejemplo; adaptarlo al namespace del equipo.

## Preparación

1. Iniciar API y emuladores en el computador según ../../docs/integracion-equipos.md.
2. Crear/usar el proyecto Android del equipo y copiar src/debug/ al src/debug/ de su módulo app, combinando el manifest con el existente. Mantener estos adaptadores fuera de release.
3. Agregar las dependencias principales Firebase Auth, Firestore y Storage, además de coroutines Android y coroutines-play-services para Task.await. Las dependencias Firebase se alinean con BoM; no usar los módulos antiguos -ktx.

Referencia de versiones de las páginas oficiales consultadas: Firebase BoM 35.0.0 y coroutines 1.11.0. Alinear con Kotlin, AGP, SDK y catálogo de versiones del proyecto antes de sincronizar. Esto no sustituye la compilación del equipo:

```kotlin
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
}
```

ConexionLocal crea una FirebaseApp nombrada con parámetros ficticios y conecta los emuladores antes de usarlos; el ejemplo no requiere google-services.json ni una aplicación real registrada. No usar getInstance() sin pasar esta app local, porque seleccionaría otra configuración. No añadir Analytics ni inicialización automática del proyecto real para este escenario. La configuración release se prepara por separado cuando exista applicationId.

## Red de desarrollo

Android Emulator usa 10.0.2.2 para llegar al computador anfitrión. No cambiar el servidor a 0.0.0.0. El manifest debug concede INTERNET y el XML permite HTTP únicamente para hosts locales; no copiar esa excepción a release. Si el proyecto ya tiene networkSecurityConfig, integrar las excepciones locales en su variante debug y resolver la fusión de manifests.

Para teléfono físico por USB, habilitar depuración, seleccionar el dispositivo correcto con adb -s <serial> y ejecutar reverse para los cuatro puertos. Cambiar HOST a 127.0.0.1 en el adaptador debug para esa sesión:

```text
adb -s <serial> reverse tcp:9099 tcp:9099
adb -s <serial> reverse tcp:8080 tcp:8080
adb -s <serial> reverse tcp:9199 tcp:9199
adb -s <serial> reverse tcp:8787 tcp:8787
```

Los túneles pueden necesitar recrearse al reconectar. No usar la IP de otro compañero como sustituto de un backend remoto compartido.

## Uso desde una coroutine de la app

```kotlin
val servicios = ConexionLocal.obtener(context.applicationContext)
val cliente = ClienteLocal(servicios)
val tiendas = cliente.listarTiendas() // Sin sesión
// Para otra página, conservar el último DocumentSnapshot; no paginar si está vacío.

cliente.iniciarSesion(correoDelFormulario, claveDelFormulario)
val acceso = cliente.consultarAcceso()
val tiendaId = acceso?.get("tiendaId") as? String ?: error("Cuenta sin tienda asignada")
val ficha = servicios.db.collection("tiendasPrivadas").document(tiendaId).get().await()
val fecha = ficha.getTimestamp("actualizadoEn") ?: error("Ficha sin versión")
val solicitud = JSONObject()
    .put("tiendaId", tiendaId)
    .put("operacionId", java.util.UUID.randomUUID().toString())
    .put("versionEsperada", ClienteLocal.version(fecha))
cliente.llamar("publicarTienda", solicitud)
```

Importar kotlinx.coroutines.tasks.await y org.json.JSONObject en el archivo que use este ejemplo. Invocar desde viewModelScope u otro scope ligado al ciclo de vida; no bloquear el hilo principal. ClienteLocal mueve HTTP a Dispatchers.IO. Conservar solicitud e ID frente a una respuesta incierta; no repetir automáticamente con otro UUID. Manejar errores de Firebase para sesión/lecturas y ErrorApi para la API. Limpiar el estado privado de la UI al cerrar sesión.

Las operaciones administrativas usan el mismo llamar con los cuerpos de contratos/06 y 09. Las de productos y stock usan los contratos/07 y 08. Nunca tomar roles o UID de un selector visual.

## Imágenes

La conexión expone servicios.storage. Subir a tiendas/{tiendaId}/productos/{productoId}/{uuid}.png, .jpg o .webp con contentType concordante y hasta 5 MiB. Usar StorageMetadata.Builder().setContentType(...), sin customMetadata; luego seleccionar las rutas en Firestore según contrato 05, con actualizadoEn de servidor. La integración Android de subida/selección deberá aplicar el mismo control de versión que el helper web.

Para leer una imagen publicada: servicios.storage.reference.child(ruta).getBytes(5L * 1024 * 1024).await(). No guardar downloadUrl con token en Firestore. La pantalla debe mostrar placeholder para el fixture, que tiene imágenes vacías.

## Pendiente de verificación Android

Sin compilación ni ejecución en este equipo: faltan proyecto Gradle, SDK configurado y dispositivo. Verificar login, catálogo público, token de API, reglas, imágenes, cierre de sesión y hosts al integrarlo. Mantener la misma intención/operacionId entre reintentos y no cambiar seconds:nanoseconds por milisegundos.

Fuentes: [configuración Firebase y BoM](https://firebase.google.com/docs/android/setup), [módulos principales en lugar de KTX](https://firebase.google.com/docs/android/learn-more), [Task.await](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-play-services/), [HTTP en configuración de red Android](https://developer.android.com/privacy-and-security/security-config), [host del emulador](https://firebase.google.com/docs/emulator-suite/connect_auth).

## Ciclo de cuentas

ClienteLocal incluye crearIdentidad, enviarVerificacion, confirmarCorreo, solicitarRecuperacion, comprobarRecuperacion y confirmarRecuperacion. Después de verificar el correo, llamar a registrarComprador con nombreMostrar; para una invitación, aceptarInvitacion con invitacionId. Solicitudes administrativas y permisos en [contrato 10](../../contratos/10-ciclo-cuentas.md).

Ejemplo desde una coroutine de una acción de usuario, con cliente ya inicializado:

```kotlin
cliente.crearIdentidad(correo, clave)
cliente.enviarVerificacion()
// Otra acción, cuando llegue el código del enlace:
cliente.confirmarCorreo(codigo)
cliente.llamar("registrarComprador", JSONObject().put("nombreMostrar", nombreMostrar))
```

No se han creado pantallas ni manejadores de enlaces Android. El ejemplo sigue pendiente de compilación en el proyecto del equipo. En desarrollo Auth simula los correos; las invitaciones se entregan por su identificador.
