// Sincroniza la marca desde el .env raiz hacia el frontend.
//
// 1) Lee el .env de la raiz (o .env.example si no existe).
// 2) Genera o copia el favicon y el logo en frontend/public/.
// 3) Escribe frontend/.env con las variables VITE_* (gitignored) que Vite
//    consumira en el build y en el dev server.
//
// Uso: node scripts/sync-brand.mjs   (se ejecuta con `npm run prebuild`/`predev`)

import { cpSync, copyFileSync, existsSync, mkdirSync, readFileSync, unlinkSync, writeFileSync } from 'node:fs'
import { basename, extname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const FRONTEND_ROOT = resolve(fileURLToPath(new URL('..', import.meta.url)))
const REPO_ROOT = resolve(FRONTEND_ROOT, '..')
const PUBLIC_DIR = join(FRONTEND_ROOT, 'public')
const ENV_PATH = join(REPO_ROOT, '.env')
const ENV_EXAMPLE_PATH = join(REPO_ROOT, '.env.example')
const FRONTEND_ENV_PATH = join(FRONTEND_ROOT, '.env')

const DEFAULTS = {
  VITE_APP_NAME: 'E-Commerce',
  VITE_PRIMARY_COLOR: '#6366f1',
  VITE_SECONDARY_COLOR: '#a855f7',
  VITE_ACCENT_COLOR: '#ec4899',
  VITE_PRIMARY_COLOR_DARK: '#818cf8',
  VITE_SECONDARY_COLOR_DARK: '#c084fc',
  VITE_ACCENT_COLOR_DARK: '#f472b6',
  VITE_FAVICON_PATH: '',
  VITE_LOGO_PATH: '',
}

const VITE_KEYS = Object.keys(DEFAULTS)

function parseEnv(filePath) {
  const out = {}
  if (!existsSync(filePath)) return out
  for (const rawLine of readFileSync(filePath, 'utf8').split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.startsWith('#')) continue
    const eq = line.indexOf('=')
    if (eq <= 0) continue
    let key = line.slice(0, eq).trim()
    let value = line.slice(eq + 1).trim()
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1)
    }
    out[key] = value
  }
  return out
}

function cleanPattern(prefix, extensions) {
  for (const ext of extensions) {
    try {
      unlinkSync(join(PUBLIC_DIR, `${prefix}${ext}`))
    } catch {
      /* noop */
    }
  }
}

const IMG_EXTS = ['.svg', '.png', '.ico', '.jpg', '.jpeg', '.gif', '.webp']

// Vite (dotenv) trata un '#' sin comillas en el valor como inicio de comentario,
// asi que los colores como #ffffff deben escribirse entre comillas dobles.
function quoteEnvValue(value) {
  return /^#/.test(String(value)) ? `"${value}"` : value
}

function pickAsset(sourcePath, publicName) {
  if (!sourcePath) return null
  const src = resolve(REPO_ROOT, sourcePath)
  if (!existsSync(src)) {
    console.warn(`  ! No se encontro "${sourcePath}" (relativo a la raiz). Se usa el valor por defecto.`)
    return null
  }
  const ext = extname(src).toLowerCase()
  const dest = `${publicName}${ext}`
  copyFileSync(src, join(PUBLIC_DIR, dest))
  return `/${dest}`
}

function initials(name) {
  const words = name.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return 'E'
  if (words.length === 1) return words[0][0].toUpperCase()
  return (words[0][0] + words[1][0]).toUpperCase()
}

function generateFaviconSvg(name, primary, secondary) {
  const letter = initials(name).split('').join(' ')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="${escXml(primary)}"/>
      <stop offset="1" stop-color="${escXml(secondary)}"/>
    </linearGradient>
  </defs>
  <rect width="64" height="64" rx="16" fill="url(#g)"/>
  <text x="32" y="41" font-family="Arial, Helvetica, sans-serif" font-size="26" font-weight="700" letter-spacing="1" fill="#ffffff" text-anchor="middle">${escXml(letter)}</text>
</svg>
`
  writeFileSync(join(PUBLIC_DIR, 'favicon.svg'), svg)
}

function escXml(value) {
  return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

console.log('[sync-brand] Leyendo marca de .env raiz...')

const source = existsSync(ENV_PATH) ? ENV_PATH : ENV_EXAMPLE_PATH
const env = parseEnv(source)

for (const key of VITE_KEYS) {
  if (env[key] === undefined) env[key] = DEFAULTS[key]
}

mkdirSync(PUBLIC_DIR, { recursive: true })

if (env.VITE_FAVICON_PATH && existsSync(resolve(REPO_ROOT, env.VITE_FAVICON_PATH))) {
  env.VITE_FAVICON_URL = pickAsset(env.VITE_FAVICON_PATH, 'favicon')
  console.log(`[sync-brand] Favicon: ${env.VITE_FAVICON_URL}`)
} else {
  generateFaviconSvg(env.VITE_APP_NAME, env.VITE_PRIMARY_COLOR, env.VITE_SECONDARY_COLOR)
  env.VITE_FAVICON_URL = '/favicon.svg'
  cleanPattern('favicon', IMG_EXTS.filter((e) => e !== '.svg'))
  console.log('[sync-brand] Favicon generado: /favicon.svg')
}

cleanPattern('brand-logo', IMG_EXTS)
if (env.VITE_LOGO_PATH) {
  env.VITE_LOGO_URL = pickAsset(env.VITE_LOGO_PATH, 'brand-logo')
  console.log(`[sync-brand] Logo: ${env.VITE_LOGO_URL ?? 'ninguno (usa el mark con iniciales)'}`)
} else {
  env.VITE_LOGO_URL = ''
  console.log('[sync-brand] Logo: ninguno (mark con iniciales)')
}

const lines = VITE_KEYS.map((key) => `${key}=${quoteEnvValue(env[key])}`)
lines.push(`VITE_FAVICON_URL=${quoteEnvValue(env.VITE_FAVICON_URL || '')}`)
lines.push(`VITE_LOGO_URL=${quoteEnvValue(env.VITE_LOGO_URL || '')}`)
writeFileSync(FRONTEND_ENV_PATH, lines.join('\n') + '\n')
console.log(`[sync-brand] frontend/.env escrito (marca: "${env.VITE_APP_NAME}")`)