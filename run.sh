#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
JAR="target/yourday-0.1.0-all.jar"
if [ ! -f "$JAR" ]; then
    export JAVA_HOME="${JAVA_HOME:-/home/rafael/.jdks/jdk-21.0.12.1+1}"
    ./mvnw -q -DskipTests package
fi
exec java -jar "$JAR" "$@"