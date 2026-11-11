Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$source = Join-Path (Split-Path $PSScriptRoot -Parent) 'destroy-environment.ps1'
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
if ($errors.Count -gt 0) {
    throw ($errors | Out-String)
}
if ([IO.File]::ReadAllBytes($source)[0] -ne 0xEF) {
    throw 'destroy-environment.ps1 must be saved as UTF-8 with BOM (Windows PowerShell 5.1).'
}

$environmentParameter = $ast.ParamBlock.Parameters | Where-Object { $_.Name.VariablePath.UserPath -eq 'Environment' }
if ($environmentParameter.Extent.Text -notmatch "ValidateSet\('homolog', 'production'\)") {
    throw 'The destroy script must accept homolog and production.'
}

foreach ($assignment in $ast.FindAll({
    param($node)
    $node -is [Management.Automation.Language.AssignmentStatementAst] -and
    $node.Parent -eq $ast.EndBlock -and
    $node.Left.Extent.Text -in @('$DestroySteps', '$ServiceNamespaces', '$RunSuccessStatuses', '$RunFailureStatuses', '$GitHubOwner', '$ApplyGates')
}, $true)) {
    Invoke-Expression $assignment.Extent.Text
}
foreach ($functionAst in $ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] }, $false)) {
    Invoke-Expression $functionAst.Extent.Text
}

$Environment = 'homolog'
$TerraformOrganization = 'test-organization'
$TerraformProjectId = 'prj-test'
$Region = 'us-west-2'
$PollSeconds = 0
$RunTimeoutMinutes = 1
$EniTimeoutMinutes = 1
function Start-Sleep { param([int]$Seconds) }

$expectedOrder = 'auth,namespaces,newrelic,db-diagnostico-reparo,db-orcamento-pagamento,db-os,lambda-enis,kubernetes,verificacao'
if ((@($DestroySteps | ForEach-Object { $_.Name }) -join ',') -ne $expectedOrder) {
    throw "Destroy order changed: $(@($DestroySteps | ForEach-Object { $_.Name }) -join ',')"
}
if ((@(Get-DestroyStepsFrom -Steps $DestroySteps -StartAt kubernetes | ForEach-Object { $_.Name }) -join ',') -ne 'kubernetes,verificacao') {
    throw 'StartAt must resume from the given step.'
}
try {
    Get-DestroyStepsFrom -Steps $DestroySteps -StartAt unknown | Out-Null
    throw 'Unknown step must be rejected.'
}
catch {
    if ($_.Exception.Message -notlike 'Etapa desconhecida*') { throw }
}
if ((Get-DestroyWorkspaceName -Component auth -EnvironmentName homolog) -ne 'oficina-fase4-auth-homolog') {
    throw 'Workspace names must use the fase4 prefix.'
}

$previousPreference = $ErrorActionPreference
function failing-native { Write-Error 'native stderr'; $global:LASTEXITCODE = 3 }
$nativeResult = Invoke-NativeCommand -Command 'failing-native'
if ($nativeResult.ExitCode -ne 3 -or $ErrorActionPreference -ne $previousPreference) {
    throw 'Invoke-NativeCommand must return the exit code and restore ErrorActionPreference.'
}

function New-TestWorkspace {
    param([int]$ResourceCount, [string]$ProjectId = 'prj-test', [bool]$Locked = $false)

    [pscustomobject]@{
        id            = 'ws-1'
        attributes    = [pscustomobject]@{ name = 'oficina-fase4-auth-homolog'; 'resource-count' = $ResourceCount; locked = $Locked }
        relationships = [pscustomobject]@{ project = [pscustomobject]@{ data = [pscustomobject]@{ id = $ProjectId } } }
    }
}

function Reset-Hcp {
    $script:hcpCalls = [Collections.Generic.List[object]]::new()
    $script:workspaceQueue = [Collections.Generic.Queue[object]]::new()
    $script:runStatuses = [Collections.Generic.Queue[string]]::new()
    $script:workspaceMissing = $false
    $script:configurationVersions = @(
        [pscustomobject]@{ id = 'cv-spec'; attributes = [pscustomobject]@{ speculative = $true; status = 'uploaded' } }
        [pscustomobject]@{ id = 'cv-apply'; attributes = [pscustomobject]@{ speculative = $false; status = 'uploaded' } }
    )
    $script:stateOutputs = @([pscustomobject]@{ attributes = [pscustomobject]@{ name = 'vpc_id'; value = 'vpc-123' } })
}
function Invoke-HcpApi {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Context)

    $script:hcpCalls.Add([pscustomobject]@{ Method = $Method; Path = $Path; Body = $Body })
    if ($Path -like 'organizations/*/workspaces/*') {
        if ($script:workspaceMissing) {
            $exception = [InvalidOperationException]::new('not found')
            $exception.Data['HcpStatusCode'] = 404
            throw $exception
        }
        return @{ data = $script:workspaceQueue.Dequeue() }
    }
    if ($Path -like 'workspaces/*/configuration-versions*') { return @{ data = $script:configurationVersions } }
    if ($Path -like 'workspaces/*/current-state-version-outputs') { return @{ data = $script:stateOutputs } }
    if ($Method -eq 'POST' -and $Path -eq 'runs') { return @{ data = [pscustomobject]@{ id = 'run-1' } } }
    if ($Path -eq 'runs/run-1') { return @{ data = [pscustomobject]@{ attributes = [pscustomobject]@{ status = $script:runStatuses.Dequeue() } } } }
    throw "Unexpected HCP call: $Method $Path"
}
function Get-PostCalls { @($script:hcpCalls | Where-Object { $_.Method -eq 'POST' }) }

