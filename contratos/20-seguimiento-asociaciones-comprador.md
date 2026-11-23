# Seguimiento de asociaciones del comprador

## Alcance

A10 permite que una persona con sesión de Firebase siga una asociación activa desde su detalle en el directorio de la app Android unificada. El control consulta el estado actual y muestra «Siguiendo» después de guardar; al volver a abrir el detalle, recupera el estado desde Firestore. A12 conserva el destino al abrir Mi sesión y, tras autenticar con Google, completa el seguimiento y vuelve al detalle sin repetir la acción; el flujo y sus límites están en el contrato 22.

La web del comprador reutiliza el mismo camino en la ficha de asociación del directorio: el botón «Seguir asociación» consulta y alterna el documento propio, y sin sesión abre el ingreso del comprador.

## Datos y permisos

Cada seguimiento se guarda en `usuarios/{uid}/asociacionesSeguidas/{asociacionId}` con el campo `asociacionId`. La persona solo puede consultar y crear su propio seguimiento. Puede crearlo si la asociación tiene una proyección pública activa. Los reintentos idénticos son idempotentes; el documento no admite cambios de contenido. La eliminación para dejar de seguir se describe en el contrato A11.

La publicación de las reglas de `firebase/firestore.rules` es requisito para usar el guardado en Firebase compartido. La implementación local no publica reglas, ni crea listas públicas de seguidores o notificaciones.
