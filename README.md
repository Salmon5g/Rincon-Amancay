# Rincón Amancay

Aplicación web y Android para el catálogo de emprendedoras de la feria Rincón Amancay. El proyecto utiliza Firebase Authentication y Firestore como identidad y base compartida; la API autenticada local realiza las operaciones privilegiadas y Supabase Storage guarda las imágenes.

La web pública está en [rincon-amancay.web.app](https://rincon-amancay.web.app). Para probar la integración completa con una API local, ejecuta también la web en tu computador: una web publicada no puede conectarse a la API `localhost` de otro integrante.

## Qué está conectado

- **Web Nuxt:** el catálogo comprador lee tiendas, productos y variantes públicos de Firestore, sin exigir inicio de sesión. Convierte las rutas `catalogo/...` en URLs públicas de Supabase. El panel `/admin` lee y escribe mediante la API autenticada en modo `real-api`.
- **Android unificado:** está en `android/app-combinada`. El comprador lee el catálogo público e imágenes; la emprendedora inicia sesión con Firebase Auth, valida rol y tienda, edita la ficha, crea y edita productos, carga fotos privadas, ve su vista previa, publica/retira y ajusta stock. La huella es opcional y desbloquea la sesión guardada en el dispositivo.
- **API local:** opera sobre el Firebase real mediante ADC y las políticas de Storage de Supabase. Autoriza cada operación con el ID token de Firebase. Administra, entre otros, productos, publicación, imágenes y stock. Escucha solo en el propio computador; no está desplegada en una URL compartida.
- **Datos comunes:** web y Android leen/escriben el mismo proyecto `rincon-amancay`. Las imágenes originales permanecen privadas en Supabase; al publicar, la API crea derivadas WebP públicas y Firestore guarda sus rutas.

El modelo de datos sigue siendo provisional. Coordina cambios de colecciones, campos y estados entre web, Android, API y contratos. Las ventas y las invitaciones a ferias en la app todavía no están conectadas a Firestore.

## Formas de probar

| Objetivo | Web | API | Datos |
|---|---|---|---|
| Ver comprador e imágenes publicadas | `real-lectura` | No necesaria | Firebase y Supabase reales, solo lectura desde la web |
| Probar administración web | `real-api` | API compartida local en puerto 8080 | Firebase real; las operaciones cambian datos reales |
| Probar app emprendedora y comprador con fotos | No necesaria | API compartida local en puerto 8080 | Firebase y Supabase reales; las operaciones cambian datos reales |
| Trabajar aislado con fixtures | `local` | API demo en puerto 8787 y emuladores | Datos ficticios locales; no muestra automáticamente imágenes reales de Supabase |

Para un compañero que quiere comprobar lo implementado en una cuenta real, sigue **Prueba local integrada con Firebase y Supabase** más abajo. No ejecutes seeds ni suites de prueba contra el proyecto real.

## Prueba local integrada con Firebase y Supabase

Cada integrante que ejecute su propia API debe preparar su computador. Los datos de Firebase sí son compartidos entre integrantes; la API escucha solo en `127.0.0.1` y no se comparte por la red.

### 1. Requisitos y dependencias

Instala Node.js **24.15.0 o superior**, Google Cloud CLI y Android Studio con Java 21 o superior si probarás Android. Desde PowerShell, en la raíz del repositorio, instala dependencias (una vez por computador o cuando cambien los lockfiles):

```powershell
npm.cmd --prefix backend ci
npm.cmd --prefix web ci
```

### 2. Configurar la identidad de Firebase para la API

La API usa Firebase Admin y requiere que la identidad de Google Cloud tenga permisos IAM para Firestore y Firebase Authentication. La cuenta administradora dentro de la aplicación no otorga esos permisos IAM.

```powershell
gcloud auth application-default login
gcloud config set project rincon-amancay
```

Completa el consentimiento en el navegador con tu propia cuenta Google autorizada por el responsable del proyecto. La contraseña de Firebase Authentication no concede permisos a la API: el propietario del proyecto debe otorgar a cada integrante los permisos IAM necesarios para que Firebase Admin acceda a Firestore y Authentication. Si la API informa `EADDRINUSE`, ya hay algo usando el puerto 8080; comprueba que sea la API de este proyecto antes de continuar. No generes ni compartas claves JSON de cuentas de servicio.

### 3. Configurar Supabase en el backend local

Si todavía no existe el archivo local, créalo desde el ejemplo:

```powershell
if (!(Test-Path backend/.env.compartido.local)) {
  Copy-Item backend/.env.compartido.local.example backend/.env.compartido.local
}
```

Abre `backend/.env.compartido.local` y completa `AMANCAY_SUPABASE_ANON_KEY`, `AMANCAY_SUPABASE_MEDIA_EMAIL` y `AMANCAY_SUPABASE_MEDIA_PASSWORD` con los datos autorizados para el grupo. El UID debe coincidir con `AMANCAY_SUPABASE_MEDIA_UID` del ejemplo. Esas credenciales se entregan por un canal privado; el archivo está ignorado por Git.

La API valida el proyecto Firebase, exige Supabase por HTTPS y rechaza `service_role`. No copies la cuenta técnica a Android, a la web, al README o a otros archivos versionados. Los buckets y políticas deben existir en la instancia del curso; su detalle está en [Supabase Storage para imágenes](docs/supabase-storage-imagenes.md).

### 4. Iniciar la API real local

En una terminal desde la raíz:

```powershell
npm.cmd --prefix backend run start:compartido:local
```

Déjala abierta. Comprueba `http://localhost:8080/health`; debe responder con estado `ok`, entorno `compartido` y proyecto `rincon-amancay`. No arranques emuladores ni ejecutes `seed`, scripts demo o pruebas automatizadas mientras trabajas con este modo. Si el puerto 8080 ya está ocupado, comprueba si la API correcta ya está activa antes de iniciar otra instancia.

### 5. Configurar y arrancar la web

Si aún no existe `web/.env`, créalo:

```powershell
if (!(Test-Path web/.env)) {
  Copy-Item web/.env.example web/.env
}
```

Completa las variables `NUXT_PUBLIC_FIREBASE_*` con la configuración de la aplicación web del proyecto Firebase `rincon-amancay`. Para permitir operaciones desde el panel local, asegúrate de tener:

```ini
NUXT_PUBLIC_FIREBASE_MODO=real-api
NUXT_PUBLIC_API_URL=http://localhost:8080
NUXT_PUBLIC_SUPABASE_URL=https://supabase-fixmol.codepulse.cl
```

Para **solo mirar el catálogo comprador**, configura `NUXT_PUBLIC_FIREBASE_MODO=real-lectura`; en ese modo no necesitas iniciar la API. Las imágenes públicas se cargan directamente desde Supabase. Reinicia Nuxt después de cambiar `.env`:

```powershell
npm.cmd --prefix web run dev
```

Abre `http://localhost:3000` (redirige a `/comprador`) o `http://localhost:3000/admin`. Inicia sesión en el panel con una cuenta Firebase que tenga acceso activo y el rol correspondiente. En el navegador, las imágenes procesadas deben solicitarse a `supabase-fixmol.codepulse.cl`, no a Firebase Storage.

### 6. Configurar y ejecutar Android

1. En Android Studio, abre `android/app-combinada` y sincroniza Gradle.
2. Añade localmente `android/app-combinada/app/google-services.json`, descargado para el ID de aplicación `com.example.amancay`. No lo subas a Git.
3. En `android/app-combinada/local.properties`, conserva la línea `sdk.dir` que creó Android Studio y añade:

   ```properties
   amancay.firebase.emulators=false
   amancay.api.baseUrl=http://10.0.2.2:8080/api/v1/
   ```

   `10.0.2.2` conecta el emulador Android con la API de este mismo computador. La API escucha en loopback y no se puede consumir desde otro computador por una IP de red.

4. Antes de entrar, debe existir una cuenta emprendedora activa en Firebase Authentication y su documento `accesos/{uid}` debe tener el rol `emprendedora` y el `tiendaId` correspondiente. La tienda privada debe estar vinculada al mismo UID. Si no existe una cuenta ficticia para la prueba, créala desde el panel web de administración con la API local encendida y entrega sus credenciales por un canal privado.
5. Ejecuta la configuración `app` en un emulador Android e inicia sesión con esa cuenta.

En un teléfono físico conectado por USB, puedes usar `adb reverse tcp:8080 tcp:8080` y configurar `amancay.api.baseUrl=http://127.0.0.1:8080/api/v1/`. Retira el puente al terminar con `adb reverse --remove tcp:8080`.

### 7. Recorrido de comprobación

1. Desde Android emprendedora, abre **Productos** y crea un borrador usando el asistente. Selecciona una foto y guarda: Firestore conserva la ficha y Supabase el original privado.
2. Abre el producto desde **Mis productos**. Su imagen privada debe aparecer en la miniatura/vista previa; solo la propietaria puede pedir esa imagen a la API.
3. Publica la tienda si aún está en borrador y después publica el producto. La API crea WebP públicas en Supabase y sincroniza la proyección de Firestore.
4. En la web local, abre el catálogo comprador o recarga la tienda. Deben aparecer los datos y la imagen sin iniciar sesión. Comprueba en Network que la imagen viene de la URL pública `amancay-catalogo` en Supabase.
5. Prueba desde la app editar, retirar, ajustar stock y eliminar un producto de prueba. Eliminar lo quita de la lista y catálogo público, conserva historial y original privado, y retira las derivadas públicas.
6. Si quieres probar el panel admin, entra a `/admin` en modo `real-api`; sus cambios también irán a Firebase real.

Usa cuentas y productos ficticios autorizados por el grupo. Estas operaciones no son una sandbox: escriben en el proyecto compartido real. La eliminación conserva el historial privado y no es un borrado físico completo.

## Emuladores locales

Los emuladores son un entorno aislado para datos ficticios y suites automatizadas. Sigue [Arranque por equipo](docs/arranque-equipos.md). No mezcles `local` con `real-api`; en el modo local se usan el proyecto demo y Firebase Storage Emulator, y en el modo compartido se usan Firebase real y Supabase Storage.

## Publicación

La web está publicada en Firebase Hosting. La API todavía no tiene una URL HTTPS compartida; por eso, las escrituras privilegiadas requieren web y API locales en el computador que las ejecuta. Consulta [Publicación web en Firebase Hosting](docs/hosting-web.md) antes de reconstruir o publicar.

## Estructura

```text
web/                    Web Nuxt para comprador y administración
android/app-combinada/   App Android unificada (comprador y emprendedora)
backend/                 API autenticada y módulos de negocio
firebase/                Reglas, índices, emuladores y fixtures
infra/supabase/          Políticas SQL de Storage
contratos/               Modelo y acuerdos de datos/operaciones
datos-prueba/            Fixtures ficticios para emuladores
docs/                    Guías de operación, equipo y arquitectura
firebase.json            Configuración Firebase y Hosting
```

## Seguridad y coordinación

No subas `.env`, `.env.compartido.local`, `google-services.json`, contraseñas, claves de cuentas de servicio ni credenciales de Supabase. La configuración web de Firebase identifica el cliente; nunca sustituye Authentication, las reglas ni IAM del backend.

Las pruebas automatizadas y semillas pueden reponer datos ficticios. No las ejecutes contra el proyecto Firebase compartido `rincon-amancay`. La carga cloud ficticia solo se permite en el proyecto separado de desarrollo descrito en [Entrega 4 — Capa de datos](docs/Entrega%204%20%E2%80%94%20Acceso,%20administraci%C3%B3n%20y%20tienda/6-capa-datos-y-datos-prueba.md). Las instrucciones para el modelo y las operaciones están en [contratos](contratos/README.md), [integración de equipos](docs/integracion-equipos.md) y [reglas Firebase](firebase/README.md).

Los entregables técnicos de la Entrega 4 sobre seguridad y capa de datos están en [docs/Entrega 4 — Acceso, administración y tienda](docs/Entrega%204%20%E2%80%94%20Acceso,%20administraci%C3%B3n%20y%20tienda/README.md).
