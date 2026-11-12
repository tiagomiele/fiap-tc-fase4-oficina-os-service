[CmdletBinding()]
param(
    # Vazio: o script pergunta qual ambiente subir.
    [string]$Environment,
    [ValidateSet('configure', 'kubernetes', 'db-os', 'db-orcamento-pagamento', 'db-diagnostico-reparo', 'configure-bancos', 'nos', 'labrole', 'auth', 'configure-auth', 'cd-os', 'cd-diagnostico-reparo', 'cd-orcamento-pagamento', 'auth-rotas', 'kubernetes-synthetic', 'configure-final', 'health', 'e2e')]
    [string]$StartAt = 'configure',
    [switch]$RunE2E,
    [switch]$RunLoadTest,
    [string]$RepositoriesRoot,
    [string]$Region = 'us-west-2',
    [int]$PollSeconds = 30,
    [int]$RunTimeoutMinutes = 90,
    [int]$DispatchTimeoutSeconds = 120,
    [int]$HealthTimeoutMinutes = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'oficina-lab.ps1')

# Mesma ordem do Bootstrap (seções 7.2 a 15): cada etapa depende dos outputs da anterior.
$ProvisionSteps = @(
    @{ Name = 'configure';              Kind = 'configure'; Initial = $true }
    @{ Name = 'kubernetes';             Kind = 'workflow';  Repository = 'fiap-tc-fase4-oficina-kubernetes-infra';            Workflow = 'deploy-{0}.yml' }
    @{ Name = 'db-os';                  Kind = 'workflow';  Repository = 'fiap-tc-fase4-oficina-database-infra';              Workflow = 'os.yml' }
    @{ Name = 'db-orcamento-pagamento'; Kind = 'workflow';  Repository = 'fiap-tc-fase4-oficina-database-infra';              Workflow = 'orcamento-pagamento.yml' }
    @{ Name = 'db-diagnostico-reparo';  Kind = 'workflow';  Repository = 'fiap-tc-fase4-oficina-database-infra';              Workflow = 'diagnostico-reparo.yml' }
    @{ Name = 'configure-bancos';       Kind = 'configure'; AssertDeployGates = $true }
    @{ Name = 'nos';                    Kind = 'nodes' }
    @{ Name = 'labrole';                Kind = 'labrole' }
    @{ Name = 'auth';                   Kind = 'workflow';  Repository = 'fiap-tc-fase4-oficina-auth-serverless';             Workflow = 'terraform-deploy.yml' }
    @{ Name = 'configure-auth';         Kind = 'configure' }
    @{ Name = 'cd-os';                  Kind = 'workflow';  Repository = 'fiap-tc-fase4-oficina-os-service';                  Workflow = 'cd.yml'; DeployGate = $true }
    @{ Name = 'cd-diagnostico-reparo';  Kind = 'workflow';  Repository = 'fiap-tc-fase4-oficina-diagnostico-reparo-service';  Workflow = 'cd.yml'; DeployGate = $true }
    @{ Name = 'cd-orcamento-pagamento'; Kind = 'workflow';  Repository = 'fiap-tc-fase4-oficina-orcamento-pagamento-service'; Workflow = 'cd.yml'; DeployGate = $true }
    @{ Name = 'auth-rotas';             Kind = 'workflow';  Repository = 'fiap-tc-fase4-oficina-auth-serverless';             Workflow = 'terraform-deploy.yml' }
    @{ Name = 'kubernetes-synthetic';   Kind = 'workflow';  Repository = 'fiap-tc-fase4-oficina-kubernetes-infra';            Workflow = 'deploy-{0}.yml' }
    @{ Name = 'configure-final';        Kind = 'configure' }
    @{ Name = 'health';                 Kind = 'health' }
    @{ Name = 'e2e';                    Kind = 'workflow';  Repository = 'fiap-tc-fase4-oficina-os-service';                  Workflow = 'e2e.yml'; Optional = $true }
)
$GitHubOwner = 'tiagomiele'
$DeployGateRepositories = @('fiap-tc-fase4-oficina-os-service', 'fiap-tc-fase4-oficina-orcamento-pagamento-service', 'fiap-tc-fase4-oficina-diagnostico-reparo-service')
$HealthServices = @(
    @{ Name = 'OS';                    Service = 'oficina-app';                 Namespace = 'oficina-{0}' }
    @{ Name = 'Diagnóstico e Reparo';  Service = 'oficina-diagnostico-reparo';  Namespace = 'oficina-diagnostico-reparo-{0}' }
    @{ Name = 'Orçamento e Pagamento'; Service = 'oficina-orcamento-pagamento'; Namespace = 'oficina-orcamento-pagamento-{0}' }
)

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory)][string]$Command,
        [string[]]$Arguments = @()
    )

    # stderr de comando nativo vira erro com ErrorActionPreference=Stop no Windows PowerShell 5.1.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& $Command @Arguments 2>&1 | ForEach-Object { "$_" })
        $exitCode = $global:LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previous
    }
    [pscustomobject]@{ ExitCode = $exitCode; Output = $output }
}

