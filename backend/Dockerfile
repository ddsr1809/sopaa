# --- Compilación ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# --- Ejecución ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 sopa
COPY --from=build /app/target/sopa-backend-*.jar app.jar
USER sopa
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
