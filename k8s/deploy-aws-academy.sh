#!/usr/bin/env bash
# Deploy da aplicacao no EKS do AWS Academy usando o RDS (banco gerenciado).
#
# Na Fase 3, o banco gerenciado pertence ao repositorio
# fiap-tc-fase4-oficina-database-infra. A aplicacao usa o RDS provisionado por ele.
#
# Este script descobre o endpoint do RDS e cria namespace, ConfigMap, Secret,
# Deployment, Service e HPA somente para a aplicacao.
#
# Uso:
#   DB_PASSWORD="<sua-senha-do-rds>" ./deploy-aws-academy.sh
# Variaveis:
#   REGION (default us-west-2), IMAGE (default GHCR latest)
#   DB_INSTANCE_ID (default oficina-homolog-db), DB_NAME e DB_USER
#   JWT_SECRET (se vazio, e gerado aleatorio) / ADMIN_PASSWORD (default de DEV)
#   SERVERLESS_JWT_PUBLIC_KEY (obrigatoria), SERVERLESS_JWT_ISSUER e SERVERLESS_JWT_AUDIENCE
#   AUTH_BASE_URL: URL base do API Gateway usada pelo Swagger para autenticar clientes.
#   Notificacao serverless (preferencial): NOTIFICATION_ENDPOINT e NOTIFICATION_API_KEY.
#   Fallback SMTP: MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD e MAIL_FROM.
#   Ex. Mailtrap:
#     DB_PASSWORD=... MAIL_HOST=sandbox.smtp.mailtrap.io MAIL_USERNAME=... \
#       MAIL_PASSWORD=... ./deploy-aws-academy.sh
set -euo pipefail

REGION="${REGION:-us-west-2}"
IMAGE="${IMAGE:-ghcr.io/tiagomiele/fiap-tc-fase4-oficina-os-service:latest}"
DB_INSTANCE_ID="${DB_INSTANCE_ID:-oficina-homolog-db}"
DB_NAME="${DB_NAME:-oficina}"
DB_USER="${DB_USER:-oficina_admin}"
MAIL_HOST="${MAIL_HOST:-}"
MAIL_PORT="${MAIL_PORT:-587}"
MAIL_USERNAME="${MAIL_USERNAME:-}"
MAIL_PASSWORD="${MAIL_PASSWORD:-}"
MAIL_FROM="${MAIL_FROM:-nao-responder@oficina.local}"
AUTH_BASE_URL="${AUTH_BASE_URL:-}"
NOTIFICATION_ENDPOINT="${NOTIFICATION_ENDPOINT:-}"
NOTIFICATION_API_KEY="${NOTIFICATION_API_KEY:-}"
SERVERLESS_JWT_ISSUER="${SERVERLESS_JWT_ISSUER:-fiap-tc-fase4-oficina-auth-serverless}"
SERVERLESS_JWT_AUDIENCE="${SERVERLESS_JWT_AUDIENCE:-oficina-backend}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -n "$NOTIFICATION_ENDPOINT" ]]; then
  if [[ -z "$NOTIFICATION_API_KEY" ]]; then
    echo "ERRO: defina NOTIFICATION_API_KEY ao usar NOTIFICATION_ENDPOINT." >&2
    exit 1
  fi
  NOTIFICACAO_TIPO="serverless"
  echo "==> Notificacao: modo serverless"
elif [[ -n "$MAIL_HOST" ]]; then
  NOTIFICACAO_TIPO="smtp"
  echo "==> Notificacao: modo 'smtp' via ${MAIL_HOST}:${MAIL_PORT}"
else
  NOTIFICACAO_TIPO="log"
  echo "==> Notificacao: modo 'log'"
fi

if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "ERRO: defina DB_PASSWORD com a MESMA senha da variavel db_password do Terraform." >&2
  echo "Ex.: DB_PASSWORD='<sua-senha-do-rds>' ./deploy-aws-academy.sh" >&2
  exit 1
fi

if [[ -z "${SERVERLESS_JWT_PUBLIC_KEY:-}" ]]; then
  echo "ERRO: defina SERVERLESS_JWT_PUBLIC_KEY com a chave publica da autenticacao." >&2
  exit 1
fi

