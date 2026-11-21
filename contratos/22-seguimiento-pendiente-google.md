# Seguimiento pendiente de inicio de sesión

## Alcance

A12 permite que una persona sin sesión pulse «Seguir» en el detalle de una tienda o asociación y continúe por Mi sesión. La app conserva temporalmente el tipo, identificador y nombre del destino en el estado guardado de la actividad. Después de que Firebase Authentication confirma la sesión, crea el seguimiento en el documento privado del usuario y vuelve a la pestaña y al detalle elegidos. La persona no tiene que pulsar «Seguir» otra vez; el detalle consulta Firestore y muestra «Siguiendo».

Si la autenticación se cancela, se conserva el destino pendiente para que pueda volver a iniciar sesión. Si Firestore rechaza la escritura o no hay conexión, la app mantiene la acción y ofrece reintentar. No se escribe ningún seguimiento antes de autenticar, no se amplían permisos de Firestore y no se crean listas públicas ni notificaciones.

## Datos y permisos

Se reutilizan los documentos y reglas definidos en [A09](19-seguimiento-tiendas-comprador.md) y [A10](20-seguimiento-asociaciones-comprador.md): `usuarios/{uid}/tiendasSeguidas/{tiendaId}` y `usuarios/{uid}/asociacionesSeguidas/{asociacionId}`. El guardado es idempotente por identificador y solo se intenta con el UID autenticado actual.
