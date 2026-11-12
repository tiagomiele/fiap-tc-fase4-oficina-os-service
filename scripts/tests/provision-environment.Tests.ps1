Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$source = Join-Path (Split-Path $PSScriptRoot -Parent) 'provision-environment.ps1'
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
if ($errors.Count -gt 0) {
    throw ($errors | Out-String)
}
if ([IO.File]::ReadAllBytes($source)[0] -ne 0xEF) {
    throw 'provision-environment.ps1 must be saved as UTF-8 with BOM (Windows PowerShell 5.1).'
}

foreach ($assignment in $ast.FindAll({
    param($node)
    $node -is [Management.Automation.Language.AssignmentStatementAst] -and
    $node.Parent -eq $ast.EndBlock -and
    $node.Left.Extent.Text -in @('$ProvisionSteps', '$GitHubOwner', '$DeployGateRepositories', '$HealthServices')
}, $true)) {
    Invoke-Expression $assignment.Extent.Text
}
foreach ($functionAst in $ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] }, $false)) {
    Invoke-Expression $functionAst.Extent.Text
}

$Region = 'us-west-2'
$PollSeconds = 0
$RunTimeoutMinutes = 1
$DispatchTimeoutSeconds = 60
$RunLoadTest = $false
function Start-Sleep { param([int]$Seconds) }

$stepParameter = $ast.ParamBlock.Parameters | Where-Object { $_.Name.VariablePath.UserPath -eq 'StartAt' }
$stepNames = @($ProvisionSteps | ForEach-Object { $_.Name })
foreach ($name in $stepNames) {
    if ($stepParameter.Extent.Text -notmatch "'$name'") { throw "StartAt must accept step '$name'." }
}

$expectedOrder = 'configure,kubernetes,db-os,db-orcamento-pagamento,db-diagnostico-reparo,configure-bancos,nos,labrole,auth,configure-auth,cd-os,cd-diagnostico-reparo,cd-orcamento-pagamento,auth-rotas,kubernetes-synthetic,configure-final,health,e2e'
if (($stepNames -join ',') -ne $expectedOrder) {
    throw "Provision order changed: $($stepNames -join ',')"
}

if ((@(Get-ProvisionStepsFrom -Steps $ProvisionSteps -StartAt configure) | ForEach-Object { $_.Name }) -contains 'e2e') {
    throw 'E2E must be skipped unless requested.'
}
if ((@(Get-ProvisionStepsFrom -Steps $ProvisionSteps -StartAt health -IncludeOptional $true | ForEach-Object { $_.Name }) -join ',') -ne 'health,e2e') {
    throw 'StartAt must resume from the given step and include E2E when requested.'
}
if ((@(Get-ProvisionStepsFrom -Steps $ProvisionSteps -StartAt e2e | ForEach-Object { $_.Name }) -join ',') -ne 'e2e') {
    throw 'StartAt e2e must run the E2E.'
}
function Get-PlanNames([string]$StartAt, [bool]$E2E = $false) {
    @(Get-ProvisionPlan -Steps $ProvisionSteps -StartAt $StartAt -IncludeOptional $E2E | ForEach-Object { $_.Name }) -join ','
}
if ((Get-PlanNames configure) -ne (($stepNames | Where-Object { $_ -ne 'e2e' }) -join ',')) { throw 'A fresh run must follow the provision order.' }
if ((Get-PlanNames db-os) -ne 'credenciais,db-os,db-orcamento-pagamento,db-diagnostico-reparo,configure-bancos,nos,labrole,auth,configure-auth,cd-os,cd-diagnostico-reparo,cd-orcamento-pagamento,auth-rotas,kubernetes-synthetic,configure-final,health') {
    throw "Resume before the nodes step must only renew credentials: $(Get-PlanNames db-os)"
}
if ((Get-PlanNames cd-os) -notlike 'credenciais,nos,cd-os,*') { throw "Resume after the nodes step must check the nodes: $(Get-PlanNames cd-os)" }
if ((Get-PlanNames configure-auth) -notlike 'configure-auth,nos,cd-os,*') { throw "A configure resume must check the nodes after renewing: $(Get-PlanNames configure-auth)" }
if ((Get-PlanNames nos) -notlike 'credenciais,nos,labrole,*') { throw "Resume at nos must not repeat it: $(Get-PlanNames nos)" }
if ((Get-PlanNames health $true) -ne 'credenciais,nos,health,e2e') { throw "Unexpected health resume: $(Get-PlanNames health $true)" }

