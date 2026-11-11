<#
Teste manual do Mercado Pago (sandbox) em producao.
  1a execucao (sem parametros): cria uma OS ate AGUARDANDO_PAGAMENTO e mostra o link de pagamento.
  2a execucao (-NumeroOs ...): depois de pagar no link, confere se a OS ficou PAGA.
Requer: gh logado, aws com credenciais do Lab e kubectl apontando para oficina-production.
#>
param([string]$NumeroOs)

$ErrorActionPreference = 'Stop'
$Repo = 'tiagomiele/fiap-tc-fase4-oficina-os-service'
$TargetEnv = 'production'

function Get-Var($name) { (gh variable get $name --repo $Repo --env $TargetEnv).Trim() }
function Get-Lb($svc, $ns) {
    $h = kubectl get svc $svc -n $ns -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
    if ([string]::IsNullOrWhiteSpace($h)) { throw "LoadBalancer de $svc sem hostname." }
    "http://$h"
}
function Call($method, $url, $token, $body) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{ Method = $method; Uri = $url; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $body) { $params['Body'] = ($body | ConvertTo-Json -Compress); $params['ContentType'] = 'application/json' }
    Invoke-RestMethod @params
}
function Wait-For($label, $expected, [scriptblock]$read, [int]$attempts = 40) {
    for ($i = 1; $i -le $attempts; $i++) {
        try { $current = & $read } catch { $current = "HTTP $([int]$_.Exception.Response.StatusCode)" }
        if ($current -eq $expected) { Write-Host "  $label -> $current"; return }
        Start-Sleep 3
    }
    throw "$label nao chegou em $expected (ultimo valor: $current)."
}

Write-Host '== Lendo enderecos de producao'
$backend = Get-Var BACKEND_BASE_URL
$apiGw   = Get-Var API_GATEWAY_BASE_URL
$auth    = try { Get-Var AUTH_BASE_URL } catch { $apiGw }
if ([string]::IsNullOrWhiteSpace($auth)) { $auth = $apiGw }
$diag    = Get-Lb oficina-diagnostico-reparo oficina-diagnostico-reparo-production
$pag     = Get-Lb oficina-orcamento-pagamento oficina-orcamento-pagamento-production

$adminEmail = try { Get-Var ADMIN_EMAIL } catch { 'admin@oficina.local' }
if ([string]::IsNullOrWhiteSpace($adminEmail)) { $adminEmail = 'admin@oficina.local' }
$secretFile = 'C:\fiap-secrets\oficina-production\backend-admin-password.clixml'
$secure = if (Test-Path $secretFile) { Import-Clixml $secretFile } else { Read-Host 'Senha do admin do backend' -AsSecureString }
$adminPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))

$adminToken = (Call POST "$backend/auth/login" $null @{ email = $adminEmail; senha = $adminPassword }).accessToken

if ($NumeroOs) {
    Write-Host "== Conferindo a OS $NumeroOs"
    Wait-For 'OS' 'PAGA' { (Call GET "$backend/ordens-servico/$NumeroOs" $adminToken $null).status } 60
    $p = Call GET "$pag/pagamentos/$NumeroOs" $adminToken $null
    Write-Host "  Pagamento: status=$($p.status) provedor=$($p.provedor) comprovante=$($p.comprovante)"
    Write-Host 'Mercado Pago validado: o webhook chegou e a Saga marcou a OS como PAGA.'
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
Wait-For 'Execucao' 'NA_FILA_DIAGNOSTICO' { (Call GET "$diag/execucoes/$os" $tecToken $null).etapa }
Call POST "$diag/execucoes/$os/iniciar-diagnostico" $tecToken $null | Out-Null
Wait-For 'OS' 'EM_DIAGNOSTICO' { (Call GET "$backend/ordens-servico/$os" $adminToken $null).status }
Call POST "$backend/ordens-servico/$os/servicos" $tecToken @{ idServicoSku = $servicoId; quantidade = 1 } | Out-Null
Call POST "$diag/execucoes/$os/concluir-diagnostico" $tecToken @{ diagnostico = 'Teste do Mercado Pago' } | Out-Null
Wait-For 'OS' 'AGUARDANDO_APROVACAO' { (Call GET "$backend/ordens-servico/$os" $adminToken $null).status }

Write-Host '== Cliente aprova o orcamento (login por CPF no API Gateway)'
$ownerToken = (Call POST "$auth/auth/cpf" $null @{ cpf = $cpf }).accessToken
Wait-For 'Orcamento' 'AGUARDANDO_APROVACAO' { (Call GET "$apiGw/orcamentos/$os" $ownerToken $null).status }
Call POST "$apiGw/orcamentos/$os/aprovar" $ownerToken $null | Out-Null
Wait-For 'OS' 'EM_EXECUCAO' { (Call GET "$backend/ordens-servico/$os" $adminToken $null).status }

Write-Host '== Saga: reparo'
Wait-For 'Execucao' 'NA_FILA_REPARO' { (Call GET "$diag/execucoes/$os" $tecToken $null).etapa }
Call POST "$diag/execucoes/$os/iniciar-reparo" $tecToken $null | Out-Null
Call POST "$diag/execucoes/$os/concluir-reparo" $tecToken $null | Out-Null
Wait-For 'OS' 'AGUARDANDO_PAGAMENTO' { (Call GET "$backend/ordens-servico/$os" $adminToken $null).status }
Wait-For 'Pagamento' 'PENDENTE' { (Call GET "$apiGw/pagamentos/$os" $ownerToken $null).status }

$p = Call GET "$apiGw/pagamentos/$os" $ownerToken $null
if ($p.provedor -notmatch 'mercado') { Write-Warning "Provedor atual: $($p.provedor). Rode o configure-environment com -ConfigureMercadoPago e o CD do Orcamento e Pagamento antes." }
Write-Host ''
Write-Host "OS:    $os   Valor: $($p.valor)"
Write-Host "LINK:  $($p.linkPagamento)"
Write-Host ''
Write-Host 'Abra o LINK numa janela anonima, entre com a conta Comprador de teste e pague com titular APRO.'
Write-Host "Depois rode:  .\pagamento-mercadopago.ps1 -NumeroOs $os"
