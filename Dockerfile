FROM openjdk:11-jre-slim

WORKDIR /app

# Copiar el JAR y el script de inicio
COPY server/build/libs/server-1.6.0-SNAPSHOT-all.jar app.jar
COPY start.sh start.sh

# Hacer el script ejecutable
RUN chmod +x start.sh

# Crear directorio de storage
RUN mkdir -p /app/storage

# Exponer puerto
EXPOSE 8080

# Variables de entorno por defecto
ENV PORT=8080

# Comando para ejecutar el servidor
CMD ["./start.sh", \
     "--port", "${PORT}", \
     "--username", "${GLOBALLY_DYNAMIC_USERNAME:-admin}", \
     "--password", "${GLOBALLY_DYNAMIC_PASSWORD:-secret123}", \
     "--storage-backend", "${GLOBALLY_DYNAMIC_STORAGE_BACKEND:-local}", \
     "--local-storage-path", "${GLOBALLY_DYNAMIC_LOCAL_STORAGE_PATH:-/app/storage}", \
     "--https-redirect", "${GLOBALLY_DYNAMIC_HTTPS_REDIRECT:-false}", \
     "--override-existing-bundles", "${GLOBALLY_DYNAMIC_OVERRIDE_EXISTING:-true}", \
     "--validate-signature-on-download", "${GLOBALLY_DYNAMIC_VALIDATE_SIGNATURE:-false}"]
