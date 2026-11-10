#!/usr/bin/env bash
#
# Corre la suite de tests dentro de un contenedor con JDK 21, sin depender del
# Java ni del Maven del host.
#
# Testcontainers se usa en modo "docker fuera de docker": se monta el socket del
# host, asi que los contenedores que levantan los tests son hermanos de este, no
# hijos. Por eso TESTCONTAINERS_HOST_OVERRIDE apunta a host.docker.internal: es
# la direccion con la que este contenedor alcanza los puertos que esos hermanos
# publican en el host.
#
# Uso: ./scripts/test.sh [argumentos extra para maven]
#   ./scripts/test.sh                       # toda la suite
#   ./scripts/test.sh -Dtest=PolizaTest     # una sola clase
#
set -euo pipefail

cd "$(dirname "$0")/.."

docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$PWD":/workspace -w /workspace \
  -v demo-ti-m2:/root/.m2 \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  maven:3.9-eclipse-temurin-21 ./mvnw -B test "$@"
