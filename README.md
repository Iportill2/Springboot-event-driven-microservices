# Spring Boot Event-Driven Microservices

Plataforma de comercio electrónico distribuida basada en eventos (Spring Boot, Spring Cloud Gateway, Kafka, Redis, PostgreSQL, Docker). Proyecto de aprendizaje/portfolio.

## Quick Start

```bash
git clone git@github.com:Iportill2/Springboot-event-driven-microservices.git
cd Springboot-event-driven-microservices

cp .env.example .env          # Windows PowerShell: Copy-Item .env.example .env
make up                       # infra + microservicios + nginx
```

`make up` construye las imágenes, levanta el stack y deja la app en **https://localhost** (certificado autofirmado: acepta el aviso del navegador). Login: `admin` / `admin123`.

No tienes `make` (Windows: `winget install ezwinports.make`) o prefieres Docker directo:

```bash
docker compose --env-file .env -f infra/docker-compose.yml --profile apps up --build
```

<details>
<summary>Arranque paso a paso, perfiles opcionales y desarrollo sin Docker</summary>

## Requisitos

```
Cliente / SPA
      │
      ▼
   Nginx (reverse proxy)
      │
      ▼
Spring Cloud Gateway  ── JWT, rate limiting (Redis)
      │
 ┌────┼───────────┐
 ▼    ▼           ▼
User Product    AI
8081  8082      8084
 │      │
 ▼      ▼
PostgreSQL / Redis
 │
 ▼
Kafka (eventos de negocio)
```

- **User Service**: registro, login, usuarios, roles (`USER`, `ADMIN`). Emite `UserRegistered`.
- **Product Service**: CRUD de catálogo con caché Redis. Emite `ProductCreated`.
- **AI Service**: abstracción de proveedores de IA (Ollama / API externa).
- **API Gateway**: punto de entrada único, validación JWT, rate limiting.
- **Nginx**: reverse proxy delante del Gateway.

## Requisitos

- **Docker** + **Docker Compose**
- **GNU Make** (obligatorio para los atajos `make`; en Windows: `winget install ezwinports.make`)
- **JDK 21** y **Maven 3.9+** (solo para desarrollo local sin Docker)

> Tras instalar `make` en Windows, abre una consola nueva para que tome el PATH. El `Makefile` funciona igual en PowerShell/`cmd.exe`, Git Bash y Linux.

## Configuración

Toda la configuración y credenciales viven en un fichero `.env` en la raíz (no se sube al repositorio).

- **Primer arranque (recomendado):** si no existe `.env`, `make up` / `dc.ps1 up` abren un **asistente web** en `http://localhost:4600` con una interfaz gráfica: campos agrupados (marca, PostgreSQL, JWT, admin, Grafana, rate limiting), generador de clave JWT, vista en crudo y vista previa de la marca. El botón **«Ir a la web»** (visible cuando el formulario está completo) crea el `.env`, reconstruye el frontend (`sync-brand` + `vite build`), lanza `docker compose up --build` mostrando el progreso y abre la aplicación.
- **Alternativa manual** (crea el fichero desde la plantilla):

```powershell
Copy-Item .env.example .env
```

Ajusta los valores si lo necesitas. No hay secretos hardcodeados en el código ni en `docker-compose.yml`.

## Levantar el proyecto

**Solo infraestructura** (PostgreSQL, Redis, Kafka, Prometheus, Grafana):

```powershell
docker compose --env-file .env -f infra/docker-compose.yml up -d
```

**Infraestructura + microservicios** (gateway, user, product, ai, nginx):

```powershell
docker compose --env-file .env -f infra/docker-compose.yml --profile apps up --build
```

**Perfiles opcionales**:

```powershell
# MongoDB + RabbitMQ (se usarán para el Chat Service y tareas async)
docker compose --env-file .env -f infra/docker-compose.yml --profile async up -d

# Ollama (IA local)
docker compose --env-file .env -f infra/docker-compose.yml --profile ai up -d
```

> El script de inicialización de PostgreSQL solo se ejecuta la primera vez que se crea el volumen. Si cambias las credenciales o los nombres de las bases de datos, recrea el volumen:
> `docker compose --env-file .env -f infra/docker-compose.yml down -v`

## Atajos

Existen dos formas equivalentes de gestionar los contenedores: un `Makefile` (portable, útil para CI/Linux) y `scripts/dc.ps1` (Windows, sin dependencias).

> **Sin `.env`:** los comandos que arrancan la pila (`up`, `infra`, `async`, `ai`) no lo crean automáticamente: abren el asistente en `http://localhost:4600` y ceden el control. Después de configurarlo (o de crearlo a mano), si vuelves a ejecutarlos levantan sin problema.

**Con Make** (requiere GNU Make; funciona en PowerShell, Git Bash y Linux):

```powershell
make            # lista de comandos
make up         # infraestructura + microservicios (--build)
make infra      # solo infraestructura
make async      # MongoDB + RabbitMQ
make ai         # Ollama
make ps         # estado
make logs SVC=gateway
make verify     # valida docker-compose
make down       # baja todo (conserva volúmenes)
make down-v     # baja todo y borra volúmenes
make clean      # down -v + huérfanos + imágenes locales
```

**Con PowerShell** (misma semántica):

