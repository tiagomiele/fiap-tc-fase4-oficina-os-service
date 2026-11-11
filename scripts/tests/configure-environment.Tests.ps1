Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$source = Join-Path (Split-Path $PSScriptRoot -Parent) 'configure-environment.ps1'
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
if ($errors.Count -gt 0) {
    throw ($errors | Out-String)
}

# '?' é aceito em nomes de variável: interpolação sem chaves engole o separador da query string.
$unbracedInterpolation = Select-String -Path $source -Pattern '\$[A-Za-z_][A-Za-z0-9_]*\?' | Select-Object -First 1
if ($null -ne $unbracedInterpolation) {
    throw "Use `${variavel} antes de '?' em interpolação: $($unbracedInterpolation.Line.Trim())"
}

$sourceText = Get-Content $source -Raw
foreach ($requiredProductionSetting in @(
    'UseAwsAcademyDisposableProductionProfile',
    "Set-HcpWorkspaceVariable -Workspace `$workspace -Key multi_az",
    "Set-HcpWorkspaceVariable -Workspace `$workspace -Key deletion_protection",
    "Set-HcpWorkspaceVariable -Workspace `$workspace -Key skip_final_snapshot",
    "Set-HcpWorkspaceVariable -Workspace `$workspace -Key final_snapshot_identifier"
)) {
    if (-not $sourceText.Contains($requiredProductionSetting)) {
        throw "Production database setting not found: $requiredProductionSetting"
    }
}

foreach ($requiredNotificationSetting in @(
    '[switch]$EnableSesDelivery',
    'CreateSesIdentity exige EnableSesDelivery',
    'Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key notification_delivery_mode',
    "if (`$EnableSesDelivery) { 'ses' } else { 'log' }"
)) {
    if (-not $sourceText.Contains($requiredNotificationSetting)) {
        throw "Notification delivery setting not found: $requiredNotificationSetting"
    }
}

foreach ($requiredDeployGateSetting in @(
    '[switch]$RequireBackendDeployReady',
    '$backendDeployReady = $kubernetesReady -and $databaseReady',
    'Get-GitHubVariable -Repository $RepositoryNames.Backend',
    'Gate do Backend confirmado: DEPLOY_ENABLED=',
    'Deploy do Backend bloqueado:'
)) {
    if (-not $sourceText.Contains($requiredDeployGateSetting)) {
        throw "Backend deploy gate setting not found: $requiredDeployGateSetting"
    }
}
if ($sourceText.Contains("Set-GitHubVariable -Repository `$RepositoryNames.Backend -EnvironmentName `$Environment -Name DEPLOY_ENABLED -Value 'false'")) {
    throw 'DEPLOY_ENABLED must not be reset before Terraform outputs are evaluated.'
}
foreach ($authoritativeOutputSetting in @(
    'workspaces/$($Workspace.id)/current-state-version-outputs',
    "`$NotificationSourceEmail = 'nao-responder@example.invalid'"
)) {
    if (-not $sourceText.Contains($authoritativeOutputSetting)) {
        throw "Authoritative HCP output setting not found: $authoritativeOutputSetting"
    }
}

foreach ($planEnvironmentSetting in @(
    '$planEnvironment = "$targetEnvironment-plan"',
    'Set-GitHubSecret -Repository $repository -EnvironmentName $planEnvironment -Name TF_API_TOKEN',
    'Set-GitHubVariable -Repository $repository -EnvironmentName $planEnvironment -Name TF_WORKSPACE_PRODUCTION'
)) {
    if (-not $sourceText.Contains($planEnvironmentSetting)) {
        throw "Plan GitHub Environment setting not found: $planEnvironmentSetting"
    }
}

$applyGateGuard = [regex]::Match($sourceText, "if \(\`$targetEnvironment -eq \`$Environment\) \{\s*Set-GitHubVariable[^\n]*ENABLE_TERRAFORM_APPLY -Value 'true'\s*Set-GitHubVariable[^\n]*TF_APPLY_ENABLED -Value 'true'\s*\}")
if (-not $applyGateGuard.Success) {
    throw 'Terraform apply gates must be enabled only for the configured environment.'
}
if (([regex]::Matches($sourceText, "(ENABLE_TERRAFORM_APPLY|TF_APPLY_ENABLED) -Value 'true'")).Count -ne 2) {
    throw 'Terraform apply gates must not be enabled outside the configured-environment guard.'
}

foreach ($backendSynchronizationSetting in @(
    'Set-GitHubSecret -Repository $RepositoryNames.Backend -EnvironmentName $targetEnvironment -Name TF_API_TOKEN',
    'Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $targetEnvironment -Name TF_WORKSPACE_AUTH',
    'Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $targetEnvironment -Name TF_WORKSPACE_OBSERVABILITY'
)) {
    if (-not $sourceText.Contains($backendSynchronizationSetting)) {
        throw "Backend synchronization setting not found: $backendSynchronizationSetting"
    }
}

