# Publicación y retiro — incremento local

La API HTTP local verifica tokens contra Authentication en el emulador; consultar 04-api-local.md. Storage local comprueba los objetos según 05-imagenes-storage.md. El módulo permanece sin despliegue.

## Entrada y salida

IdentidadVerificada contiene el UID extraído por la API de un token Firebase verificado. Nunca del cuerpo enviado por el cliente. Las pruebas del módulo también inyectan identidades para comprobar las reglas de negocio de forma independiente.

Solicitud: tiendaId, productoId solo para acciones de producto, operacionId único por intención y versionEsperada. La versión es `seconds:nanoseconds` de actualizadoEn de la ficha privada leída previamente, no la hora del teléfono.

Respuesta: accion y operacionId. Releer la ficha para obtener la nueva versión. Errores con code: no-autenticado, sin-permiso, datos-invalidos, no-encontrado, conflicto, id-reutilizado, imagen-requerida, imagen-no-autorizada, no-publicable, tienda-no-publicada, pendiente, limite-local.

## Operaciones atómicas

| Acción | Resultado |
|---|---|
| publicarTienda | Valida propietaria activa y catálogos. Marca fuente publicada y reemplaza la ficha pública con campos permitidos, habilitada y mostrarPrecios derivados. |
| retirarTienda | Marca fuente archivada y elimina ficha, productos y variantes públicos. Conserva las fuentes privadas. |
| publicarProducto | Exige tienda publicada, imágenes autorizadas y datos válidos; publica una proyección explícita y variantes vigentes; elimina variantes públicas que ya no corresponden. |
| retirarProducto | Marca fuente archivada y elimina producto/variantes públicos; conserva variantes privadas. |

Retirar no borra imágenes. Republicar tienda no republica productos automáticamente. Solo se gestionan las subcolecciones conocidas productos/variantes.

Los cambios y el recibo en tiendasPrivadas/{id}/operacionesPublicacion/{operacionId} se confirman juntos. Reintentar la misma solicitud devuelve el resultado anterior sin reescribir. Reutilizar el ID con otros datos falla. También se comprueba autorización actual antes de devolver un recibo. Los clientes no tienen acceso a estos comprobantes; su retención está pendiente.

La transacción detecta cambios concurrentes en sus lecturas. versionEsperada se refiere a la ficha raíz, no a todas las variantes: futuras ediciones de variantes deberán actualizar también actualizadoEn del producto para detectar cambios desde la lectura del usuario. La publicación siempre usa los valores leídos dentro de la transacción, no un catálogo enviado por la pantalla.

## Límites explícitos

- CLP y cantidades enteras por unidad. Pieza única exige una unidad y sin variantes. No se registran ventas.
- Precio/stock en variantes si las hay; sin duplicación operativa en producto padre. Producto simple no conserva variantes privadas.
- Campos públicos seleccionados explícitamente; atributos y opciones según tipo versionado. Los tipos de campo admitidos en este incremento son texto.
- Se exige entre 1 y 5 referencias de imagen distintas. La API verifica ruta, existencia, tamaño, MIME y firma inicial de los objetos de Storage local. Las pruebas aisladas de negocio usan un sustituto; las HTTP usan Storage. Falta procesamiento completo de imágenes. La dependencia es de lectura y tolera reintentos; Storage no integra la transacción Firestore.
- Publicar productos requiere mostrarPrecios=true en configuración privada y ficha pública. Consultar precio requiere el siguiente incremento. Publicar tienda con false mantiene su catálogo bloqueado por las reglas actuales.
- Máximo 100 documentos por subcolección consultada y 200 cambios públicos por operación; al excederlos falla sin cambios. Catálogos grandes requerirán retirada por indicador y limpieza paginada.
- Variantes agotadas con política ocultar se omiten. Si ninguna queda publicable, se rechaza publicar y debe solicitarse retirar explícitamente. No hay sincronización automática de stock.
- Las reglas no controlan al SDK Admin: la API mantiene las verificaciones del módulo. La desactivación coordinada está implementada según 09-desactivacion-cuentas.md; reactivación y otras operaciones administrativas siguen pendientes.

Probado solo en emulador. Sin API pública, Storage real ni despliegue; la nube sigue cerrada.

