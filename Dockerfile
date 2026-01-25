# --- ETAPA 1: Compilación ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copiar archivos de configuración
COPY gradlew .
COPY gradle gradle
COPY *.gradle ./
COPY *.properties ./
COPY server server

# Construir
RUN chmod +x gradlew
RUN ./gradlew :server:assemble -x test --no-daemon

# --- ETAPA 2: Imagen de ejecución ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copiar el script de inicio
COPY start.sh start.sh
RUN chmod +x start.sh

# Crear carpeta de storage
RUN mkdir -p /app/storage

# EXTRACCIÓN DINÁMICA DEL JAR:
# Esta línea busca el JAR que NO sea el 'plain' (que no funciona)
# y lo renombra a app.jar sin importar la versión.
COPY --from=build /app/server/build/libs/*standalone.jar app.jar

# Si el anterior falla, esta es la alternativa más agresiva (usar el más pesado)
# RUN cp /app/server/build/libs/$(ls /app/server/build/libs/ | grep .jar | grep -v plain | head -n 1) app.jar

EXPOSE 8080
ENV PORT=8080

ENTRYPOINT ["./start.sh"]