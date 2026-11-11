#!/usr/bin/env bash
# Sobe os 3 serviços no docker compose e roda o Cucumber.
# Os repositórios fiap-tc-fase4-oficina-diagnostico-reparo-service e fiap-tc-fase4-oficina-orcamento-pagamento-service
# devem estar clonados ao lado do fiap-tc-fase4-oficina-os-service.
set -euo pipefail
cd "$(dirname "$0")"

mkdir -p target/keys
if [[ ! -f target/keys/cliente-private.pem ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out target/keys/cliente-private.pem 2>/dev/null
  openssl pkey -in target/keys/cliente-private.pem -pubout -out target/keys/cliente-public.pem
fi
SERVERLESS_JWT_PUBLIC_KEY="$(cat target/keys/cliente-public.pem)"
export SERVERLESS_JWT_PUBLIC_KEY

cleanup() {
  if [[ "${BDD_KEEP_UP:-false}" != "true" ]]; then
    docker compose logs --no-color > target/docker-compose.log 2>&1 || true
    docker compose down -v --remove-orphans >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

docker compose up -d --build

for porta in 8081 8082 8083; do
  echo "Aguardando o serviço na porta $porta..."
  for _ in $(seq 1 90); do
    if curl -fsS "http://localhost:$porta/actuator/health" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done
  curl -fsS "http://localhost:$porta/actuator/health" >/dev/null
done

MVN="${MVN:-../mvnw}"
"$MVN" -B -f pom.xml test \
  -Dbdd.cliente-private-key=target/keys/cliente-private.pem \
  "$@"