function ConvertFrom-NativeJson {
    param([Parameter(Mandatory)][object]$Result)

    $text = ($Result.Output -join [Environment]::NewLine).Trim()
    if ([string]::IsNullOrWhiteSpace($text)) {
        return $null
    }
    $text | ConvertFrom-Json
}

function Resolve-ProvisionEnvironment {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        Write-Host 'Qual ambiente subir?'
        Write-Host '  1) homolog'
        Write-Host '  2) production'
        $Value = Read-Host 'Escolha 1 ou 2 (ou digite o nome)'
    }
    switch ($Value.Trim().ToLowerInvariant()) {
        { $_ -in @('1', 'homolog') } { return 'homolog' }
        { $_ -in @('2', 'production') } { return 'production' }
        default { throw "Ambiente inválido: '$Value'. Use homolog ou production." }
    }
}

function Get-ProvisionStepsFrom {
    param(
        [Parameter(Mandatory)][object[]]$Steps,
        [Parameter(Mandatory)][string]$StartAt,
        [bool]$IncludeOptional = $false
    )

    $names = @($Steps | ForEach-Object { $_.Name })
    $index = [Array]::IndexOf($names, $StartAt)
    if ($index -lt 0) {
        throw "Etapa desconhecida: $StartAt"
    }
    @($Steps[$index..($Steps.Count - 1)] | Where-Object { $IncludeOptional -or -not $_.ContainsKey('Optional') -or $_.Name -eq $StartAt })
}

function Get-ProvisionPlan {
    param(
        [Parameter(Mandatory)][object[]]$Steps,
        [Parameter(Mandatory)][string]$StartAt,
        [bool]$IncludeOptional = $false
    )

    $plan = @(Get-ProvisionStepsFrom -Steps $Steps -StartAt $StartAt -IncludeOptional $IncludeOptional)
    $names = @($Steps | ForEach-Object { $_.Name })
    # Ao retomar, renova primeiro as credenciais do Lab no computador, no HCP e nos GitHub Environments.
    if ($plan[0].Kind -eq 'configure') {
        $head = @($plan[0])
        $rest = @($plan | Select-Object -Skip 1)
    }
    else {
        $head = @(@{ Name = 'credenciais'; Kind = 'configure' })
        $rest = $plan
    }
    # Retomando depois dos nós: um Start Lab no meio pode ter derrubado os nós do EKS.
    if ([Array]::IndexOf($names, $StartAt) -gt [Array]::IndexOf($names, 'nos')) {
        $head += @{ Name = 'nos'; Kind = 'nodes' }
    }
    @($head + $rest)
}

function Get-WorkflowRef {
    param([Parameter(Mandatory)][string]$EnvironmentName)

    # Os workflows escolhem o ambiente pela branch: homolog → homolog, main → production.
    if ($EnvironmentName -eq 'production') { 'main' } else { 'homolog' }
}

function Get-WorkflowFields {
    param(
        [Parameter(Mandatory)][hashtable]$Step,
        [Parameter(Mandatory)][string]$EnvironmentName,
        [bool]$LoadTest = $false
    )

    if ($Step.Workflow -ne 'e2e.yml') {
        return @()
    }
    @('-f', "environment=$EnvironmentName", '-f', "run_load_test=$($LoadTest.ToString().ToLowerInvariant())")
}

