# Conexión Android y API

Esta guía reemplaza las notas antiguas de conexión Android; las rutas `android/ejemplos`, `ConexionLocal.kt` y `ClienteLocal.kt` ya no describen la app unificada.

La configuración actual está en:

- [Conexión local de la app emprendedora](android-emprendedora-firebase.md): Firebase real o emuladores, API local, cuentas y flujos disponibles.
- [Arranque por equipo](arranque-equipos.md#2-equipo-app-android): ubicación de la app y requisitos generales.
- [API local con Firebase real](admin-firebase-real-api-local.md): credenciales ADC, IAM y servidor local.
- [Contrato de edición de tienda](../contratos/17-edicion-tienda-emprendedora.md): operación autenticada y sincronización con la proyección pública.
- [Contratos de productos y stock](../contratos/07-edicion-productos.md) y [ajustes de stock](../contratos/08-ajustes-stock.md).

En resumen: Authentication y lecturas autorizadas de Firestore usan el SDK Firebase Android. Cambios de tienda, productos, publicaciones y stock pasan por la API. Para Firebase real desde el emulador Android, el computador ejecuta la API en `localhost:8080` y Android la alcanza en `10.0.2.2:8080`. No hay una URL HTTPS desplegada para que otros computadores compartan esta API.
