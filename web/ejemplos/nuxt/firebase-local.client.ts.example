// Copiar a app/plugins/firebase-local.client.ts al inicializar Nuxt 4.
import { obtenerFirebaseLocal } from '~/lib/firebase-local';
import { crearClienteLocal } from '~/lib/cliente-local';

export default defineNuxtPlugin(() => {
  // No mezclar el adaptador demo con una compilación para producción.
  if (!import.meta.dev) throw new Error('Configurar el entorno real antes de publicar la web.');
  return { provide: { amancay: crearClienteLocal(obtenerFirebaseLocal()) } };
});
