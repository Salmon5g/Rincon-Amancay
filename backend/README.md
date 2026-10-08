# Operaciones de servidor

Implementado el módulo TypeScript de publicación/retiro y una API HTTP autenticada local en src/http/api.ts. Arranque con npm run dev, puerto 8787, solo emuladores. Ver ../contratos/04-api-local.md. Sin despliegue; falta elegir alojamiento y evaluar costos.

src/config/ inicializará las conexiones de servidor; src/modules/ agrupará casos de uso por negocio; src/shared/ contendrá autorización y errores comunes. tests/ comprobará permisos y consistencia.

Contrato: ../contratos/03-publicacion.md. Alta de cuentas, ventas y actualización de stock siguen pendientes.

## Ejecutar

Requiere Node.js 24.15.0 o superior. Primero iniciar los emuladores desde firebase/ con npm run emulators. Desde backend/:

```sh
npm ci
npm run typecheck
npm test
```

Solo se usa demo-rincon-amancay: Firestore en 127.0.0.1:8080 y Authentication en 127.0.0.1:9099. No requiere credenciales de servicio. Las pruebas reponen los documentos del fixture: no ejecutarlas simultáneamente con otras suites o ediciones manuales del mismo catálogo.

La API verifica el ID token Firebase con comprobación de revocación y pasa su UID al módulo. Rechaza uid/roles en el cuerpo. La función de autorización de imágenes es obligatoria; solo las pruebas la sustituyen. Storage real no está conectado y la API no habilita esa publicación sin el verificador real.

Verificado el 8 de octubre de 2026: TypeScript correcto; 16 casos de negocio y 23 casos de API pasados. La API comprueba tokens inválidos, vencidos y revocados, cuentas deshabilitadas, permisos, validación de solicitudes y operaciones autenticadas. Incluye permisos, campos privados, variantes, producto a pedido, imágenes inválidas, stock inválido, retiradas, idempotencia y concurrencia. npm audit del backend: 0 vulnerabilidades reportadas; no equivale a auditoría completa.

