// Configurador web del .env del proyecto.
//
// Sirve una interfaz local (http://localhost:4600) para editar el .env raiz
// sin abrirlo a mano: campos agrupados por seccion, generador de JWT_SECRET,
// vista cruda, y un boton que reconstruye el frontend.
//
// Uso:  node scripts/env-editor.mjs     (o `make env-config`)
//
// Consideraciones:
// - Escucha SOLO en 127.0.0.1: ninguna otra maquina puede conectarse.
// - No requiere ninguna dependencia (usa solo modulos nativos de Node).

import { createServer } from 'node:http'
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { exec, execSync, spawn } from 'node:child_process'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { promisify } from 'node:util'

const ROOT = resolve(fileURLToPath(new URL('..', import.meta.url)))
const FIELD_SPEC_PATH = join(ROOT, 'scripts', 'env-fields.json')
const ENV_PATH = join(ROOT, '.env')
const ENV_EXAMPLE_PATH = join(ROOT, '.env.example')
const PORT = Number(process.env.ENV_EDITOR_PORT || 4600)

const FIELD_SPEC = JSON.parse(readFileSync(FIELD_SPEC_PATH, 'utf8'))
const FIELD_KEYS = FIELD_SPEC.sections.flatMap((s) => s.fields.map((f) => f.key))

const execAsync = promisify(exec)

function parseEnv(source) {
  const values = {}
  for (const rawLine of String(source).split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.startsWith('#')) continue
    const eq = line.indexOf('=')
    if (eq <= 0) continue
    const key = line.slice(0, eq).trim()
    let value = line.slice(eq + 1).trim()
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1)
    }
    values[key] = value
  }
  return values
}

function ensureEnvExists() {
  if (!existsSync(ENV_PATH) && existsSync(ENV_EXAMPLE_PATH)) {
    copyFileSync(ENV_EXAMPLE_PATH, ENV_PATH)
  }
}

function readEnvValues() {
  const path = existsSync(ENV_PATH) ? ENV_PATH : ENV_EXAMPLE_PATH
  return { values: parseEnv(readFileSync(path, 'utf8')), exists: existsSync(ENV_PATH), source: path }
}

// Vite (dotenv) trata un '#' sin comillas en el valor como inicio de comentario,
// asi que los colores como #ffffff se guardan entre comillas dobles.
function quoteHex(value) {
  return /^#/.test(String(value)) ? `"${value}"` : value
}

function mergeEnv(content, values) {
  const out = []
  const used = new Set()
  for (const line of String(content).split(/\r?\n/)) {
    const trimmed = line.trim()
    if (trimmed && !trimmed.startsWith('#')) {
      const eq = trimmed.indexOf('=')
      if (eq > 0) {
        const key = trimmed.slice(0, eq).trim()
        if (key in values) {
          out.push(`${key}=${quoteHex(values[key])}`)
          used.add(key)
          continue
        }
      }
    }
    out.push(line)
  }
  const missing = []
  for (const key of Object.keys(values)) {
    if (!used.has(key)) {
      out.push(`${key}=${values[key]}`)
      missing.push(key)
    }
  }
  return { content: out.join('\n'), missing }
}

async function runBuild() {
  const cmd = process.platform === 'win32' ? 'npm.cmd --prefix frontend run build' : 'npm --prefix frontend run build'
  const { stdout, stderr } = await execAsync(cmd, {
    cwd: ROOT,
    maxBuffer: 64 * 1024 * 1024,
    windowsHide: true,
  })
  return (stdout + '\n' + stderr).trim()
}

let composeUpRunning = false

function spawnProcess(cmd, args, onOutput) {
  return new Promise((resolve, reject) => {
    const proc = spawn(cmd, args, { cwd: ROOT, windowsHide: true })
    proc.stdout.on('data', (d) => onOutput(d.toString()))
    proc.stderr.on('data', (d) => onOutput(d.toString()))
    proc.on('error', (err) => reject(err))
    proc.on('close', (code) => resolve(code ?? 0))
  })
}

// Levanta la infraestructura con docker compose (mismo comando que `make up`).
// Transmite la salida en directo via onOutput y devuelve el exit code.
async function runComposeUp(onOutput) {
  if (composeUpRunning) {
    throw new Error('Ya hay un arranque de infraestructura en curso')
  }
  composeUpRunning = true
  try {
    return await spawnProcess(
      'docker',
      ['compose', '--env-file', '.env', '-f', 'infra/docker-compose.yml', '--profile', 'apps', 'up', '-d', '--build'],
      onOutput,
    )
  } finally {
    composeUpRunning = false
  }
}

function sendJson(res, status, data) {
  const body = JSON.stringify(data)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  })
  res.end(body)
}

