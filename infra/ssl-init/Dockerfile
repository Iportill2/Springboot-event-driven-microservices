# Genera un certificado autofirmado para desarrollo (HTTPS local).
# Se ejecuta una sola vez: si el certificado ya existe en el volumen, no lo regenera.

FROM alpine:3.20

RUN apk add --no-cache openssl

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]