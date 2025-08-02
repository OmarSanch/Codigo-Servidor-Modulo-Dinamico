FROM openjdk:11-jre-slim

WORKDIR /app

# Copiar el JAR compilado
COPY server/build/libs/globallydynamic-server.jar app.jar

# Crear directorio de storage
RUN mkdir -p /app/storage

# Exponer puerto
EXPOSE 8080

# Variables de entorno por defecto
ENV PORT=8080

# Comando para ejecutar el servidor
CMD ["java", "-jar", "app.jar", \
     "--port", "${PORT}", \
     "--username", "${GLOBALLY_DYNAMIC_USERNAME:-admin}", \
     "--password", "${GLOBALLY_DYNAMIC_PASSWORD:-secret123}", \
     "--storage-backend", "${GLOBALLY_DYNAMIC_STORAGE_BACKEND:-local}", \
     "--local-storage-path", "${GLOBALLY_DYNAMIC_LOCAL_STORAGE_PATH:-/app/storage}", \
     "--https-redirect", "${GLOBALLY_DYNAMIC_HTTPS_REDIRECT:-false}", \
     "--override-existing-bundles", "${GLOBALLY_DYNAMIC_OVERRIDE_EXISTING:-true}", \
     "--validate-signature-on-download", "${GLOBALLY_DYNAMIC_VALIDATE_SIGNATURE:-false}"
