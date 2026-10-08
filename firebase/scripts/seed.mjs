import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { database, fields, project, request } from './local.mjs';

export async function loadFixture() {
  const fixture = JSON.parse(await readFile(new URL('../../datos-prueba/catalogo-local.json', import.meta.url), 'utf8'));
  if (fixture.proyecto !== project || !fixture.soloEmulador) throw new Error('Fixture no local');
  const paths = new Set();
  for (const doc of fixture.documentos) {
    if (!/^[a-zA-Z0-9_/-]+$/.test(doc.ruta) || doc.ruta.split('/').length % 2 !== 0 || paths.has(doc.ruta)) {
      throw new Error(`Ruta inválida o repetida: ${doc.ruta}`);
    }
    paths.add(doc.ruta);
  }
  return fixture;
}
export async function seed() {
  const fixture = await loadFixture();
  const result = await request(':commit', {
    method: 'POST',
    body: { writes: fixture.documentos.map(doc => ({ update: {
      name: `${database}/documents/${doc.ruta}`, fields: fields(doc.datos),
    } })) },
  });
  if (result.status !== 200) throw new Error(`No se cargó el emulador: ${JSON.stringify(result.data)}`);
  console.log(`Cargados ${fixture.documentos.length} documentos ficticios en ${project} (localhost).`);
  return fixture;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await seed();