foreach ($phase4Setting in @(
    "[string]`$TerraformProject = 'soat-fase4'",
    "[string]`$TerraformProjectId = 'prj-UTzD6p6PqRVEr27C'",
    "Get-StoredSecret -Name 'hcp-terraform-token-fase4'",
    "Backend            = 'fiap-tc-fase4-oficina-os-service'",
    "OrcamentoPagamento = 'fiap-tc-fase4-oficina-orcamento-pagamento-service'",
    "DiagnosticoReparo  = 'fiap-tc-fase4-oficina-diagnostico-reparo-service'",
    'homolog    = "oficina-fase4-$($entry.Value)-homolog"',
    "DatabaseOrcamentoPagamento = 'orcamento-pagamento'",
    "DatabaseDiagnosticoReparo  = 'diagnostico-reparo'",
    '[switch]$ConfigureSonarCloud',
    '[switch]$ConfigureMercadoPago',
    '[switch]$SkipBranchProtection',
    'Set-GitHubBranchRuleset -Repository $entry.Value -Branch $branch',
    '-Name MERCADOPAGO_ACCESS_TOKEN',
    '-Name SONAR_TOKEN',
    '-Name REPO_SYNC_TOKEN',
    "Get-StoredSecret -Name 'github-sync-token'",
    "foreach (`$serviceKey in @('OrcamentoPagamento', 'DiagnosticoReparo'))",
    "-Name `$queue.Key"
)) {
    if (-not $sourceText.Contains($phase4Setting)) {
        throw "Phase 4 setting not found: $phase4Setting"
    }
}
if ($sourceText -match 'credentials\.tfrc\.json|TFC_TOKEN|organizations/\$TerraformOrganization/projects|organizations/\$TerraformOrganization/varsets\?|-Path "organizations/\$TerraformOrganization"\s') {
    throw 'Phase 4 bootstrap must use only the soat-fase4 project credential and project-scoped APIs.'
}
if ($sourceText -match 'fiap-tech-challenge-fase3|soat-fase3|SYNC_APP_CLIENT_ID|SYNC_APP_PRIVATE_KEY') {
    throw 'Phase 3 names must not be used by the Phase 4 bootstrap.'
}

foreach ($legacyEnvironmentSetting in @(
    '$authApplyEnvironment = "$targetEnvironment-apply"'
)) {
    if ($sourceText.Contains($legacyEnvironmentSetting)) {
        throw "Legacy GitHub Environment must not be created: $legacyEnvironmentSetting"
    }
}

foreach ($name in @('Assert-TerraformPlatform', 'Assert-RdsPassword', 'Assert-NotificationSourceEmail', 'ConvertFrom-SecureText', 'New-RandomSecret', 'Get-StoredSecret', 'Get-HcpApiStatusCode', 'Get-HcpApiErrorDetail', 'Invoke-HcpApi', 'Get-HcpProject', 'Initialize-HcpWorkspace', 'Set-HcpWorkspaceVariable', 'Set-HcpVariableSetVariables', 'Set-AwsVariableSet', 'Set-LocalAwsCredentials', 'Get-HcpTerraformOutputs', 'ConvertTo-HclList', 'Get-TerraformOutput', 'Get-KubernetesBackendUrl', 'Get-NewRelicLayerVersion', 'Invoke-Gh', 'Get-GitHubVariable', 'Set-GitHubSecret', 'Set-GitHubRepositorySecret', 'New-BranchRulesetPayload', 'Set-GitHubBranchRuleset')) {
    $functionAst = $ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name
    }, $true) | Select-Object -First 1
    if ($null -eq $functionAst) {
        throw "Function not found: $name"
    }
    Invoke-Expression $functionAst.Extent.Text
}

Assert-RdsPassword -Password 'SafeRdsPassword!123456789'
foreach ($invalidPassword in @(
    'invalid@password123456',
    'invalid/password123456',
    'invalid"password123456',
    'invalid password123456',
    'invalidçpassword123456'
)) {
    try {
        Assert-RdsPassword -Password $invalidPassword
        throw 'Invalid RDS password must be rejected.'
    }
    catch {
        if ($_.Exception.Message -eq 'Invalid RDS password must be rejected.') {
            throw
        }
    }
}

