FROM alpine:3
#For alpine versions need to create a group before adding a user to the image
WORKDIR /transferservice
RUN addgroup --system transferservicegroup && adduser --system transferserviceuser -G transferservicegroup && \
    chown -R transferserviceuser /transferservice && \
    apk update && apk upgrade p11-kit busybox expat libretls zlib openssl libcrypto3 libssl3 && \
    wget -O /etc/apk/keys/amazoncorretto.rsa.pub https://apk.corretto.aws/amazoncorretto.rsa.pub && \
    echo "https://apk.corretto.aws/" >> /etc/apk/repositories && \
    apk update && apk add --no-cache 'amazon-corretto-21>=21.3.128'
COPY target/scala-2.13/transferservice.jar /transferservice

USER transferserviceuser
CMD java -XX:-UseBASE64Intrinsics -jar /transferservice/transferservice.jar
