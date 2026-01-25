# --- ETAPA 1: Compilación ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 1. Copiamos los archivos de Gradle Wrapper
COPY gradlew .
COPY gradle gradle

# 2. COPIA CRUCIAL: Todos los archivos de configuración .gradle de la raíz
# Esto incluirá build.gradle, settings.gradle y el deps.gradle que falta
COPY *.gradle ./

# 3. Copiamos el código fuente del módulo server
# (Si tienes otros módulos como 'core' o 'common', deberás agregarlos aquí también)
COPY server server

# 4. Compilamos
RUN chmod +x gradlew
RUN ./gradlew :server:assemble -x test

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