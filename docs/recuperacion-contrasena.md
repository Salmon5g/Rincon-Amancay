# Recuperación de contraseña y correos de Firebase

La web tiene una página propia en `/auth/accion` con la identidad visual de Rincón Amancay. Valida los enlaces de restablecimiento antes de mostrar el formulario y permite completar la acción con Firebase Authentication. También atiende los modos `verifyEmail` y `recoverEmail`, porque la URL de acción personalizada de Firebase se aplica a las plantillas de acciones de correo del proyecto.

La página retira los parámetros de la barra del navegador después de leerlos, no guarda la contraseña ni el código en `localStorage`, y envía una política `no-store` desde Firebase Hosting. Web y Android solicitan los correos de restablecimiento en español (`es`).

## Activar la pantalla de Rincón Amancay en los enlaces

La ruta debe estar publicada antes de seleccionarla en Firebase:

1. Genera y publica la web de Rincón Amancay en Firebase Hosting con la configuración del proyecto `rincon-amancay`.
2. Abre **Firebase Console → Authentication → Plantillas**.
3. Edita las plantillas de restablecimiento de contraseña y verificación de correo. Configura el idioma español y, si se desea, un asunto y texto que nombren a Rincón Amancay. Por ejemplo, asunto: `Restablece tu contraseña de Rincón Amancay`.
4. En **Personalizar URL de acción**, configura `https://rincon-amancay.web.app/auth/accion` y guarda.
5. Confirma en **Authentication → Configuración → Dominios autorizados** que `rincon-amancay.web.app` esté autorizado.
6. Solicita un correo nuevo y comprueba que el enlace abra `/auth/accion`, muestre la página con la marca y termine el cambio. Los enlaces anteriores pueden conservar la configuración anterior.

Firebase aplica la URL de acción personalizada a sus correos de gestión de cuentas. La página contempla restablecimiento de contraseña, verificación de correo y recuperación de una dirección anterior. No cambies esta URL a una página que solo soporte uno de esos modos.

## Riesgo del enlace

El código de restablecimiento incluido en el enlace es una credencial temporal de un solo uso. Quien obtenga el enlace antes que la persona destinataria puede fijar una contraseña y tomar esa cuenta; el enlace no entrega acceso a Firebase Console, a claves de servicio ni a las cuentas de otras personas. El daño posible queda limitado por los permisos de esa identidad en Authentication, Firestore y la API. Firebase invalida las sesiones de actualización al restablecer la contraseña y pide volver a iniciar sesión.

No copies el enlace en chats grupales, capturas, tickets ni registros. Si sospechas que se filtró, solicita un restablecimiento nuevo y desactiva la cuenta desde administración si necesitas cortar su acceso de inmediato. Nunca se registra el código en el proyecto.

## Entrega en Spam

El idioma, el asunto y la página de destino se pueden controlar desde el proyecto. La entrega en la bandeja principal también depende de Gmail y de la reputación del remitente; el código no puede garantizarla.

- Para el correo que ya llegó, marcarlo como **No es spam** y añadir el remitente a contactos puede ayudar al destinatario.
- Para mejorar la identidad del remitente a largo plazo, hace falta un dominio propio que el grupo controle y completar la verificación DNS que indique Firebase. Configurar un dominio de enlaces no basta por sí solo para construir reputación del remitente.
- SPF, DKIM y DMARC del dominio de envío deben quedar alineados según las instrucciones DNS actuales de Firebase y del proveedor de correo. No se debe inventar ni copiar un registro SPF de ejemplo: hay que usar los valores que entregue el servicio.
- Una solución con plantillas totalmente propias y un proveedor SMTP externo requiere diseñar y operar otro servicio de correo. No es necesario para usar el restablecimiento actual de Firebase.

Los enlaces de acción no deben incluirse en evidencia; basta mostrar la página de marca ya sin el código en la URL y el correo recibido con la dirección y el enlace ocultos.