Assert-RdsPassword -Password (New-RandomSecret)
Assert-NotificationSourceEmail -Email 'nao-responder@example.com'
foreach ($invalidEmail in @('sem-arroba', 'nome@dominio', "nome com espaco@example.com", (('a' * 310) + '@example.com'))) {
    try {
        Assert-NotificationSourceEmail -Email $invalidEmail
        throw 'Invalid notification source email must be rejected.'
    }
    catch {
        if ($_.Exception.Message -eq 'Invalid notification source email must be rejected.') {
            throw
        }
    }
}

$secretsDirectory = Join-Path ([IO.Path]::GetTempPath()) ("configure-environment-tests-" + [Guid]::NewGuid())
$rdsValidator = {
    param($candidate)

    Assert-RdsPassword -Password $candidate
}
try {
    $script:promptedValues = [Collections.Generic.Queue[string]]::new()
    function Read-SecretText {
        param([Parameter(Mandatory)][string]$Prompt)

        $script:promptedValues.Dequeue()
    }

    $script:promptedValues.Enqueue('invalid@password123456')
    $script:promptedValues.Enqueue('SafeRdsPassword!123456789')
    $storedPath = Join-Path $secretsDirectory 'database-password.clixml'
    $password = Get-StoredSecret -Name 'database-password' -Prompt 'Senha atual do RDS' -GenerateWhenEmpty -Validator $rdsValidator -Directory $secretsDirectory
    if ($password -ne 'SafeRdsPassword!123456789') {
        throw 'Rejected password must be replaced by the next valid entry.'
    }
    if ((ConvertFrom-SecureText (Import-Clixml $storedPath)) -ne 'SafeRdsPassword!123456789') {
        throw 'Only the validated password may be persisted.'
    }
    $reusedPassword = Get-StoredSecret -Name 'database-password' -Prompt 'Senha atual do RDS' -GenerateWhenEmpty -Validator $rdsValidator -Directory $secretsDirectory
    if ($reusedPassword -ne 'SafeRdsPassword!123456789') {
        throw 'Stored password must be reused without prompting.'
    }

    Remove-Item $storedPath
    $script:promptedValues.Enqueue('')
    $generated = Get-StoredSecret -Name 'database-password' -Prompt 'Senha atual do RDS' -GenerateWhenEmpty -Validator $rdsValidator -Directory $secretsDirectory
    Assert-RdsPassword -Password $generated

    Remove-Item $storedPath
    $script:promptedValues.Enqueue('invalid@password123456')
    $script:promptedValues.Enqueue('short')
    try {
        Get-StoredSecret -Name 'database-password' -Prompt 'Senha atual do RDS' -GenerateWhenEmpty -Validator $rdsValidator -MaxAttempts 2 -Directory $secretsDirectory
        throw 'Exhausted attempts must fail.'
    }
    catch {
        if ($_.Exception.Message -eq 'Exhausted attempts must fail.') {
            throw
        }
    }
    if (Test-Path $storedPath) {
        throw 'Invalid password must never be persisted.'
    }

    ConvertTo-SecureString 'invalid@password123456' -AsPlainText -Force | Export-Clixml $storedPath
    try {
        Get-StoredSecret -Name 'database-password' -Prompt 'Senha atual do RDS' -GenerateWhenEmpty -Validator $rdsValidator -Directory $secretsDirectory
        throw 'Invalid stored password must fail.'
    }
    catch {
        if ($_.Exception.Message -notlike "*Remova somente $storedPath*") {
            throw
        }
    }
}
finally {
    Remove-Item Function:\Read-SecretText -ErrorAction SilentlyContinue
    Remove-Item $secretsDirectory -Recurse -Force -ErrorAction SilentlyContinue
}

$apiException = [InvalidOperationException]::new('HTTP 400')
$apiErrorRecord = [Management.Automation.ErrorRecord]::new(
    $apiException,
    'HcpApiFailure',
    [Management.Automation.ErrorCategory]::InvalidOperation,
    $null
)
$apiErrorRecord.ErrorDetails = [Management.Automation.ErrorDetails]::new('{"errors":[{"title":"invalid attribute","detail":"execution-mode is invalid","source":{"pointer":"/data/attributes/execution-mode"}}]}')
$apiErrorDetail = Get-HcpApiErrorDetail -ErrorRecord $apiErrorRecord
if ($apiErrorDetail -ne 'invalid attribute: execution-mode is invalid [/data/attributes/execution-mode]') {
    throw "HCP API error detail was not preserved: $apiErrorDetail"
}

