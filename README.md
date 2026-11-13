# TDR Transfer Service

API endpoints to allow clients to interact with TDR services.

## Running Locally

### Intellij

1. Add the relevant environment variables to the `application.conf`
2. Run the application: `TransferServiceServer.scala`
3. Go to https://localhost:8080/docs/ to see the API documentation

For local runs, set `TLS_KEY_STORE_PATH` to an existing PKCS#12 bundle with an empty password, private key and certificate chain.

### Docker

1. Add the relevant environment variables to the `application.conf`
2. In the root of the project run the following command to create the jar: `sbt assembly`
3. In the root of the project run the following command to create the docker image: `docker build -t {your image tag} .`
4. Run the container with the following command: `docker run -it --rm --name {your container name} -p 8080:8080 {your image tag}`
5. Go to https://localhost:8080/docs/ to see the API documentation

The Docker entrypoint generates a self-signed TLS certificate and private key in a PKCS#12 bundle at
`/transferservice/certs/this.p12`. Set `TLS_KEY_STORE_PATH` to change the path. The bundle has an empty
password and owner-only file permissions; the certificate is regenerated on each container start. Clients
must trust the self-signed certificate.
