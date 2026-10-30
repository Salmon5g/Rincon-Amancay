#!/usr/bin/env node
import { pathToFileURL } from 'node:url';

/**
 * Importar el fixture ficticio a Firebase real está deshabilitado.
 * Para desarrollo local, usar npm run seed con los emuladores.
 */
function seed() {
  throw new Error(
    'Carga rechazada: los datos de datos-prueba/catalogo-local.json son exclusivos de emuladores. No se permite importarlos a Firebase real.'
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    seed();
  } catch (err) {
    console.error(err instanceof Error ? err.message : err);
    process.exit(1);
  }
}

export { seed };