function Invoke-RestMethod {
    throw $apiErrorRecord
}
$script:HcpHeaders = @{ Authorization = 'Bearer test' }
try {
    Invoke-HcpApi -Method PATCH -Path workspaces/ws-1 -Body @{ data = @{} } -Context 'atualizar o workspace teste'
    throw 'HCP API failure was not propagated.'
}
catch {
    $expectedMessage = 'Falha no HCP Terraform ao atualizar o workspace teste (PATCH /api/v2/workspaces/ws-1): invalid attribute: execution-mode is invalid [/data/attributes/execution-mode]'
    if ($_.Exception.Message -ne $expectedMessage) {
        throw "HCP API context was not preserved: $($_.Exception.Message)"
    }
}
Remove-Item Function:\Invoke-RestMethod

function Invoke-RestMethod {
    param($Method, $Uri, $Headers, $ContentType, $Body, $ErrorAction)
    $script:capturedHcpBody = $Body
}
Invoke-HcpApi -Method POST -Path organizations/org/varsets -Body @{ data = @{ attributes = @{ description = 'Credenciais temporárias' } } } -Context 'teste de acentos'
if ($script:capturedHcpBody -isnot [byte[]] -or [Text.Encoding]::UTF8.GetString($script:capturedHcpBody) -notlike '*temporárias*') {
    throw 'HCP API body must be sent as UTF-8 bytes (Windows PowerShell 5.1 sends strings as ISO-8859-1).'
}
Remove-Item Function:\Invoke-RestMethod

$script:newRelicLayersResponse = [pscustomobject]@{
    Layers = @(
        [pscustomobject]@{
            LayerName = 'NewRelicAgentJavaARM64-slim'
            LatestMatchingVersion = [pscustomobject]@{ Version = 8 }
        }
    )
}
function Invoke-RestMethod {
    $script:newRelicLayersResponse
}
if ((Get-NewRelicLayerVersion) -ne 8) {
    throw 'Latest New Relic Java ARM64 slim layer was not selected.'
}
$script:newRelicLayersResponse = [pscustomobject]@{ Layers = @() }
try {
    Get-NewRelicLayerVersion
    throw 'Missing New Relic layer must fail.'
}
catch {
    if ($_.Exception.Message -notlike 'A camada NewRelicAgentJavaARM64-slim não foi encontrada*') {
        throw
    }
}
Remove-Item Function:\Invoke-RestMethod

$singleItemHclList = ConvertTo-HclList -Values @('sg-00672ffa5d6c0fb23')
if ($singleItemHclList -ne '["sg-00672ffa5d6c0fb23"]') {
    throw "Single-item HCL list was serialized incorrectly: $singleItemHclList"
}
$multipleItemHclList = ConvertTo-HclList -Values @('subnet-a', 'subnet-b')
if ($multipleItemHclList -ne '["subnet-a","subnet-b"]') {
    throw "Multiple-item HCL list was serialized incorrectly: $multipleItemHclList"
}

if ($null -ne (Get-TerraformOutput -Outputs $null -Name vpc_id)) {
    throw 'Null Terraform outputs must return null.'
}
$emptyOutputs = '{}' | ConvertFrom-Json
if ($null -ne (Get-TerraformOutput -Outputs $emptyOutputs -Name vpc_id)) {
    throw 'Missing Terraform output must return null.'
}
$partialOutputs = '{"vpc_id":{"sensitive":false}}' | ConvertFrom-Json
if ($null -ne (Get-TerraformOutput -Outputs $partialOutputs -Name vpc_id)) {
    throw 'Terraform output without value must return null.'
}
$completeOutputs = '{"vpc_id":{"sensitive":false,"value":"vpc-123"}}' | ConvertFrom-Json
$vpcOutput = Get-TerraformOutput -Outputs $completeOutputs -Name vpc_id
if ($null -eq $vpcOutput -or $vpcOutput.value -ne 'vpc-123') {
    throw 'Existing Terraform output was not returned.'
}

$ValidateOnly = $false
$script:awsCalls = @()
function aws {
    param([Parameter(ValueFromRemainingArguments = $true)][object[]]$Arguments)

    $script:awsCalls += , @($Arguments)
    if ($Arguments -contains 'get-caller-identity') {
        '{"Account":"123456789012"}'
    }
    $global:LASTEXITCODE = 0
}
$testCredentials = @{
    AWS_ACCESS_KEY_ID     = 'test-access-key'
    AWS_SECRET_ACCESS_KEY = 'test-secret-key'
    AWS_SESSION_TOKEN     = 'test-session-token'
}
Set-LocalAwsCredentials -Credentials $testCredentials
if ($script:awsCalls.Count -ne 5) {
    throw 'AWS credentials must configure four values and validate STS once.'
}
if ($ErrorActionPreference -ne 'Stop') {
    throw 'AWS credential setup did not restore ErrorActionPreference.'
}

