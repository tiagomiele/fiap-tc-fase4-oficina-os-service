[CmdletBinding()]
param(
    # Produção só com o perfil descartável do AWS Academy: o perfil endurecido (deletion_protection) é recusado.
    [ValidateSet('homolog', 'production')]
    [string]$Environment = 'homolog',
    [ValidateSet('auth', 'namespaces', 'newrelic', 'db-diagnostico-reparo', 'db-orcamento-pagamento', 'db-os', 'lambda-enis', 'kubernetes', 'verificacao')]
    [string]$StartAt = 'auth',
    [string]$TerraformOrganization = 'oficina-fiap-soat-fase-2',
    [string]$TerraformProjectId = 'prj-UTzD6p6PqRVEr27C',
    [string]$Region = 'us-west-2',
    [string]$SecretsRoot,
    [int]$PollSeconds = 20,
    [int]$RunTimeoutMinutes = 60,
    [int]$EniTimeoutMinutes = 45
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Ordem inversa da criação: Auth e bancos usam a VPC do workspace Kubernetes, que é sempre o último.
$DestroySteps = @(
    @{ Name = 'auth';                   Workspace = 'auth' }
    @{ Name = 'namespaces';             Workspace = $null }
    @{ Name = 'newrelic';               Workspace = 'newrelic' }
    @{ Name = 'db-diagnostico-reparo';  Workspace = 'db-diagnostico-reparo' }
    @{ Name = 'db-orcamento-pagamento'; Workspace = 'db-orcamento-pagamento' }
    @{ Name = 'db-os';                  Workspace = 'db-os' }
    @{ Name = 'lambda-enis';            Workspace = $null }
    @{ Name = 'kubernetes';             Workspace = 'kubernetes' }
    @{ Name = 'verificacao';            Workspace = $null }
)
$ServiceNamespaces = @('oficina-{0}', 'oficina-orcamento-pagamento-{0}', 'oficina-diagnostico-reparo-{0}')
$GitHubOwner = 'tiagomiele'
# Desligados antes do destroy: com o ambiente fora do ar, um merge na branch dele recriaria recursos órfãos.
$ApplyGates = @(
    @{ Repository = 'fiap-tc-fase4-oficina-auth-serverless';             Name = 'TF_APPLY_ENABLED' }
    @{ Repository = 'fiap-tc-fase4-oficina-database-infra';              Name = 'ENABLE_TERRAFORM_APPLY' }
    @{ Repository = 'fiap-tc-fase4-oficina-os-service';                  Name = 'DEPLOY_ENABLED' }
    @{ Repository = 'fiap-tc-fase4-oficina-orcamento-pagamento-service'; Name = 'DEPLOY_ENABLED' }
    @{ Repository = 'fiap-tc-fase4-oficina-diagnostico-reparo-service';  Name = 'DEPLOY_ENABLED' }
)
$RunSuccessStatuses = @('applied', 'planned_and_finished')
$RunFailureStatuses = @('errored', 'canceled', 'force_canceled', 'discarded', 'policy_soft_failed')

function Get-DestroyWorkspaceName {
    param(
        [Parameter(Mandatory)][string]$Component,
        [Parameter(Mandatory)][string]$EnvironmentName
    )

    # Prefixo fase4: os workspaces da Fase 3 (mesma organização) nunca entram na rotina.
    "oficina-fase4-$Component-$EnvironmentName"
}

function Get-DestroyStepsFrom {
    param(
        [Parameter(Mandatory)][object[]]$Steps,
        [Parameter(Mandatory)][string]$StartAt
    )

    $names = @($Steps | ForEach-Object { $_.Name })
    $index = [Array]::IndexOf($names, $StartAt)
    if ($index -lt 0) {
        throw "Etapa desconhecida: $StartAt"
    }
    @($Steps[$index..($Steps.Count - 1)])
}

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

function Invoke-HcpApi {
    param(
        [Parameter(Mandatory)][ValidateSet('GET', 'POST')][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [object]$Body,
        [string]$Context = 'executar requisição'
    )

    $parameters = @{
        Method      = $Method
        Uri         = "https://app.terraform.io/api/v2/$Path"
        Headers     = $script:HcpHeaders
        ContentType = 'application/vnd.api+json'
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        # Bytes UTF-8: o Windows PowerShell 5.1 envia corpo string em ISO-8859-1.
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 20 -Compress))
    }

    try {
        Invoke-RestMethod @parameters
    }
    catch {
        $statusCode = $null
        $responseProperty = $_.Exception.PSObject.Properties['Response']
        if ($null -ne $responseProperty -and $null -ne $responseProperty.Value) {
            $statusCode = [int]$responseProperty.Value.StatusCode
        }
        $exception = [InvalidOperationException]::new("Falha no HCP Terraform ao $Context ($Method /api/v2/$Path): $($_.Exception.Message)", $_.Exception)
        if ($null -ne $statusCode) {
            $exception.Data['HcpStatusCode'] = $statusCode
        }
        throw $exception
    }
}

