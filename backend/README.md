# Operaciones de servidor

Implementado el módulo TypeScript de publicación/retiro y una API HTTP autenticada local en src/http/api.ts. Arranque con npm run dev, puerto 8787, solo emuladores. Ver ../contratos/04-api-local.md. Sin despliegue; falta elegir alojamiento y evaluar costos.

src/config/ inicializará las conexiones de servidor; src/modules/ agrupará casos de uso por negocio; src/shared/ contendrá autorización y errores comunes. tests/ comprobará permisos y consistencia.

Contratos: ../contratos/03-publicacion.md y ../contratos/06-alta-emprendedoras.md. Implementada alta administrativa de rol y tienda para una identidad existente; npm run cuentas:demo crea identidades de práctica. Invitaciones de producción y ventas siguen pendientes. Disponibilidad e imágenes procesadas: contrato 11.

crearProducto y editarProducto gestionan fichas y variantes privadas según ../contratos/07-edicion-productos.md. npm run test:productos comprueba este flujo; editar no publica ni modifica stock existente.

ajustarStock registra reposiciones, pérdidas y correcciones según ../contratos/08-ajustes-stock.md. Se prueba con npm run test:stock; el cambio de saldo, disponibilidad e historial es atómico. La disponibilidad se mantiene desde la ficha aprobada; solo publicaciones antiguas sin esa copia requieren republicar. No registra ventas.

desactivarEmprendedora bloquea gestión y catálogo antes de deshabilitar Auth; ver ../contratos/09-desactivacion-cuentas.md. npm run test:desactivacion prueba también fallos parciales y reintentos. Reactivación local disponible en el contrato 10; la reconciliación automática sigue pendiente.

El ciclo de cuentas (registro comprador, invitaciones, verificación/recuperación y reactivación) se describe en [contrato 10](../contratos/10-ciclo-cuentas.md). Probar con npm run test:ciclo. Ejecutar una sola instancia de la API local: las fases de Auth se serializan por UID dentro de este proceso.

## Ejecutar

Requiere Node.js 24.15.0 o superior. Primero iniciar los emuladores desde firebase/ con npm run emulators. Desde backend/:

```sh
npm ci
npm run typecheck
npm test
```

Solo se usa demo-rincon-amancay: Firestore en 127.0.0.1:8080, Authentication en 127.0.0.1:9099 y Storage en 127.0.0.1:9199. No requiere credenciales de servicio. Las pruebas reponen los documentos del fixture: no ejecutarlas simultáneamente con otras suites o ediciones manuales del mismo catálogo.

La API verifica el ID token Firebase con comprobación de revocación y pasa su UID al módulo. Rechaza uid/roles en el cuerpo. La función de validación de imágenes conecta Storage local: ruta, existencia, tamaño, MIME, decodificación completa, orientación y generación de WebP/miniaturas; consultar ../contratos/05-imagenes-storage.md para sus límites. Las pruebas HTTP utilizan esa conexión; solo las pruebas aisladas de negocio la sustituyen.

Validación del incremento de cuentas: TypeScript, suite completa del backend, cliente web y reglas locales. Los resultados actuales se obtienen con los comandos de pruebas; el procesamiento de imágenes añade Sharp 0.35.5, fijado en el lockfile. Android permanece pendiente de compilación en el proyecto del equipo.

