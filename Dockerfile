FROM alpine:3
#For alpine versions need to create a group before adding a user to the image
WORKDIR /transferservice
RUN addgroup --system transferservicegroup && adduser --system transferserviceuser -G transferservicegroup && \
    chown -R transferserviceuser /transferservice && \
    apk update && apk upgrade p11-kit busybox expat libretls zlib openssl libcrypto3 libssl3 && \
    apk add openssl openjdk21 --repository=https://dl-cdn.alpinelinux.org/alpine/edge/community
RUN mkdir -p /etc/ssl/private /etc/ssl/certs && \
    openssl req -x509 -newkey rsa:3072 -sha256 -noenc -days 365 \
      -keyout /etc/ssl/private/transfer-service.key \
      -out /etc/ssl/certs/transfer-service.crt \
      -subj "/CN=localhost" \
      -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" && \
    chown transferserviceuser:transferservicegroup /etc/ssl/private /etc/ssl/private/transfer-service.key && \
    chmod 700 /etc/ssl/private && \
    chmod 600 /etc/ssl/private/transfer-service.key && \
    chmod 644 /etc/ssl/certs/transfer-service.crt
COPY target/scala-2.13/transferservice.jar /transferservice

USER transferserviceuser
CMD java -jar /transferservice/transferservice.jar