function Get-HcpTokenFromStore {
    param([Parameter(Mandatory)][string]$Directory)

    $path = Join-Path $Directory 'hcp-terraform-token-fase4.clixml'
    if (-not (Test-Path $path)) {
        throw "Token do HCP não encontrado em $path. Rode antes .\scripts\configure-environment.ps1 -Environment $Environment."
    }
    $secure = Import-Clixml $path
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Assert-WorkspaceInProject {
    param([Parameter(Mandatory)][object]$Workspace)

    $projectData = $Workspace.relationships.project.data
    $projectId = if ($null -eq $projectData) { '' } else { [string]$projectData.id }
    if ($projectId -ne $TerraformProjectId) {
        throw "O workspace $($Workspace.attributes.name) pertence ao projeto '$projectId', esperado '$TerraformProjectId'. Nada foi destruído."
    }
}

function Get-DestroyWorkspace {
    param([Parameter(Mandatory)][string]$Name)

    try {
        $workspace = (Invoke-HcpApi -Method GET -Path "organizations/$TerraformOrganization/workspaces/$Name" -Context "consultar o workspace $Name").data
    }
    catch {
        if ($_.Exception.Data['HcpStatusCode'] -eq 404) {
            return $null
        }
        throw
    }
    Assert-WorkspaceInProject -Workspace $workspace
    $workspace
}

function Get-WorkspaceResourceCount {
    param([Parameter(Mandatory)][object]$Workspace)

    $property = $Workspace.attributes.PSObject.Properties['resource-count']
    if ($null -eq $property -or $null -eq $property.Value) {
        return 0
    }
    [int]$property.Value
}

function Get-LatestApplyableConfigurationVersion {
    param([Parameter(Mandatory)][object]$Workspace)

    # Os plans de PR enviam versões especulativas, que não podem ser aplicadas.
    $versions = @((Invoke-HcpApi -Method GET -Path "workspaces/$($Workspace.id)/configuration-versions?page%5Bsize%5D=50" -Context "listar as configurações de $($Workspace.attributes.name)").data)
    $version = @($versions | Where-Object {
        -not [bool]$_.attributes.speculative -and [string]$_.attributes.status -eq 'uploaded'
    }) | Select-Object -First 1
    if ($null -eq $version) {
        throw "O workspace $($Workspace.attributes.name) não tem configuração aplicável (só plans de PR). Rode o workflow de deploy dele uma vez e tente de novo."
    }
    $version
}

function New-DestroyRunBody {
    param(
        [Parameter(Mandatory)][string]$WorkspaceId,
        [Parameter(Mandatory)][string]$ConfigurationVersionId
    )

    @{
        data = @{
            type          = 'runs'
            attributes    = @{
                'is-destroy' = $true
                'auto-apply' = $true
                message      = 'Destroy via scripts/destroy-environment.ps1'
            }
            relationships = @{
                workspace               = @{ data = @{ type = 'workspaces'; id = $WorkspaceId } }
                'configuration-version' = @{ data = @{ type = 'configuration-versions'; id = $ConfigurationVersionId } }
            }
        }
    }
}

function Wait-HcpRun {
    param(
        [Parameter(Mandatory)][string]$RunId,
        [Parameter(Mandatory)][string]$RunUrl
    )

    $deadline = (Get-Date).AddMinutes($RunTimeoutMinutes)
    $lastStatus = ''
    while ($true) {
        $status = [string](Invoke-HcpApi -Method GET -Path "runs/$RunId" -Context "acompanhar o run $RunId").data.attributes.status
        if ($status -ne $lastStatus) {
            Write-Host "  status: $status"
            $lastStatus = $status
        }
        if ($status -in $RunSuccessStatuses) {
            return $status
        }
        if ($status -in $RunFailureStatuses) {
            throw "O destroy terminou com status '$status'. Veja o log em $RunUrl"
        }
        if ((Get-Date) -gt $deadline) {
            throw "O destroy passou de $RunTimeoutMinutes min sem terminar. Acompanhe em $RunUrl e retome com -StartAt."
        }
        Start-Sleep -Seconds $PollSeconds
    }
}

function Invoke-WorkspaceDestroy {
    param([Parameter(Mandatory)][string]$Name)

    $workspace = Get-DestroyWorkspace -Name $Name
    if ($null -eq $workspace) {
        Write-Warning "Workspace $Name não existe; etapa ignorada."
        return
    }
    if ([bool]$workspace.attributes.locked) {
        throw "O workspace $Name está travado (run em andamento ou lock manual). Espere terminar ou destrave no HCP e retome com -StartAt."
    }
    if ((Get-WorkspaceResourceCount -Workspace $workspace) -eq 0) {
        Write-Host "  $Name já está sem recursos; etapa ignorada."
        return
    }

    $configurationVersion = Get-LatestApplyableConfigurationVersion -Workspace $workspace
    $body = New-DestroyRunBody -WorkspaceId $workspace.id -ConfigurationVersionId $configurationVersion.id
    $run = (Invoke-HcpApi -Method POST -Path 'runs' -Body $body -Context "criar o destroy de $Name").data
    $runUrl = "https://app.terraform.io/app/$TerraformOrganization/workspaces/$Name/runs/$($run.id)"
    Write-Host "  run: $runUrl"
    Wait-HcpRun -RunId $run.id -RunUrl $runUrl | Out-Null

    $remaining = Get-WorkspaceResourceCount -Workspace (Get-DestroyWorkspace -Name $Name)
    if ($remaining -gt 0) {
        throw "O destroy de $Name terminou, mas o HCP ainda mostra $remaining recurso(s). Confira $runUrl antes de seguir."
    }
}

function Test-EksClusterExists {
    param([Parameter(Mandatory)][string]$ClusterName)

    (Invoke-NativeCommand -Command 'aws' -Arguments @('eks', 'describe-cluster', '--region', $Region, '--name', $ClusterName, '--output', 'json')).ExitCode -eq 0
}

function Remove-ServiceNamespaces {
    param([Parameter(Mandatory)][string]$EnvironmentName)

    $clusterName = "oficina-$EnvironmentName"
    if (-not (Test-EksClusterExists -ClusterName $clusterName)) {
        Write-Host "  Cluster $clusterName não existe; etapa ignorada."
        return
    }
    $kubeconfig = Invoke-NativeCommand -Command 'aws' -Arguments @('eks', 'update-kubeconfig', '--region', $Region, '--name', $clusterName)
    if ($kubeconfig.ExitCode -ne 0) {
        throw "Falha no aws eks update-kubeconfig: $($kubeconfig.Output -join ' ')"
    }

    # Apagar o namespace remove o Service LoadBalancer, e o Kubernetes só libera o namespace depois de apagar o ELB.
    $namespaces = @($ServiceNamespaces | ForEach-Object { $_ -f $EnvironmentName })
    Write-Host "  Apagando os namespaces $($namespaces -join ', ') (aguarda os LoadBalancers saírem)..."
    $delete = Invoke-NativeCommand -Command 'kubectl' -Arguments (@('delete', 'namespace') + $namespaces + @('--ignore-not-found', '--wait=true', '--timeout=15m'))
    if ($delete.ExitCode -ne 0) {
        throw "Falha ao apagar os namespaces dos serviços: $($delete.Output -join ' ')"
    }

    $services = Invoke-NativeCommand -Command 'kubectl' -Arguments @('get', 'services', '--all-namespaces', '-o', 'json')
    if ($services.ExitCode -eq 0) {
        $serviceList = ConvertFrom-NativeJson -Result $services
        $items = if ($null -eq $serviceList) { @() } else { @($serviceList.items) }
        $loadBalancers = @($items | Where-Object { [string]$_.spec.type -eq 'LoadBalancer' } | ForEach-Object { "$($_.metadata.namespace)/$($_.metadata.name)" })
        if ($loadBalancers.Count -gt 0) {
            Write-Warning "Ainda há Services LoadBalancer no cluster: $($loadBalancers -join ', '). Eles podem travar o destroy da VPC."
        }
        else {
            Write-Host '  Namespaces apagados; nenhum Service LoadBalancer restante.'
        }
    }
}

function Get-KubernetesVpcId {
    param([Parameter(Mandatory)][string]$EnvironmentName)

    $workspace = Get-DestroyWorkspace -Name (Get-DestroyWorkspaceName -Component 'kubernetes' -EnvironmentName $EnvironmentName)
    if ($null -eq $workspace) {
        return $null
    }
    try {
        $outputs = @((Invoke-HcpApi -Method GET -Path "workspaces/$($workspace.id)/current-state-version-outputs" -Context 'ler o vpc_id do workspace Kubernetes').data)
    }
    catch {
        if ($_.Exception.Data['HcpStatusCode'] -eq 404) {
            return $null
        }
        throw
    }
    $vpc = @($outputs | Where-Object { [string]$_.attributes.name -eq 'vpc_id' }) | Select-Object -First 1
    if ($null -eq $vpc) {
        return $null
    }
    [string]$vpc.attributes.value
}

function Get-LambdaEniCount {
    param([Parameter(Mandatory)][string]$VpcId)

    $result = Invoke-NativeCommand -Command 'aws' -Arguments @('ec2', 'describe-network-interfaces', '--region', $Region, '--filters', 'Name=interface-type,Values=lambda', "Name=vpc-id,Values=$VpcId", '--output', 'json')
    if ($result.ExitCode -ne 0) {
        throw "Falha ao listar as ENIs de Lambda: $($result.Output -join ' ')"
    }
    $parsed = ConvertFrom-NativeJson -Result $result
    if ($null -eq $parsed) {
        return 0
    }
    @($parsed.NetworkInterfaces).Count
}

function Wait-LambdaEnisReleased {
    param([Parameter(Mandatory)][string]$EnvironmentName)

    $vpcId = Get-KubernetesVpcId -EnvironmentName $EnvironmentName
    if ([string]::IsNullOrWhiteSpace($vpcId)) {
        Write-Host '  Workspace Kubernetes sem vpc_id no state; etapa ignorada.'
        return
    }

    # A AWS leva de 10 a 40 min para liberar as ENIs das Lambdas em VPC; com elas, a subnet e o security group não saem.
    $deadline = (Get-Date).AddMinutes($EniTimeoutMinutes)
    while ($true) {
        $count = Get-LambdaEniCount -VpcId $vpcId
        if ($count -eq 0) {
            Write-Host "  Nenhuma ENI de Lambda na VPC $vpcId."
            return
        }
        if ((Get-Date) -gt $deadline) {
            throw "Ainda há $count ENI(s) de Lambda na VPC $vpcId depois de $EniTimeoutMinutes min. Retome com -StartAt lambda-enis."
        }
        Write-Host "  $count ENI(s) de Lambda ainda na VPC $vpcId; aguardando..."
        Start-Sleep -Seconds ([Math]::Max($PollSeconds, 30))
    }
}

function Show-RemainingResources {
    param([Parameter(Mandatory)][string]$EnvironmentName)

    $prefix = "oficina-$EnvironmentName"
    $leftovers = [Collections.Generic.List[string]]::new()

    if (Test-EksClusterExists -ClusterName $prefix) {
        $leftovers.Add("EKS $prefix")
    }
    $rds = ConvertFrom-NativeJson -Result (Invoke-NativeCommand -Command 'aws' -Arguments @('rds', 'describe-db-instances', '--region', $Region, '--output', 'json'))
    if ($null -ne $rds) {
        @($rds.DBInstances) | Where-Object { [string]$_.DBInstanceIdentifier -like "$prefix*" } | ForEach-Object { $leftovers.Add("RDS $($_.DBInstanceIdentifier)") }
    }
    $tables = ConvertFrom-NativeJson -Result (Invoke-NativeCommand -Command 'aws' -Arguments @('dynamodb', 'list-tables', '--region', $Region, '--output', 'json'))
    if ($null -ne $tables) {
        @($tables.TableNames) | Where-Object { [string]$_ -like "$prefix*" } | ForEach-Object { $leftovers.Add("DynamoDB $_") }
    }
    $queues = ConvertFrom-NativeJson -Result (Invoke-NativeCommand -Command 'aws' -Arguments @('sqs', 'list-queues', '--region', $Region, '--queue-name-prefix', "$prefix-", '--output', 'json'))
    if ($null -ne $queues -and $null -ne $queues.PSObject.Properties['QueueUrls']) {
        @($queues.QueueUrls) | ForEach-Object { $leftovers.Add("SQS $_") }
    }

    if ($leftovers.Count -eq 0) {
        Write-Host "Nenhum EKS, RDS, DynamoDB ou SQS '$prefix' restante."
    }
    else {
        Write-Warning "Recursos que ainda existem: $($leftovers -join '; '). Não apague pelo console: confira o workspace dono e rode o destroy dele de novo."
    }
    $leftovers.Count
}

function Assert-RdsDeletionProtectionDisabled {
    param([Parameter(Mandatory)][string]$EnvironmentName)

    $result = Invoke-NativeCommand -Command 'aws' -Arguments @('rds', 'describe-db-instances', '--region', $Region, '--output', 'json')
    if ($result.ExitCode -ne 0) {
        throw "Falha ao listar as instâncias RDS: $($result.Output -join ' ')"
    }
    $parsed = ConvertFrom-NativeJson -Result $result
    $instances = if ($null -eq $parsed) { @() } else { @($parsed.DBInstances) }
    $protected = @($instances | Where-Object {
        $property = $_.PSObject.Properties['DeletionProtection']
        [string]$_.DBInstanceIdentifier -like "oficina-$EnvironmentName*" -and $null -ne $property -and [bool]$property.Value
    } | ForEach-Object { [string]$_.DBInstanceIdentifier })
    if ($protected.Count -gt 0) {
        throw "RDS com deletion protection: $($protected -join ', '). O perfil endurecido não é destruído por script. Nada foi destruído."
    }
}

function Disable-ApplyGates {
    param([Parameter(Mandatory)][string]$EnvironmentName)

    foreach ($gate in $ApplyGates) {
        $result = Invoke-NativeCommand -Command 'gh' -Arguments @('variable', 'set', $gate.Name, '--body', 'false', '--repo', "$GitHubOwner/$($gate.Repository)", '--env', $EnvironmentName)
        if ($result.ExitCode -ne 0) {
            throw "Falha ao desligar $($gate.Name) em $($gate.Repository) ($EnvironmentName): $($result.Output -join ' ')"
        }
        Write-Host "  $($gate.Repository): $($gate.Name)=false"
    }
}

function Invoke-DestroyStep {
    param([Parameter(Mandatory)][hashtable]$Step)

    switch ($Step.Name) {
        'namespaces' { Remove-ServiceNamespaces -EnvironmentName $Environment }
        'lambda-enis' { Wait-LambdaEnisReleased -EnvironmentName $Environment }
        'verificacao' { Show-RemainingResources -EnvironmentName $Environment | Out-Null }
        default { Invoke-WorkspaceDestroy -Name (Get-DestroyWorkspaceName -Component $Step.Workspace -EnvironmentName $Environment) }
    }
}

if ([string]::IsNullOrWhiteSpace($SecretsRoot)) {
    $SecretsRoot = if ($env:OS -eq 'Windows_NT') { 'C:\fiap-secrets' } else { Join-Path $HOME '.oficina-secrets' }
}
foreach ($command in @('aws', 'kubectl', 'gh')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Comando obrigatório não encontrado: $command"
    }
}

