#!/bin/sh
# Filas da Saga (com DLQ) e tabela do Diagnóstico e Reparo, iguais às do database-infra.
set -e
for fila in os-eventos diagnostico-reparo-comandos orcamento-pagamento-comandos; do
  awslocal sqs create-queue --queue-name "$fila-dlq" >/dev/null
  dlq_arn="arn:aws:sqs:us-east-1:000000000000:$fila-dlq"
  awslocal sqs create-queue --queue-name "$fila" \
    --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$dlq_arn\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}" >/dev/null
done
awslocal dynamodb create-table --table-name oficina-bdd-execucoes \
  --billing-mode PAY_PER_REQUEST \
  --attribute-definitions AttributeName=numeroOs,AttributeType=S AttributeName=etapa,AttributeType=S AttributeName=entradaFilaEm,AttributeType=S \
  --key-schema AttributeName=numeroOs,KeyType=HASH \
  --global-secondary-indexes '[{"IndexName":"etapa-entradaFilaEm-index","KeySchema":[{"AttributeName":"etapa","KeyType":"HASH"},{"AttributeName":"entradaFilaEm","KeyType":"RANGE"}],"Projection":{"ProjectionType":"ALL"}}]' >/dev/null
echo "LocalStack pronto"
