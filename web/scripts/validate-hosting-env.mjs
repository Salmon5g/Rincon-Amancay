import { existsSync, readFileSync } from 'node:fs'

const envPath = new URL('../.env', import.meta.url)

if (!existsSync(envPath)) {
  throw new Error('Falta web/.env. Créalo desde web/.env.example y configura Firebase real antes de generar Hosting.')
}

const values = new Map()
for (const line of readFileSync(envPath, 'utf8').split(/\r?\n/)) {
  const trimmed = line.trim()
  if (!trimmed || trimmed.startsWith('#')) continue
  const match = trimmed.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/)
  if (!match) continue
  let value = match[2].trim()
  if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
    value = value.slice(1, -1)
  }
  values.set(match[1], value)
}

const configured = (key) => process.env[key] ?? values.get(key)
const modo = configured('NUXT_PUBLIC_FIREBASE_MODO')
const proyecto = configured('NUXT_PUBLIC_FIREBASE_PROJECT_ID')
const apiKey = configured('NUXT_PUBLIC_FIREBASE_API_KEY')
const apiUrl = configured('NUXT_PUBLIC_API_URL') ?? ''
const faltantes = [
  'NUXT_PUBLIC_FIREBASE_API_KEY',
  'NUXT_PUBLIC_FIREBASE_AUTH_DOMAIN',
  'NUXT_PUBLIC_FIREBASE_STORAGE_BUCKET',
  'NUXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID',
  'NUXT_PUBLIC_FIREBASE_APP_ID',
].filter((key) => !configured(key) || configured(key) === 'REEMPLAZAR_DESDE_FIREBASE')

if (!['real-lectura', 'real-api'].includes(modo)) {
  throw new Error('Hosting solo admite Firebase real: configura NUXT_PUBLIC_FIREBASE_MODO como real-lectura o real-api.')
}
if (proyecto !== 'rincon-amancay') {
  throw new Error('La generación para este Hosting debe apuntar al proyecto Firebase rincon-amancay.')
}
if (!apiKey || apiKey === 'REEMPLAZAR_DESDE_FIREBASE' || faltantes.length) {
  throw new Error('Completa la configuración pública de Firebase Web en web/.env antes de generar Hosting.')
}
if (modo === 'real-lectura' && apiUrl) {
  throw new Error('En real-lectura, deja NUXT_PUBLIC_API_URL vacía.')
}
if (modo === 'real-api' && !apiUrl.startsWith('https://')) {
  throw new Error('Para publicar con real-api, NUXT_PUBLIC_API_URL debe ser una URL HTTPS.')
}

console.log(`Entorno Hosting validado: ${modo} · proyecto ${proyecto}.`)
