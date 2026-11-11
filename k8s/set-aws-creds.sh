#!/usr/bin/env bash
# Configura as credenciais do AWS Academy (Learner Lab) no shell atual.
#
# As credenciais do AWS Academy sao temporarias e expiram quando o lab para.
# Cole o bloco "AWS CLI" do Academy (AWS Details -> AWS CLI -> Show) no comando
# abaixo, OU defina a variavel AWS_CREDS_BLOCK com o conteudo e rode este script.
#
# Uso (cole o bloco entre as aspas):
#   source ./k8s/set-aws-creds.sh <<'EOF'
#   [default]
#   aws_access_key_id=ASIA...
#   aws_secret_access_key=...
#   aws_session_token=...
#   EOF
#
# IMPORTANTE: rode com 'source' (ou '.'), senao as variaveis nao ficam no shell.
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-west-2}"

if [[ -n "${AWS_CREDS_BLOCK:-}" ]]; then
  txt="$AWS_CREDS_BLOCK"
else
  txt="$(cat)"   # le do stdin (heredoc)
fi

ak="$(printf '%s\n' "$txt" | sed -n 's/.*aws_access_key_id[[:space:]]*=[[:space:]]*\([^[:space:]]*\).*/\1/p' | head -n1)"
sk="$(printf '%s\n' "$txt" | sed -n 's/.*aws_secret_access_key[[:space:]]*=[[:space:]]*\([^[:space:]]*\).*/\1/p' | head -n1)"
st="$(printf '%s\n' "$txt" | sed -n 's/.*aws_session_token[[:space:]]*=[[:space:]]*\([^[:space:]]*\).*/\1/p' | head -n1)"

if [[ -z "$ak" || -z "$sk" || -z "$st" ]]; then
  echo "ERRO: nao encontrei as 3 chaves. Cole o bloco COMPLETO do 'AWS CLI'." >&2
  return 1 2>/dev/null || exit 1
fi

export AWS_ACCESS_KEY_ID="$ak"
export AWS_SECRET_ACCESS_KEY="$sk"
export AWS_SESSION_TOKEN="$st"
export AWS_DEFAULT_REGION="$REGION"

echo "==> Credenciais aplicadas neste shell (regiao $REGION). Validando..."
aws sts get-caller-identity
