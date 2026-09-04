# ============================================================
# Multi-stage Dockerfile — AI Patient Case-Taking System
# Stage 1: Build with Maven + JDK 21
# Stage 2: Run with JRE 21 (smaller runtime image)
# ============================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-alpine AS build

# Install Maven
RUN apk add --no-cache maven

WORKDIR /workspace

# Copy dependency descriptor first — layer cache will skip download
# if pom.xml has not changed
COPY pom.xml .
RUN mvn -f pom.xml dependency:go-offline -q

# Copy source and build (skip tests — tests run in CI before image build)
COPY src/ src/
RUN mvn -f pom.xml clean package -DskipTests -q

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S patientcase && adduser -S patientcase -G patientcase

WORKDIR /app

# Copy the fat JAR from build stage
COPY --from=build /workspace/target/ai-patient-case-system-*.jar app.jar

# Document storage directory — override with a bind mount or named volume
RUN mkdir -p /var/patientcase/documents \
    && chown -R patientcase:patientcase /var/patientcase /app

USER patientcase

EXPOSE 8080

# JVM tuning: respect container memory limits, improve startup randomness
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Health check uses the Actuator endpoint added in this batch
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
