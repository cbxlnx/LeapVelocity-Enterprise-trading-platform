# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install Maven
RUN apk add --no-cache maven

WORKDIR /build

# Copy pom.xml and source - maintain full directory structure
COPY pom.xml .
COPY backend ./backend

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy built JAR from builder
COPY --from=builder /build/target/team-skeleton.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

EXPOSE 8080