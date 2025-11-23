# Stage 1: Build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy maven wrapper and pom
COPY mvnw pom.xml ./
COPY .mvn ./.mvn

# Make mvnw executable and download dependencies
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Stage 2: Production
FROM eclipse-temurin:25-jre AS runner
WORKDIR /app

RUN groupadd --system --gid 1001 spring && \
    useradd --system --uid 1001 --gid spring spring

COPY --from=builder /app/target/*.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]