Reset-Hcp
$script:workspaceQueue.Enqueue((New-TestWorkspace -ResourceCount 12))
$script:workspaceQueue.Enqueue((New-TestWorkspace -ResourceCount 0))
foreach ($status in @('planning', 'applying', 'applied')) { $script:runStatuses.Enqueue($status) }
Invoke-WorkspaceDestroy -Name 'oficina-fase4-auth-homolog' 6>$null
$posts = @(Get-PostCalls)
if ($posts.Count -ne 1) { throw 'A destroy run must be created once.' }
$runData = $posts[0].Body.data
if (-not $runData.attributes['is-destroy'] -or -not $runData.attributes['auto-apply']) {
    throw 'The run must be a destroy with auto-apply.'
}
if ($runData.relationships['configuration-version'].data.id -ne 'cv-apply' -or $runData.relationships.workspace.data.id -ne 'ws-1') {
    throw 'The destroy must use the latest non-speculative configuration version.'
}

Reset-Hcp
$script:workspaceQueue.Enqueue((New-TestWorkspace -ResourceCount 0))
Invoke-WorkspaceDestroy -Name 'oficina-fase4-auth-homolog' 6>$null
if (@(Get-PostCalls).Count -ne 0) { throw 'An empty workspace must be skipped.' }

Reset-Hcp
$script:workspaceMissing = $true
Invoke-WorkspaceDestroy -Name 'oficina-fase4-auth-homolog' 3>$null
if (@(Get-PostCalls).Count -ne 0) { throw 'A missing workspace must be skipped.' }

foreach ($case in @(
    @{ Workspace = (New-TestWorkspace -ResourceCount 3 -ProjectId 'prj-fase3'); Message = '*pertence ao projeto*' }
    @{ Workspace = (New-TestWorkspace -ResourceCount 3 -Locked $true); Message = '*travado*' }
)) {
    Reset-Hcp
    $script:workspaceQueue.Enqueue($case.Workspace)
    try {
        Invoke-WorkspaceDestroy -Name 'oficina-fase4-auth-homolog'
        throw 'Workspace must be rejected.'
    }
    catch {
        if ($_.Exception.Message -notlike $case.Message) { throw }
    }
    if (@(Get-PostCalls).Count -ne 0) { throw 'A rejected workspace must not be destroyed.' }
}

Reset-Hcp
$script:workspaceQueue.Enqueue((New-TestWorkspace -ResourceCount 3))
$script:workspaceQueue.Enqueue((New-TestWorkspace -ResourceCount 2))
$script:runStatuses.Enqueue('applied')
try {
    Invoke-WorkspaceDestroy -Name 'oficina-fase4-auth-homolog' 6>$null
    throw 'Remaining resources after destroy must fail.'
}
catch {
    if ($_.Exception.Message -notlike '*ainda mostra 2 recurso*') { throw }
}

Reset-Hcp
$script:runStatuses.Enqueue('errored')
try {
    Wait-HcpRun -RunId 'run-1' -RunUrl 'https://example.invalid/run-1' 6>$null
    throw 'Errored run must fail.'
}
catch {
    if ($_.Exception.Message -notlike "*'errored'*https://example.invalid/run-1") { throw }
}

Reset-Hcp
$script:configurationVersions = @([pscustomobject]@{ id = 'cv-spec'; attributes = [pscustomobject]@{ speculative = $true; status = 'uploaded' } })
try {
    Get-LatestApplyableConfigurationVersion -Workspace (New-TestWorkspace -ResourceCount 1) | Out-Null
    throw 'Speculative-only workspace must fail.'
}
catch {
    if ($_.Exception.Message -notlike '*configuração aplicável*') { throw }
}