function Get-ConfigureArguments {
    param(
        [Parameter(Mandatory)][string]$EnvironmentName,
        [bool]$Initial = $false,
        [bool]$ReadClipboard = $true,
        [string]$CredentialsFile,
        [string]$RepositoriesRootPath
    )

    $arguments = @{ Environment = $EnvironmentName }
    if (-not [string]::IsNullOrWhiteSpace($RepositoriesRootPath)) {
        $arguments.RepositoriesRoot = $RepositoriesRootPath
    }
    # Toda execução regrava os workspaces de produção: sem o perfil descartável, os RDS voltariam a Multi-AZ e deletion protection.
    $arguments.UseAwsAcademyDisposableProductionProfile = $true
    if ($Initial) {
        $arguments.ConfigureSonarCloud = $true
        $arguments.ConfigureNewRelic = $true
    }
    if (-not $ReadClipboard) {
        # Depois da 1ª execução, as credenciais do Lab já estão no arquivo local; a área de transferência pode ter mudado.
        $arguments.AwsCredentialsFile = $CredentialsFile
    }
    $arguments
}

function Get-LocalAwsCredentialsFile {
    if (-not [string]::IsNullOrWhiteSpace($env:AWS_SHARED_CREDENTIALS_FILE)) {
        return $env:AWS_SHARED_CREDENTIALS_FILE
    }
    Join-Path (Join-Path $HOME '.aws') 'credentials'
}

function Invoke-ConfigureEnvironment {
    param(
        [Parameter(Mandatory)][hashtable]$Step,
        [Parameter(Mandatory)][string]$EnvironmentName
    )

    $initial = $Step.ContainsKey('Initial') -and [bool]$Step.Initial
    $arguments = Get-ConfigureArguments -EnvironmentName $EnvironmentName -Initial $initial -ReadClipboard (-not $script:CredentialsLoaded) -CredentialsFile (Get-LocalAwsCredentialsFile) -RepositoriesRootPath $RepositoriesRoot
    & $script:ConfigureScript @arguments
    if (-not $script:CredentialsLoaded) {
        Assert-AwsCredentials
        $script:CredentialsLoaded = $true
    }
    if ($Step.ContainsKey('AssertDeployGates') -and [bool]$Step.AssertDeployGates) {
        foreach ($repository in $DeployGateRepositories) {
            Assert-DeployGate -Repository $repository -EnvironmentName $EnvironmentName
        }
    }
}

function Assert-AwsCredentials {
    # O sts get-caller-identity responde mesmo com a credencial cancelada (voc-cancel-cred); uma leitura real não.
    $result = Invoke-NativeCommand -Command 'aws' -Arguments @('sqs', 'list-queues', '--region', $Region, '--queue-name-prefix', 'oficina-', '--output', 'json')
    if ($result.ExitCode -ne 0) {
        throw "Credenciais AWS sem acesso: $($result.Output -join ' '). Dê Start Lab, copie o [default] de novo e rode o script outra vez."
    }
}

function Assert-DeployGate {
    param(
        [Parameter(Mandatory)][string]$Repository,
        [Parameter(Mandatory)][string]$EnvironmentName
    )

    $result = Invoke-NativeCommand -Command 'gh' -Arguments @('variable', 'get', 'DEPLOY_ENABLED', '--repo', "$GitHubOwner/$Repository", '--env', $EnvironmentName)
    $value = ($result.Output -join '').Trim()
    if ($result.ExitCode -ne 0 -or $value -ne 'true') {
        throw "DEPLOY_ENABLED de $Repository ($EnvironmentName) está '$value'. Falta output de algum workspace: confira os runs dos bancos e rode de novo a partir de -StartAt configure-bancos."
    }
    Write-Host "  $Repository`: DEPLOY_ENABLED=true"
}

function Get-GitHubLogin {
    $result = Invoke-NativeCommand -Command 'gh' -Arguments @('api', 'user', '--jq', '.login')
    $login = ($result.Output -join '').Trim()
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($login)) {
        throw "Não foi possível identificar o usuário do gh. Rode gh auth login. $($result.Output -join ' ')"
    }
    $login
}