```powershell
.\scripts\dc.ps1 up
.\scripts\dc.ps1 down -Volumes
.\scripts\dc.ps1 logs -Service gateway
.\scripts\dc.ps1 verify
.\scripts\dc.ps1 help
```

## Desarrollo local (sin Docker para los servicios)

Con la infraestructura levantada, arranca cada servicio con el script que carga el `.env`:

```powershell
.\scripts\dev.ps1 user
.\scripts\dev.ps1 product
.\scripts\dev.ps1 gateway
.\scripts\dev.ps1 ai
```

## Frontend (Vue 3 + TypeScript + Vite)

SPA en `frontend/` (login + catálogo). Hay dos formas de acceder a ella:

**Serving por Nginx (HTTPS, producción)** — con la pila levantada y el frontend construido, la app se sirve en `https://localhost`:

```powershell
make up         # infraestructura + microservicios + nginx (HTTPS)
make frontend   # construye frontend/dist  (cd frontend && npm run build)
```

**Dev server de Vite (hot reload)** — Vite reenvía `/api` al API Gateway por proxy (misma origen, sin CORS):

```powershell
cd frontend
npm install
npm run dev      # http://localhost:5173  (login: admin / admin123)
```

- **Login**: `POST /api/users/login`, guarda el token JWT y el usuario en `localStorage` (`src/api/client.ts`).
- **Catálogo**: `GET /api/products`, se llama con el token (`Authorization: Bearer ...`).
- **Protección de rutas**: `src/router/index.ts` redirige a `/login` si no hay sesión.

## Endpoints y puertos

| Componente | URL |
|---|---|
| API Gateway | http://localhost:8080 |
| User Service | http://localhost:8081 |
| Product Service | http://localhost:8082 |
| AI Service | http://localhost:8084 |
| Nginx | https://localhost (http://localhost:80 redirige a :443) |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |
| RabbitMQ Management | http://localhost:15672 |

Credenciales por defecto (definidas en `.env`): admin `admin / admin123`, Grafana `admin / admin`.

> **HTTPS**: el certificado es autofirmado (`CN=localhost`) y se genera automáticamente la primera vez que se levanta el stack (contenedor `ssl-init` → volumen `ssl-certs`). El navegador mostrará un aviso de seguridad que debes aceptar; en producción se usarán certificados de una CA.

## Ejemplo de uso

```powershell
$base = "http://localhost:8080"

# Login del admin creado al arrancar el User Service
$auth = Invoke-RestMethod -Method Post -Uri "$base/api/users/login" `
  -ContentType application/json -Body (@{username="admin"; password="admin123"} | ConvertTo-Json)
$h = @{ Authorization = "Bearer $($auth.data.token)" }

# Catálogo (público)
Invoke-RestMethod -Method Get -Uri "$base/api/products"

# Crear producto (requiere ADMIN)
Invoke-RestMethod -Method Post -Uri "$base/api/products" -Headers $h `
  -ContentType application/json `
  -Body (@{name="Laptop"; sku="SKU-1"; description="Portátil"; price=999.99} | ConvertTo-Json)
```

## Estructura del repositorio

```
.
├── .env.example          # Plantilla de configuración
├── Makefile              # Atajos de contenedores (make up/down/...)
├── infra/                # docker-compose, postgres, nginx, prometheus, grafana, ssl-init
├── scripts/              # Utilidades de desarrollo (dev.ps1, dc.ps1)
├── services/             # Microservicios (gateway, user, product, ai)
├── shared/common/        # Código compartido (seguridad, eventos, API)
├── frontend/             # SPA Vue 3 + TypeScript (login, catálogo)
└── pom.xml               # Maven multi-módulo
```

## CI/CD (GitHub Actions)

Cada microservicio tiene su propio workflow que **solo se dispara cuando cambia su código o algo que le afecta** (`shared/common`, `pom.xml`, `services/Dockerfile`). Gracias a `mvn -pl services/<svc> -am` solo se compila el servicio afectado y sus dependencias.

| Workflow | Qué valida |
|---|---|
| `gateway.yml` / `user.yml` / `product.yml` / `ai.yml` | `mvn ... package` (compila + tests) y construye la imagen Docker (sin push) |
| `frontend.yml` | `npm ci` + `npm run build` (typecheck de la SPA Vue) |
| `compose-verify.yml` | `docker compose config --quiet` sobre `infra/docker-compose.yml` |

Los 4 pipelines de servicio reutilizan `service-pipeline.yml` (`workflow_call`). Si cambias `shared/common` o el `pom.xml` raíz, GitHub relanza los 4.

> Los workflows se ejecutan al hacer `git push` a la rama `main` (o en una Pull Request) **después de subir el repo a GitHub**. Si el repo es nuevo, revisa la pestaña **Actions** por si pide aceptar la ejecución.

## Roadmap

- [ ] Order Service (pedidos) + eventos `OrderCreated` / `OrderPaid` / `OrderCancelled`
- [ ] Inventory Service (stock) + `StockUpdated`
- [ ] Notification Service (RabbitMQ)
- [ ] Chat Service (WebSockets + MongoDB)
- [ ] AI Service con Ollama
- [ ] Analytics Service
- [x] Frontend Vue 3 + TypeScript + Vite (login + catálogo)
- [x] Nginx HTTPS (certificado autofirmado de desarrollo)
- [x] Pipeline CI/CD (GitHub Actions: CI por servicio + frontend + compose verify)

</details>
