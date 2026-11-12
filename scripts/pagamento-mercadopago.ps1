<#
.SYNOPSIS
    Teste manual do Mercado Pago (sandbox): cria uma OS de R$ 5,00 até AGUARDANDO_PAGAMENTO, mostra o link e confere se ela ficou PAGA.

.DESCRIPTION
    Antes: .\scripts\provedor-pagamento.ps1 -Provedor mercadopago (e o webhook cadastrado no painel).
    O link vai para a área de transferência. Pague numa janela anônima com o comprador de teste e o titular APRO,
    volte ao PowerShell e tecle Enter: o script confere se o webhook chegou e se a Saga marcou a OS como PAGA.
    Para conferir depois uma OS já paga: -NumeroOs OS-...

.EXAMPLE
    .\scripts\pagamento-mercadopago.ps1
    .\scripts\pagamento-mercadopago.ps1 -Environment production -NumeroOs OS-112026-000001
#>
[CmdletBinding()]
param(
    # Vazio: o script pergunta qual ambiente.
    [string]$Environment,
    [string]$NumeroOs,
    [string]$SecretsRoot,
    [string]$Region = 'us-west-2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'oficina-lab.ps1')

$Repo = 'fiap-tc-fase4-oficina-os-service'

function Get-Var($name) {
    $value = Get-OficinaGitHubVar -Repositorio $Repo -Nome $name -Ambiente $Environment
    if ([string]::IsNullOrWhiteSpace($value)) { return $null }
    $value.Trim()
}
function Get-Lb($svc, $ns) {
    $url = Get-OficinaLbUrl -Service $svc -Namespace $ns
    if ($null -eq $url) { throw "LoadBalancer de $svc sem hostname em $ns." }
    $url
}
function Call($method, $url, $token, $body) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Method = $method; Uri = $url; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $body) { $params['Body'] = ($body | ConvertTo-Json -Compress); $params['ContentType'] = 'application/json' }
    Invoke-RestMethod @params
}
function Wait-For($label, $expected, [scriptblock]$read, [int]$attempts = 40) {
    $current = $null
    for ($i = 1; $i -le $attempts; $i++) {
        try { $current = & $read } catch { $current = "HTTP $([int]$_.Exception.Response.StatusCode)" }
        if ($current -eq $expected) { Write-Host "  $label -> $current"; return }
        Start-Sleep 3
    }
    throw "$label não chegou em $expected (último valor: $current)."
}
function Confirm-Paga($numero) {
    Write-Host "== Conferindo a OS $numero"
    Wait-For 'OS' 'PAGA' { (Call GET "$backend/ordens-servico/$numero" $adminToken $null).status } 60
    $p = Call GET "$pag/pagamentos/$numero" $adminToken $null
    Write-Host "  Pagamento: status=$($p.status) provedor=$($p.provedor) comprovante=$($p.comprovante)"
    Write-Host "Mercado Pago validado em '$Environment': o webhook chegou e a Saga marcou a OS $numero como PAGA." -ForegroundColor Green
    Write-Host "Ao terminar: .\scripts\provedor-pagamento.ps1 -Environment $Environment -Provedor fake"
}

Assert-OficinaComandos
$Environment = Select-OficinaAmbiente -Valor $Environment
Assert-OficinaAwsAccess -Ambiente $Environment -Region $Region
Connect-OficinaCluster -Ambiente $Environment -Region $Region

Write-Host "== Lendo endereços de $Environment"
$config = Get-OficinaPagamentoConfig -Ambiente $Environment
if ($null -eq $config) { throw "O Orçamento e Pagamento não está no ar em '$Environment'." }
if ($config.Provedor -ne 'mercadopago') {
    throw "Pagamento em '$($config.Provedor)'. Ligue antes: .\scripts\provedor-pagamento.ps1 -Environment $Environment -Provedor mercadopago"
}
Write-Host "  webhook que deve estar no painel: $($config.Webhook)"
$backend = Get-Var BACKEND_BASE_URL
$apiGw   = Get-Var API_GATEWAY_BASE_URL
$auth    = Get-Var AUTH_BASE_URL
if ($null -eq $auth) { $auth = $apiGw }
if ($null -eq $backend -or $null -eq $apiGw) { throw "BACKEND_BASE_URL ou API_GATEWAY_BASE_URL ausente no Environment '$Environment'." }
$diag    = Get-Lb oficina-diagnostico-reparo "oficina-diagnostico-reparo-$Environment"
$pag     = Get-Lb oficina-orcamento-pagamento "oficina-orcamento-pagamento-$Environment"

$adminEmail = Get-Var ADMIN_EMAIL
if ($null -eq $adminEmail) { $adminEmail = 'admin@oficina.local' }
if ([string]::IsNullOrWhiteSpace($SecretsRoot)) {
    $SecretsRoot = if ($env:OS -eq 'Windows_NT') { 'C:\fiap-secrets' } else { Join-Path $HOME '.oficina-secrets' }
}
$secretFile = Join-Path (Join-Path $SecretsRoot "oficina-$Environment") 'backend-admin-password.clixml'
$secure = if (Test-Path $secretFile) { Import-Clixml $secretFile } else { Read-Host 'Senha do admin do backend' -AsSecureString }
$adminPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

$adminToken = (Call POST "$backend/auth/login" $null @{ email = $adminEmail; senha = $adminPassword }).accessToken

