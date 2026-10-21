FROM alpine:3
#For alpine versions need to create a group before adding a user to the image
WORKDIR /transferservice
RUN addgroup --system transferservicegroup && adduser --system transferserviceuser -G transferservicegroup && \
    chown -R transferserviceuser /transferservice && \
    apk update && apk upgrade p11-kit busybox expat libretls zlib openssl libcrypto3 libssl3 && \
    apk add openssl openjdk21 --repository=https://dl-cdn.alpinelinux.org/alpine/edge/community
RUN mkdir -p /transferservice/certs && \
    chown transferserviceuser:transferservicegroup /transferservice/certs
COPY target/scala-2.13/transferservice.jar /transferservice
COPY docker-entrypoint.sh /transferservice

USER transferserviceuser
ENTRYPOINT ["/bin/sh", "/transferservice/docker-entrypoint.sh"]
CMD java -jar /transferservice/transferservice.jar
