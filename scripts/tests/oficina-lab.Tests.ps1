Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptsDir = Split-Path $PSScriptRoot -Parent
foreach ($name in 'oficina-lab.ps1', 'lab-reiniciado.ps1', 'validar-ambiente.ps1', 'provedor-pagamento.ps1', 'pagamento-mercadopago.ps1', 'provision-environment.ps1', 'destroy-environment.ps1') {
    $path = Join-Path $scriptsDir $name
    $tokens = $null
    $errors = $null
    [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count -gt 0) { throw "$name has parse errors: $($errors | Out-String)" }
    if ([IO.File]::ReadAllBytes($path)[0] -ne 0xEF) { throw "$name must be saved as UTF-8 with BOM (Windows PowerShell 5.1)." }
}

. (Join-Path $scriptsDir 'oficina-lab.ps1')
function Start-Sleep { param([int]$Seconds) }

foreach ($case in @(@{ In = '1'; Out = 'homolog' }, @{ In = ' 2 '; Out = 'production' }, @{ In = 'PRODUCTION'; Out = 'production' }, @{ In = 'homolog'; Out = 'homolog' })) {
    if ((Select-OficinaAmbiente -Valor $case.In) -ne $case.Out) { throw "Environment '$($case.In)' must resolve to $($case.Out)." }
}
try {
    Select-OficinaAmbiente -Valor 'staging' | Out-Null
    throw 'An invalid parameter must be rejected.'
}
catch {
    if ($_.Exception.Message -notlike 'Opção inválida*') { throw }
}
$script:answers = [Collections.Generic.Queue[string]]::new()
foreach ($answer in 'x', '', '2') { $script:answers.Enqueue($answer) }
function Read-Host { param([string]$Prompt) $script:answers.Dequeue() }
if ((Select-OficinaAmbiente 6>$null) -ne 'production') { throw 'The menu must ask again until a valid option.' }
$script:answers.Enqueue('1')
if ((Select-OficinaOpcao -Pergunta 'Provedor?' -Opcoes @('mercadopago', 'fake') 6>$null) -ne 'mercadopago') { throw 'Generic menu must map numbers to options.' }

if ((Get-OficinaBranch -Ambiente production) -ne 'main' -or (Get-OficinaBranch -Ambiente homolog) -ne 'homolog') { throw 'Unexpected branch mapping.' }

$block = "[default]`r`naws_access_key_id=AKIA1`r`naws_secret_access_key = segredo`r`naws_session_token=tok/en+=="
$parsed = ConvertFrom-OficinaAwsBlock -Texto $block
if ($parsed.AWS_ACCESS_KEY_ID -ne 'AKIA1' -or $parsed.AWS_SECRET_ACCESS_KEY -ne 'segredo' -or $parsed.AWS_SESSION_TOKEN -ne 'tok/en+==') { throw 'The [default] block must be parsed.' }
if ($null -ne (ConvertFrom-OficinaAwsBlock -Texto 'texto qualquer')) { throw 'Text without the 3 keys is not a credential block.' }
if ($null -ne (ConvertFrom-OficinaAwsBlock -Texto $null)) { throw 'Empty clipboard is not a credential block.' }

$script:nodes = @()
$script:awsCalls = [Collections.Generic.List[string]]::new()
$script:instances = @()
function kubectl {
    param([Parameter(ValueFromRemainingArguments = $true)][object[]]$Arguments)
    $global:LASTEXITCODE = 0
    $line = $Arguments -join ' '
    if ($line -eq 'get nodes --no-headers') {
        $current = $script:nodes
        if ($script:nodeQueue.Count -gt 0) { $script:nodes = $script:nodeQueue.Dequeue() }
        return $current
    }
    if ($line -like 'get configmap orcamento-pagamento-config*') { return $script:configmap }
    throw "Unexpected kubectl call: $line"
}
function aws {
    param([Parameter(ValueFromRemainingArguments = $true)][object[]]$Arguments)
    $global:LASTEXITCODE = 0
    $line = $Arguments -join ' '
    $script:awsCalls.Add($line)
    if ($line -like 'eks update-kubeconfig*') { return 'Updated context' }
    if ($line -like 'eks describe-nodegroup*') { return '2' }
    if ($line -like 'ec2 describe-instances*') { return $script:instances }
    if ($line -like 'ec2 start-instances*' -or $line -like 'ec2 terminate-instances*') { return '' }
    throw "Unexpected aws call: $line"
}

$script:nodeQueue = [Collections.Generic.Queue[object]]::new()
$script:nodes = @('ip-1   Ready    <none>   1d   v1.30', 'ip-2   NotReady <none>   1d   v1.30')
if ((Get-OficinaReadyNodeCount) -ne 1) { throw 'Only Ready nodes count.' }

$script:nodes = @('ip-1   Ready    <none>   1d   v1.30', 'ip-2   Ready    <none>   1d   v1.30')
$script:awsCalls.Clear()
Repair-OficinaNodes -Ambiente production 6>$null
if (@($script:awsCalls | Where-Object { $_ -like 'ec2*' }).Count -ne 0) { throw 'Healthy nodes must not touch the instances.' }

$script:nodes = @()
$script:nodeQueue.Enqueue(@())
$script:nodeQueue.Enqueue(@('ip-1   Ready    <none>   1m   v1.30', 'ip-2   Ready    <none>   1m   v1.30'))
$script:instances = @("i-aaa`tstopped", "i-bbb`trunning")
$script:awsCalls.Clear()
Repair-OficinaNodes -Ambiente homolog -EsperaMinutos 1 6>$null
if (-not ($script:awsCalls -contains 'ec2 start-instances --region us-west-2 --instance-ids i-aaa')) { throw "Stopped nodes must be started: $($script:awsCalls -join ' | ')" }
if (@($script:awsCalls | Where-Object { $_ -like 'ec2 terminate-instances*' }).Count -ne 0) { throw 'Nodes that come back must not be recreated.' }

$script:nodes = @()
$script:nodeQueue.Clear()
$script:instances = @("i-ccc`trunning", "i-ddd`trunning")
$script:awsCalls.Clear()
try {
    Repair-OficinaNodes -Ambiente homolog -EsperaMinutos 0 -RecriacaoMinutos 0 6>$null
    throw 'Nodes that never join must stop the script.'
}
catch {
    if ($_.Exception.Message -notlike 'Os nós de oficina-homolog não ficaram Ready*') { throw }
}
if (-not ($script:awsCalls -contains 'ec2 terminate-instances --region us-west-2 --instance-ids i-ccc i-ddd')) { throw "Running nodes outside the cluster must be recreated: $($script:awsCalls -join ' | ')" }

$script:configmap = 'mercadopago|https://gw.example/webhooks/mercadopago'
$config = Get-OficinaPagamentoConfig -Ambiente homolog
if ($config.Provedor -ne 'mercadopago' -or $config.Webhook -ne 'https://gw.example/webhooks/mercadopago') { throw 'Unexpected payment config.' }
$script:configmap = '|'
if ((Get-OficinaPagamentoConfig -Ambiente homolog).Provedor -ne 'fake') { throw 'Empty PAYMENT_PROVIDER is the fake (deploy default).' }

$global:LASTEXITCODE = 0
Write-Host 'oficina-lab.ps1 tests passed.'
