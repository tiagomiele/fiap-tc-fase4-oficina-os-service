<#
.SYNOPSIS
    Valida um ambiente no ar contra as especificações da Fase 4 e mostra as evidências.

.DESCRIPTION
    1. credenciais, nós e os 3 serviços prontos;
    2. health UP dos 3 serviços e URL do API Gateway;
    3. pagamento no fake (o E2E usa o pagamento simulado); se estiver no Mercado Pago, oferece voltar ao fake (S/N);
    4. E2E no GitHub Actions: Saga completa até ENTREGUE, segurança do API Gateway, correlationId e, por padrão, carga k6;
       e o último run do BDD da Saga (compensações) na branch do ambiente;
    5. DLQs das 3 filas vazias.
    Renova as credenciais AWS nos GitHub Environments antes do E2E; só altera o ambiente se você aceitar voltar ao fake no passo 3.

.EXAMPLE
    .\scripts\validar-ambiente.ps1
    .\scripts\validar-ambiente.ps1 -Environment production -SemCarga
#>
[CmdletBinding()]
param(
    # Vazio: o script pergunta qual ambiente.
    [string]$Environment,
    # Não roda o smoke de carga k6 depois do Newman.
    [switch]$SemCarga,
    [string]$RepositoriesRoot,
    [string]$Region = 'us-west-2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'oficina-lab.ps1')

Assert-OficinaComandos
$Environment = Select-OficinaAmbiente -Valor $Environment

Write-Host ''
Write-Host '== 1/5 credenciais, nós e serviços'
Assert-OficinaAwsAccess -Ambiente $Environment -Region $Region
if (-not (Test-OficinaCluster -Ambiente $Environment -Region $Region)) {
    throw "O ambiente '$Environment' não está no ar. Para subir: .\scripts\provision-environment.ps1 -Environment $Environment"
}
Connect-OficinaCluster -Ambiente $Environment -Region $Region
if ((Get-OficinaReadyNodeCount) -eq 0) {
    throw "O cluster oficina-$Environment está sem nós. Rode .\scripts\lab-reiniciado.ps1 -Environment $Environment e valide de novo."
}
Wait-OficinaServicos -Ambiente $Environment -Minutos 5

Write-Host ''
Write-Host '== 2/5 health'
$urls = Wait-OficinaHealth -Ambiente $Environment -Minutos 5

Write-Host ''
Write-Host '== 3/5 pagamento'
$pagamento = Get-OficinaPagamentoConfig -Ambiente $Environment
if ($null -eq $pagamento) { throw "ConfigMap do Orçamento e Pagamento não encontrado em oficina-orcamento-pagamento-$Environment." }
if ($pagamento.Provedor -ne 'fake') {
    # O E2E usa o pagamento simulado (/pagamentos/{n}/simular), que só existe no fake.
    Write-Host "  Pagamento em '$($pagamento.Provedor)': o E2E precisa do fake." -ForegroundColor Yellow
    $resposta = Read-Host 'Voltar ao fake agora (roda o CD do Orçamento e Pagamento, de 3 a 15 min)? [S/N]'
    if ("$resposta".Trim().ToLowerInvariant() -notin @('s', 'sim')) {
        throw "Validação interrompida. Para voltar ao fake depois: .\scripts\provedor-pagamento.ps1 -Environment $Environment -Provedor fake"
    }
    & (Join-Path $PSScriptRoot 'provedor-pagamento.ps1') -Environment $Environment -Provedor fake -Region $Region -RepositoriesRoot $RepositoriesRoot
    $pagamento = Get-OficinaPagamentoConfig -Ambiente $Environment
    if ($null -eq $pagamento -or $pagamento.Provedor -ne 'fake') { throw 'O pagamento não voltou ao fake. Veja o run do CD mostrado acima.' }
}
Write-Host '  fake'

Write-Host ''
Write-Host '== credenciais nos GitHub Environments (o E2E roda no GitHub Actions)'
Update-OficinaCredenciais -Ambiente $Environment -RepositoriesRoot $RepositoriesRoot

Write-Host ''
$carga = if ($SemCarga) { 'false' } else { 'true' }
Write-Host "== 4/5 E2E (Newman$(if ($carga -eq 'true') { ' + k6' }))"
$e2e = Invoke-OficinaWorkflow -Repositorio 'fiap-tc-fase4-oficina-os-service' -Workflow 'e2e.yml' -Ref (Get-OficinaBranch -Ambiente $Environment) -Campos @('-f', "environment=$Environment", '-f', "run_load_test=$carga")

# Compensações (estoque insuficiente, orçamento rejeitado, refazer diagnóstico, pagamento recusado): BDD da Saga no CI.
$bdd = Invoke-OficinaNative -Command 'gh' -Arguments @('run', 'list', '--repo', "$OficinaOwner/fiap-tc-fase4-oficina-os-service", '--workflow', 'bdd.yml', '--branch', (Get-OficinaBranch -Ambiente $Environment), '--limit', '1', '--json', 'databaseId,conclusion', '--jq', '.[0]')
$bddRun = if ($bdd.ExitCode -eq 0 -and $bdd.Text -like '{*') { $bdd.Text | ConvertFrom-Json } else { $null }

Write-Host ''
Write-Host '== 5/5 DLQs'
$dlqs = [ordered]@{}
foreach ($fila in $OficinaFilas) {
    $nome = "oficina-$Environment-$fila-dlq"
    $queueUrl = Invoke-OficinaNative -Command 'aws' -Arguments @('sqs', 'get-queue-url', '--region', $Region, '--queue-name', $nome, '--query', 'QueueUrl', '--output', 'text')
    if ($queueUrl.ExitCode -ne 0) {
        $dlqs[$nome] = 'não encontrada'
        continue
    }
    $mensagens = Invoke-OficinaNative -Command 'aws' -Arguments @('sqs', 'get-queue-attributes', '--region', $Region, '--queue-url', $queueUrl.Text, '--attribute-names', 'ApproximateNumberOfMessages', '--query', 'Attributes.ApproximateNumberOfMessages', '--output', 'text')
    $dlqs[$nome] = if ($mensagens.ExitCode -eq 0) { $mensagens.Text } else { 'erro na leitura' }
}
foreach ($entry in $dlqs.GetEnumerator()) { Write-Host "  $($entry.Key): $($entry.Value)" }
$dlqsLimpas = @($dlqs.Values | Where-Object { $_ -ne '0' }).Count -eq 0

Write-Host ''
Write-Host "Validação de '$Environment'" -ForegroundColor Green
Write-Host "  [OK] 3 microsserviços no EKS, health UP:"
foreach ($entry in $urls.GetEnumerator()) { Write-Host "         $($entry.Key): $($entry.Value)" }
Write-Host "  [OK] Saga orquestrada pelo OS Service até ENTREGUE, API Gateway + JWT e correlationId (E2E): $e2e"
Write-Host "       resultado detalhado no artefato newman-$Environment-<run> do run"
if ($carga -eq 'true') { Write-Host "  [OK] carga k6 com 0% de falha: mesmo run" }
if ($null -ne $bddRun -and [string]$bddRun.conclusion -eq 'success') {
    Write-Host "  [OK] compensações da Saga (BDD): https://github.com/$OficinaOwner/fiap-tc-fase4-oficina-os-service/actions/runs/$($bddRun.databaseId) (artefato bdd-report)"
}
else {
    Write-Host "  [ATENÇÃO] BDD da Saga sem run verde na branch $(Get-OficinaBranch -Ambiente $Environment): veja a seção 19.2 do Bootstrap" -ForegroundColor Yellow
}
if ($dlqsLimpas) {
    Write-Host '  [OK] DLQs vazias: nenhuma mensagem falhou 5 vezes'
}
else {
    Write-Host '  [ATENÇÃO] há mensagem em DLQ: veja as seções 16.5 e 16.6 do Bootstrap (a 16.6 deixa uma de propósito)' -ForegroundColor Yellow
}
Write-Host '  Pagamento real (Mercado Pago sandbox): .\scripts\provedor-pagamento.ps1 -Provedor mercadopago e .\scripts\pagamento-mercadopago.ps1'
Write-Host '  Observabilidade: New Relic, seção 18 do Bootstrap (APM dos 3 serviços, cluster e Synthetic)'
