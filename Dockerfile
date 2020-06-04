FROM alpine:3.10

# instaling openjdk8 and bash in this image
RUN apk add --update \
    openjdk8-jre \
    bash \
  && rm -rf /var/cache/apk/*

LABEL maintainer="dev.pje"

VOLUME /tmp

EXPOSE 8901

ENV JAVA_OPTS=""

ARG JAR_FILE=target/apoiador-requisitante*.ja
ADD ${JAR_FILE} bot-apoiador-requisitante.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/bot-apoiador-requisitante.jar", "--logging.file=/tmp/bot-apoiador-requisitante.log"]
