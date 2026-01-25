# Cambia openjdk:11-jre-slim por eclipse-temurin:17-jre
FROM eclipse-temurin:17-jre

WORKDIR /app

# El resto se mantiene igual a como lo tenías para que funcione
COPY server/build/libs/globallydynamic-server-1.6.0-SNAPSHOT-standalone.jar app.jar
COPY start.sh start.sh

RUN chmod +x start.sh
RUN mkdir -p /app/storage

EXPOSE 8080
ENV PORT=8080

CMD ["./start.sh"]