function Get-ResumeCommand {
    param(
        [Parameter(Mandatory)][string]$EnvironmentName,
        [Parameter(Mandatory)][string]$StepName,
        [bool]$E2E = $false,
        [bool]$LoadTest = $false,
        [string]$RepositoriesRootPath
    )

    $command = ".\scripts\provision-environment.ps1 -Environment $EnvironmentName -StartAt $StepName"
    if ($E2E -and $StepName -ne 'e2e') { $command += ' -RunE2E' }
    if ($LoadTest) { $command += ' -RunLoadTest' }
    if (-not [string]::IsNullOrWhiteSpace($RepositoriesRootPath)) { $command += " -RepositoriesRoot `"$RepositoriesRootPath`"" }
    $command
}

function Get-LatestDispatchRunId {
    param(
        [Parameter(Mandatory)][string]$Repository,
        [Parameter(Mandatory)][string]$Workflow,
        [Parameter(Mandatory)][string]$Ref
    )

    # Filtra pelo usuário do gh: um dispatch de outra pessoa no mesmo workflow não é confundido com o deste script.
    $result = Invoke-NativeCommand -Command 'gh' -Arguments @('run', 'list', '--repo', "$GitHubOwner/$Repository", '--workflow', $Workflow, '--branch', $Ref, '--event', 'workflow_dispatch', '--user', $script:GitHubLogin, '--limit', '1', '--json', 'databaseId')
    if ($result.ExitCode -ne 0) {
        throw "Falha ao listar os runs de $Repository/$Workflow`: $($result.Output -join ' ')"
    }
    $runs = ConvertFrom-NativeJson -Result $result
    $first = @($runs) | Where-Object { $null -ne $_ } | Select-Object -First 1
    if ($null -eq $first) { return [long]0 }
    [long]$first.databaseId
}

function Wait-WorkflowRun {
    param(
        [Parameter(Mandatory)][string]$Repository,
        [Parameter(Mandatory)][long]$RunId
    )

    $url = "https://github.com/$GitHubOwner/$Repository/actions/runs/$RunId"
    $deadline = (Get-Date).AddMinutes($RunTimeoutMinutes)
    $lastStatus = ''
    while ($true) {
        $result = Invoke-NativeCommand -Command 'gh' -Arguments @('run', 'view', "$RunId", '--repo', "$GitHubOwner/$Repository", '--json', 'status,conclusion')
        if ($result.ExitCode -eq 0) {
            $run = ConvertFrom-NativeJson -Result $result
            $status = [string]$run.status
            if ($status -ne $lastStatus) {
                Write-Host "  status: $status"
                $lastStatus = $status
            }
            if ($status -eq 'completed') {
                $conclusion = [string]$run.conclusion
                if ($conclusion -ne 'success') {
                    throw "O run terminou com '$conclusion'. Veja o log em $url"
                }
                return $url
            }
        }
        if ((Get-Date) -gt $deadline) {
            throw "O run passou de $RunTimeoutMinutes min sem terminar. Acompanhe em $url e retome com -StartAt."
        }
        Start-Sleep -Seconds $PollSeconds
    }
}

function Invoke-ProvisionWorkflow {
    param(
        [Parameter(Mandatory)][hashtable]$Step,
        [Parameter(Mandatory)][string]$EnvironmentName
    )

    if ($Step.ContainsKey('DeployGate') -and [bool]$Step.DeployGate) {
        Assert-DeployGate -Repository $Step.Repository -EnvironmentName $EnvironmentName
    }
    $workflow = $Step.Workflow -f $EnvironmentName
    $ref = Get-WorkflowRef -EnvironmentName $EnvironmentName
    $previousId = Get-LatestDispatchRunId -Repository $Step.Repository -Workflow $workflow -Ref $ref

    $fields = @(Get-WorkflowFields -Step $Step -EnvironmentName $EnvironmentName -LoadTest ([bool]$RunLoadTest))
    $dispatch = Invoke-NativeCommand -Command 'gh' -Arguments (@('workflow', 'run', $workflow, '--repo', "$GitHubOwner/$($Step.Repository)", '--ref', $ref) + $fields)
    if ($dispatch.ExitCode -ne 0) {
        throw "Falha ao disparar $($Step.Repository)/$workflow`: $($dispatch.Output -join ' ')"
    }

    # O run aparece na lista alguns segundos depois do dispatch; ids de run são crescentes.
    $deadline = (Get-Date).AddSeconds($DispatchTimeoutSeconds)
    while ($true) {
        Start-Sleep -Seconds 5
        $runId = Get-LatestDispatchRunId -Repository $Step.Repository -Workflow $workflow -Ref $ref
        if ($runId -gt $previousId) { break }
        if ((Get-Date) -gt $deadline) {
            throw "O run de $($Step.Repository)/$workflow não apareceu em $DispatchTimeoutSeconds s. Confira em https://github.com/$GitHubOwner/$($Step.Repository)/actions."
        }
    }
    Write-Host "  run: https://github.com/$GitHubOwner/$($Step.Repository)/actions/runs/$runId"
    $url = Wait-WorkflowRun -Repository $Step.Repository -RunId $runId
    $script:RunLinks.Add("$($Step.Name): $url")
}

