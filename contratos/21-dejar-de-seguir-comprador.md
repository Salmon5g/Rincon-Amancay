# Dejar de seguir tiendas y asociaciones

## Alcance

A11 permite que una persona con sesión deje de seguir una tienda o una asociación desde el detalle correspondiente en la app Android unificada. El detalle muestra el estado «Siguiendo» y ofrece la acción «Dejar de seguir». Cuando Firestore confirma la eliminación, el control vuelve al estado «Seguir»; al reabrir el detalle consulta el estado persistido.

## Datos y permisos

La app elimina únicamente el documento propio `usuarios/{uid}/tiendasSeguidas/{tiendaId}` o `usuarios/{uid}/asociacionesSeguidas/{asociacionId}`. Las reglas autorizan borrar documentos solo bajo el `uid` autenticado. Después de que Firestore confirma la eliminación, el estado se actualiza y puede recuperarse al reabrir el detalle. No se publican listas de seguidores ni se generan notificaciones.