if [[ -z "${JWT_SECRET:-}" ]]; then
  JWT_SECRET="$(openssl rand -base64 32 2>/dev/null || head -c 32 /dev/urandom | base64)"
  echo "AVISO: JWT_SECRET nao informado; gerando um valor aleatorio forte para esta execucao." >&2
fi
if [[ -z "${ADMIN_PASSWORD:-}" ]]; then
  ADMIN_PASSWORD="admin123"
  echo "AVISO: ADMIN_PASSWORD nao informado; usando valor de DESENVOLVIMENTO. Em producao defina ADMIN_PASSWORD." >&2
fi

echo "==> Descobrindo o endpoint do RDS ($DB_INSTANCE_ID)..."
RDS="$(aws rds describe-db-instances --region "$REGION" --db-instance-identifier "$DB_INSTANCE_ID" --query 'DBInstances[0].Endpoint.Address' --output text)"
if [[ -z "$RDS" || "$RDS" == "None" ]]; then
  echo "ERRO: endpoint do RDS vazio. A instancia '$DB_INSTANCE_ID' existe e as credenciais do lab estao validas?" >&2
  exit 1
fi
STATUS="$(aws rds describe-db-instances --region "$REGION" --db-instance-identifier "$DB_INSTANCE_ID" --query 'DBInstances[0].DBInstanceStatus' --output text)"
echo "    RDS: $RDS (status: $STATUS)"

kubectl apply -f "$HERE/namespace.yaml"

cat <<YAML | kubectl apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: oficina-config
  namespace: oficina
  labels:
    app.kubernetes.io/part-of: oficina-backend
data:
  DB_URL: "jdbc:postgresql://${RDS}:5432/${DB_NAME}"
  DB_USER: "${DB_USER}"
  SERVER_PORT: "8080"
  SPRING_PROFILES_ACTIVE: ""
  ADMIN_EMAIL: "admin@oficina.local"
  AUTH_BASE_URL: "${AUTH_BASE_URL}"
  NOTIFICACAO_TIPO: "${NOTIFICACAO_TIPO}"
  NOTIFICACAO_REMETENTE: "${MAIL_FROM}"
  MAIL_HOST: "${MAIL_HOST}"
  MAIL_PORT: "${MAIL_PORT}"
  NOTIFICATION_ENDPOINT: "${NOTIFICATION_ENDPOINT}"
  SERVERLESS_JWT_ISSUER: "${SERVERLESS_JWT_ISSUER}"
  SERVERLESS_JWT_AUDIENCE: "${SERVERLESS_JWT_AUDIENCE}"
YAML

# Secret via 'kubectl create secret --from-literal' (em vez de YAML inline) para que
# o kubectl faca o escaping correto de senhas com aspas, barras ou outros caracteres.
SECRET_ARGS=(generic oficina-secrets --namespace oficina
  --from-literal=DB_PASSWORD="${DB_PASSWORD}"
  --from-literal=JWT_SECRET="${JWT_SECRET}"
  --from-literal=ADMIN_PASSWORD="${ADMIN_PASSWORD}"
  --from-literal=SERVERLESS_JWT_PUBLIC_KEY="${SERVERLESS_JWT_PUBLIC_KEY}")
if [[ "$NOTIFICACAO_TIPO" == "smtp" ]]; then
  SECRET_ARGS+=(--from-literal=MAIL_USERNAME="${MAIL_USERNAME}")
  SECRET_ARGS+=(--from-literal=MAIL_PASSWORD="${MAIL_PASSWORD}")
elif [[ "$NOTIFICACAO_TIPO" == "serverless" ]]; then
  SECRET_ARGS+=(--from-literal=NOTIFICATION_API_KEY="${NOTIFICATION_API_KEY}")
fi
kubectl create secret "${SECRET_ARGS[@]}" --dry-run=client -o yaml | kubectl apply -f -

sed "s#image: oficina-backend:latest#image: ${IMAGE}#" "$HERE/app-deployment.yaml" | kubectl apply -f -
kubectl apply -f "$HERE/app-service.yaml"
kubectl apply -f "$HERE/hpa.yaml"
kubectl apply -f "$HERE/pdb.yaml"

echo
echo "==> Deploy aplicado. Acompanhe:"
echo "    kubectl get pods -n oficina -w"
echo "    kubectl get svc oficina-app -n oficina   # pegue o EXTERNAL-IP (ELB)"
echo "    depois: http://<EXTERNAL-IP>/swagger-ui/index.html"