function Invoke-LabRoleTest {
    param([Parameter(Mandatory)][string]$EnvironmentName)

    $path = Join-Path (Join-Path (Join-Path $RepositoriesRoot 'fiap-tc-fase4-oficina-database-infra') 'scripts') 'test-labrole-access.ps1'
    if (-not (Test-Path $path)) {
        throw "Script não encontrado: $path. Clone o fiap-tc-fase4-oficina-database-infra ao lado do fiap-tc-fase4-oficina-os-service."
    }
    & $path -Environment $EnvironmentName
}

function Get-LoadBalancerUrl {
    param(
        [Parameter(Mandatory)][string]$Service,
        [Parameter(Mandatory)][string]$Namespace
    )

    $result = Invoke-NativeCommand -Command 'kubectl' -Arguments @('get', 'service', $Service, '-n', $Namespace, '-o', 'jsonpath={.status.loadBalancer.ingress[0].hostname}')
    $hostname = ($result.Output -join '').Trim()
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($hostname)) {
        throw "O Service $Namespace/$Service não tem LoadBalancer: $($result.Output -join ' ')"
    }
    "http://$hostname"
}

function Test-ServiceHealth {
    param([Parameter(Mandatory)][string]$Url)

    try {
        $health = Invoke-RestMethod -Uri "$Url/actuator/health" -TimeoutSec 15 -ErrorAction Stop
        return [string]$health.status -eq 'UP'
    }
    catch {
        return $false
    }
}

function Invoke-HealthCheck {
    param([Parameter(Mandatory)][string]$EnvironmentName)

    $kubeconfig = Invoke-NativeCommand -Command 'aws' -Arguments @('eks', 'update-kubeconfig', '--region', $Region, '--name', "oficina-$EnvironmentName")
    if ($kubeconfig.ExitCode -ne 0) {
        throw "Falha no aws eks update-kubeconfig: $($kubeconfig.Output -join ' ')"
    }

    foreach ($entry in $HealthServices) {
        $url = Get-LoadBalancerUrl -Service $entry.Service -Namespace ($entry.Namespace -f $EnvironmentName)
        # O DNS de um LoadBalancer novo pode levar alguns minutos para responder.
        $deadline = (Get-Date).AddMinutes($HealthTimeoutMinutes)
        while (-not (Test-ServiceHealth -Url $url)) {
            if ((Get-Date) -gt $deadline) {
                throw "$($entry.Name) não respondeu UP em $url/actuator/health depois de $HealthTimeoutMinutes min. Retome com -StartAt health."
            }
            Write-Host "  $($entry.Name) ainda não está UP; aguardando..."
            Start-Sleep -Seconds ([Math]::Max($PollSeconds, 15))
        }
        Write-Host "  $($entry.Name): UP em $url"
        $script:ServiceUrls.Add("$($entry.Name): $url")
    }

    $gateway = Invoke-NativeCommand -Command 'gh' -Arguments @('variable', 'get', 'API_GATEWAY_BASE_URL', '--repo', "$GitHubOwner/fiap-tc-fase4-oficina-os-service", '--env', $EnvironmentName)
    if ($gateway.ExitCode -eq 0) {
        $script:ServiceUrls.Add("API Gateway: $(($gateway.Output -join '').Trim())")
    }
}

