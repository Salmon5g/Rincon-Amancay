# Despliegue en Coolify

Guía para publicar la API (`backend/`) y la web (`web/`) en un servidor con Coolify. Los datos y el inicio de sesión siguen en Firebase: Coolify solo reemplaza dónde corren la web y la API.

Se crean **dos aplicaciones** desde el mismo repositorio de GitHub.

## 1. API

### Cuenta de servicio

El servidor no tiene la sesión de `gcloud` del equipo local (ADC), así que la API necesita una cuenta de servicio:

1. Firebase Console → Configuración del proyecto → **Cuentas de servicio** → «Generar nueva clave privada». Se descarga un JSON.
2. **No subir ese archivo al repo.** El `.gitignore` ya ignora nombres como `*adminsdk*.json`, pero conviene guardarlo fuera de la carpeta del proyecto.
3. Su contenido se pega completo en la variable `AMANCAY_CREDENCIAL_SERVICIO` de Coolify, marcada como secreta. También se acepta codificado en base64:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\ruta\a\la-cuenta-de-servicio.json"))
```

La API rechaza la credencial si no es una cuenta de servicio o si pertenece a otro proyecto distinto de `AMANCAY_PROJECT_ID`. Nunca muestra su contenido en los mensajes de error.

### Aplicación en Coolify

- **New Resource → Application → repositorio** (rama `main`).
- Build pack: **Dockerfile**. Base directory: `/backend`.
- Puerto expuesto: **8080**.
- Dominio: por ejemplo `https://api.tudominio.cl`. Coolify genera el certificado HTTPS.

El `Dockerfile` ya define `PORT=8080` y `AMANCAY_HOST=0.0.0.0`, para que la API acepte conexiones dentro del contenedor.

### Variables de entorno

| Variable | Valor |
|---|---|
| `AMANCAY_ENTORNO` | `compartido` |
| `AMANCAY_PROJECT_ID` | ID del proyecto Firebase |
| `AMANCAY_PROJECT_ID_ESPERADO` | El mismo ID |
| `AMANCAY_ORIGENES_WEB` | Dirección de la web, por ejemplo `https://tudominio.cl` (HTTPS exacto, sin `/` final; varias separadas por coma) |
| `AMANCAY_WEB_URL` | Dirección pública de la web |
| `AMANCAY_CREDENCIAL_SERVICIO` | JSON de la cuenta de servicio (secreto) |
| `AMANCAY_SUPABASE_URL`, `AMANCAY_SUPABASE_ANON_KEY`, `AMANCAY_SUPABASE_MEDIA_EMAIL`, `AMANCAY_SUPABASE_MEDIA_PASSWORD`, `AMANCAY_SUPABASE_MEDIA_UID` | Los mismos de `backend/.env.compartido.local` (secretos) |

No definir `GOOGLE_APPLICATION_CREDENTIALS` ni variables de emuladores: la API se niega a arrancar con ellas.

Comprobación: `https://api.tudominio.cl/health` debe responder `200` con el proyecto correcto.

## 2. Web

- **New Resource → Application → repositorio** (rama `main`).
- Build pack: **Nixpacks**, marcando *Is it a static site?*.
- Base directory: `/web`.
- Build command: `npm run generate`.
- Publish directory: `.output/public`.
- Dominio: por ejemplo `https://tudominio.cl`.

Las variables `NUXT_PUBLIC_…` se cargan como **variables de build**, porque se usan al generar el sitio. Son las de `web/.env`, con estos cambios:

- `NUXT_PUBLIC_FIREBASE_MODO=real-api`
- `NUXT_PUBLIC_API_URL=https://api.tudominio.cl` (la API de Coolify; el generador rechaza direcciones que no sean HTTPS)

`npm run generate` valida primero la configuración (`web/scripts/validate-hosting-env.mjs`). Lee `web/.env` si existe y, si no, las variables del entorno, así que en Coolify no hace falta el archivo.

## 3. Ajustes fuera de Coolify

- **Firebase Console → Authentication → Settings → Dominios autorizados:** agregar el dominio de la web. Sin esto falla el inicio de sesión.
- **Recuperación de contraseña:** si se usa la página propia, actualizar la URL de acción a `https://tudominio.cl/auth/accion` (ver `docs/recuperacion-contrasena.md`).
- **App Android:** cambiar la dirección de la API (hoy `127.0.0.1:8080` por USB) por `https://api.tudominio.cl/` y generar un APK nuevo.

## Cuidados

- La cuenta de servicio tiene acceso total al proyecto: guardarla solo como secreto en Coolify y revocarla en Firebase Console si se filtra.
- Antes de abrir la web al público, activar App Check y revisar las reglas con límites de Firestore, para evitar otro exceso de lecturas.
- Mientras el proyecto Firebase esté suspendido, la web y la API cargan, pero no pueden leer ni guardar datos.
