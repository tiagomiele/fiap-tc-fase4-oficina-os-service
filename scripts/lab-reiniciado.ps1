<#
.SYNOPSIS
    Retoma um ambiente depois do Start Lab: renova as credenciais, recupera os nós do EKS e confere os 3 serviços.

.DESCRIPTION
    Use toda vez que clicar em Start Lab (sessão nova, ExpiredToken ou voc-cancel-cred) com o ambiente já no ar.
    1. espera o bloco [default] válido na área de transferência;
    2. renova as credenciais no computador, no HCP e nos GitHub Environments (configure-environment.ps1);
    3. liga ou recria os nós do EKS que não voltaram (seção 21.2 do Bootstrap);
    4. espera os 3 serviços ficarem prontos e responderem UP.
    Se o ambiente não estiver no ar, para sem alterar nada e mostra o comando para subir.

.EXAMPLE
    .\scripts\lab-reiniciado.ps1
    .\scripts\lab-reiniciado.ps1 -Environment production
#>
[CmdletBinding()]
param(
    # Vazio: o script pergunta qual ambiente.
    [string]$Environment,
    [string]$RepositoriesRoot,
    [string]$Region = 'us-west-2',
    # Só confere e recupera os nós do EKS (sem renovar credenciais nem conferir os serviços).
    [switch]$NodesOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'oficina-lab.ps1')

Assert-OficinaComandos
$Environment = Select-OficinaAmbiente -Valor $Environment

if (-not $NodesOnly) {
    Write-Host ''
    Write-Host '== 1/4 credenciais do Lab'
    Wait-OficinaLabCredentials -Region $Region | Out-Null
    if (-not (Test-OficinaCluster -Ambiente $Environment -Region $Region)) {
        Write-Host "O ambiente '$Environment' não está no ar (o cluster oficina-$Environment não existe). Nada foi alterado." -ForegroundColor Yellow
        Write-Host "Para subir: .\scripts\provision-environment.ps1 -Environment $Environment"
        return
    }
    $configure = @{ Environment = $Environment; UseAwsAcademyDisposableProductionProfile = $true }
    if (-not [string]::IsNullOrWhiteSpace($RepositoriesRoot)) { $configure.RepositoriesRoot = $RepositoriesRoot }
    & (Join-Path $PSScriptRoot 'configure-environment.ps1') @configure
}

Write-Host ''
Write-Host '== 2/4 nós do EKS'
Repair-OficinaNodes -Ambiente $Environment -Region $Region
if ($NodesOnly) { return }

Write-Host ''
Write-Host '== 3/4 serviços'
Wait-OficinaServicos -Ambiente $Environment

Write-Host ''
Write-Host '== 4/4 health'
$urls = Wait-OficinaHealth -Ambiente $Environment
$pagamento = Get-OficinaPagamentoConfig -Ambiente $Environment

Write-Host ''
Write-Host "Ambiente '$Environment' pronto para uso." -ForegroundColor Green
foreach ($entry in $urls.GetEnumerator()) { Write-Host "  $($entry.Key): $($entry.Value)" }
if ($null -ne $pagamento) { Write-Host "  Pagamento: $($pagamento.Provedor)" }
Write-Host "Próximo passo: .\scripts\validar-ambiente.ps1 -Environment $Environment"
