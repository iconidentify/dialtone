# Multi-stage Dockerfile for Dialtone AOL Server
# Stage 1: Frontend Build
# Stage 2: Backend Build
# Stage 3: Production Runtime
# Stage 4: Development Runtime (hot reload)

# ============================================
# Stage 1: Frontend Build Stage
# ============================================
FROM node:18-alpine AS frontend-builder

WORKDIR /frontend

# Copy package files and install dependencies
COPY src/main/resources/static/package*.json ./
RUN npm ci

# Copy frontend source and build
COPY src/main/resources/static/ ./
RUN npm run build

# ============================================
# Stage 2: Backend Build Stage
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS backend-builder

# Build args for GitHub Packages authentication
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN

WORKDIR /build

# Create Maven settings.xml with GitHub Packages credentials for both repos
RUN mkdir -p /root/.m2 && \
    echo '<settings><servers>' \
         '<server><id>github-atomforge</id><username>'${GITHUB_ACTOR}'</username><password>'${GITHUB_TOKEN}'</password></server>' \
         '<server><id>github-skalholt</id><username>'${GITHUB_ACTOR}'</username><password>'${GITHUB_TOKEN}'</password></server>' \
         '</servers></settings>' > /root/.m2/settings.xml

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code (excluding static since we'll copy the built version)
COPY src ./src

# Copy built frontend assets from previous stage
COPY --from=frontend-builder /public ./src/main/resources/public/

# Build fat JAR
RUN mvn clean package -DskipTests

# ============================================
# Stage 3: Production Runtime
# ============================================
FROM eclipse-temurin:21-jre AS production

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=backend-builder /build/target/dialtone-1.0-SNAPSHOT.jar /app/dialtone.jar

# Copy resources (includes built frontend from backend-builder)
COPY --from=backend-builder /build/src/main/resources /app/resources

# Create logs and database directories
RUN mkdir -p /app/logs /app/db

# Expose both AOL server and web interface ports
EXPOSE 5191 5200

# Health check for both services
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD timeout 5 bash -c '</dev/tcp/localhost/5191' && timeout 5 bash -c '</dev/tcp/localhost/5200' || exit 1

# Run the application (starts both servers)
CMD ["java", "-jar", "/app/dialtone.jar"]

# ============================================
# Stage 4: Development Runtime (Hot Reload)
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS development

# Build args for GitHub Packages authentication
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN

WORKDIR /app

# Create Maven settings.xml with GitHub Packages credentials for both repos
RUN mkdir -p /root/.m2 && \
    echo '<settings><servers>' \
         '<server><id>github-atomforge</id><username>'${GITHUB_ACTOR}'</username><password>'${GITHUB_TOKEN}'</password></server>' \
         '<server><id>github-skalholt</id><username>'${GITHUB_ACTOR}'</username><password>'${GITHUB_TOKEN}'</password></server>' \
         '</servers></settings>' > /root/.m2/settings.xml

# Install dependencies for faster rebuilds
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Source code will be mounted as volume
# This stage uses exec:java for hot reload

EXPOSE 5191 5200

# Run with Maven exec plugin for hot reload (fixed main class)
CMD ["mvn", "compile", "exec:java", "-Dexec.mainClass=com.dialtone.DialtoneApplication"]
