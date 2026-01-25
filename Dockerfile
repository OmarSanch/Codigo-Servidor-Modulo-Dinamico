FROM eclipse-temurin:17-jre

WORKDIR /app

# Copiar el JAR standalone
COPY globallydynamic-server-1.6.0-SNAPSHOT-standalone.jar app.jar
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
COPY globallydynamic-server-1.6.0-SNAPSHOT-standalone.jar app.jar
COPY globallydynamic-server-1.6.0-SNAPSHOT-standalone.jar app.jar
