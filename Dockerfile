# =======================================================
# Estágio 1: Build da Aplicação com Maven e Java 17
# =======================================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw

RUN ./mvnw dependency:go-offline -B || true

COPY src ./src
RUN ./mvnw clean package -DskipTests

# =======================================================
# Estágio 2: Imagem Final de Execução (Leve e Segura)
# =======================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=55.0 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -Xss512k -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:${PORT:-8080}/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
