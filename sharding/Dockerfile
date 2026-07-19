FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
# Copy the maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
# Download dependencies (this layer will be cached)
RUN ./mvnw dependency:go-offline -B
# Copy source code and build
COPY src src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Copy the built war file from the builder stage
COPY --from=builder /app/target/*.war app.war
# Expose the application port
EXPOSE 8080
# Run the application
ENTRYPOINT ["java", "-jar", "app.war"]
