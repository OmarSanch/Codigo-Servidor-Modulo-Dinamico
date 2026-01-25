# --- ETAPA 1: Compilación (Igual) ---
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

# Copiamos el archivo exacto que encontraste con 'ls'
COPY --from=build /app/server/build/libs/server-*-all.jar app.jar
COPY start.sh start.sh

RUN chmod +x start.sh
RUN mkdir -p /app/storage

EXPOSE 8080
ENV PORT=8080

ENTRYPOINT ["./start.sh"]