FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /workspace

# Copy pom and sources then package (skip tests for faster build)
COPY pom.xml mvnw* ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy jar produced by the builder stage
COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
