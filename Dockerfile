FROM alpine
#For alpine versions need to create a group before adding a user to the image
WORKDIR /transferservice
RUN addgroup --system transferservicegroup && adduser --system transferserviceuser -G transferservicegroup && \
    chown -R transferserviceuser /transferservice && \
    apk add openjdk17 --repository=http://dl-cdn.alpinelinux.org/alpine/edge/community
COPY target/scala-2.13/transferservice.jar /transferservice

USER transferserviceuser
CMD java -jar /transferservice/transferservice.jar
