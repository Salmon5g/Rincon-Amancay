# Entrega 4 — Acceso, administración y tienda

Documentación de los entregables técnicos 5 y 6. Describe el estado comprobado del repositorio y los pasos para trabajar con reglas de seguridad y datos ficticios sin contaminar el proyecto Firebase compartido.

| Entregable | Archivo | Estado actual |
|---|---|---|
| 5. Reglas de seguridad de Firestore y almacenamiento | [5-seguridad-firestore-storage.md](5-seguridad-firestore-storage.md) | Firestore coincide con las reglas publicadas. Las imágenes usan Supabase Storage con políticas RLS aplicadas y verificaciones manuales documentadas. |
| 6. Capa de datos y carga de datos ficticios | [6-capa-datos-y-datos-prueba.md](6-capa-datos-y-datos-prueba.md) | Las operaciones Firestore de la interfaz web y Android están centralizadas en repositorios. Hay seed local y un procedimiento restringido para una instancia Firebase de desarrollo separada. |

No ejecutar la semilla de desarrollo en `rincon-amancay`: ese proyecto es el entorno compartido y no es el destino de fixtures. No se incluyen contraseñas, claves ni archivos de configuración locales.
