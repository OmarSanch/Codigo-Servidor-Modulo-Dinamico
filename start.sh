#!/bin/bash
echo "=== Iniciando GloballyDynamic Server ==="
# Usamos la clase que encontramos anteriormente
java -Dfile.encoding=UTF-8 \
     -cp app.jar com.jeppeman.globallydynamic.server.GloballyDynamicMainKt \
     --port "${PORT:-8080}" \
     --storage-backend "local" \
     --local-storage-path "/app/storage"
