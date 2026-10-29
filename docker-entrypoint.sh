#!/bin/sh
set -eu
umask 077
openssl req -quiet -x509 -newkey rsa:3072 -sha256 -noenc -days 365 -subj "/CN=localhost" \
        -keyout /${SERVICE_NAME}/certs/this.key \
        -out /${SERVICE_NAME}/certs/this.crt 
chmod 600 /${SERVICE_NAME}/certs/this.key
chmod 644 /${SERVICE_NAME}/certs/this.crt

exec "$@"
