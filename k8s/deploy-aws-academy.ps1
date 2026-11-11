<#
.SYNOPSIS
  Faz o deploy da aplicacao no EKS do AWS Academy usando o RDS (banco gerenciado).

.DESCRIPTION
  Na Fase 3, o banco gerenciado pertence ao repositório
  fiap-tc-fase4-oficina-database-infra. A aplicação usa o RDS provisionado por ele.

  Este script:
    1. Descobre o endpoint do RDS automaticamente (evita o bug de host vazio).
    2. Cria namespace, ConfigMap (apontando pro RDS), Secret (com a senha do RDS),
       Deployment, Service (LoadBalancer) e HPA.
    3. Implanta somente a aplicacao; o PostgreSQL permanece no RDS.

.PARAMETER DbPassword
  A MESMA senha que voce passou na variavel `db_password` do Terraform
  (= senha master do RDS). Obrigatoria.

.PARAMETER Region
  Regiao AWS. Padrao: us-west-2.

.PARAMETER Image
  Imagem do container. Padrao: ghcr.io/tiagomiele/fiap-tc-fase4-oficina-os-service:latest

.PARAMETER JwtSecret
  Segredo usado para assinar os JWT. Se nao informado, e gerado um valor
  aleatorio forte a cada execucao.

.PARAMETER AdminPassword
  Senha do usuario admin inicial. Troque por um valor forte.

.PARAMETER DbInstanceId
  Identificador da instancia RDS criada pelo Terraform. Padrao: oficina-homolog-db.

.PARAMETER MailHost
  Host SMTP para notificacao por e-mail (ex.: sandbox.smtp.mailtrap.io). Se informado,
  ativa o modo SMTP (NOTIFICACAO_TIPO=smtp); caso contrario usa o modo 'log' (fic ticio).

.PARAMETER MailPort
  Porta SMTP. Padrao: 587.

.PARAMETER MailUser
  Usuario SMTP (no Mailtrap, o 'Username' do inbox).

.PARAMETER MailPassword
  Senha SMTP (no Mailtrap, o 'Password' do inbox).

.PARAMETER MailFrom
  Remetente dos e-mails. Padrao: nao-responder@oficina.local.

.EXAMPLE
  ./deploy-aws-academy.ps1 -DbPassword "<sua-senha-do-rds>"

.EXAMPLE
  # Com e-mail real via Mailtrap (sandbox):
  ./deploy-aws-academy.ps1 -DbPassword "<senha-rds>" `
    -MailHost "sandbox.smtp.mailtrap.io" -MailPort "587" `
    -MailUser "<usuario-mailtrap>" -MailPassword "<senha-mailtrap>"
#>
param(
  [Parameter(Mandatory = $true)]
  [string]$DbPassword,
  [string]$Region        = "us-west-2",
  [string]$Image         = "ghcr.io/tiagomiele/fiap-tc-fase4-oficina-os-service:latest",
  [string]$JwtSecret     = $env:JWT_SECRET,
  [string]$AdminPassword = $env:ADMIN_PASSWORD,
  [string]$DbInstanceId  = "oficina-homolog-db",
  [string]$DbName        = "oficina",
  [string]$DbUser        = "oficina_admin",
  [string]$MailHost      = $env:MAIL_HOST,
  [string]$MailPort      = "587",
  [string]$MailUser      = $env:MAIL_USERNAME,
  [string]$MailPassword          = $env:MAIL_PASSWORD,
  [string]$MailFrom              = "nao-responder@oficina.local",
  [string]$AuthBaseUrl           = $env:AUTH_BASE_URL,
  [string]$NotificationEndpoint  = $env:NOTIFICATION_ENDPOINT,
  [string]$NotificationApiKey    = $env:NOTIFICATION_API_KEY,
  [string]$ServerlessJwtPublicKey = $env:SERVERLESS_JWT_PUBLIC_KEY,
  [string]$ServerlessJwtIssuer    = "fiap-tc-fase4-oficina-auth-serverless",
  [string]$ServerlessJwtAudience  = "oficina-backend"
)

if ([string]::IsNullOrWhiteSpace($ServerlessJwtPublicKey)) {
  throw "ServerlessJwtPublicKey e obrigatoria para validar os tokens emitidos pela autenticacao."
}

if (-not [string]::IsNullOrWhiteSpace($NotificationEndpoint)) {
  if ([string]::IsNullOrWhiteSpace($NotificationApiKey)) {
    throw "NotificationApiKey e obrigatoria quando NotificationEndpoint for informado."
  }
  $NotificacaoTipo = "serverless"
  Write-Host "==> Notificacao: modo serverless" -ForegroundColor Green
} elseif (-not [string]::IsNullOrWhiteSpace($MailHost)) {
  $NotificacaoTipo = "smtp"
  Write-Host "==> Notificacao: modo 'smtp' via $MailHost`:$MailPort" -ForegroundColor Green
} else {
  $NotificacaoTipo = "log"
  Write-Host "==> Notificacao: modo 'log'" -ForegroundColor Yellow
}

