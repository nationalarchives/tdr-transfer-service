#!/bin/sh
set -eu
umask 077
openssl req -quiet -x509 -newkey rsa:3072 -sha256 -noenc -days 365 -subj "/CN=localhost" \
        -keyout /transferservice/certs/transfer-service.key \
        -out /transferservice/certs/transfer-service.crt 
chmod 600 /transferservice/certs/transfer-service.key
chmod 644 /transferservice/certs/transfer-service.crt

exec "$@"
