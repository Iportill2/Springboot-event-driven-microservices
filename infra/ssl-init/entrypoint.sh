#!/bin/sh
# Genera un certificado autofirmado de desarrollo en /certs si no existe.
set -e

CERT_DIR=/certs

if [ ! -f "$CERT_DIR/server.crt" ] || [ ! -f "$CERT_DIR/server.key" ]; then
  echo "[ssl-init] Generando certificado autofirmado (CN=localhost)..."
  openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
    -keyout "$CERT_DIR/server.key" \
    -out "$CERT_DIR/server.crt" \
    -subj "/C=ES/ST=Madrid/L=Madrid/O=Dev/OU=Local/CN=localhost"
  echo "[ssl-init] Certificado creado: /certs/server.crt"
else
  echo "[ssl-init] El certificado ya existe, no se regenera."
fi