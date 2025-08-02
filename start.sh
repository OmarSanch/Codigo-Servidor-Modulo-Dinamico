#!/bin/bash
java -cp server/build/libs/server-1.6.0-SNAPSHOT-all.jar \
  com.jeppeman.globallydynamic.server.GloballyDynamicMainKt \
  "$@"