$script:ConfigurationIssues = [Collections.Generic.List[string]]::new()
$TerraformOrganization = 'test-organization'
$script:terraformPlatform = 'linux_amd64'
function terraform {
    "{`"terraform_version`":`"1.10.0`",`"platform`":`"$script:terraformPlatform`"}"
    $global:LASTEXITCODE = 0
}

$originalOperatingSystem = $env:OS
try {
    $env:OS = 'Windows_NT'
    $script:terraformPlatform = 'windows_386'
    try {
        Assert-TerraformPlatform
        throw '32-bit Terraform must be rejected on Windows.'
    }
    catch {
        if ($_.Exception.Message -notlike 'Terraform incompatível: plataforma windows_386*') {
            throw
        }
    }
    $script:terraformPlatform = 'windows_amd64'
    Assert-TerraformPlatform
}
finally {
    $env:OS = $originalOperatingSystem
    $script:terraformPlatform = 'linux_amd64'
}
Remove-Item Function:\terraform

$testWorkspace = [pscustomobject]@{
    id         = 'ws-1'
    attributes = [pscustomobject]@{ name = 'target-workspace' }
}
$script:stateVersionMissing = $false
$script:stateOutputFailure = $false
$script:stateOutputItems = @(
    [pscustomobject]@{
        attributes = [pscustomobject]@{
            name      = 'jdbc_url'
            sensitive = $false
            value     = 'jdbc:postgresql://database.example.com:5432/oficina'
        }
    }
)
function Invoke-HcpApi {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Context)

    if ($script:stateVersionMissing) {
        $exception = [InvalidOperationException]::new('State version outputs not found')
        $exception.Data['HcpStatusCode'] = 404
        throw $exception
    }
    if ($script:stateOutputFailure) {
        throw [InvalidOperationException]::new('Outputs unavailable')
    }
    @{ data = $script:stateOutputItems }
}

$terraformOutputs = Get-HcpTerraformOutputs -Workspace $testWorkspace
if ($terraformOutputs.jdbc_url.value -ne 'jdbc:postgresql://database.example.com:5432/oficina') {
    throw 'HCP state output was not parsed.'
}

$script:stateVersionMissing = $true
if ($null -ne (Get-HcpTerraformOutputs -Workspace $testWorkspace)) {
    throw 'Workspace without state must return null.'
}
if ($script:ConfigurationIssues.Count -ne 0) {
    throw 'Workspace without state must not register a blocking issue.'
}

$script:stateVersionMissing = $false
$script:stateOutputFailure = $true
if ($null -ne (Get-HcpTerraformOutputs -Workspace $testWorkspace)) {
    throw 'Failed HCP output lookup must return null.'
}
if ($script:ConfigurationIssues.Count -ne 1 -or $script:ConfigurationIssues[0] -notlike '*outputs do workspace target-workspace*') {
    throw 'Failed HCP output lookup must register a blocking issue.'
}
$script:ConfigurationIssues.Clear()
Remove-Item Function:\Invoke-HcpApi

$script:awsExitCode = 1
$script:kubectlExitCode = 1
$script:loadBalancerHostname = $null
function aws {
    if ($script:awsExitCode -ne 0) {
        Write-Error 'EKS cluster was not found.'
    }
    $global:LASTEXITCODE = $script:awsExitCode
}
function kubectl {
    if ($script:kubectlExitCode -ne 0) {
        Write-Error 'Kubernetes service was not found.'
    }
    else {
        $script:loadBalancerHostname
    }
    $global:LASTEXITCODE = $script:kubectlExitCode
}

if ($null -ne (Get-KubernetesBackendUrl -EnvironmentName homolog -ClusterName oficina-homolog)) {
    throw 'Missing EKS cluster must return null.'
}
$script:awsExitCode = 0
if ($null -ne (Get-KubernetesBackendUrl -EnvironmentName homolog -ClusterName oficina-homolog)) {
    throw 'Missing Kubernetes service must return null.'
}
$script:kubectlExitCode = 0
$script:loadBalancerHostname = 'backend.example.com'
$backendUrl = Get-KubernetesBackendUrl -EnvironmentName homolog -ClusterName oficina-homolog
if ($backendUrl -ne 'http://backend.example.com') {
    throw 'Existing LoadBalancer hostname was not returned.'
}
if ($ErrorActionPreference -ne 'Stop') {
    throw 'ErrorActionPreference was not restored.'
}

$script:requests = @()
$script:existingVariables = @()
$script:existingSets = @()
function Invoke-HcpApi {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Context)

    if ($Method -eq 'GET') {
        if ($Path -like 'projects/*/varsets*') {
            return @{ data = $script:existingSets }
        }
        if ($Path -like 'projects/*') {
            return @{ data = $script:projectResponse }
        }
        return @{ data = $script:existingVariables }
    }
    $script:requests += [pscustomobject]@{ Method = $Method; Path = $Path; Body = $Body; Context = $Context }
    if ($null -eq $Body) {
        return @{}
    }
    @{ data = $Body.data }
}
function Get-HcpWorkspaceVariables {
    param([string]$WorkspaceId)

    $script:existingVariables
}

$workspace = [pscustomobject]@{
    id         = 'ws-1'
    attributes = [pscustomobject]@{ name = 'workspace-1' }
}
$script:existingVariables = @([pscustomobject]@{
    id         = 'var-1'
    attributes = [pscustomobject]@{ key = 'TOKEN'; category = 'terraform'; sensitive = $true }
})
Set-HcpWorkspaceVariable -Workspace $workspace -Key TOKEN -Value updated -Sensitive $true
$patch = $script:requests | Where-Object Method -eq PATCH | Select-Object -First 1
if ($null -eq $patch) {
    throw 'Same-sensitivity workspace variable was not patched.'
}
if (@($patch.Body.data.attributes.Keys).Count -ne 1 -or -not $patch.Body.data.attributes.ContainsKey('value')) {
    throw 'Sensitive workspace PATCH must update only value.'
}

$script:requests = @()
Set-HcpWorkspaceVariable -Workspace $workspace -Key TOKEN -Value readable -Sensitive $false
if (@($script:requests | Where-Object Method -eq DELETE).Count -ne 1) {
    throw 'Changed-sensitivity workspace variable was not deleted.'
}
if (@($script:requests | Where-Object Method -eq POST).Count -ne 1) {
    throw 'Changed-sensitivity workspace variable was not recreated.'
}
if (@($script:requests | Where-Object Method -eq PATCH).Count -ne 0) {
    throw 'Changed-sensitivity workspace variable must not be patched.'
}

$definition = @{
    type       = 'vars'
    attributes = @{
        key       = 'AWS_SESSION_TOKEN'
        value     = 'updated'
        category  = 'env'
        hcl       = $false
        sensitive = $true
    }
}
$script:requests = @()
$script:existingVariables = @([pscustomobject]@{
    id         = 'varset-var-1'
    attributes = [pscustomobject]@{ key = 'AWS_SESSION_TOKEN'; category = 'env'; sensitive = $true }
})
Set-HcpVariableSetVariables -VariableSetId varset-1 -Definitions @($definition)
$patch = $script:requests | Where-Object Method -eq PATCH | Select-Object -First 1
if ($null -eq $patch) {
    throw 'Same-sensitivity Variable Set variable was not patched.'
}
if (@($patch.Body.data.attributes.Keys).Count -ne 1 -or -not $patch.Body.data.attributes.ContainsKey('value')) {
    throw 'Sensitive Variable Set PATCH must update only value.'
}

$definition.attributes.sensitive = $false
$script:requests = @()
Set-HcpVariableSetVariables -VariableSetId varset-1 -Definitions @($definition)
if (@($script:requests | Where-Object Method -eq DELETE).Count -ne 1) {
    throw 'Changed-sensitivity Variable Set variable was not deleted.'
}
if (@($script:requests | Where-Object Method -eq POST).Count -ne 1) {
    throw 'Changed-sensitivity Variable Set variable was not recreated.'
}
if (@($script:requests | Where-Object Method -eq PATCH).Count -ne 0) {
    throw 'Changed-sensitivity Variable Set variable must not be patched.'
}

$TerraformProject = 'soat-fase4'
$TerraformProjectId = 'prj-1'
$TerraformOrganization = 'org-fase4'
$script:projectResponse = [pscustomobject]@{
    id            = 'prj-1'
    attributes    = [pscustomobject]@{ name = 'soat-fase4' }
    relationships = [pscustomobject]@{ organization = [pscustomobject]@{ data = [pscustomobject]@{ id = 'org-fase4' } } }
}
if ((Get-HcpProject).id -ne 'prj-1') {
    throw 'Phase 4 project must be read by id.'
}
$script:projectResponse.relationships.organization.data.id = 'outra-organizacao'
$wrongProjectRejected = $false
try { Get-HcpProject | Out-Null } catch { $wrongProjectRejected = $true }
if (-not $wrongProjectRejected) {
    throw 'A project from another organization must be rejected.'
}

$script:existingWorkspace = [pscustomobject]@{
    id            = 'ws-existing'
    attributes    = [pscustomobject]@{
        name                = 'existing-workspace'
        'execution-mode'    = 'remote'
        'auto-apply'        = $false
        'working-directory' = ''
    }
    relationships = [pscustomobject]@{
        project = [pscustomobject]@{
            data = [pscustomobject]@{ id = 'prj-1' }
        }
    }
}
function Get-HcpWorkspace {
    param([string]$Name, [switch]$AllowMissing)

    $script:existingWorkspace
}
$script:requests = @()
$project = [pscustomobject]@{ id = 'prj-1' }
$result = Initialize-HcpWorkspace -Project $project -Name existing-workspace
if ($result.id -ne 'ws-existing' -or $script:requests.Count -ne 0) {
    throw 'Unchanged workspace must not be patched.'
}
$script:existingWorkspace.attributes.'auto-apply' = $true
$result = Initialize-HcpWorkspace -Project $project -Name existing-workspace
$workspacePatch = $script:requests | Where-Object Method -eq PATCH | Select-Object -First 1
if ($null -eq $workspacePatch -or $workspacePatch.Body.data.attributes.Count -ne 1 -or $workspacePatch.Body.data.attributes.'auto-apply' -ne $false) {
    throw 'Workspace PATCH must contain only changed attributes.'
}
if ($workspacePatch.Body.data.ContainsKey('relationships')) {
    throw 'Workspace PATCH must never move a workspace between projects.'
}
$script:existingWorkspace.relationships.project.data.id = 'prj-fase3'
$foreignWorkspaceRejected = $false
try { Initialize-HcpWorkspace -Project $project -Name existing-workspace | Out-Null } catch { $foreignWorkspaceRejected = $true }
if (-not $foreignWorkspaceRejected) {
    throw 'A workspace from another project must not be reused.'
}

$script:requests = @()
$script:existingSets = @([pscustomobject]@{
    id         = 'varset-1'
    attributes = [pscustomobject]@{
        name        = 'aws-academy-credentials-fase4'
        description = 'Credenciais temporárias compartilhadas do AWS Academy. Gerenciado pelo script central.'
        global      = $false
        priority    = $false
    }
})
$script:existingVariables = @(
    [pscustomobject]@{ id = 'access'; attributes = [pscustomobject]@{ key = 'AWS_ACCESS_KEY_ID'; category = 'env'; sensitive = $true } },
    [pscustomobject]@{ id = 'secret'; attributes = [pscustomobject]@{ key = 'AWS_SECRET_ACCESS_KEY'; category = 'env'; sensitive = $true } },
    [pscustomobject]@{ id = 'session'; attributes = [pscustomobject]@{ key = 'AWS_SESSION_TOKEN'; category = 'env'; sensitive = $true } }
)
Set-AwsVariableSet -Project $project -Credentials $testCredentials
$associationRequest = $script:requests | Where-Object Path -eq 'varsets/varset-1/relationships/projects'
if ($null -eq $associationRequest -or $associationRequest.Method -ne 'POST') {
    throw 'Existing Variable Set must use the project relationship endpoint.'
}
if (@($associationRequest.Body.data).Count -ne 1 -or $associationRequest.Body.data[0].id -ne 'prj-1' -or $associationRequest.Body.data[0].type -ne 'projects') {
    throw 'Variable Set project relationship payload is invalid.'
}
$script:requests = @()
$script:existingSets = @()
Set-AwsVariableSet -Project $project -Credentials $testCredentials
$createRequest = $script:requests | Where-Object { $_.Method -eq 'POST' -and $_.Path -like 'organizations/*/varsets' }
if ($null -eq $createRequest -or $createRequest.Body.data.relationships.parent.data.type -ne 'projects' -or $createRequest.Body.data.relationships.parent.data.id -ne 'prj-1') {
    throw 'New Variable Set must be owned by the Phase 4 project.'
}
$metadataPatch = $script:requests | Where-Object { $_.Path -eq 'varsets/varset-1' -and $_.Method -eq 'PATCH' }
if ($null -ne $metadataPatch) {
    throw 'Unchanged Variable Set metadata must not be patched.'
}

$GitHubOwner = 'test-owner'
$script:ghCalls = @()
function gh {
    param([Parameter(ValueFromRemainingArguments = $true)][object[]]$Arguments)

    $script:ghCalls += , @($Arguments)
    $global:LASTEXITCODE = 0
}
Set-GitHubSecret -Repository fiap-tc-fase4-oficina-os-service -EnvironmentName homolog -Name APP_ADMIN_PASSWORD -Value 'senha-sem-quebra'
if ($script:ghCalls.Count -ne 1) {
    throw 'Secret update must call the GitHub CLI once.'
}
$ghArguments = $script:ghCalls[0]
$bodyIndex = [Array]::IndexOf($ghArguments, '--body')
if ($bodyIndex -lt 0 -or $ghArguments[$bodyIndex + 1] -ne 'senha-sem-quebra') {
    throw 'Secret value must be sent verbatim via --body, never through the pipeline.'
}
Remove-Item Function:\gh

$GitHubOwner = 'tiagomiele'
$script:ghCalls = @()
function gh {
    $script:ghCalls += ,@($args)
    $global:LASTEXITCODE = 0
}
Set-GitHubRepositorySecret -Repository fiap-tc-fase4-oficina-os-service -Name SONAR_TOKEN -Value 'token-sem-quebra'
$repositorySecretArguments = @($script:ghCalls[0])
if ($repositorySecretArguments -contains '--env' -or $repositorySecretArguments[[Array]::IndexOf($repositorySecretArguments, '--body') + 1] -ne 'token-sem-quebra') {
    throw 'Repository secrets must be stored at repository level with the exact value.'
}
Remove-Item Function:\gh

function gh { $global:LASTEXITCODE = 1 }
try {
    Set-GitHubRepositorySecret -Repository fiap-tc-fase4-oficina-os-service -Name REPO_SYNC_TOKEN -Value 'valor-secreto'
    throw 'A failing gh call must throw.'
}
catch {
    if ($_.Exception.Message -like '*valor-secreto*' -or $_.Exception.Message -notlike '*--body `*`*`* *') {
        throw "Secret value leaked in the GitHub CLI error: $($_.Exception.Message)"
    }
}
Remove-Item Function:\gh
$global:LASTEXITCODE = 0
if ($sourceText -match "-Name\s+'?GITHUB_") {
    throw 'GitHub rejects secret names starting with GITHUB_.'
}