if ($NumeroOs) {
    Confirm-Paga $NumeroOs
    return
}

function New-Cpf([string]$base) {
    $d = @($base.ToCharArray() | ForEach-Object { [int][string]$_ })
    foreach ($start in 10, 11) {
        $sum = 0; for ($i = 0; $i -lt $d.Count; $i++) { $sum += $d[$i] * ($start - $i) }
        $r = ($sum * 10) % 11; if ($r -eq 10) { $r = 0 }; $d += $r
    }
    -join $d
}
$runId = [string][DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$cpf   = New-Cpf $runId.Substring($runId.Length - 9)
$plate = "TST$($runId.Substring($runId.Length - 3, 1))A$($runId.Substring($runId.Length - 2))"
$tecEmail = "tecnico-$runId@teste.local"
# Senha aleatória por execução: a conta sintética do técnico não tem senha conhecida fora deste script.
$tecSenha = 'Tec#' + (-join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ }))

Write-Host '== Cadastros'
Call POST "$backend/usuarios" $adminToken @{ email = $tecEmail; senha = $tecSenha; papel = 'TECNICO_DA_OFICINA' } | Out-Null
$tecToken  = (Call POST "$backend/auth/login" $null @{ email = $tecEmail; senha = $tecSenha }).accessToken
$clienteId = (Call POST "$backend/clientes" $adminToken @{ nome = "Cliente Mercado Pago $runId"; documento = $cpf; email = "mp-$runId@teste.local"; telefone = '11999999999' }).idCliente
Call POST "$backend/veiculos" $adminToken @{ placa = $plate; marca = 'Fiat'; modelo = 'Uno'; ano = 2020; idCliente = $clienteId } | Out-Null
# Pagamentos de teste do Mercado Pago ficam abaixo de R$ 10.
$servicoId = (Call POST "$backend/servicos" $adminToken @{ nome = "Teste Mercado Pago $runId"; descricao = 'Teste do Mercado Pago'; precoBase = 5.00 }).idServico

Write-Host '== Saga: diagnostico'
$os = (Call POST "$backend/ordens-servico" $adminToken @{ idCliente = $clienteId; placa = $plate; descricaoProblema = 'Teste do Mercado Pago' }).numero
Write-Host "  OS criada: $os"
Wait-For 'Execução' 'NA_FILA_DIAGNOSTICO' { (Call GET "$diag/execucoes/$os" $tecToken $null).etapa }
Call POST "$diag/execucoes/$os/iniciar-diagnostico" $tecToken $null | Out-Null
Wait-For 'OS' 'EM_DIAGNOSTICO' { (Call GET "$backend/ordens-servico/$os" $adminToken $null).status }
Call POST "$backend/ordens-servico/$os/servicos" $tecToken @{ idServicoSku = $servicoId; quantidade = 1 } | Out-Null
Call POST "$diag/execucoes/$os/concluir-diagnostico" $tecToken @{ diagnostico = 'Teste do Mercado Pago' } | Out-Null
Wait-For 'OS' 'AGUARDANDO_APROVACAO' { (Call GET "$backend/ordens-servico/$os" $adminToken $null).status }

Write-Host '== Cliente aprova o orcamento (login por CPF no API Gateway)'
$ownerToken = (Call POST "$auth/auth/cpf" $null @{ cpf = $cpf }).accessToken
Wait-For 'Orçamento' 'AGUARDANDO_APROVACAO' { (Call GET "$apiGw/orcamentos/$os" $ownerToken $null).status }
Call POST "$apiGw/orcamentos/$os/aprovar" $ownerToken $null | Out-Null
Wait-For 'OS' 'EM_EXECUCAO' { (Call GET "$backend/ordens-servico/$os" $adminToken $null).status }

Write-Host '== Saga: reparo'
Wait-For 'Execução' 'NA_FILA_REPARO' { (Call GET "$diag/execucoes/$os" $tecToken $null).etapa }
Call POST "$diag/execucoes/$os/iniciar-reparo" $tecToken $null | Out-Null
Call POST "$diag/execucoes/$os/concluir-reparo" $tecToken $null | Out-Null
Wait-For 'OS' 'AGUARDANDO_PAGAMENTO' { (Call GET "$backend/ordens-servico/$os" $adminToken $null).status }
Wait-For 'Pagamento' 'PENDENTE' { (Call GET "$apiGw/pagamentos/$os" $ownerToken $null).status }

$p = Call GET "$apiGw/pagamentos/$os" $ownerToken $null
if ($p.provedor -notmatch 'mercado') { throw "Provedor da cobrança: $($p.provedor). Rode .\scripts\provedor-pagamento.ps1 -Environment $Environment -Provedor mercadopago." }
try { $p.linkPagamento | Set-Clipboard } catch { }
Write-Host ''
Write-Host "OS:    $os   Valor: $($p.valor)"
Write-Host "LINK:  $($p.linkPagamento)   (já copiado para a área de transferência)"
Write-Host ''
Write-Host 'Abra o LINK numa janela anônima (Ctrl+Shift+N), entre com o comprador de teste e pague com o Mastercard 5031 4332 1540 6351,'
Write-Host 'titular APRO, CVV 123, validade 11/30, CPF 12345678909.'
Read-Host 'Depois de pagar, volte aqui e tecle Enter para conferir (Ctrl+C sai; confira depois com -NumeroOs)' | Out-Null
Confirm-Paga $os
