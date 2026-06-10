# ── Stage 1: build ────────────────────────────────────────────────────────────
# Full Maven + JDK image. Used to compile and package, then thrown away.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy the dependency manifest first. Docker caches each layer separately, so
# as long as pom.xml doesn't change, the Maven download step is fully cached.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Now copy source and build. -DskipTests because CI already ran them.
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: run ──────────────────────────────────────────────────────────────
# Minimal JRE image — no compiler, no Maven, no source code.
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy only the fat JAR produced by Stage 1.
COPY --from=build /app/target/doc-platform-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
