#!/bin/sh
# Provisiona las bases de datos y roles de cada microservicio.
# Los valores llegan desde .env a traves de docker-compose (nada hardcodeado).
# Solo se ejecuta la primera vez que se crea el volumen de PostgreSQL.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE ROLE ${USER_DB_USER} WITH LOGIN PASSWORD '${USER_DB_PASSWORD}';
    CREATE DATABASE ${USER_DB} OWNER ${USER_DB_USER};

    CREATE ROLE ${PRODUCT_DB_USER} WITH LOGIN PASSWORD '${PRODUCT_DB_PASSWORD}';
    CREATE DATABASE ${PRODUCT_DB} OWNER ${PRODUCT_DB_USER};
EOSQL