$script:awsCalls = [Collections.Generic.List[string]]::new()
$script:kubectlCalls = [Collections.Generic.List[string]]::new()
$script:clusterExists = $true
$script:eniCounts = [Collections.Generic.Queue[int]]::new()
$script:rdsJson = '{"DBInstances":[{"DBInstanceIdentifier":"oficina-homolog-os"},{"DBInstanceIdentifier":"outro-projeto"}]}'
function aws {
    param([Parameter(ValueFromRemainingArguments = $true)][object[]]$Arguments)

    $line = $Arguments -join ' '
    $script:awsCalls.Add($line)
    $global:LASTEXITCODE = 0
    if ($line -like 'eks describe-cluster*') {
        if (-not $script:clusterExists) { $global:LASTEXITCODE = 254 }
        return '{}'
    }
    if ($line -like 'ec2 describe-network-interfaces*') {
        $count = $script:eniCounts.Dequeue()
        return (@{ NetworkInterfaces = @(1..$count | Where-Object { $count -gt 0 } | ForEach-Object { @{ NetworkInterfaceId = "eni-$_" } }) } | ConvertTo-Json -Depth 5)
    }
    if ($line -like 'rds describe-db-instances*') { return $script:rdsJson }
    if ($line -like 'dynamodb list-tables*') { return '{"TableNames":["oficina-homolog-execucoes"]}' }
    if ($line -like 'sqs list-queues*') { return '{}' }
}
function kubectl {
    param([Parameter(ValueFromRemainingArguments = $true)][object[]]$Arguments)

    $script:kubectlCalls.Add($Arguments -join ' ')
    $global:LASTEXITCODE = 0
    if ($Arguments[0] -eq 'get') { return '{"items":[{"metadata":{"namespace":"kube-system","name":"kube-dns"},"spec":{"type":"ClusterIP"}}]}' }
}

Remove-ServiceNamespaces -EnvironmentName homolog 3>$null
if ($script:kubectlCalls[0] -ne 'delete namespace oficina-homolog oficina-orcamento-pagamento-homolog oficina-diagnostico-reparo-homolog --ignore-not-found --wait=true --timeout=15m') {
    throw "Unexpected namespace deletion: $($script:kubectlCalls[0])"
}
if (-not ($script:awsCalls -contains 'eks update-kubeconfig --region us-west-2 --name oficina-homolog')) {
    throw 'kubeconfig must be updated before deleting namespaces.'
}

$script:kubectlCalls.Clear()
$script:clusterExists = $false
Remove-ServiceNamespaces -EnvironmentName homolog 6>$null
if ($script:kubectlCalls.Count -ne 0) { throw 'Missing cluster must skip kubectl.' }

Reset-Hcp
$script:workspaceQueue.Enqueue((New-TestWorkspace -ResourceCount 40))
foreach ($count in @(2, 0)) { $script:eniCounts.Enqueue($count) }
Wait-LambdaEnisReleased -EnvironmentName homolog 6>$null
if ($script:eniCounts.Count -ne 0) { throw 'ENI wait must poll until no Lambda ENI remains.' }
if (-not (@($script:awsCalls) | Where-Object { $_ -like '*Name=vpc-id,Values=vpc-123*' })) {
    throw 'ENI lookup must be limited to the Kubernetes VPC.'
}

$script:clusterExists = $false
$leftovers = Show-RemainingResources -EnvironmentName homolog 3>$null 6>$null
if ($leftovers -ne 2) { throw "Expected 2 leftovers (RDS and DynamoDB), got $leftovers." }

$script:rdsJson = '{"DBInstances":[{"DBInstanceIdentifier":"oficina-production-os","DeletionProtection":false},{"DBInstanceIdentifier":"outro-projeto","DeletionProtection":true}]}'
Assert-RdsDeletionProtectionDisabled -EnvironmentName production
$script:rdsJson = '{"DBInstances":[{"DBInstanceIdentifier":"oficina-production-os","DeletionProtection":true}]}'
try {
    Assert-RdsDeletionProtectionDisabled -EnvironmentName production
    throw 'Protected RDS must block the destroy.'
}
catch {
    if ($_.Exception.Message -notlike '*deletion protection: oficina-production-os*') { throw }
}

$script:ghCalls = [Collections.Generic.List[string]]::new()
$script:ghExitCode = 0
function gh {
    param([Parameter(ValueFromRemainingArguments = $true)][object[]]$Arguments)

    $script:ghCalls.Add($Arguments -join ' ')
    $global:LASTEXITCODE = $script:ghExitCode
}
Disable-ApplyGates -EnvironmentName production 6>$null
foreach ($expected in @(
    'variable set TF_APPLY_ENABLED --body false --repo tiagomiele/fiap-tc-fase4-oficina-auth-serverless --env production',
    'variable set ENABLE_TERRAFORM_APPLY --body false --repo tiagomiele/fiap-tc-fase4-oficina-database-infra --env production',
    'variable set DEPLOY_ENABLED --body false --repo tiagomiele/fiap-tc-fase4-oficina-os-service --env production',
    'variable set DEPLOY_ENABLED --body false --repo tiagomiele/fiap-tc-fase4-oficina-orcamento-pagamento-service --env production',
    'variable set DEPLOY_ENABLED --body false --repo tiagomiele/fiap-tc-fase4-oficina-diagnostico-reparo-service --env production'
)) {
    if (-not ($script:ghCalls -contains $expected)) { throw "Missing gate update: $expected" }
}
$script:ghExitCode = 1
try {
    Disable-ApplyGates -EnvironmentName homolog 6>$null
    throw 'gh failure must stop the destroy.'
}
catch {
    if ($_.Exception.Message -notlike 'Falha ao desligar TF_APPLY_ENABLED*') { throw }
}

$global:LASTEXITCODE = 0
Write-Host 'destroy-environment.ps1 tests passed.'