foreach ($rulesetCase in @(
    @{ Response = @(); Expected = 'POST' },
    @{ Response = @("8`tProte??o main", "7`tProte??o homolog"); Expected = 'PUT' }
)) {
    $script:rulesetResponse = $rulesetCase.Response
    $script:ghCalls = @()
    function gh {
        $script:ghCalls += , @($args)
        $global:LASTEXITCODE = 0
        if ($args -contains '--jq') { $script:rulesetResponse }
    }
    Set-GitHubBranchRuleset -Repository fiap-tc-fase4-oficina-os-service -Branch homolog -RequiredChecks @('Repository validation')
    $writeCall = @($script:ghCalls[1])
    if ($writeCall -notcontains $rulesetCase.Expected) {
        throw "Ruleset sync must use $($rulesetCase.Expected) when the API returns $($rulesetCase.Response -join ', ')."
    }
    if ($rulesetCase.Expected -eq 'PUT' -and ($writeCall -join ' ') -notlike '*rulesets/7*') {
        throw 'Existing ruleset must be updated by id (ASCII name match, independent of console encoding).'
    }
    Remove-Item Function:\gh
}
$global:LASTEXITCODE = 0

$mainRuleset = New-BranchRulesetPayload -Branch main -RequiredChecks @('Repository validation', 'SonarCloud')
$homologRuleset = New-BranchRulesetPayload -Branch homolog -RequiredChecks @('Repository validation')
if ($mainRuleset.name -ne 'Proteção main' -or $mainRuleset.conditions.ref_name.include[0] -ne 'refs/heads/main') {
    throw 'Main ruleset must target refs/heads/main.'
}
$ruleTypes = @($mainRuleset.rules | ForEach-Object { $_.type })
foreach ($ruleType in @('deletion', 'non_fast_forward', 'pull_request', 'required_status_checks')) {
    if ($ruleTypes -notcontains $ruleType) {
        throw "Ruleset rule not found: $ruleType"
    }
}
$mainChecks = ($mainRuleset.rules | Where-Object type -eq 'required_status_checks').parameters.required_status_checks
if (@($mainChecks).Count -ne 2 -or $mainChecks[1].context -ne 'SonarCloud' -or $mainChecks[1].integration_id -ne 15368) {
    throw 'Required status checks must use the GitHub Actions integration.'
}
$mainPullRequest = ($mainRuleset.rules | Where-Object type -eq 'pull_request').parameters
$homologPullRequest = ($homologRuleset.rules | Where-Object type -eq 'pull_request').parameters
if ($mainPullRequest.dismiss_stale_reviews_on_push -ne $true -or $homologPullRequest.dismiss_stale_reviews_on_push -ne $false) {
    throw 'Only main must dismiss stale reviews on push.'
}
$serializedChecks = @(((New-BranchRulesetPayload -Branch homolog -RequiredChecks @('Repository validation')) | ConvertTo-Json -Depth 10 | ConvertFrom-Json).rules | Where-Object type -eq 'required_status_checks')
if (@($serializedChecks[0].parameters.required_status_checks).Count -ne 1) {
    throw 'A single required check must serialize as a JSON array.'
}

Write-Host 'Environment automation tests passed.'
