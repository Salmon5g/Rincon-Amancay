/* Service worker de avisos de ferias (FCM web).
   La configuración llega por parámetros al registrar el servicio desde
   `app/lib/avisos-ferias.ts`, porque este archivo es estático. */
importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-messaging-compat.js');

const parametros = new URLSearchParams(self.location.search);
const configuracion = {
  apiKey: parametros.get('apiKey') || undefined,
  projectId: parametros.get('projectId') || undefined,
  messagingSenderId: parametros.get('messagingSenderId') || undefined,
  appId: parametros.get('appId') || undefined,
};

firebase.initializeApp(configuracion);

const mensajeria = firebase.messaging();

mensajeria.onBackgroundMessage((carga) => {
  const datos = carga.data || {};
  const titulo = datos.titulo || (carga.notification && carga.notification.title) || 'Rincón Amancay';
  const cuerpo = datos.mensaje || (carga.notification && carga.notification.body) || '';
  const url = datos.feriaId
    ? '/comprador/ferias?feria=' + encodeURIComponent(datos.feriaId)
    : '/comprador/ferias';
  self.registration.showNotification(titulo, {
    body: cuerpo,
    icon: '/logo-amancay.png',
    badge: '/logo-amancay.png',
    data: { url },
  });
});

self.addEventListener('notificationclick', (evento) => {
  evento.notification.close();
  const url = (evento.notification.data && evento.notification.data.url) || '/comprador/ferias';
  evento.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((ventanas) => {
      for (const ventana of ventanas) {
        if ('focus' in ventana) {
          if ('navigate' in ventana) ventana.navigate(url);
          return ventana.focus();
        }
      }
      return clients.openWindow(url);
    }),
  );
});
