<#
.SYNOPSIS
    Troca o provedor de pagamento do Orçamento e Pagamento (mercadopago ou fake) e aplica no ambiente.

.DESCRIPTION
    mercadopago: grava as credenciais sandbox (configure-environment.ps1 -ConfigureMercadoPago), roda o CD e mostra a URL do webhook.
    fake: grava PAYMENT_PROVIDER=fake, renova as credenciais nos GitHub Environments e roda o CD. Com o ambiente fora do ar, só grava a variável: a próxima subida já vem no fake.
    Volte ao fake ao terminar o teste do Mercado Pago: o E2E usa o pagamento simulado.

.EXAMPLE
    .\scripts\provedor-pagamento.ps1
    .\scripts\provedor-pagamento.ps1 -Environment production -Provedor fake
#>
[CmdletBinding()]
param(
    # Vazio: o script pergunta qual ambiente.
    [string]$Environment,
    # Vazio: o script pergunta qual provedor.
    [string]$Provedor,
    [string]$RepositoriesRoot,
    [string]$Region = 'us-west-2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'oficina-lab.ps1')

$Repositorio = 'fiap-tc-fase4-oficina-orcamento-pagamento-service'

Assert-OficinaComandos
$Environment = Select-OficinaAmbiente -Valor $Environment
$Provedor = Select-OficinaOpcao -Pergunta 'Qual provedor de pagamento?' -Opcoes @('mercadopago', 'fake') -Valor $Provedor

Assert-OficinaAwsAccess -Ambiente $Environment -Region $Region
$noAr = Test-OficinaCluster -Ambiente $Environment -Region $Region

Write-Host ''
Write-Host "== 1/3 PAYMENT_PROVIDER=$Provedor em $Environment"
if ($Provedor -eq 'mercadopago') {
    if (-not $noAr) {
        throw "O ambiente '$Environment' não está no ar. Suba antes: .\scripts\provision-environment.ps1 -Environment $Environment"
    }
    Update-OficinaCredenciais -Ambiente $Environment -RepositoriesRoot $RepositoriesRoot -ConfigureMercadoPago   # na 1ª vez pede Access Token, Public Key e assinatura do webhook
}
else {
    $set = Invoke-OficinaNative -Command 'gh' -Arguments @('variable', 'set', 'PAYMENT_PROVIDER', '--repo', "$OficinaOwner/$Repositorio", '--env', $Environment, '--body', 'fake')
    if ($set.ExitCode -ne 0) { throw "Falha ao gravar PAYMENT_PROVIDER: $($set.Text)" }
    if (-not $noAr) {
        Write-Host "O ambiente '$Environment' não está no ar: PAYMENT_PROVIDER=fake gravado; a próxima subida já vem no fake." -ForegroundColor Green
        return
    }
    Update-OficinaCredenciais -Ambiente $Environment -RepositoriesRoot $RepositoriesRoot
}

$gate = Get-OficinaGitHubVar -Repositorio $Repositorio -Nome 'DEPLOY_ENABLED' -Ambiente $Environment
if ($gate -ne 'true') {
    throw "DEPLOY_ENABLED do Orçamento e Pagamento em '$Environment' está '$gate'. Rode .\scripts\lab-reiniciado.ps1 -Environment $Environment e este script de novo."
}

Write-Host ''
Write-Host '== 2/3 nós do EKS e CD do Orçamento e Pagamento'
Repair-OficinaNodes -Ambiente $Environment -Region $Region
$run = Invoke-OficinaWorkflow -Repositorio $Repositorio -Workflow 'cd.yml' -Ref (Get-OficinaBranch -Ambiente $Environment)

Write-Host ''
Write-Host '== 3/3 conferência'
Connect-OficinaCluster -Ambiente $Environment -Region $Region
$pagamento = Get-OficinaPagamentoConfig -Ambiente $Environment
if ($null -eq $pagamento -or $pagamento.Provedor -ne $Provedor) {
    throw "O ConfigMap ainda mostra '$(if ($pagamento) { $pagamento.Provedor })'. Confira o run $run"
}
Write-Host "Pagamento em '$Environment': $($pagamento.Provedor)" -ForegroundColor Green
if ($Provedor -eq 'mercadopago') {
    Write-Host ''
    Write-Host 'Cadastre (ou confira) o webhook no painel do Mercado Pago, modo de teste:'
    Write-Host "  URL:    $($pagamento.Webhook)"
    Write-Host '  Evento: só Pagamentos (legacy). Não gere uma assinatura nova.'
    Write-Host '  O painel aceita uma URL por vez: aponte para o ambiente que está testando.'
    Write-Host "Depois: .\scripts\pagamento-mercadopago.ps1 -Environment $Environment"
    Write-Host "Ao terminar: .\scripts\provedor-pagamento.ps1 -Environment $Environment -Provedor fake"
}
