# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install Maven
RUN apk add --no-cache maven

WORKDIR /build

# Copy backend Maven project
COPY backend/pom.xml ./backend/pom.xml
COPY backend/src ./backend/src

# Build the application
RUN mvn -f backend/pom.xml clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy built JAR from builder
COPY --from=builder /build/backend/target/*.jar app.jar


# Run the application
ENTRYPOINT ["java", "-cp", "app.jar", "com.Main"]

EXPOSE 8080
