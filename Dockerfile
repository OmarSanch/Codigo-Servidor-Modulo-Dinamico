# Etapa 1: Build
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# Copia archivos de configuración de Gradle
COPY gradle gradle
COPY gradlew .
COPY gradlew.bat .
COPY settings.gradle .
COPY build.gradle .
COPY deps.gradle .
COPY gradle.properties .

# Copia el código fuente
COPY server server

# Da permisos de ejecución
RUN chmod +x gradlew

# Compila usando la tarea executableJar que genera el fat JAR
RUN ./gradlew clean :server:executableJar -x test --no-daemon

# Muestra los JARs generados
RUN echo "=== JARs generados ===" && ls -lah /build/server/build/libs/

# Etapa 2: Runtime
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copia el JAR standalone (fat JAR con todas las dependencias - 71MB)
COPY --from=builder /build/server/build/libs/*-standalone.jar app.jar

# Verifica que el JAR existe
RUN ls -lh app.jar

# Copia el script de inicio
COPY start.sh start.sh
RUN chmod +x start.sh

# Crea el directorio de storage
RUN mkdir -p /app/storage

# Expone el puerto
EXPOSE 8080
ENV PORT=8080

# Ejecuta el servidor
CMD ["./start.sh"]
