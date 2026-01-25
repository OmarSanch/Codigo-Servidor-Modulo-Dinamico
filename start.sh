#!/bin/bash
echo "=== Iniciando GloballyDynamic Server ==="
echo "PORT: ${PORT}"
echo "Java version:"
java -version

# Ejecutar directamente con -jar y especificar la clase principal
java -Dfile.encoding=UTF-8 \
     -cp app.jar com.globallydynamic.server.GloballyDynamicServer \
     --port "${PORT:-8080}" \
     --username "${GLOBALLY_DYNAMIC_USERNAME:-admin}" \
     --password "${GLOBALLY_DYNAMIC_PASSWORD:-secret123}" \
     --storage-backend "${GLOBALLY_DYNAMIC_STORAGE_BACKEND:-local}" \
     --local-storage-path "${GLOBALLY_DYNAMIC_LOCAL_STORAGE_PATH:-/app/storage}" \
     --https-redirect "${GLOBALLY_DYNAMIC_HTTPS_REDIRECT:-false}" \
     --override-existing-bundles "${GLOBALLY_DYNAMIC_OVERRIDE_EXISTING:-true}" \
     --validate-signature-on-download "${GLOBALLY_DYNAMIC_VALIDATE_SIGNATURE:-false}"
