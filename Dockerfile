FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /kubernetesservice

COPY pom.xml ./
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-alpine@sha256:6ea5548706b60ac0a602eaf48af74792cbab012d90e811ca8db6184b16b5c3d6

RUN apk add --no-cache curl
RUN addgroup -S -g 1000 msinit && adduser -S -u 1000 -G msinit msinit

WORKDIR /kubernetesservice

COPY --chown=msinit:msinit --from=builder /kubernetesservice/target/*.jar kubernetesservice.jar
COPY --chown=msinit:msinit k8s /manifests

ENV JAVA_OPTS=""

EXPOSE 6011

USER msinit
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar kubernetesservice.jar"]
