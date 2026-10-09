# Primer incremento de permisos — solo emulador

Reglas en firebase/firestore.rules. No desplegadas al proyecto real: rincon-amancay continúa con denegación total. No usar estas reglas como una implementación completa del semestre.

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

Firestore no filtra resultados prohibidos: una consulta sin los filtros necesarios se rechaza. El siguiente trabajo de integración deberá añadir paginación e índices según las consultas finales; el emulador no garantiza todos los requisitos de índices de producción.

## Indicadores derivados y operaciones obligatorias

Se añaden habilitada y mostrarPrecios a la ficha pública, y activa a variantes públicas. Son indicadores públicos no sensibles, escritos exclusivamente por operaciones confiables; no son una segunda autoridad editable. Roles y propiedad siguen en accesos y la ficha privada.

El futuro backend debe actualizar coordinadamente las fuentes privadas y sus indicadores públicos:

- Desactivar una emprendedora: accesos.estado y tiendasPublicas.habilitada=false en una misma transacción o lote atómico. La lectura de productos/variantes comprueba también el padre público, bloqueando enlaces directos inmediatamente.
- Archivar tienda/producto o desactivar variante: actualizar también el estado público correspondiente de forma atómica.
- Ocultar precios: cambiar configuración privada y mostrarPrecios público a false atómicamente. En este incremento esto bloquea TODOS los productos y variantes de esa tienda, incluso si algún documento ya no tiene precio. Falta implementar la regeneración sin importes y una política versionada para recuperar la lectura segura.

Cambiar SOLO un documento privado desde consola/Admin SDK no retira por sí mismo su copia pública. Las reglas no pueden imponer controles al SDK Admin: las pruebas simulan las escrituras coordinadas con autoridad de servidor, no prueban un servicio de publicación existente. No desplegar ni utilizar con datos reales hasta implementar y validar ese servicio.

Las proyecciones públicas se cargan mediante una lista explícita de campos. Las reglas no ocultan campos dentro de documentos legibles; el backend no debe introducir datos privados en ellos. Retirar acceso no elimina copias ya descargadas.

## Comprobaciones

Resultado del 8 de octubre de 2026: 38 casos de permisos pasaron, junto con la verificación de carga repetible de los 29 documentos. La CLI conserva 11 alertas de dependencias (7 altas, 4 moderadas); se alineó @grpc/grpc-js a 1.14.5 como en web/ para resolver los avisos adicionales del SDK de pruebas. Esto no constituye una auditoría completa de seguridad.

Con emuladores en ejecución, desde firebase/: `npm run test:rules`. Para una ejecución aislada, detener primero los emuladores interactivos y usar `npm run check` (incluye datos y permisos). Las pruebas restauran los documentos del fixture al terminar; no conservan ediciones manuales de esos documentos.

Las sesiones de prueba son simuladas mediante @firebase/rules-unit-testing. No son cuentas de Authentication ni un flujo real de login. Se incluyen visitante, propietaria, otra propietaria activa, cuenta sin acceso, cuenta desactivada, claims falsos y administrador.

Referencias oficiales: https://firebase.google.com/docs/rules/unit-tests y https://firebase.google.com/docs/firestore/security/rules-conditions
