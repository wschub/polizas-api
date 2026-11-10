# syntax=docker/dockerfile:1

# ---------- etapa de build ----------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# El wrapper y el pom se copian antes que el codigo: mientras el pom no cambie,
# Docker reutiliza la capa y la cache de ~/.m2 evita volver a bajar dependencias.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/

RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

# ---------- etapa de runtime ----------
FROM eclipse-temurin:21-jre

# Usuario sin privilegios: si alguien escapa del proceso Java, no es root.
RUN groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --create-home app

WORKDIR /app

# El plugin de Spring Boot deja el jar ejecutable como *.jar y el original como
# *.jar.original, asi que este glob toma solo el primero.
COPY --from=build /build/target/*.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
