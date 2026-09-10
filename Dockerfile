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


# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

EXPOSE 8080