try {
    Get-ProvisionStepsFrom -Steps $ProvisionSteps -StartAt unknown | Out-Null
    throw 'Unknown step must be rejected.'
}
catch {
    if ($_.Exception.Message -notlike 'Etapa desconhecida*') { throw }
}

foreach ($case in @(@{ In = '1'; Out = 'homolog' }, @{ In = 'homolog'; Out = 'homolog' }, @{ In = ' 2 '; Out = 'production' }, @{ In = 'PRODUCTION'; Out = 'production' })) {
    if ((Resolve-ProvisionEnvironment -Value $case.In) -ne $case.Out) { throw "Environment '$($case.In)' must resolve to $($case.Out)." }
}
function Read-Host { param([string]$Prompt) '2' }
if ((Resolve-ProvisionEnvironment -Value '' 6>$null) -ne 'production') { throw 'Empty environment must be asked.' }
try {
    Resolve-ProvisionEnvironment -Value 'staging' | Out-Null
    throw 'Unknown environment must be rejected.'
}
catch {
    if ($_.Exception.Message -notlike 'Ambiente inválido*') { throw }
}

if ((Get-WorkflowRef -EnvironmentName homolog) -ne 'homolog' -or (Get-WorkflowRef -EnvironmentName production) -ne 'main') {
    throw 'Workflows must run on homolog for homolog and on main for production.'
}

$e2eStep = $ProvisionSteps | Where-Object { $_.Name -eq 'e2e' }
$fields = @(Get-WorkflowFields -Step $e2eStep -EnvironmentName production -LoadTest $true)
if (($fields -join ' ') -ne '-f environment=production -f run_load_test=true') { throw "Unexpected E2E inputs: $fields" }
if (@(Get-WorkflowFields -Step ($ProvisionSteps | Where-Object { $_.Name -eq 'auth' }) -EnvironmentName homolog).Count -ne 0) {
    throw 'Only the E2E receives inputs.'
}

$initial = Get-ConfigureArguments -EnvironmentName homolog -Initial $true -ReadClipboard $true -CredentialsFile 'creds'
if (-not $initial.ConfigureSonarCloud -or -not $initial.ConfigureNewRelic -or $initial.ContainsKey('AwsCredentialsFile')) {
    throw 'The first configure must read the clipboard and configure SonarCloud and New Relic.'
}
$later = Get-ConfigureArguments -EnvironmentName production -ReadClipboard $false -CredentialsFile 'creds'
if ($later.AwsCredentialsFile -ne 'creds' -or $later.ContainsKey('ConfigureSonarCloud') -or $later.Environment -ne 'production') {
    throw 'Later configure runs must reuse the local credentials file.'
}
if ($initial.ContainsKey('RepositoriesRoot')) { throw 'RepositoriesRoot must only be passed when informed.' }
$customRoot = Get-ConfigureArguments -EnvironmentName homolog -RepositoriesRootPath 'D:\projetos'
if ($customRoot.RepositoriesRoot -ne 'D:\projetos') { throw 'RepositoriesRoot must be forwarded to configure-environment.ps1.' }
foreach ($arguments in @($initial, $later)) {
    if (-not $arguments.UseAwsAcademyDisposableProductionProfile) { throw 'Every configure run must keep the disposable production profile.' }
}

$resume = Get-ResumeCommand -EnvironmentName homolog -StepName e2e -E2E $true -LoadTest $true
if ($resume -ne '.\scripts\provision-environment.ps1 -Environment homolog -StartAt e2e -RunLoadTest') { throw "Unexpected resume: $resume" }
$resume = Get-ResumeCommand -EnvironmentName production -StepName cd-os -E2E $true -LoadTest $true -RepositoriesRootPath 'D:\p'
if ($resume -ne '.\scripts\provision-environment.ps1 -Environment production -StartAt cd-os -RunE2E -RunLoadTest -RepositoriesRoot "D:\p"') { throw "Resume must keep the optional validations: $resume" }
if ((Get-ResumeCommand -EnvironmentName homolog -StepName auth) -ne '.\scripts\provision-environment.ps1 -Environment homolog -StartAt auth') { throw 'Plain resume changed.' }

