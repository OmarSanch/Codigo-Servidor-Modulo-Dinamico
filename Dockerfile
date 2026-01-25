# --- ETAPA 1: Compilación ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 1. Copiamos el wrapper
COPY gradlew .
COPY gradle gradle

# 2. COPIA AMPLIADA: Copiamos todos los archivos de configuración
# Esto incluye: build.gradle, settings.gradle, deps.gradle Y gradle.properties
COPY *.gradle ./
COPY *.properties ./

# 3. Copiamos el código fuente
COPY server server

# 4. Compilamos
RUN chmod +x gradlew
# Añadimos un flag para ignorar propiedades de entorno si fuera necesario
RUN ./gradlew :server:assemble -x test --no-daemon

# --- ETAPA 2: Imagen de ejecución ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copiamos el JAR generado desde la etapa de compilación
# Usamos el comodín para atrapar el archivo -standalone.jar
COPY --from=build /app/server/build/libs/*standalone.jar app.jar
COPY start.sh start.sh

RUN chmod +x start.sh
RUN mkdir -p /app/storage

EXPOSE 8080
ENV PORT=8080

ENTRYPOINT ["./start.sh"]