if ([string]::IsNullOrWhiteSpace($JwtSecret)) {
  $bytes = New-Object 'System.Byte[]' 32
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  $JwtSecret = [Convert]::ToBase64String($bytes)
  Write-Warning "JWT_SECRET nao informado; gerando um valor aleatorio forte para esta execucao."
}
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
  $AdminPassword = "admin123"
  Write-Warning "ADMIN_PASSWORD nao informado; usando valor de DESENVOLVIMENTO. Em producao passe -AdminPassword ou \$env:ADMIN_PASSWORD."
}

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "==> Descobrindo o endpoint do RDS ($DbInstanceId)..." -ForegroundColor Cyan
$rds = (aws rds describe-db-instances --region $Region --db-instance-identifier $DbInstanceId --query "DBInstances[0].Endpoint.Address" --output text).Trim()
if ([string]::IsNullOrWhiteSpace($rds) -or $rds -eq "None") {
  throw "Endpoint do RDS vazio. A instancia '$DbInstanceId' existe e o lab esta com credenciais validas? (aws sts get-caller-identity)"
}
$status = (aws rds describe-db-instances --region $Region --db-instance-identifier $DbInstanceId --query "DBInstances[0].DBInstanceStatus" --output text).Trim()
Write-Host "    RDS: $rds  (status: $status)" -ForegroundColor Green
if ($status -ne "available") {
  Write-Warning "RDS nao esta 'available' ainda. Aguarde e rode de novo se a app nao subir."
}

# 1) namespace
kubectl apply -f (Join-Path $here "namespace.yaml")

# 2) ConfigMap apontando pro RDS (note as chaves em ${rds} pra evitar o $rds:5432 do PowerShell)
@"
apiVersion: v1
kind: ConfigMap
metadata:
  name: oficina-config
  namespace: oficina
  labels:
    app.kubernetes.io/part-of: oficina-backend
data:
  DB_URL: "jdbc:postgresql://${rds}:5432/${DbName}"
  DB_USER: "${DbUser}"
  SERVER_PORT: "8080"
  SPRING_PROFILES_ACTIVE: ""
  ADMIN_EMAIL: "admin@oficina.local"
  AUTH_BASE_URL: "${AuthBaseUrl}"
  NOTIFICACAO_TIPO: "${NotificacaoTipo}"
  NOTIFICACAO_REMETENTE: "${MailFrom}"
  MAIL_HOST: "${MailHost}"
  MAIL_PORT: "${MailPort}"
  NOTIFICATION_ENDPOINT: "${NotificationEndpoint}"
  SERVERLESS_JWT_ISSUER: "${ServerlessJwtIssuer}"
  SERVERLESS_JWT_AUDIENCE: "${ServerlessJwtAudience}"
"@ | kubectl apply -f -

# 3) Secret com a MESMA senha do RDS.
# Usamos 'kubectl create secret --from-literal' (em vez de YAML inline) para que o
# kubectl faca o escaping correto de senhas com aspas, barras ou outros caracteres.
$secretArgs = @(
  "generic", "oficina-secrets", "--namespace", "oficina",
  "--from-literal=DB_PASSWORD=$DbPassword",
  "--from-literal=JWT_SECRET=$JwtSecret",
  "--from-literal=ADMIN_PASSWORD=$AdminPassword",
  "--from-literal=SERVERLESS_JWT_PUBLIC_KEY=$ServerlessJwtPublicKey"
)
if ($NotificacaoTipo -eq "smtp") {
  $secretArgs += "--from-literal=MAIL_USERNAME=$MailUser"
  $secretArgs += "--from-literal=MAIL_PASSWORD=$MailPassword"
} elseif ($NotificacaoTipo -eq "serverless") {
  $secretArgs += "--from-literal=NOTIFICATION_API_KEY=$NotificationApiKey"
}
kubectl create secret @secretArgs --dry-run=client -o yaml | kubectl apply -f -

# 4) App (Deployment + Service + HPA). NAO aplicamos postgres-*.yaml.
$deploy = Get-Content (Join-Path $here "app-deployment.yaml") -Raw
$deploy = $deploy -replace 'image:\s*oficina-backend:latest', "image: $Image"
$deploy | kubectl apply -f -
kubectl apply -f (Join-Path $here "app-service.yaml")
kubectl apply -f (Join-Path $here "hpa.yaml")
kubectl apply -f (Join-Path $here "pdb.yaml")

Write-Host ""
Write-Host "==> Deploy aplicado. Acompanhe os pods:" -ForegroundColor Cyan
Write-Host "    kubectl get pods -n oficina -w"
Write-Host "==> URL publica (aguarde ~2-4 min o ELB):" -ForegroundColor Cyan
Write-Host "    kubectl get svc oficina-app -n oficina"
Write-Host "    depois: http://<EXTERNAL-IP>/swagger-ui/index.html"
