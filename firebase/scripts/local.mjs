// Solo emulador: no acepta destinos remotos ni credenciales del proyecto real.
export const project = 'demo-rincon-amancay';
export const host = '127.0.0.1:8080';
for (const variable of ['GCLOUD_PROJECT', 'GOOGLE_CLOUD_PROJECT']) {
  if (process.env[variable] && process.env[variable] !== project) {
    throw new Error(`${variable} debe ser ${project}; no se cargan datos en la nube.`);
  }
}
if (process.env.FIRESTORE_EMULATOR_HOST && process.env.FIRESTORE_EMULATOR_HOST !== host) {
  throw new Error(`Solo se permite FIRESTORE_EMULATOR_HOST=${host}`);
}
export const database = `projects/${project}/databases/(default)`;
export const url = `http://${host}/v1/${database}/documents`;
export async function request(path, { admin = true, method = 'GET', body } = {}) {
  const response = await fetch(url + path, {
    method,
    redirect: 'error',
    signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', ...(admin ? { Authorization: 'Bearer owner' } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const data = await response.json();
  return { status: response.status, data };
}
export function value(input) {
  if (input === null) return { nullValue: null };
  if (typeof input === 'boolean') return { booleanValue: input };
  if (typeof input === 'string') return { stringValue: input };
  if (typeof input === 'number' && Number.isSafeInteger(input)) return { integerValue: String(input) };
  if (Array.isArray(input)) return { arrayValue: input.length ? { values: input.map(value) } : {} };
  if (typeof input === 'object') {
    if (Object.keys(input).length === 1 && '$timestamp' in input) {
      if (Number.isNaN(Date.parse(input.$timestamp))) throw new Error('Fecha inválida');
      return { timestampValue: input.$timestamp };
    }
    return { mapValue: { fields: fields(input) } };
  }
  throw new Error('Tipo no soportado en la carga ficticia');
}
export function fields(data) {
  return Object.fromEntries(Object.entries(data).map(([key, input]) => [key, value(input)]));
}
