FROM openjdk:11-jre-slim

WORKDIR /app

# Copiar el JAR standalone (el que tiene Main-Class configurado)
COPY server/build/libs/globallydynamic-server-1.6.0-SNAPSHOT-standalone.jar app.jar
COPY start.sh start.sh

# Hacer el script ejecutable
RUN chmod +x start.sh

# Crear directorio de storage
RUN mkdir -p /app/storage

# Exponer puerto
EXPOSE 8080

# Variables de entorno por defecto
ENV PORT=8080

# Ejecutar el script
CMD ["./start.sh"]