$script:GitHubLogin = 'tester'
$script:ghCalls = [Collections.Generic.List[string]]::new()
$script:runIds = [Collections.Generic.Queue[long]]::new()
$script:runStates = [Collections.Generic.Queue[string]]::new()
$script:gateValue = 'true'
function gh {
    param([Parameter(ValueFromRemainingArguments = $true)][object[]]$Arguments)

    $line = $Arguments -join ' '
    $script:ghCalls.Add($line)
    $global:LASTEXITCODE = 0
    if ($line -like 'variable get DEPLOY_ENABLED*') { return $script:gateValue }
    if ($line -eq 'api user --jq .login') { return 'tester' }
    if ($line -like 'run list*') {
        if ($line -notlike '*--event workflow_dispatch --user tester *') { throw "Run list must filter by the gh user: $line" }
        $id = $script:runIds.Dequeue()
        if ($id -eq 0) { return '[]' }
        return "[{`"databaseId`":$id}]"
    }
    if ($line -like 'run view*') {
        $state = $script:runStates.Dequeue() -split ':'
        return "{`"status`":`"$($state[0])`",`"conclusion`":`"$($state[1])`"}"
    }
    if ($line -like 'workflow run*') { return '' }
    throw "Unexpected gh call: $line"
}

if ((Get-GitHubLogin) -ne 'tester') { throw 'Must read the gh login.' }
$script:RunLinks = [Collections.Generic.List[string]]::new()
foreach ($id in @(100, 100, 101)) { $script:runIds.Enqueue($id) }
foreach ($state in @('queued:', 'in_progress:', 'completed:success')) { $script:runStates.Enqueue($state) }
$cdStep = $ProvisionSteps | Where-Object { $_.Name -eq 'cd-os' }
Invoke-ProvisionWorkflow -Step $cdStep -EnvironmentName production 6>$null
if (-not ($script:ghCalls -contains 'variable get DEPLOY_ENABLED --repo tiagomiele/fiap-tc-fase4-oficina-os-service --env production')) {
    throw 'CD must check DEPLOY_ENABLED before dispatching.'
}
if (-not ($script:ghCalls -contains 'workflow run cd.yml --repo tiagomiele/fiap-tc-fase4-oficina-os-service --ref main')) {
    throw 'Production CD must run on main.'
}
if (-not ($script:ghCalls -contains 'run view 101 --repo tiagomiele/fiap-tc-fase4-oficina-os-service --json status,conclusion')) {
    throw 'Must watch the newly dispatched run, not the previous one.'
}
if ($script:RunLinks[0] -ne 'cd-os: https://github.com/tiagomiele/fiap-tc-fase4-oficina-os-service/actions/runs/101') {
    throw "Run link not recorded: $($script:RunLinks[0])"
}

$script:ghCalls.Clear()
foreach ($id in @(0, 7)) { $script:runIds.Enqueue($id) }
$script:runStates.Enqueue('completed:success')
Invoke-ProvisionWorkflow -Step ($ProvisionSteps | Where-Object { $_.Name -eq 'kubernetes' }) -EnvironmentName homolog 6>$null
if (-not ($script:ghCalls -contains 'workflow run deploy-homolog.yml --repo tiagomiele/fiap-tc-fase4-oficina-kubernetes-infra --ref homolog')) {
    throw 'Kubernetes must use deploy-<environment>.yml.'
}

foreach ($id in @(5, 6)) { $script:runIds.Enqueue($id) }
$script:runStates.Enqueue('completed:failure')
try {
    Invoke-ProvisionWorkflow -Step ($ProvisionSteps | Where-Object { $_.Name -eq 'auth' }) -EnvironmentName homolog 6>$null
    throw 'A failed run must stop the provisioning.'
}
catch {
    if ($_.Exception.Message -notlike "*'failure'*actions/runs/6") { throw }
}

$script:ghCalls.Clear()
$script:gateValue = 'false'
try {
    Invoke-ProvisionWorkflow -Step $cdStep -EnvironmentName homolog 6>$null
    throw 'A closed deploy gate must stop the CD.'
}
catch {
    if ($_.Exception.Message -notlike "DEPLOY_ENABLED de fiap-tc-fase4-oficina-os-service (homolog) está 'false'*") { throw }
}
if (@($script:ghCalls | Where-Object { $_ -like 'workflow run*' }).Count -ne 0) { throw 'CD must not be dispatched with the gate closed.' }

$global:LASTEXITCODE = 0
Write-Host 'provision-environment.ps1 tests passed.'
