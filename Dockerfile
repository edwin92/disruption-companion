FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && \
    mvn -B clean package -DskipTests && \
    java -Djarmode=tools -jar target/latam-disruption-companion-1.0.0.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -g 65532 -S nonroot && adduser -u 65532 -G nonroot -S -D -H nonroot
COPY --from=builder --chown=65532:65532 /workspace/extracted/dependencies/ ./
COPY --from=builder --chown=65532:65532 /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=65532:65532 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=65532:65532 /workspace/extracted/application/ ./
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "latam-disruption-companion-1.0.0.jar"]
