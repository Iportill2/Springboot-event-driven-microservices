# Atajos para gestionar los contenedores del proyecto.
#
# Uso:  make <target>
#
# Compatible con cmd.exe / PowerShell y con sh POSIX (Linux, macOS, Git Bash).
#
# Variables sobreescribibles:
#   FILE=infra/docker-compose.yml   ENV_FILE=.env   ENV_EXAMPLE=.env.example   SVC=gateway

COMPOSE     ?= docker compose
ENV_FILE    ?= .env
ENV_EXAMPLE ?= .env.example
FILE        ?= infra/docker-compose.yml

APPS  := --profile apps
ASYNC := --profile async
AI    := --profile ai
ALL   := $(APPS) $(ASYNC) $(AI)

BASE := $(COMPOSE) --env-file $(ENV_FILE) -f $(FILE)
SVC  ?=

.DEFAULT_GOAL := help
.PHONY: help env env-or-gui infra frontend up async ai down down-v stop start restart ps logs build verify clean

ifeq ($(OS),Windows_NT)
  COPY_ENV := powershell -NoProfile -Command "Copy-Item -LiteralPath $(ENV_EXAMPLE) -Destination $(ENV_FILE)"
  LAUNCH_EDITOR := powershell -NoProfile -Command "Start-Process -FilePath node -ArgumentList 'scripts/env-editor.mjs' -WorkingDirectory '$(CURDIR)'"
else
  COPY_ENV := cp $(ENV_EXAMPLE) $(ENV_FILE)
  LAUNCH_EDITOR := nohup node scripts/env-editor.mjs >/dev/null 2>&1 &
endif

help: ## Muestra esta ayuda
	$(info Uso: make <target>)
	$(info )
	$(info Targets disponibles:)
	$(info   env       Crea $(ENV_FILE) desde $(ENV_EXAMPLE) si no existe)
	$(info   env-or-gui  Si no hay $(ENV_FILE), abre el asistente web (localhost:4600))
	$(info   infra     Levanta solo la infraestructura (Postgres, Redis, Kafka, Prometheus, Grafana))
	$(info   frontend  Construye la SPA Vue (frontend/dist) para que Nginx la sirva por HTTPS)
	$(info   up        Levanta infraestructura + microservicios (--build))
	$(info   async     Levanta MongoDB + RabbitMQ (perfil async))
	$(info   ai        Levanta Ollama (perfil ai))
	$(info   ps        Muestra el estado de los contenedores)
	$(info   logs      Muestra logs en directo (opcional: SVC=gateway))
	$(info   build     Reconstruye las imagenes de los microservicios)
	$(info   verify    Valida la configuracion de docker-compose)
	$(info   stop      Para los contenedores sin eliminarlos)
	$(info   start     Arranca contenedores ya creados)
	$(info   restart   Reinicia los contenedores)
	$(info   down      Para y elimina los contenedores (conserva volumenes))
	$(info   down-v    down + elimina los volumenes (borra los datos))
	$(info   clean     down -v + elimina huerfanos e imagenes locales)
	@cd .

env: ## Crea .env desde .env.example si no existe
ifneq ($(wildcard $(ENV_FILE)),)
	@echo $(ENV_FILE) ya existe.
else
	@$(COPY_ENV)
	@echo $(ENV_FILE) creado desde $(ENV_EXAMPLE).
endif

env-or-gui: ## Abre el asistente web (localhost:4600) si falta .env, que creara el .env y levantara la infra
ifneq ($(wildcard $(ENV_FILE)),)
	@echo $(ENV_FILE) OK.
else
	@echo "No hay $(ENV_FILE). Abriendo el asistente de configuracion en http://localhost:4600 ..."
	@$(LAUNCH_EDITOR)
	@echo ""
	@echo "Configura el .env en el navegador y pulsa «Ir a la web»: crea el .env, construye"
	@echo "el frontend, levanta la infraestructura y abre la aplicacion."
	@echo "El arranque lo lanza el asistente; si cierras el navegador sin guardar,"
	@echo "puedes crear el .env a mano con: cp $(ENV_EXAMPLE) $(ENV_FILE), y repetir make."
	@exit 1
endif

infra: env-or-gui ## Levanta solo la infraestructura (Postgres, Redis, Kafka, Prometheus, Grafana)
	$(BASE) up -d

frontend: ## Construye la SPA Vue (frontend/dist) para que Nginx la sirva por HTTPS
	cd frontend && npm run build

up: env-or-gui ## Levanta infraestructura + microservicios (--build)
	$(BASE) $(APPS) up -d --build

async: env-or-gui ## Levanta MongoDB + RabbitMQ (perfil async)
	$(BASE) $(ASYNC) up -d

ai: env-or-gui ## Levanta Ollama (perfil ai)
	$(BASE) $(AI) up -d

down: ## Para y elimina los contenedores (conserva volumenes)
	$(BASE) $(ALL) down

down-v: ## down + elimina los volumenes (borra los datos)
	$(BASE) $(ALL) down -v

stop: ## Para los contenedores sin eliminarlos
	$(BASE) $(ALL) stop

start: ## Arranca contenedores ya creados
	$(BASE) $(ALL) start

restart: ## Reinicia los contenedores
	$(BASE) $(ALL) restart

ps: ## Muestra el estado de los contenedores
	$(BASE) $(ALL) ps

logs: ## Muestra logs en directo (opcional: SVC=gateway)
	$(BASE) logs -f $(SVC)

build: ## Reconstruye las imagenes de los microservicios
	$(BASE) $(APPS) build

verify: ## Valida la configuracion de docker-compose
	$(BASE) $(ALL) config --quiet && echo docker-compose OK

clean: ## down-v + elimina huerfanos e imagenes locales
	$(BASE) $(ALL) down -v --remove-orphans --rmi local
