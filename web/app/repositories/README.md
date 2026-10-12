Consultas y escrituras autorizadas de la web y llamadas a operaciones del servidor. Las páginas no contendrán consultas Firestore dispersas.

- `admin.ts`: lectura del panel (`cargarDatosAdmin`, `listarAccesos`, `listarEmprendedoras`, `listarInvitaciones`, `listarCatalogos`). Recibe el cliente de `~/lib/cliente-local`; las reglas exigen administrador activo para accesos, emprendedoras e invitaciones. Pendiente: escrituras (invitar/cancelar, desactivar/reactivar).
