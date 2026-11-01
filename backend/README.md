# Operaciones de servidor

API HTTP autenticada en `src/http/api.ts` con publicación, cuentas, productos, stock e imágenes. `npm run dev` inicia `src/server.ts` en puerto 8787, solo emuladores. `src/server-compartido.ts` ejecuta la API local contra Firebase real usando ADC y puerto 8080. El proveedor de despliegue público (se ha conversado Railway) aún no está elegido ni configurado. Ver [arranque de equipos](../docs/arranque-equipos.md).

`src/config/` separa conexiones y configuración HTTP; `src/modules/` implementa operaciones por función; `src/montar-api.ts` conecta módulos y servidor. `tests/` comprueba permisos y consistencia. `src/shared/` queda reservado para futuras utilidades comunes.

Contratos: ../contratos/03-publicacion.md, ../contratos/06-alta-emprendedoras.md, ../contratos/10-ciclo-cuentas.md, ../contratos/14-edicion-cuentas-emprendedoras.md, ../contratos/15-configuracion-y-administradores.md y ../contratos/16-administracion-ferias.md. La API incluye invitaciones, edición administrativa, desactivación/reactivación, catálogos, asociaciones, cuentas administradoras, configuración de navegación y ferias, además de productos, stock, disponibilidad e imágenes. El panel web comparte el enlace de invitación de cuenta y la destinataria crea sus credenciales, verifica el correo y acepta la tienda desde la web; el panel no envía invitaciones por correo. Ver el estado y los límites en `../docs/admin-firebase-real-api-local.md`.

crearProducto, editarProducto y eliminarProducto gestionan fichas y variantes según ../contratos/07-edicion-productos.md. La eliminación requiere la versión actual, retira la proyección y variantes públicas, conserva la ficha privada como tombstone con su historial y programa la limpieza de imágenes públicas. `npm run test:productos` cubre la creación y edición existentes; la eliminación aún no tiene cobertura automatizada.

ajustarStock registra reposiciones, pérdidas y correcciones según ../contratos/08-ajustes-stock.md. Se prueba con npm run test:stock; el cambio de saldo, disponibilidad e historial es atómico. La disponibilidad se mantiene desde la ficha aprobada; solo publicaciones antiguas sin esa copia requieren republicar. No registra ventas.

desactivarEmprendedora bloquea gestión y catálogo antes de deshabilitar Auth; ver ../contratos/09-desactivacion-cuentas.md. npm run test:desactivacion prueba también fallos parciales y reintentos. Reactivación local disponible en el contrato 10; la reconciliación automática sigue pendiente.

El ciclo de cuentas se describe en [contrato 10](../contratos/10-ciclo-cuentas.md). Probar con `npm run test:ciclo`. Hay cola por UID y reserva Firestore persistente para excluir procesos concurrentes. La API local usa una instancia por su puerto fijo. Reservas huérfanas: `npm run cuentas:recuperar` siguiendo [el procedimiento técnico](../docs/recuperacion-cuentas.md); no se recuperan automáticamente por tiempo.

## Ejecutar

Requiere Node.js 24.15.0 o superior. Primero iniciar los emuladores desde firebase/ con npm run emulators. Desde backend/:

```sh
npm ci
npm run typecheck
npm test
```

Solo se usa demo-rincon-amancay: Firestore en 127.0.0.1:8080, Authentication en 127.0.0.1:9099 y Storage en 127.0.0.1:9199. No requiere credenciales de servicio. Las pruebas reponen los documentos del fixture: no ejecutarlas simultáneamente con otras suites o ediciones manuales del mismo catálogo.

La API verifica el ID token Firebase con comprobación de revocación y pasa su UID al módulo. Rechaza uid/roles en el cuerpo. En el modo emulador, el procesamiento de imágenes lee Firebase Storage local. En `start:compartido:local`, Supabase Storage guarda originales privados y copias WebP públicas mediante la cuenta Auth técnica normal configurada en `backend/.env.compartido.local`; esa entrada nunca acepta `service_role`. Sus rutas binarias son `POST /api/v1/tiendas/{tiendaId}/logo` y `POST /api/v1/tiendas/{tiendaId}/productos/{productoId}/imagenes`. Las rutas y políticas están documentadas en ../contratos/05-imagenes-storage.md y ../docs/supabase-storage-imagenes.md.

Validación del incremento de cuentas: TypeScript, suite completa del backend, cliente web y reglas locales. Los resultados actuales se obtienen con los comandos de pruebas; el procesamiento de imágenes añade Sharp 0.35.5, fijado en el lockfile. Android permanece pendiente de compilación en el proyecto del equipo.


Propuesta de nube: [entorno compartido](../docs/entorno-compartido.md). Para ejecutar contra Firebase real y Supabase Storage localmente, seguir [API local con Firebase real](../docs/admin-firebase-real-api-local.md) y completar el archivo local de credenciales. El servidor escucha solo en `127.0.0.1` y modifica datos reales; no ejecutar seeds ni pruebas contra ese modo. La API aún no tiene un comando de despliegue. `npm run plan:desarrollo` solo genera un informe local.
