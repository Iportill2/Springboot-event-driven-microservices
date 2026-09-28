# Atajos para gestionar los contenedores (equivalente al Makefile, sin dependencias).
#
# Uso:
#   .\scripts\dc.ps1 up
#   .\scripts\dc.ps1 down -Volumes
#   .\scripts\dc.ps1 logs -Service gateway
#   .\scripts\dc.ps1 verify

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('help', 'up', 'infra', 'async', 'ai', 'down', 'stop', 'start', 'restart', 'ps', 'logs', 'build', 'verify', 'clean')]
    [string]$Command = 'help',

    [string]$Service,

    [switch]$Volumes
)

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
$composeFile = Join-Path $root 'infra/docker-compose.yml'

$base = @('compose', '--env-file', $envFile, '-f', $composeFile)
$appsProfile = @('--profile', 'apps')
$asyncProfile = @('--profile', 'async')
$aiProfile = @('--profile', 'ai')
$allProfiles = $appsProfile + $asyncProfile + $aiProfile

function Ensure-Env {
    if (-not (Test-Path -LiteralPath $envFile)) {
        Write-Host "No hay .env. Abriendo el asistente de configuracion en http://localhost:4600 ..." -ForegroundColor Yellow
        Start-Process -FilePath 'node' -ArgumentList 'scripts/env-editor.mjs' -WorkingDirectory $root
        Write-Host ""
        Write-Host "Configura el .env en el navegador y pulsa «Ir a la web». El asistente crea el .env," -ForegroundColor Cyan
        Write-Host "construye el frontend, arranca la infraestructura y abre la aplicacion." -ForegroundColor Cyan
        Write-Host "El arranque lo lanza el asistente. Alternativa rapida: Copy-Item .env.example .env; y repite el comando."
        exit 1
    }
}

function Invoke-Compose {
    param([string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

switch ($Command) {
    'help' {
        @'
Uso: .\scripts\dc.ps1 <comando> [opciones]

Comandos:
  up            Levanta infraestructura + microservicios (--build)
  infra         Solo infraestructura
  async         MongoDB + RabbitMQ
  ai            Ollama
  down          Para y elimina contenedores (conserva volumenes)
  stop          Para contenedores sin eliminarlos
  start         Arranca contenedores ya creados
  restart       Reinicia contenedores
  ps            Estado de contenedores
  logs          Logs en directo (todos o -Service <nombre>)
  build         Reconstruye imagenes de microservicios
  verify        Valida docker-compose
  clean         down -v + huerfanos + imagenes locales

Opciones:
  -Service <nombre>   Servicio para 'logs'
  -Volumes            En 'down'/'clean', borra tambien los volumenes

Sin .env: up/infra/async/ai abren el asistente web (http://localhost:4600)
para configurarlo; su boton «Ir a la web» crea el .env, construye el frontend,
levanta la infraestructura y abre la aplicacion.
'@ | Write-Host
    }
    'up' {
        Ensure-Env
        Invoke-Compose ($base + $appsProfile + @('up', '-d', '--build'))
    }
    'infra' {
        Ensure-Env
        Invoke-Compose ($base + @('up', '-d'))
    }
    'async' {
        Ensure-Env
        Invoke-Compose ($base + $asyncProfile + @('up', '-d'))
    }
    'ai' {
        Ensure-Env
        Invoke-Compose ($base + $aiProfile + @('up', '-d'))
    }
    'down' {
        $extra = if ($Volumes) { @('-v') } else { @() }
        Invoke-Compose ($base + $allProfiles + @('down') + $extra)
    }
    'stop' { Invoke-Compose ($base + $allProfiles + @('stop')) }
    'start' { Invoke-Compose ($base + $allProfiles + @('start')) }
    'restart' { Invoke-Compose ($base + $allProfiles + @('restart')) }
    'ps' { Invoke-Compose ($base + $allProfiles + @('ps')) }
    'logs' {
        $logArgs = $base + @('logs', '-f')
        if ($Service) { $logArgs += $Service }
        Invoke-Compose $logArgs
    }
    'build' { Invoke-Compose ($base + $appsProfile + @('build')) }
    'verify' {
        Invoke-Compose ($base + $allProfiles + @('config', '--quiet'))
        Write-Host 'docker-compose OK' -ForegroundColor Green
    }
    'clean' {
        Invoke-Compose ($base + $allProfiles + @('down', '-v', '--remove-orphans', '--rmi', 'local'))
    }
}
