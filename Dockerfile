# Etapa 1: Build
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# Copia TODOS los archivos de configuración de Gradle
COPY gradle gradle
COPY gradlew .
COPY gradlew.bat .
COPY settings.gradle .
COPY build.gradle .
COPY deps.gradle .

# Copia el código fuente completo
COPY server server

# Da permisos de ejecución
RUN chmod +x gradlew

# Compila el proyecto (sin tests)
RUN ./gradlew clean :server:build -x test --no-daemon

# Etapa 2: Runtime
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copia el JAR compilado desde la etapa anterior
COPY --from=builder /build/server/build/libs/globallydynamic-server-1.6.0-SNAPSHOT-standalone.jar app.jar

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
