# Operaciones de servidor

Implementado el módulo TypeScript de publicación/retiro y una API HTTP autenticada local en src/http/api.ts. Arranque con npm run dev, puerto 8787, solo emuladores. Ver ../contratos/04-api-local.md. Sin despliegue; falta elegir alojamiento y evaluar costos.

src/config/ inicializará las conexiones de servidor; src/modules/ agrupará casos de uso por negocio; src/shared/ contendrá autorización y errores comunes. tests/ comprobará permisos y consistencia.

Contratos: ../contratos/03-publicacion.md y ../contratos/06-alta-emprendedoras.md. Implementada alta administrativa de rol y tienda para una identidad existente; npm run cuentas:demo crea identidades de práctica. Invitaciones de producción, ventas y sincronización automática de disponibilidad pública siguen pendientes.

crearProducto y editarProducto gestionan fichas y variantes privadas según ../contratos/07-edicion-productos.md. npm run test:productos comprueba este flujo; editar no publica ni modifica stock existente.

ajustarStock registra reposiciones, pérdidas y correcciones según ../contratos/08-ajustes-stock.md. Se prueba con npm run test:stock; el cambio de saldo y la retirada pública son atómicos. El producto requiere republicación explícita. No registra ventas.

## Ejecutar

Requiere Node.js 24.15.0 o superior. Primero iniciar los emuladores desde firebase/ con npm run emulators. Desde backend/:

```sh
npm ci
npm run typecheck
npm test
```

Solo se usa demo-rincon-amancay: Firestore en 127.0.0.1:8080, Authentication en 127.0.0.1:9099 y Storage en 127.0.0.1:9199. No requiere credenciales de servicio. Las pruebas reponen los documentos del fixture: no ejecutarlas simultáneamente con otras suites o ediciones manuales del mismo catálogo.

La API verifica el ID token Firebase con comprobación de revocación y pasa su UID al módulo. Rechaza uid/roles en el cuerpo. La función de validación de imágenes conecta Storage local: ruta, existencia, tamaño, MIME y firma inicial; consultar ../contratos/05-imagenes-storage.md para sus límites. Las pruebas HTTP utilizan esa conexión; solo las pruebas aisladas de negocio la sustituyen.

Verificado el 8 de octubre de 2026: TypeScript correcto; 16 casos de negocio, 24 de API y 9 de imágenes pasados (52 incluyendo grupos padre). Incluye sesión, permisos, proyecciones, variantes, producto a pedido, imágenes inválidas, stock inválido, retiradas, idempotencia y concurrencia. Auditoría previa del backend: 0 vulnerabilidades reportadas; sin nuevas dependencias en este incremento. No equivale a auditoría completa.