$identity = Invoke-NativeCommand -Command 'aws' -Arguments @('sts', 'get-caller-identity', '--output', 'json')
if ($identity.ExitCode -ne 0) {
    throw "Credenciais AWS inválidas ou expiradas. Dê Start Lab, copie o [default] e rode .\scripts\configure-environment.ps1 -Environment $Environment antes deste script."
}
$account = [string](ConvertFrom-NativeJson -Result $identity).Account

$script:HcpHeaders = @{ Authorization = "Bearer $(Get-HcpTokenFromStore -Directory (Join-Path $SecretsRoot 'shared'))" }
$steps = Get-DestroyStepsFrom -Steps $DestroySteps -StartAt $StartAt
Assert-RdsDeletionProtectionDisabled -EnvironmentName $Environment

Write-Host ''
Write-Host "Destroy de '$Environment' na conta AWS $account ($Region), projeto HCP $TerraformProjectId."
Write-Host "Etapas: $(($steps | ForEach-Object { $_.Name }) -join ' -> ')"
Write-Host 'O HCP usa as credenciais do Variable Set: rode o configure-environment.ps1 logo antes, com o Lab recém-iniciado.'
$answer = Read-Host "Digite '$Environment' para confirmar"
if ($answer -ne $Environment) {
    throw 'Confirmação diferente do nome do ambiente. Nada foi destruído.'
}

Write-Host ''
Write-Host '== gates de deploy'
Disable-ApplyGates -EnvironmentName $Environment

foreach ($step in $steps) {
    Write-Host ''
    Write-Host "== $($step.Name)"
    try {
        Invoke-DestroyStep -Step $step
    }
    catch {
        Write-Host ''
        Write-Host "A etapa '$($step.Name)' falhou. Depois de corrigir, retome com:" -ForegroundColor Red
        Write-Host "  .\scripts\destroy-environment.ps1 -Environment $Environment -StartAt $($step.Name)" -ForegroundColor Red
        throw
    }
}

Write-Host ''
Write-Host "Ambiente '$Environment' destruído e gates de deploy desligados. Para recriar: Start Lab + configure-environment.ps1 -Environment $Environment (religa os gates) e os workflows de deploy na ordem."
