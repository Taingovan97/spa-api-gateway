# ===== Build stage =====
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests clean package

# ===== Runtime stage =====
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy jar
COPY --from=build /workspace/target/*.jar app.jar

# Non-root (optional but recommended)
RUN useradd -r -u 10001 appuser && chown -R appuser:appuser /app
USER 10001

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
