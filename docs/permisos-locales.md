# Pruebas locales de reglas Firestore

Este documento describe las pruebas históricas ejecutadas con los emuladores; no es el indicador del estado remoto actual. El 1 de noviembre de 2026 se comprobó que el release Firestore de `rincon-amancay` coincide con `firebase/firestore.rules`. El resumen vigente, incluidos los permisos del almacenamiento de imágenes Supabase, está en [Entrega 4 — Seguridad](Entrega%204%20%E2%80%94%20Acceso,%20administraci%C3%B3n%20y%20tienda/5-seguridad-firestore-storage.md).

## Acceso implementado

| Actor | Permitido |
|---|---|
| Visitante o cuenta | Leer tiendas habilitadas/publicadas, productos publicados, variantes activas y catálogos activos |
| Cuenta | Leer su perfil y su documento de acceso, incluso para conocer que fue desactivada |
| Cuenta activa | Cambiar nombreMostrar propio (1–100 caracteres) y actualizadoEn del servidor |
| Propietaria activa y asignada | Leer ficha/configuración/productos/variantes privados de su tienda; editar nombre (1–120) y descripción (máximo 2000) privados, con fecha de servidor |
| Administrador activo | Leer/listar accesos y metadatos administrativos de emprendedoras; no leer tiendas privadas o ventas |

Los límites de longitud son provisionales de esta implementación. Las ediciones de texto privado no publican automáticamente. Ningún cliente puede crear cuentas Firestore, cambiar roles, asignar propiedad, publicar, modificar stock o configuración, borrar tiendas ni escribir ventas. Las operaciones implementadas se ejecutan mediante la API validada de los contratos 03–10; las restantes permanecen bloqueadas. Ventas y otros módulos todavía no implementados permanecen bloqueados también para propietarias.

## Consultas acordadas

- tiendasPublicas: where estadoPublicacion == publicado y habilitada == true.
- tiendasPublicas/{id}/productos: where estadoPublicacion == publicado.
- .../variantes: where activa == true.
- Catálogos: where activo == true.

Firestore no filtra resultados prohibidos: una consulta sin los filtros necesarios se rechaza. El emulador no garantiza todos los requisitos de índices de producción. El índice compuesto declarado en el repositorio está en `firebase/firestore.indexes.json`; cualquier consulta nueva debe comprobar si necesita un índice adicional.

## Indicadores derivados y operaciones de servidor

Las reglas exigen `estadoPublicacion`, `habilitada`, `mostrarPrecios` y `activa` para permitir lecturas públicas. Si una tienda oculta precios, la regla bloquea los documentos de producto y variante derivados de esa tienda; no existe una redacción por campo dentro de Firestore.

Las operaciones actuales de publicación, retiro, desactivación y stock pasan por la API autenticada, que mantiene coordinados los documentos privados y sus proyecciones públicas. Cambiar manualmente un documento privado desde consola o Admin SDK no retira por sí mismo su copia pública. Las pruebas de reglas simulan escrituras de servidor con permisos desactivados; no sustituyen una revisión de las validaciones de la API.

Las proyecciones públicas se cargan mediante una lista explícita de campos. Las reglas no ocultan campos dentro de documentos legibles; el backend no debe introducir datos privados en ellos. Retirar acceso no elimina copias ya descargadas.

## Comprobaciones

Resultado del 8 de octubre de 2026: 38 casos de permisos pasaron, junto con la verificación de carga repetible de los 29 documentos. La CLI conserva 11 alertas de dependencias (7 altas, 4 moderadas); se alineó @grpc/grpc-js a 1.14.5 como en web/ para resolver los avisos adicionales del SDK de pruebas. Esto no constituye una auditoría completa de seguridad.

Con emuladores en ejecución, desde firebase/: `npm run test:rules`. Para una ejecución aislada, detener primero los emuladores interactivos y usar `npm run check` (incluye datos y permisos). Las pruebas restauran los documentos del fixture al terminar; no conservan ediciones manuales de esos documentos.

Las sesiones de prueba son simuladas mediante @firebase/rules-unit-testing. No son cuentas de Authentication ni un flujo real de login. Se incluyen visitante, propietaria, otra propietaria activa, cuenta sin acceso, cuenta desactivada, claims falsos y administrador.

Referencias oficiales: https://firebase.google.com/docs/rules/unit-tests y https://firebase.google.com/docs/firestore/security/rules-conditions
