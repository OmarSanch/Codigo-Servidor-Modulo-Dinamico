# --- ETAPA 1: Compilación ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copiamos los archivos de configuración de Gradle
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Copiamos el código fuente de los módulos
COPY server server

# Damos permisos y compilamos el JAR (usamos bootJar o jar dependiendo de tu config)
# El flag -x test es para saltar los tests y que suba más rápido
RUN chmod +x gradlew
RUN ./gradlew :server:assemble

# --- ETAPA 2: Imagen de ejecución ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copiamos el JAR generado desde la etapa de compilación
# Nota: Ajusta la ruta si el nombre del jar cambia, pero el asterisco (*) ayuda.
COPY --from=build /app/server/build/libs/*-standalone.jar app.jar
COPY start.sh start.sh

# Configuraciones adicionales que ya tenías
RUN chmod +x start.sh
RUN mkdir -p /app/storage

EXPOSE 8080
ENV PORT=8080

# Usamos ENTRYPOINT para asegurar que el script reciba las señales de parada
ENTRYPOINT ["./start.sh"]