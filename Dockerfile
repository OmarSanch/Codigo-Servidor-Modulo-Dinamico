# --- ETAPA 1: Compilación (Igual que antes) ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY *.gradle ./
COPY *.properties ./
COPY server server
RUN chmod +x gradlew
RUN ./gradlew :server:assemble -x test --no-daemon

# --- ETAPA 2: Imagen de ejecución ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copiamos todo el contenido de libs para asegurar que el JAR esté ahí
COPY --from=build /app/server/build/libs/ /app/libs/

# ESTA LÍNEA ES LA CLAVE:
# Busca el archivo más grande en esa carpeta (el Fat JAR) y lo nombra app.jar
RUN find /app/libs/ -name "*.jar" ! -name "*plain*" -exec cp {} /app/app.jar \;

COPY start.sh start.sh
RUN chmod +x start.sh
RUN mkdir -p /app/storage

EXPOSE 8080
ENV PORT=8080

ENTRYPOINT ["./start.sh"]