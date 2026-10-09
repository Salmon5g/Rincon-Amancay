# Recuperación técnica de una cuenta interrumpida

Responsable: quien mantiene el backend. Los equipos de pantallas no deben ejecutar esta herramienta ni modificar accesos en la consola para desbloquear usuarios.

Auth y Firestore no comparten una transacción. Una operación guarda un comprobante y bloquea la gestión antes de llamar a Auth. `ejecucionesCuentas/{uid}` impide que dos servidores actúen simultáneamente. Si el proceso muere, la reserva permanece: no tiene caducidad automática porque una petición Auth antigua podría terminar después de que otra instancia hubiera tomado la cuenta.

## 1. Inspeccionar (solo lectura)

Desde la raíz, con Firestore local iniciado:

```powershell
npm --prefix backend run cuentas:recuperar -- --entorno local --uid UID_DE_LA_CUENTA
```

La salida muestra el `ejecutorId`, proceso/revisión, estado de acceso y operación pendiente. Los comprobantes nuevos conservan `solicitud` con su versión original; contiene datos administrativos y no debe compartirse en canales públicos. Si `reserva` es null, no hay reserva física que liberar: un fallo parcial normal se resuelve reintentando la misma solicitud mediante la API.

## 2. Detener y comprobar antes de liberar

1. Suspender nuevas solicitudes administrativas y detener **todas** las instancias que puedan actuar sobre este proyecto, incluidos scripts y revisiones antiguas. En local, detener la API con Ctrl+C; conservar los emuladores activos.
2. Confirmar que el proceso identificado terminó. Una desconexión del navegador o un timeout no demuestran esto. En la nube también deben resolverse las peticiones Auth que pudieran seguir en vuelo; revisar su resultado y el estado de la identidad. Si no puede garantizarse que una petición antigua ya no tendrá efectos, **no liberar** y escalar la revisión técnica.
3. Inspeccionar otra vez y copiar el `ejecutorId` actual. Revisar la operación pendiente. No borrar comprobantes ni cambiar el estado de acceso, la versión o el corte de sesiones.

El programa no puede demostrar que las instancias remotas estén detenidas: la confirmación es una condición operativa real, no una espera arbitraria ni una protección automática.

## 3. Liberar exactamente la reserva inspeccionada

```powershell
npm --prefix backend run cuentas:recuperar -- --entorno local --uid UID_DE_LA_CUENTA --ejecutor EJECUTOR_COPIADO --operador "Responsable del backend" --motivo "Proceso terminado y revisado" --confirmar-servidores-detenidos
```

La transacción comprueba el ejecutor y la coherencia del comprobante pendiente, escribe `recuperacionesCuentas/{ejecutorId}` y elimina únicamente la reserva física. Repetir una liberación completada no duplica su registro. Si otra ejecución tomó la cuenta, el ejecutor ya no coincide y no se elimina su reserva. Las reglas deniegan acceso cliente a estos registros.

**No habilita ni deshabilita Authentication, no activa accesos y no publica tiendas.** La reserva lógica `operacionCuentaPendiente` permanece para impedir comenzar la operación contraria.

## 4. Retomar y verificar

1. Reiniciar una instancia de la API.
2. Con la misma cuenta administradora que inició la operación, reenviar a la acción indicada la `solicitud` original: mismo `operacionId`, versión, destino y motivo. No generar un ID nuevo ni sustituir la versión por la actual.
3. Comprobar `estado: completada`, la ausencia de `operacionCuentaPendiente`, el estado Auth esperado y que la tienda continúe oculta. Tras una reactivación, la emprendedora debe iniciar una sesión nueva y publicar expresamente.
4. Si no había operación pendiente, revisar el comprobante original antes de decidir un reintento. Los comprobantes completados son idempotentes; una reserva creada antes de guardar el comprobante no implica que la operación haya llegado a Auth.

Para registros antiguos sin `solicitud`, usar el cuerpo que conservó el cliente. Si se perdió, el administrador original dejó de tener permisos, se eliminó la identidad o los datos son inconsistentes, detenerse y revisar técnicamente. Esta herramienta no inventa una solicitud ni sustituye al administrador original.

## Entorno compartido futuro

El mismo comando admite `--entorno compartido`, con `AMANCAY_ENTORNO=compartido`, `AMANCAY_PROJECT_ID=rincon-amancay` e identidad técnica ADC autorizada. Rechaza variables de emuladores y archivos de credenciales de servicio. Nunca otorgar esos permisos a web o Android. Todavía no se probó esta intervención contra servicios desplegados; debe validarse en desarrollo remoto antes del uso real.

## Verificación automatizada

`backend/tests/recuperacion-cuentas.test.ts` inicia un proceso Node hijo, alcanza la fase Auth, comprueba la exclusión desde un segundo proceso, termina el hijo y recupera tanto desactivación como reactivación. Verifica ejecutor incorrecto, confirmación ausente, comprobante inconsistente, auditoría, reintento y preservación del catálogo oculto. Auth se simula en esa prueba para controlar la interrupción; no reproduce peticiones en vuelo a Google. Las suites de cuentas prueban adicionalmente Auth local. Una recuperación automática por tiempo sigue excluida.
