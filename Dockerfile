# syntax=docker/dockerfile:1.7
#
# Single Dockerfile for all 4 services. Build with:
#   docker build --build-arg SERVICE=order-service       -t order-service .
#   docker build --build-arg SERVICE=inventory-service   -t inventory-service .
#   docker build --build-arg SERVICE=payment-service     -t payment-service .
#   docker build --build-arg SERVICE=shipping-service    -t shipping-service .
#
# The build copies the entire repo because the multi-module parent pom
# references all 4 services — Maven's reactor needs every declared module
# to exist on disk before it can pick the one we actually want to build.
ARG SERVICE=order-service

FROM maven:3.9-eclipse-temurin-21-alpine AS build
ARG SERVICE
WORKDIR /src
COPY pom.xml ./
COPY shared/             ./shared/
COPY order-service/      ./order-service/
COPY inventory-service/  ./inventory-service/
COPY payment-service/    ./payment-service/
COPY shipping-service/   ./shipping-service/
RUN mvn -B -pl shared,${SERVICE} -am package -DskipTests && \
    cp ${SERVICE}/target/${SERVICE}-*.jar /tmp/app.jar

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
ENV SERVICE_NAME=${SERVICE}
RUN addgroup -S app && adduser -S -G app app
WORKDIR /home/app
COPY --from=build /tmp/app.jar /home/app/app.jar
USER app
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75", "-jar", "/home/app/app.jar"]