function Invoke-ProvisionStep {
    param(
        [Parameter(Mandatory)][hashtable]$Step,
        [Parameter(Mandatory)][string]$EnvironmentName
    )

    switch ($Step.Kind) {
        'configure' { Invoke-ConfigureEnvironment -Step $Step -EnvironmentName $EnvironmentName }
        'workflow' { Invoke-ProvisionWorkflow -Step $Step -EnvironmentName $EnvironmentName }
        'nodes' { Repair-OficinaNodes -Ambiente $EnvironmentName -Region $Region }
        'labrole' { Invoke-LabRoleTest -EnvironmentName $EnvironmentName }
        'health' { Invoke-HealthCheck -EnvironmentName $EnvironmentName }
        default { throw "Tipo de etapa desconhecido: $($Step.Kind)" }
    }
}

foreach ($command in @('aws', 'kubectl', 'gh')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Comando obrigatório não encontrado: $command"
    }
}
if ([string]::IsNullOrWhiteSpace($RepositoriesRoot)) {
    $RepositoriesRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
}
$script:ConfigureScript = Join-Path $PSScriptRoot 'configure-environment.ps1'
$script:CredentialsLoaded = $false
$script:GitHubLogin = Get-GitHubLogin
$script:RunLinks = [Collections.Generic.List[string]]::new()
$script:ServiceUrls = [Collections.Generic.List[string]]::new()

$Environment = Resolve-ProvisionEnvironment -Value $Environment
$steps = @(Get-ProvisionPlan -Steps $ProvisionSteps -StartAt $StartAt -IncludeOptional ([bool]$RunE2E))

# A 1ª etapa lê o [default] da área de transferência: confere antes que ele é de uma sessão válida do Lab.
Write-Host ''
Write-Host '== credenciais do Lab'
Wait-OficinaLabCredentials -Region $Region | Out-Null

Write-Host ''
Write-Host "Subida de '$Environment' (branch $(Get-WorkflowRef -EnvironmentName $Environment)), região $Region."
Write-Host "Etapas: $(($steps | ForEach-Object { $_.Name }) -join ' -> ')"
Write-Host 'Leva de 1h30 a 2h. Mantenha o Lab ativo; não copie outra coisa até a 1ª etapa terminar (ela lê o [default] da área de transferência).'
if ($Environment -eq 'production') {
    Write-Host 'Produção usa o perfil descartável do AWS Academy (sem Multi-AZ, sem deletion protection, sem snapshot final).'
}
$answer = Read-Host "Confirma a subida de '$Environment'? [S/N]"
if ($answer.Trim().ToLowerInvariant() -notin @('s', 'sim')) {
    throw 'Subida cancelada. Nada foi alterado.'
}

$started = Get-Date
foreach ($step in $steps) {
    Write-Host ''
    Write-Host "== $($step.Name)  ($([int]((Get-Date) - $started).TotalMinutes) min)"
    try {
        Invoke-ProvisionStep -Step $step -EnvironmentName $Environment
    }
    catch {
        $resume = if ($step.Name -eq 'credenciais') { $StartAt } else { $step.Name }
        Write-Host ''
        Write-Host "A etapa '$($step.Name)' falhou. Depois de corrigir, retome com:" -ForegroundColor Red
        Write-Host "  $(Get-ResumeCommand -EnvironmentName $Environment -StepName $resume -E2E ([bool]$RunE2E) -LoadTest ([bool]$RunLoadTest) -RepositoriesRootPath $PSBoundParameters['RepositoriesRoot'])" -ForegroundColor Red
        throw
    }
}

Write-Host ''
Write-Host "Ambiente '$Environment' no ar em $([int]((Get-Date) - $started).TotalMinutes) min." -ForegroundColor Green
foreach ($line in $script:ServiceUrls) { Write-Host "  $line" }
Write-Host 'Runs (evidência da ordem de subida):'
foreach ($line in $script:RunLinks) { Write-Host "  $line" }
Write-Host "Próximo passo (validar): .\scripts\validar-ambiente.ps1 -Environment $Environment"
Write-Host "Depois de um Start Lab: .\scripts\lab-reiniciado.ps1 -Environment $Environment"
Write-Host "Para destruir: .\scripts\destroy-environment.ps1 -Environment $Environment"
