# Configuración de acceso y cuentas administradoras

## Configuración central

Documento: `configuracionAplicacion/navegacion`.

| Campo | Tipo | Significado |
|---|---|---|
| `modo` | string | `permitir`, `invitar` o `exigir` |
| `creadoEn` | timestamp | Primera escritura |
| `actualizadoEn` | timestamp | Última escritura; también controla la versión |

`permitir` deja recorrer el catálogo sin sesión. `invitar` conserva la navegación y permite que los clientes ofrezcan iniciar sesión. `exigir` expresa una futura política con cuenta. En esta versión web y Android todavía no leen este documento para modificar su navegación: mantener `permitir` hasta integrar y verificar esa lectura en ambos clientes.

Las operaciones `leerConfiguracionNavegacion` y `guardarConfiguracionNavegacion` requieren una sesión Firebase y un documento `accesos/{uid}` activo con rol `administrador`. La escritura requiere `versionEsperada` y `operacionId`; el servidor registra el resultado idempotente y la auditoría. El documento contiene solo una preferencia pública, por lo que las reglas permiten su lectura directa; ningún cliente puede escribirlo.

## Cuentas administradoras

La fuente de permisos es `accesos/{uid}` con `estado: "activo"` y `roles` que incluya `administrador`. La cuenta de Authentication debe existir, estar habilitada y tener correo verificado. `agregarAdministrador` recibe un correo existente, nunca una contraseña ni un UID elegido por el navegador. No promueve una cuenta emprendedora ni permite que el actor se agregue a sí mismo.

La operación añade el rol y mantiene otros roles de comprador. `quitarAdministrador` exige versión vigente, no permite quitar el propio rol ni dejar cero administradores activos. Solo retira el rol; no borra ni bloquea la cuenta de Authentication. Cada cambio conserva recibo de idempotencia en `operacionesAdministracion/{actorUid}_{operacionId}` y auditoría en `auditoriaAdministracion/{actorUid}_{operacionId}`.

El servidor usa Firebase Admin. Por eso una cuenta administradora de la aplicación no reemplaza los permisos IAM de la identidad de Cloud Run o de la cuenta ADC local. No se publican contraseñas ni claves de servicio.
