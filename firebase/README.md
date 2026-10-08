# Configuración Firebase

Estado actual: firestore.rules incorpora un primer incremento de permisos probado solo localmente; ver ../docs/permisos-locales.md. Storage sigue cerrado. Las referencias de abajo a denegación total de Firestore corresponden a la nube y al estado inicial, no a las reglas locales actuales.

Ya hay herramientas locales en package.json. Ver [guía de emuladores](../docs/emuladores.md) para instalar, cargar la instantánea ficticia y ejecutar las comprobaciones. Los comandos fijan demo-rincon-amancay, separado del proyecto real.

firestore.rules y storage.rules parten cerradas: ningún cliente puede leer o escribir. No son las reglas de negocio terminadas. El acceso por SDK de servidor requiere controles propios. Firestore ya se creó desde la consola con reglas equivalentes de denegación total; Storage no se ha provisionado.

firestore.indexes.json está vacío; agregar índices según consultas reales acordadas. Las colecciones no se crean añadiendo carpetas aquí.

firebase.json, en la raíz, referencia estos archivos y reserva puertos locales para Authentication, Firestore y Storage. .firebaserc define development = rincon-amancay; los comandos locales usan explícitamente demo-rincon-amancay. No hay configuración Hosting o Functions. Authentication y Firestore se probaron localmente, junto con la carga ficticia y denegaciones de acceso iniciales. Storage y los permisos por rol siguen pendientes.

Los contratos de documentos y permisos irán en contratos/. Las imágenes reales se almacenarán en Storage, no en este repositorio.
