<#
.SYNOPSIS
  Configura as credenciais do AWS Academy (Learner Lab) no terminal atual.

.DESCRIPTION
  As credenciais do AWS Academy sao temporarias e expiram quando o lab para.
  Este helper le o bloco "AWS CLI" do Academy (que voce copiou para a area de
  transferencia) e exporta AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY /
  AWS_SESSION_TOKEN nesta sessao do PowerShell, depois valida com
  'aws sts get-caller-identity'.

.EXAMPLE
  # 1) AWS Academy -> AWS Details -> AWS CLI -> Show -> botao de copiar
  # 2) rode:
  ./k8s/set-aws-creds.ps1

.NOTES
  Vale so para a janela atual do PowerShell. Abriu outra janela? rode de novo.
#>
param(
  [string]$Region = "us-west-2"
)

$ErrorActionPreference = "Stop"

$txt = Get-Clipboard -Raw
if ([string]::IsNullOrWhiteSpace($txt)) {
  throw "Area de transferencia vazia. No AWS Academy: AWS Details -> AWS CLI -> Show -> copie o bloco e rode de novo."
}

$ak = [regex]::Match($txt, 'aws_access_key_id\s*=\s*(\S+)').Groups[1].Value
$sk = [regex]::Match($txt, 'aws_secret_access_key\s*=\s*(\S+)').Groups[1].Value
$st = [regex]::Match($txt, 'aws_session_token\s*=\s*(\S+)').Groups[1].Value

if ([string]::IsNullOrWhiteSpace($ak) -or [string]::IsNullOrWhiteSpace($sk) -or [string]::IsNullOrWhiteSpace($st)) {
  throw "Nao encontrei as 3 chaves no texto copiado. Copie o bloco COMPLETO do 'AWS CLI' (com aws_access_key_id, aws_secret_access_key e aws_session_token)."
}

$env:AWS_ACCESS_KEY_ID     = $ak
$env:AWS_SECRET_ACCESS_KEY = $sk
$env:AWS_SESSION_TOKEN     = $st
$env:AWS_DEFAULT_REGION    = $Region

Write-Host "==> Credenciais aplicadas nesta sessao (regiao $Region). Validando..." -ForegroundColor Cyan
aws sts get-caller-identity
