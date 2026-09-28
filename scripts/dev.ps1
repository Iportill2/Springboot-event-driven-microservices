# Arranca un microservicio en local (Maven) cargando la configuración de .env.
#
# Requisitos: infraestructura levantada con Docker
#   docker compose --env-file .env -f infra/docker-compose.yml up -d
#
# Uso:
#   .\scripts\dev.ps1 user
#   .\scripts\dev.ps1 product
#   .\scripts\dev.ps1 gateway
#   .\scripts\dev.ps1 ai

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('user', 'product', 'gateway', 'ai')]
    [string]$Service
)

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "No se encontro $envFile. Copia .env.example a .env y ajusta los valores."
}

$config = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -gt 0 -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $index = $line.IndexOf('=')
        $key = $line.Substring(0, $index).Trim()
        $value = $line.Substring($index + 1).Trim()
        $config[$key] = $value
    }
}

foreach ($key in $config.Keys) {
    [Environment]::SetEnvironmentVariable($key, $config[$key], 'Process')
}

switch ($Service) {
    'user' {
        $env:DB_HOST = 'localhost'
        $env:DB_PORT = '5432'
        $env:DB_NAME = $config['USER_DB']
        $env:DB_USER = $config['USER_DB_USER']
        $env:DB_PASSWORD = $config['USER_DB_PASSWORD']
        $env:KAFKA_BOOTSTRAP_SERVERS = 'localhost:29092'
    }
    'product' {
        $env:DB_HOST = 'localhost'
        $env:DB_PORT = '5432'
        $env:DB_NAME = $config['PRODUCT_DB']
        $env:DB_USER = $config['PRODUCT_DB_USER']
        $env:DB_PASSWORD = $config['PRODUCT_DB_PASSWORD']
        $env:REDIS_HOST = 'localhost'
        $env:REDIS_PORT = '6379'
        $env:KAFKA_BOOTSTRAP_SERVERS = 'localhost:29092'
    }
    'gateway' {
        $env:REDIS_HOST = 'localhost'
        $env:REDIS_PORT = '6379'
        $env:USER_SERVICE_URI = 'http://localhost:8081'
        $env:PRODUCT_SERVICE_URI = 'http://localhost:8082'
        $env:AI_SERVICE_URI = 'http://localhost:8084'
    }
    'ai' {
        $env:OLLAMA_BASE_URL = 'http://localhost:11434'
    }
}

Write-Host "Arrancando $Service con la configuracion de .env" -ForegroundColor Cyan
mvn -f (Join-Path $root 'pom.xml') -pl "services/$Service" -am spring-boot:run
