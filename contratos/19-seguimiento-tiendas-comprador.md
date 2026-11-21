# Seguimiento de tiendas del comprador

## Alcance

A09 permite que una persona con sesión de Firebase siga una tienda publicada desde el detalle de la tienda en la app Android unificada. El control consulta el estado actual y muestra «Siguiendo» después de guardar. A12 conserva el destino elegido al abrir Mi sesión y, tras autenticar con Google, crea el seguimiento automáticamente y vuelve al detalle sin pedir que se pulse «Seguir» otra vez; el flujo y sus límites están en el contrato 22.

## Datos y permisos

Cada seguimiento se guarda en usuarios/{uid}/tiendasSeguidas/{tiendaId} con el campo tiendaId. La persona solo puede consultar sus propios documentos. Puede crear un seguimiento si la tienda tiene una proyección pública publicada y habilitada. Los reintentos idénticos son idempotentes; el documento no admite cambios de contenido. La persona puede eliminar sus propios documentos, aunque la interfaz para dejar de seguir corresponde a A11.

No se publican listas de seguidores ni se generan notificaciones o consentimiento en A09. La publicación de las reglas de firebase/firestore.rules es requisito para usar el guardado en Firebase compartido.
