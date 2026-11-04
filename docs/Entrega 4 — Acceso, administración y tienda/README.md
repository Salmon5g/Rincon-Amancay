# Entrega 4 — Acceso, administración y tienda

Documentación de los entregables técnicos 5, 6 y 7. Describe las reglas de seguridad, la capa de datos, la carga de datos ficticios y el modelo Firestore que se presentará en la Entrega 4.

| Entregable | Archivo | Estado actual |
|---|---|---|
| Informe breve consolidado (B19) | [informe-entrega-4.docx](informe-entrega-4.docx) | Informe Word proporcionado y editado por el usuario. |
| 5. Reglas de seguridad de Firestore y almacenamiento | [5-seguridad-firestore-storage.md](5-seguridad-firestore-storage.md) | Firestore coincide con las reglas publicadas. Las imágenes usan Supabase Storage con políticas RLS aplicadas y verificaciones manuales documentadas. |
| 6. Capa de datos y carga de datos ficticios | [6-capa-datos-y-datos-prueba.md](6-capa-datos-y-datos-prueba.md) | Las operaciones Firestore de la interfaz web y Android están centralizadas en repositorios. Hay seed local y un procedimiento restringido para una instancia Firebase de desarrollo separada. |
| 7. Presentación del modelo Firestore | [7-modelo-datos-firestore.pptx](7-modelo-datos-firestore.pptx) | Presenta colecciones, relaciones, privacidad, índices y la demostración de conexión. La diapositiva de ferias refleja la consulta actual de Android. |

No ejecutar la semilla de desarrollo en `rincon-amancay`: ese proyecto es el entorno compartido y no es el destino de fixtures. No se incluyen contraseñas, claves ni archivos de configuración locales.
