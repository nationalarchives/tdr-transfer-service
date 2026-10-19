# TDR Transfer Service

API endpoints to allow clients to interact with TDR services.

## Running Locally

The service only accepts HTTPS, including `/healthcheck`; TLS cannot be disabled.
It reads an X.509 PEM certificate (leaf first, followed by any intermediates) from
`/etc/ssl/certs/transfer-service.crt` and an unencrypted PKCS#8 PEM private key from
`/etc/ssl/private/transfer-service.key`. Override these locations with
`TLS_CERTIFICATE_PATH` and `TLS_PRIVATE_KEY_PATH`. Missing or invalid files prevent startup.

For local development, generate a self-signed certificate and point the service at it:

```sh
mkdir -p certs
openssl req -x509 -newkey rsa:3072 -sha256 -noenc -days 365 \
  -keyout certs/transfer-service.key -out certs/transfer-service.crt \
  -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
chmod 600 certs/transfer-service.key
export TLS_CERTIFICATE_PATH="$PWD/certs/transfer-service.crt"
export TLS_PRIVATE_KEY_PATH="$PWD/certs/transfer-service.key"
```

### Intellij

1. Add the relevant environment variables to the `application.conf`
2. Run the application: `TransferServiceServer.scala`
3. Set the TLS file paths above in the run configuration and go to https://localhost:8080/docs/ to see the API documentation

### Docker

1. Add the relevant environment variables to the `application.conf`
2. In the root of the project run the following command to create the jar: `sbt assembly`
3. In the root of the project run the following command to create the docker image: `docker build -t {your image tag} .`
4. Run the container with the following command: `docker run -it --rm --name {your container name} -p 8080:8080 {your image tag}`
5. Go to https://localhost:8080/docs/ to see the API documentation

The image currently generates a 365-day self-signed certificate and private key at
build time in the paths above. The private key is readable only by the service user.
This is a temporary development setup: anyone with access to the image can extract
the key, and containers using that image share it. For production, replace these
files with a trusted certificate and private key supplied securely at runtime,
readable by `transferserviceuser`. Browsers will warn about the self-signed
certificate; for a local health check use
`curl --cacert certs/transfer-service.crt https://localhost:8080/healthcheck`
when using the locally generated certificate. Container health checks and upstream
connections must also use HTTPS and trust the installed certificate.
