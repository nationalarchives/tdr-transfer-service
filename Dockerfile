FROM alpine:3
#For alpine versions need to create a group before adding a user to the image
ARG SERVICE_NAME=transferservice
ENV SERVICE_NAME=${SERVICE_NAME}

COPY --chmod=755 docker-entrypoint.sh /

WORKDIR ${SERVICE_NAME}

RUN addgroup --system ${SERVICE_NAME} && adduser --system ${SERVICE_NAME} -G ${SERVICE_NAME} && \
    chown -R ${SERVICE_NAME} /${SERVICE_NAME} && \
    apk update && apk upgrade p11-kit busybox expat libretls zlib openssl libcrypto3 libssl3 && \
    wget -O /etc/apk/keys/amazoncorretto.rsa.pub https://apk.corretto.aws/amazoncorretto.rsa.pub && \
    echo "https://apk.corretto.aws/" >> /etc/apk/repositories && \
    apk update && apk add --no-cache openssl 'amazon-corretto-21>=21.0.13.1.2'

RUN mkdir -p /${SERVICE_NAME}/certs && \
    chown ${SERVICE_NAME}:${SERVICE_NAME} /${SERVICE_NAME}/certs

COPY target/scala-2.13/${SERVICE_NAME}.jar /${SERVICE_NAME}

USER ${SERVICE_NAME}
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD java -XX:-UseBASE64Intrinsics -jar /${SERVICE_NAME}/${SERVICE_NAME}.jar