function readBody(req) {
  return new Promise((resolveBody, rejectBody) => {
    let raw = ''
    req.on('data', (chunk) => (raw += chunk))
    req.on('end', () => {
      try {
        resolveBody(raw ? JSON.parse(raw) : {})
      } catch {
        rejectBody(new Error('JSON invalido'))
      }
    })
    req.on('error', rejectBody)
  })
}

const UPLOAD_KEYS = new Set(['VITE_FAVICON_PATH', 'VITE_LOGO_PATH'])

function handleBrandUpload(body) {
  const key = body && body.key
  if (typeof key !== 'string' || !UPLOAD_KEYS.has(key)) {
    return { ok: false, status: 400, error: 'Campo no permitido' }
  }
  const spec = FIELD_SPEC.sections.flatMap((s) => s.fields).find((f) => f.key === key)
  const allow = (spec && spec.upload && spec.upload.accept) || ['.svg', '.png', '.ico']
  const maxBytes = (spec && spec.upload && spec.upload.maxBytes) || 1048576
  const name = String(body.name || '')
  const ext = ('.' + name.split('.').pop() || '').toLowerCase()
  if (!allow.includes(ext)) {
    return { ok: false, status: 400, error: 'Formato no permitido. Usa: ' + allow.join(', ') }
  }
  if (typeof body.data !== 'string' || !body.data) {
    return { ok: false, status: 400, error: 'Falta el contenido del archivo' }
  }
  let buf
  try {
    buf = Buffer.from(body.data, 'base64')
  } catch {
    return { ok: false, status: 400, error: 'Contenido inválido' }
  }
  if (!buf.length) return { ok: false, status: 400, error: 'El archivo está vacío' }
  if (buf.length > maxBytes) {
    return { ok: false, status: 400, error: 'El archivo supera ' + Math.round(maxBytes / 1024) + ' KB' }
  }
  const base = String(name.slice(0, name.lastIndexOf('.')) || '')
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 60)
  const fileName = (base || (key === 'VITE_FAVICON_PATH' ? 'favicon' : 'logo')) + ext
  mkdirSync(join(ROOT, 'marca'), { recursive: true })
  writeFileSync(join(ROOT, 'marca', fileName), buf)
  return { ok: true, path: './marca/' + fileName }
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`)
  const { pathname } = url

  try {
    if (req.method === 'GET' && pathname === '/api/spec') {
      return sendJson(res, 200, FIELD_SPEC)
    }

    if (req.method === 'GET' && pathname === '/api/env') {
      const { values, exists, source } = readEnvValues()
      return sendJson(res, 200, { values, exists, source: source.endsWith('.example') ? 'example' : 'env' })
    }

    if (req.method === 'POST' && pathname === '/api/save') {
      const body = await readBody(req)
      const raw = existsSync(ENV_PATH) ? readFileSync(ENV_PATH, 'utf8') : ''
      const current = raw ? readFileSync(ENV_PATH, 'utf8') : readFileSync(ENV_EXAMPLE_PATH, 'utf8')
      const incoming = {}
      for (const key of FIELD_KEYS) if (key in body.values) incoming[key] = String(body.values[key])
      const { content, missing } = mergeEnv(current, incoming)
      ensureEnvExists()
      writeFileSync(ENV_PATH, content)
      const warnings = []
      if (FIELD_SPEC.sections.flatMap((s) => s.fields).some((f) => f.key === 'JWT_SECRET' && (body.values.JWT_SECRET ?? '').length < 32)) {
        warnings.push('JWT_SECRET tiene menos de 32 caracteres (se recomienda una clave segura).')
      }
      return sendJson(res, 200, { ok: true, missing, warnings, note: missing.length ? `Se han añadido ${missing.length} variable(s) de "Marca" al final del fichero.` : undefined })
    }

    if (req.method === 'GET' && pathname === '/api/raw') {
      const path = existsSync(ENV_PATH) ? ENV_PATH : ENV_EXAMPLE_PATH
      return sendJson(res, 200, { content: readFileSync(path, 'utf8') })
    }

    if (req.method === 'POST' && pathname === '/api/raw') {
      const body = await readBody(req)
      if (typeof body.content !== 'string') return sendJson(res, 400, { ok: false, error: 'Falta el contenido' })
      ensureEnvExists()
      writeFileSync(ENV_PATH, body.content)
      return sendJson(res, 200, { ok: true })
    }

    if (req.method === 'POST' && pathname === '/api/reset') {
      if (!existsSync(ENV_EXAMPLE_PATH)) return sendJson(res, 500, { ok: false, error: 'No existe .env.example' })
      copyFileSync(ENV_EXAMPLE_PATH, ENV_PATH)
      return sendJson(res, 200, { ok: true })
    }

    if (req.method === 'POST' && pathname === '/api/upload-brand') {
      const upload = handleBrandUpload(await readBody(req))
      if (!upload.ok) return sendJson(res, upload.status, { ok: false, error: upload.error })
      return sendJson(res, 200, { ok: true, path: upload.path })
    }

    if (req.method === 'POST' && pathname === '/api/build') {
      try {
        const output = await runBuild()
        return sendJson(res, 200, { ok: true, output })
      } catch (err) {
        return sendJson(res, 200, { ok: false, error: String(err && err.message || err) })
      }
    }

    if (req.method === 'POST' && pathname === '/api/up') {
      if (!existsSync(ENV_PATH)) {
        return sendJson(res, 400, { ok: false, error: 'No existe .env. Guarda primero el .env.' })
      }
      res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' })
      try {
        const code = await runComposeUp((chunk) => res.write(chunk))
        res.write(`\n__EXIT_CODE__=${code}\n`)
      } catch (err) {
        res.write(`\nError al lanzar docker: ${String(err && err.message || err)}\n`)
        res.write('__EXIT_CODE__=1\n')
      }
      return res.end()
    }

    if (req.method === 'GET' && pathname === '/') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
      return res.end(PAGE)
    }

    if (req.method === 'GET' && pathname === '/favicon.svg') {
      res.writeHead(200, { 'Content-Type': 'image/svg+xml' })
      return res.end(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="16" fill="#6366f1"/><text x="32" y="42" font-family="Arial" font-size="30" font-weight="700" fill="#fff" text-anchor="middle">E</text></svg>',
      )
    }

    return sendJson(res, 404, { ok: false, error: 'No encontrado' })
  } catch (err) {
    return sendJson(res, 500, { ok: false, error: String(err && err.message || err) })
  }
})

server.listen(PORT, '127.0.0.1', () => {
  console.log('')
  console.log('  Configurador del .env del proyecto')
  console.log('  ──────────────────────────────────')
  console.log('  Abre esta dirección en tu navegador:')
  console.log('')
  console.log(`      http://localhost:${PORT}`)
  console.log('')
  console.log('  Solo escucha en 127.0.0.1. Pulsa Ctrl+C para salir.')
  console.log('')
  if (process.platform === 'win32') {
    try {
      execSync(`start "" "http://localhost:${PORT}"`, { stdio: 'ignore', windowsHide: true })
    } catch {
      /* abrir el navegador no es obligatorio */
    }
  }
})

