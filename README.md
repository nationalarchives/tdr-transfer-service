# TDR Transfer Service

API endpoints to allow clients to interact with TDR services.

## Running Locally

### Intellij

1. Add the relevant environment variables to the `application.conf`
2. Run the application: `TransferServiceServer.scala`
3. Go to http://localhost:8080/docs/ to see the API documentation

### Docker

1. Add the relevant environment variables to the `application.conf`
2. In the root of the project run the following command to create the jar: `sbt assembly`
3. In the root of the project run the following command to create the docker image: `docker build -t {your image tag} .`
4. Run the container with the following command: `docker run -it --name {your container name} -p 8080:8080 {your image tag}`
5. Go to http://localhost:8080/docs/ to see the API documentation