const PAGE = `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<meta name="color-scheme" content="dark" />
<title>Configurador del proyecto</title>
<style>
  :root {
    --bg: #0f1220;
    --panel: #171b2e;
    --panel-2: #1d2238;
    --line: #2a3050;
    --text: #eef1ff;
    --muted: #9aa1c0;
    --brand-a: #6366f1;
    --brand-b: #a855f7;
    --brand-c: #ec4899;
    --ok: #34d399;
    --warn: #fbbf24;
    --err: #f87171;
    --radius: 14px;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    background:
      radial-gradient(800px 400px at 85% -10%, rgba(168,85,247,0.22), transparent 60%),
      radial-gradient(700px 400px at -10% 10%, rgba(99,102,241,0.22), transparent 60%),
      var(--bg);
    color: var(--text);
    font-family: 'Segoe UI', system-ui, -apple-system, Roboto, sans-serif;
    line-height: 1.5;
  }
  header {
    position: sticky; top: 0; z-index: 20;
    display: flex; align-items: center; gap: 14px;
    padding: 12px 22px;
    background: rgba(15,18,32,0.8);
    backdrop-filter: blur(10px);
    border-bottom: 1px solid var(--line);
  }
  .mark {
    width: 32px; height: 32px; border-radius: 10px;
    display: grid; place-items: center;
    background: linear-gradient(135deg, var(--brand-a), var(--brand-c));
    font-weight: 800; color: #fff;
    box-shadow: 0 6px 18px -6px rgba(168,85,247,0.7);
  }
  h1 { font-size: 17px; margin: 0; font-weight: 700; flex: 1; }
  .chip {
    font-size: 12px; padding: 4px 10px; border-radius: 999px;
    border: 1px solid var(--line); color: var(--muted);
    background: var(--panel-2);
  }
  .chip.env { color: var(--ok); border-color: rgba(52,211,153,0.4); }
  main { max-width: 760px; margin: 0 auto; padding: 26px 18px 120px; display: grid; gap: 16px; }
  section {
    background: var(--panel); border: 1px solid var(--line); border-radius: var(--radius);
    padding: 18px 20px;
  }
  section h2 { font-size: 14px; margin: 0 0 2px; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
  section .desc { margin: 0 0 14px; font-size: 13px; color: var(--muted); }
  .banner {
    background: linear-gradient(135deg, rgba(52, 211, 153, 0.14), rgba(99, 102, 241, 0.14));
    border: 1px solid rgba(52, 211, 153, 0.45);
    border-radius: var(--radius);
    padding: 14px 18px;
    font-size: 14px;
    color: var(--ok);
  }
  .banner strong { color: var(--text); }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 14px; }
  .field-wrap { display: flex; flex-direction: column; gap: 6px; }
  .field-wrap.full { grid-column: 1 / -1; }
  label { font-size: 13px; font-weight: 600; color: var(--text); }
  .hint { font-size: 12px; color: var(--muted); margin: 0; }
  input[type=text], input[type=number], input[type=password] {
    width: 100%; padding: 9px 12px; border-radius: 10px;
    border: 1px solid var(--line); background: var(--panel-2); color: var(--text);
    font-size: 14px; outline: none; transition: border-color 0.15s ease;
  }
  input:focus { border-color: var(--brand-a); }
  .input-row { display: flex; gap: 8px; align-items: center; }
  .input-row .grow { flex: 1; }
  input[type=color] {
    width: 100%; height: 40px; padding: 4px; border-radius: 10px;
    border: 1px solid var(--line); background: var(--panel-2); cursor: pointer;
  }
  .btn {
    cursor: pointer; border: 1px solid var(--line); border-radius: 10px;
    padding: 8px 14px; font-size: 13px; font-weight: 600; color: var(--text);
    background: var(--panel-2); transition: filter 0.15s ease, transform 0.1s ease;
  }
  .btn:hover { filter: brightness(1.12); }
  .btn:active { transform: translateY(1px); }
  .btn.primary {
    background: linear-gradient(135deg, var(--brand-a), var(--brand-b));
    border: none; color: #fff;
    box-shadow: 0 10px 24px -10px rgba(99,102,241,0.8);
  }
  .btn[disabled] { opacity: 0.55; cursor: default; }
  .btn.go {
    padding: 12px 24px; font-size: 15px;
    background: linear-gradient(135deg, var(--brand-a), var(--brand-b));
    border: none; color: #fff;
    box-shadow: 0 12px 28px -10px rgba(99,102,241,0.85);
  }
  .go-hint { color: var(--muted); font-size: 13px; }
  .go-hint.danger { color: var(--err); font-weight: 600; }
  .eye {
    border: 1px solid var(--line); border-radius: 10px; background: var(--panel-2);
    color: var(--muted); cursor: pointer; padding: 8px 10px; font-size: 13px; line-height: 1;
  }
  .preview {
    display: flex; flex-wrap: wrap; gap: 16px; align-items: center;
    background: var(--panel-2); border: 1px dashed var(--line);
    border-radius: 12px; padding: 14px 16px; margin-top: 14px;
  }
  .preview .name { font-weight: 800; font-size: 20px; letter-spacing: -0.02em; display: flex; align-items: center; gap: 10px; }
  .preview .name .dot { width: 30px; height: 30px; border-radius: 9px; display: inline-block; }
  .swatches { display: flex; flex-direction: column; gap: 4px; }
  .swatch { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--muted); font-variant-numeric: tabular-nums; }
  .swatch i { width: 18px; height: 18px; border-radius: 6px; border: 1px solid rgba(255,255,255,0.15); }
  .gradbar { height: 8px; border-radius: 999px; margin-top: 8px; }
  .toolbar {
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 30;
    display: flex; flex-wrap: wrap; gap: 10px; align-items: center;
    padding: 12px 22px;
    background: rgba(15,18,32,0.92); backdrop-filter: blur(10px);
    border-top: 1px solid var(--line);
  }
  .toolbar .spacer { flex: 1; }
  .toolbar a { color: var(--brand-a); font-size: 13px; }
  details { margin-top: 4px; }
  details summary { cursor: pointer; color: var(--muted); font-size: 13px; }
  textarea {
    width: 100%; min-height: 240px; margin-top: 10px;
    padding: 10px 12px; border-radius: 10px;
    border: 1px solid var(--line); background: var(--panel-2); color: var(--text);
    font-family: Consolas, 'Cascadia Mono', monospace; font-size: 12px; resize: vertical;
  }
  #toast {
    position: fixed; left: 50%; bottom: 84px; transform: translate(-50%, 20px);
    z-index: 50; opacity: 0; pointer-events: none; transition: opacity 0.2s ease, transform 0.2s ease;
    max-width: 90vw;
  }
  #toast.show { opacity: 1; transform: translate(-50%, 0); }
  #toast .box {
    background: var(--panel-2); border: 1px solid var(--line); border-radius: 12px;
    padding: 10px 16px; font-size: 13px; box-shadow: 0 18px 44px -18px rgba(0,0,0,0.6);
  }
  #toast .box.ok { border-color: rgba(52,211,153,0.5); color: var(--ok); }
  #toast .box.err { border-color: rgba(248,113,113,0.5); color: var(--err); }
  #modal {
    position: fixed; inset: 0; z-index: 40; display: none;
    align-items: center; justify-content: center; background: rgba(10,12,24,0.7);
    backdrop-filter: blur(4px); padding: 20px;
  }
  #modal.open { display: flex; }
  #modal .box {
    width: 100%; max-width: 680px; max-height: 80vh; overflow: auto;
    background: var(--panel); border: 1px solid var(--line); border-radius: 16px;
    padding: 18px 20px;
  }
  #modal pre {
    background: #0b0e1c; border: 1px solid var(--line); border-radius: 10px;
    padding: 12px; font-size: 11.5px; line-height: 1.45; overflow: auto;
    white-space: pre-wrap; word-break: break-word; color: #c8d0f0;
  }
  .spin { display: inline-block; width: 14px; height: 14px; border: 2px solid var(--line); border-top-color: var(--brand-a); border-radius: 50%; animation: sp 0.8s linear infinite; vertical-align: -2px; }
  @keyframes sp { to { transform: rotate(360deg); } }
  footer { color: var(--muted); font-size: 12px; text-align: center; padding: 6px 0 2px; }
  .danger { border-color: rgba(248,113,113,0.5) !important; color: var(--err) !important; }
</style>
</head>
<body>
<header>
  <span class="mark">E</span>
  <h1>Configurador del proyecto</h1>
  <span id="src" class="chip">…</span>
</header>

<main>
  <div id="setup-banner" class="banner" hidden>
    No hay <strong>.env</strong> todavía. Rellena los campos obligatorios y pulsa
    <strong>«Ir a la web»</strong> para crear el .env, construir el frontend, arrancar todo y abrirla.
  </div>
  <div id="sections"></div>
</main>

<div class="toolbar">
  <button id="btn-go" class="btn go" hidden>Ir a la web →</button>
  <span id="go-hint" class="go-hint"></span>
  <span class="spacer"></span>
  <button id="btn-save" class="btn" disabled>Guardar</button>
  <button id="btn-reset" class="btn">Regenerar desde ejemplo</button>
  <a href="https://localhost" target="_blank" rel="noreferrer">Abrir la app →</a>
</div>

<div id="toast"><div class="box"></div></div>
<div id="modal"><div class="box" id="modal-box"></div></div>

<script>
(function () {
  const $ = (sel) => document.querySelector(sel)
  const root = $('#root')
  const toast = $('#toast')
  let spec = null
  let values = {}
  let saved = JSON.stringify({})
  let busy = false

  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
  }
  function isHexColor(v) { return /^#[0-9a-f]{6}$/i.test(String(v)) }
  function toastMsg(msg, type) {
    const box = toast.firstElementChild
    box.className = 'box ' + (type || 'ok')
    box.textContent = msg
    toast.classList.add('show')
    clearTimeout(toastMsg._t)
    toastMsg._t = setTimeout(() => toast.classList.remove('show'), 3200)
  }

  function svgFavicon(colors, letter) {
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="' + colors[0] + '"/><stop offset="1" stop-color="' + colors[1] + '"/></linearGradient></defs><rect width="64" height="64" rx="16" fill="url(#g)"/><text x="32" y="42" font-family="Arial" font-size="28" font-weight="700" fill="#fff" text-anchor="middle">' + esc(letter) + '</text></svg>'
    return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg)
  }

  function initialsOf(name) {
    const w = String(name || '').trim().split(/\\s+/).filter(Boolean)
    if (!w.length) return 'E'
    if (w.length === 1) return w[0][0].toUpperCase()
    return (w[0][0] + w[1][0]).toUpperCase()
  }

  function readFileAsDataURL(file) {
    return new Promise((resolveP, rejectP) => {
      const r = new FileReader()
      r.onload = () => {
        const s = String(r.result)
        const i = s.indexOf(';base64,')
        resolveP(i === -1 ? '' : s.slice(i + 8))
      }
      r.onerror = () => rejectP(new Error('No se pudo leer el archivo'))
      r.readAsDataURL(file)
    })
  }

  function buildPreview() {
    const p = ['VITE_PRIMARY_COLOR', 'VITE_SECONDARY_COLOR', 'VITE_ACCENT_COLOR'].map((k) => values[k])
    const d = ['VITE_PRIMARY_COLOR_DARK', 'VITE_SECONDARY_COLOR_DARK', 'VITE_ACCENT_COLOR_DARK'].map((k) => values[k])
    if (!p.every(isHexColor) && !d.every(isHexColor)) return
    const name = values.VITE_APP_NAME || 'Mi Tienda'
    const hex = (c) => isHexColor(c) ? c : '#6366f1'
    const fav = svgFavicon([hex(p[0]), hex(p[1])], initialsOf(name))
    const grad = 'linear-gradient(135deg, ' + hex(p[0]) + ', ' + hex(p[2]) + ')'
    const gradD = 'linear-gradient(135deg, ' + hex(d[0]) + ', ' + hex(d[2]) + ')'
    const pills = [p, d]
    let old = document.getElementById('brand-preview')
    if (old) old.remove()
    const swatchRow = function (arr, label) {
      return '<span class="swatch" style="margin-right:6px">' + arr.map(function (c) { return '<i style="background:' + hex(c) + '"></i>' }).join('') + ' ' + label + '</span>'
    }
    const div = document.createElement('div')
    div.className = 'preview'
    div.id = 'brand-preview'
    div.innerHTML =
      '<span class="name"><span class="dot" style="background:' + grad + '"></span>' + esc(name) + '</span>' +
      '<span class="swatches">' + swatchRow(p, 'claro') + '<br>' + swatchRow(d, 'oscuro') + '</span>' +
      '<span style="flex:1"></span><img width="40" height="40" alt="favicon" src="' + fav + '" />'
    document.getElementById('brand-right').insertAdjacentElement('afterend', div)
  }

  function fieldInput(f) {
    const wrap = document.createElement('div')
    wrap.className = 'field-wrap' + (f.type === 'color' ? '' : '')
    const lab = document.createElement('label')
    lab.textContent = f.label
    lab.htmlFor = 'f-' + f.key
    wrap.appendChild(lab)

    if (f.type === 'color') {
      const input = document.createElement('input')
      input.type = 'color'
      input.id = 'f-' + f.key
      input.value = isHexColor(values[f.key]) ? values[f.key] : '#6366f1'
      input.addEventListener('input', () => { values[f.key] = input.value; markDirty(); buildPreview() })
      wrap.appendChild(input)
      return wrap
    }

    const row = document.createElement('div')
    row.className = 'input-row'
    const input = document.createElement('input')
    input.type = f.type === 'password' ? 'password' : (f.type || 'text')
    input.id = 'f-' + f.key
    input.value = values[f.key] || ''
    if (f.placeholder) input.placeholder = f.placeholder
    input.classList.add('grow')
    input.addEventListener('input', () => { values[f.key] = input.value; markDirty(); buildPreview(); validateField(f, input) })
    row.appendChild(input)

    if (f.type === 'password') {
      const eye = document.createElement('button')
      eye.type = 'button'
      eye.className = 'eye'
      eye.textContent = '👁'
      eye.title = 'Mostrar / ocultar'
      eye.addEventListener('click', () => {
        input.type = input.type === 'password' ? 'text' : 'password'
        eye.textContent = input.type === 'password' ? '👁' : '🙈'
      })
      row.appendChild(eye)
      if (f.generate) {
        const gen = document.createElement('button')
        gen.type = 'button'
        gen.className = 'btn'
        gen.textContent = 'Generar'
        gen.addEventListener('click', () => {
          const arr = new Uint8Array(48)
          crypto.getRandomValues(arr)
          input.value = Array.from(arr, (b) => b.toString(16).padStart(2, '0')).join('')
          values[f.key] = input.value
          validateField(f, input)
          markDirty()
        })
        row.appendChild(gen)
      }
    }
    wrap.appendChild(row)

    if (f.upload) {
      const up = document.createElement('div')
      up.className = 'input-row'
      up.style.marginTop = '8px'
      const fileInput = document.createElement('input')
      fileInput.type = 'file'
      fileInput.accept = (f.upload.accept || []).join(',')
      fileInput.style.display = 'none'
      const upBtn = document.createElement('button')
      upBtn.type = 'button'
      upBtn.className = 'btn'
      upBtn.textContent = 'Cargar archivo…'
      upBtn.addEventListener('click', () => fileInput.click())
      fileInput.addEventListener('change', async () => {
        const file = fileInput.files && fileInput.files[0]
        fileInput.value = ''
        if (!file) return
        if (f.upload.maxBytes && file.size > f.upload.maxBytes) {
          return toastMsg('El archivo supera ' + Math.round(f.upload.maxBytes / 1024) + ' KB', 'err')
        }
        try {
          const b64 = await readFileAsDataURL(file)
          const resp = await fetch('/api/upload-brand', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ key: f.key, name: file.name, data: b64 }),
          })
          const data = await resp.json()
          if (!data.ok) return toastMsg('Error: ' + (data.error || resp.status), 'err')
          input.value = data.path
          values[f.key] = data.path
          markDirty()
          toastMsg('Copiado a ' + data.path + '. Pulsa Guardar para actualizar .env.', 'ok')
        } catch (e) {
          toastMsg('Error al subir: ' + String(e && e.message || e), 'err')
        }
      })
      up.appendChild(upBtn)
      up.appendChild(fileInput)
      wrap.appendChild(up)
    }

    if (f.hint) {
      const h = document.createElement('p')
      h.className = 'hint'
      h.textContent = f.hint
      wrap.appendChild(h)
    }
    return wrap
  }

  function validateField(f, input) {
    const v = String(values[f.key] ?? '')
    const bad = (f.required && !v) || (f.validate && f.validate.min && v.length < f.validate.min)
    if (bad) {
      input.classList.add('danger')
      input.title = f.required && !v ? 'Obligatorio' : 'Mínimo ' + f.validate.min + ' caracteres'
    } else {
      input.classList.remove('danger')
      input.title = ''
    }
  }

  function missingFields() {
    const missing = []
    for (const f of spec.sections.flatMap((s) => s.fields)) {
      const v = String(values[f.key] ?? '')
      if (f.required && !v) missing.push(f.label)
      else if (f.validate && f.validate.min && v.length < f.validate.min) missing.push(f.label + ' (mín. ' + f.validate.min + ')')
    }
    return missing
  }

  function updateGoButton() {
    const miss = missingFields()
    const go = $('#btn-go')
    const hint = $('#go-hint')
    if (miss.length) {
      go.hidden = true
      hint.textContent = 'Falta por rellenar: ' + miss.slice(0, 3).join(', ') + (miss.length > 3 ? '…' : '')
      hint.classList.add('danger')
    } else {
      go.hidden = false
      hint.textContent = ''
      hint.classList.remove('danger')
    }
  }

  function markDirty() {
    const dirty = JSON.stringify(values) !== saved
    $('#btn-save').disabled = !dirty
    updateGoButton()
  }

  function setBusy(b) {
    busy = b
    ;['btn-save', 'btn-reset', 'btn-go', 'raw-load', 'raw-save'].forEach((id) => {
      const el = document.getElementById(id)
      if (el) el.disabled = b || (id === 'btn-save' && JSON.stringify(values) === saved)
    })
  }

  function renderSections() {
    const container = document.getElementById('sections')
    container.innerHTML = ''
    spec.sections.forEach((section) => {
      const sec = document.createElement('section')
      const h = document.createElement('h2')
      h.textContent = section.title
      sec.appendChild(h)
      if (section.description) {
        const d = document.createElement('p')
        d.className = 'desc'
        d.textContent = section.description
        sec.appendChild(d)
      }
      const grid = document.createElement('div')
      grid.className = 'grid'
      if (section.id === 'brand') {
        section.fields.slice(0, 1).forEach((f) => { grid.appendChild(fieldInput(f)) })
        const right = document.createElement('div')
        right.className = 'field-wrap'
        right.id = 'brand-right'
        grid.appendChild(right)
        section.fields.slice(1).forEach((f) => { grid.appendChild(fieldInput(f)) })
      } else {
        section.fields.forEach((f) => { grid.appendChild(fieldInput(f)) })
      }
      sec.appendChild(grid)
      container.appendChild(sec)
    })
    buildPreview()
  }

  async function load() {
    const res = await fetch('/api/env')
    const data = await res.json()
    values = Object.assign({}, data.values)
    saved = JSON.stringify(values)
    const src = $('#src')
    src.className = 'chip ' + (data.exists ? 'env' : '')
    src.textContent = data.exists ? 'Edita tu .env' : '.env.example (crea .env al guardar)'
    $('#setup-banner').hidden = data.exists !== false
    spec = await (await fetch('/api/spec')).json()
    renderSections()
    markDirty()
    const raw = await (await fetch('/api/raw')).json()
    $('#raw').value = raw.content
  }

  async function save() {
    $('#btn-save').disabled = true
    const res = await fetch('/api/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ values }),
    })
    const data = await res.json()
    if (!data.ok) return toastMsg('Error: ' + data.error, 'err')
    saved = JSON.stringify(values)
    $('#setup-banner').hidden = true
    markDirty()
    let msg = 'Guardado en .env'
    if (data.missing && data.missing.length) msg += ' · añadidas: ' + data.missing.join(', ')
    toastMsg(msg, 'ok')
    const src = $('#src')
    src.className = 'chip env'
    src.textContent = 'Edita tu .env'
    if (data.warnings && data.warnings.length) setTimeout(() => toastMsg(data.warnings[0], 'err'), 900)
  }

  async function streamTo(pre, res) {
    if (!res || !res.body) {
      pre.textContent = res ? await res.text() : 'Sin respuesta'
      return { ok: false, code: 1 }
    }
    const reader = res.body.getReader()
    const dec = new TextDecoder()
    let buffer = ''
    let exitCode = null
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += dec.decode(value, { stream: true })
      const idx = buffer.lastIndexOf('__EXIT_CODE__=')
      if (idx !== -1) {
        const m = buffer.slice(idx + 14).match(/^(\\d+)/)
        exitCode = m ? Number(m[1]) : null
        buffer = buffer.slice(0, idx)
      }
      pre.textContent = buffer
      pre.scrollTop = pre.scrollHeight
    }
    pre.textContent = buffer
    return { ok: exitCode === 0, code: exitCode }
  }

  async function goToWeb() {
    if (busy) return
    const miss = missingFields()
    if (miss.length) return toastMsg('Falta por rellenar: ' + miss.join(', '), 'err')
    const tab = window.open('', '_blank')
    const box = $('#modal-box')
    box.innerHTML = '<h3 style="margin-top:0">Preparando tu web…</h3><pre id="upout" style="height:260px"></pre>'
    $('#modal').classList.add('open')
    setBusy(true)
    try {
      $('#upout').textContent = '1/3  Guardando el .env…\\n'
      const saveRes = await fetch('/api/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ values }),
      })
      const saveData = await saveRes.json()
      if (!saveData.ok) throw new Error(saveData.error || ('HTTP ' + saveRes.status))
      saved = JSON.stringify(values)
      $('#setup-banner').hidden = true
      markDirty()

      $('#upout').textContent = '2/3  Reconstruyendo el frontend (sync-brand + vite build)…\\n'
      const bRes = await fetch('/api/build', { method: 'POST' })
      const bData = await bRes.json()
      if (!(bData.ok && !bData.error)) throw new Error(bData.error || ('Build fallido (HTTP ' + bRes.status + ')'))
      $('#upout').textContent = (bData.output || 'Build completado') + '\\n'

      $('#upout').textContent += '3/3  Levantando la infraestructura…\\n'
      const upResp = await fetch('/api/up', { method: 'POST' })
      if (!upResp.ok) {
        const err = await upResp.json().catch(() => ({}))
        throw new Error(err.error || ('HTTP ' + upResp.status))
      }
      const out = await streamTo($('#upout'), upResp)
      if (!out.ok) throw new Error('El arranque falló (exit code ' + out.code + '). Revisa el log.')

      if (tab) tab.location = 'https://localhost'
      $('#upout').textContent += '\\n¡Todo listo! Abierta https://localhost'
      toastMsg('Listo. Tu web está abierta (Ctrl+F5 si estaba cargada).', 'ok')
      setTimeout(() => $('#modal').classList.remove('open'), 2600)
    } catch (e) {
      if (tab) tab.close()
      $('#upout').textContent = 'ERROR: ' + String(e && e.message || e)
      toastMsg('Fallo: ' + String(e && e.message || e), 'err')
    } finally {
      setBusy(false)
    }
  }

  async function reset() {
    if (!confirm('¿Reemplazar .env por los valores de .env.example? Perderás los cambios de marca.')) return
    await fetch('/api/reset', { method: 'POST' })
    toastMsg('.env regenerado desde .env.example', 'ok')
    load()
  }

  document.addEventListener('DOMContentLoaded', () => {
    $('#btn-go').addEventListener('click', goToWeb)
    $('#btn-save').addEventListener('click', save)
    $('#btn-reset').addEventListener('click', reset)
    const rawList = document.createElement('details')
    rawList.innerHTML = '<summary>Edición avanzada: ver y editar el .env como texto</summary>' +
      '<textarea id="raw" spellcheck="false"></textarea>' +
      '<div style="display:flex;gap:8px;margin-top:8px"><button id="raw-load" class="btn">Recargar desde disco</button><button id="raw-save" class="btn">Guardar crudo</button></div>'
    document.querySelector('main').appendChild(rawList)
    $('#raw-load').addEventListener('click', async () => {
      const r = await (await fetch('/api/raw')).json()
      $('#raw').value = r.content
    })
    $('#raw-save').addEventListener('click', async () => {
      await fetch('/api/raw', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content: $('#raw').value }) })
      toastMsg('Crudo guardado', 'ok')
      load()
    })
    $('#modal').addEventListener('click', (e) => {
      if (e.target === $('#modal')) $('#modal').classList.remove('open')
    })
  })

  load()
})()
</script>
<footer>Herramienta local (solo 127.0.0.1). No expongas nunca el .env con las claves.</footer>
</body>
</html>
`

server.on('error', (err) => {
  console.error('No se pudo iniciar en el puerto ' + PORT + ':', err.message)
  console.error('Si ya está corriendo, abre http://localhost:' + PORT + ' o lánzalo con otro puerto: ENV_EDITOR_PORT=4700 node scripts/env-editor.mjs')
  process.exit(1)
})