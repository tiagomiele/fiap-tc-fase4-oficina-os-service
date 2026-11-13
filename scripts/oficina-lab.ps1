# Funções comuns aos scripts de operação (use com dot-source: . .\scripts\oficina-lab.ps1).
# Menu de ambiente, credenciais do AWS Academy, nós do EKS, health dos serviços e disparo de workflows.

$OficinaOwner = 'tiagomiele'
$OficinaRegion = 'us-west-2'
$OficinaServicos = @(
    @{ Nome = 'OS Service';            Service = 'oficina-app';                 Namespace = 'oficina-{0}' }
    @{ Nome = 'Diagnóstico e Reparo';  Service = 'oficina-diagnostico-reparo';  Namespace = 'oficina-diagnostico-reparo-{0}' }
    @{ Nome = 'Orçamento e Pagamento'; Service = 'oficina-orcamento-pagamento'; Namespace = 'oficina-orcamento-pagamento-{0}' }
)
$OficinaFilas = @('os-eventos', 'orcamento-pagamento-comandos', 'diagnostico-reparo-comandos')
$OficinaAvisoCredenciais = 'No AWS Academy: Start Lab, espere o indicador verde, abra AWS Details -> AWS CLI -> Show e copie o bloco [default] inteiro.'

function Assert-OficinaComandos {
    foreach ($command in @('aws', 'kubectl', 'gh')) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "Comando obrigatório não encontrado: $command"
        }
    }
}

function Invoke-OficinaNative {
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
    [pscustomobject]@{ ExitCode = $exitCode; Output = $output; Text = ($output -join [Environment]::NewLine).Trim() }
}

function Select-OficinaOpcao {
    param(
        [Parameter(Mandatory)][string]$Pergunta,
        [Parameter(Mandatory)][string[]]$Opcoes,
        [string]$Valor
    )

    # Valor informado no parâmetro: valida e segue. Vazio: mostra o menu até receber uma opção válida.
    $interativo = [string]::IsNullOrWhiteSpace($Valor)
    while ($true) {
        if ($interativo) {
            Write-Host $Pergunta
            for ($i = 0; $i -lt $Opcoes.Count; $i++) { Write-Host "  $($i + 1)) $($Opcoes[$i])" }
            $Valor = Read-Host "Escolha de 1 a $($Opcoes.Count)"
        }
        $escolha = "$Valor".Trim().ToLowerInvariant()
        for ($i = 0; $i -lt $Opcoes.Count; $i++) {
            if ($escolha -eq "$($i + 1)" -or $escolha -eq $Opcoes[$i].ToLowerInvariant()) { return $Opcoes[$i] }
        }
        if (-not $interativo) {
            throw "Opção inválida: '$Valor'. Use $($Opcoes -join ' ou ')."
        }
        Write-Host "Opção inválida: '$Valor'." -ForegroundColor Yellow
    }
}

function Select-OficinaAmbiente {
    param([string]$Valor)

    Select-OficinaOpcao -Pergunta 'Qual ambiente?' -Opcoes @('homolog', 'production') -Valor $Valor
}

function Get-OficinaBranch {
    param([Parameter(Mandatory)][string]$Ambiente)

    # Os workflows escolhem o ambiente pela branch: homolog -> homolog, main -> production.
    if ($Ambiente -eq 'production') { 'main' } else { 'homolog' }
}

function ConvertFrom-OficinaAwsBlock {
    param([string]$Texto)

    if ([string]::IsNullOrWhiteSpace($Texto)) { return $null }
    $credenciais = @{}
    foreach ($linha in $Texto -split "`r?`n") {
        if ($linha -match '^\s*(aws_access_key_id|aws_secret_access_key|aws_session_token)\s*=\s*(.+?)\s*$') {
            $credenciais[$matches[1].ToUpperInvariant()] = $matches[2]
        }
    }
    if ($credenciais.Count -ne 3) { return $null }
    $credenciais
}

function Test-OficinaAwsAccess {
    param([string]$Region = $OficinaRegion)

    # O sts get-caller-identity responde mesmo com a credencial cancelada (voc-cancel-cred); uma leitura real não.
    (Invoke-OficinaNative -Command 'aws' -Arguments @('sqs', 'list-queues', '--region', $Region, '--queue-name-prefix', 'oficina-', '--output', 'json')).ExitCode -eq 0
}

function Assert-OficinaAwsAccess {
    param([Parameter(Mandatory)][string]$Ambiente, [string]$Region = $OficinaRegion)

    if (-not (Test-OficinaAwsAccess -Region $Region)) {
        throw "Credenciais AWS vencidas neste computador. $OficinaAvisoCredenciais Depois rode .\scripts\lab-reiniciado.ps1 -Environment $Ambiente e este script de novo."
    }
}

function Wait-OficinaLabCredentials {
    param([string]$Region = $OficinaRegion)

    # Lê o [default] da área de transferência e só segue quando ele dá acesso de verdade à conta do Lab.
    while ($true) {
        $texto = try { Get-Clipboard -Raw -ErrorAction Stop } catch { $null }
        $credenciais = ConvertFrom-OficinaAwsBlock -Texto $texto
        if ($null -eq $credenciais) {
            Write-Host "A área de transferência não tem o bloco [default]. $OficinaAvisoCredenciais" -ForegroundColor Yellow
        }
        else {
            $env:AWS_ACCESS_KEY_ID = $credenciais.AWS_ACCESS_KEY_ID
            $env:AWS_SECRET_ACCESS_KEY = $credenciais.AWS_SECRET_ACCESS_KEY
            $env:AWS_SESSION_TOKEN = $credenciais.AWS_SESSION_TOKEN
            $env:AWS_DEFAULT_REGION = $Region
            if (Test-OficinaAwsAccess -Region $Region) {
                Write-Host '  credenciais do Lab válidas'
                return $credenciais
            }
            Write-Host "O bloco [default] copiado está vencido ou cancelado (sessão antiga do Lab). $OficinaAvisoCredenciais" -ForegroundColor Yellow
        }
        Read-Host 'Depois de copiar, tecle Enter (Ctrl+C cancela)' | Out-Null
    }
}

function Update-OficinaCredenciais {
    param(
        [Parameter(Mandatory)][string]$Ambiente,
        [string]$RepositoriesRoot,
        [switch]$ConfigureMercadoPago
    )

    # Os workflows usam as credenciais gravadas nos GitHub Environments: regrava a partir do arquivo local já validado.
    $arquivo = if (-not [string]::IsNullOrWhiteSpace($env:AWS_SHARED_CREDENTIALS_FILE)) { $env:AWS_SHARED_CREDENTIALS_FILE } else { Join-Path (Join-Path $HOME '.aws') 'credentials' }
    $configure = @{ Environment = $Ambiente; UseAwsAcademyDisposableProductionProfile = $true; AwsCredentialsFile = $arquivo }
    if ($ConfigureMercadoPago) { $configure.ConfigureMercadoPago = $true }
    if (-not [string]::IsNullOrWhiteSpace($RepositoriesRoot)) { $configure.RepositoriesRoot = $RepositoriesRoot }
    & (Join-Path $PSScriptRoot 'configure-environment.ps1') @configure
}

function Test-OficinaCluster {
    param([Parameter(Mandatory)][string]$Ambiente, [string]$Region = $OficinaRegion)

    (Invoke-OficinaNative -Command 'aws' -Arguments @('eks', 'describe-cluster', '--region', $Region, '--name', "oficina-$Ambiente", '--query', 'cluster.status', '--output', 'text')).ExitCode -eq 0
}

function Connect-OficinaCluster {
    param([Parameter(Mandatory)][string]$Ambiente, [string]$Region = $OficinaRegion)

    $result = Invoke-OficinaNative -Command 'aws' -Arguments @('eks', 'update-kubeconfig', '--region', $Region, '--name', "oficina-$Ambiente")
    if ($result.ExitCode -ne 0) {
        throw "Não foi possível apontar o kubectl para oficina-$Ambiente`: $($result.Text)"
    }
}

function Get-OficinaReadyNodeCount {
    $result = Invoke-OficinaNative -Command 'kubectl' -Arguments @('get', 'nodes', '--no-headers')
    if ($result.ExitCode -ne 0) { return 0 }
    @($result.Output | Where-Object { $_ -match '^\S+\s+Ready(\s|,)' }).Count
}

function Get-OficinaDesiredNodeCount {
    param([Parameter(Mandatory)][string]$Ambiente, [string]$Region = $OficinaRegion)

    $cluster = "oficina-$Ambiente"
    $result = Invoke-OficinaNative -Command 'aws' -Arguments @('eks', 'describe-nodegroup', '--region', $Region, '--cluster-name', $cluster, '--nodegroup-name', "$cluster-nodes", '--query', 'nodegroup.scalingConfig.desiredSize', '--output', 'text')
    $desired = 0
    if ($result.ExitCode -eq 0 -and [int]::TryParse($result.Text, [ref]$desired) -and $desired -gt 0) { return $desired }
    1
}

function Get-OficinaNodeInstances {
    param([Parameter(Mandatory)][string]$Ambiente, [string]$Region = $OficinaRegion)

    $result = Invoke-OficinaNative -Command 'aws' -Arguments @('ec2', 'describe-instances', '--region', $Region, '--filters', "Name=tag:eks:cluster-name,Values=oficina-$Ambiente", 'Name=instance-state-name,Values=pending,running,stopping,stopped', '--query', 'Reservations[].Instances[].[InstanceId,State.Name]', '--output', 'text')
    if ($result.ExitCode -ne 0) {
        throw "Falha ao listar as instâncias dos nós: $($result.Text)"
    }
    @($result.Output | Where-Object { $_ -match '^i-' } | ForEach-Object {
        $partes = $_ -split '\s+'
        [pscustomobject]@{ Id = $partes[0]; Estado = $partes[1] }
    })
}

function Wait-OficinaNodes {
    param([Parameter(Mandatory)][int]$Esperado, [Parameter(Mandatory)][int]$Minutos)

    $limite = (Get-Date).AddMinutes($Minutos)
    while ($true) {
        $prontos = Get-OficinaReadyNodeCount
        if ($prontos -ge $Esperado) { return $true }
        if ((Get-Date) -gt $limite) { return $false }
        Write-Host "  $prontos de $Esperado nó(s) Ready; aguardando..."
        Start-Sleep -Seconds 30
    }
}

function Repair-OficinaNodes {
    param(
        [Parameter(Mandatory)][string]$Ambiente,
        [string]$Region = $OficinaRegion,
        [int]$EsperaMinutos = 5,
        [int]$RecriacaoMinutos = 12
    )

    # Seção 21.2 do Bootstrap: depois do Start Lab, os nós podem voltar parados ou sem entrar no cluster.
    Connect-OficinaCluster -Ambiente $Ambiente -Region $Region
    $esperado = Get-OficinaDesiredNodeCount -Ambiente $Ambiente -Region $Region
    if (Wait-OficinaNodes -Esperado $esperado -Minutos 0) {
        Write-Host "  $esperado nó(s) Ready"
        return
    }

    $paradas = @(Get-OficinaNodeInstances -Ambiente $Ambiente -Region $Region | Where-Object { $_.Estado -eq 'stopped' } | ForEach-Object { $_.Id })
    if ($paradas.Count -gt 0) {
        Write-Host "  ligando as instâncias paradas: $($paradas -join ', ')"
        $start = Invoke-OficinaNative -Command 'aws' -Arguments (@('ec2', 'start-instances', '--region', $Region, '--instance-ids') + $paradas)
        if ($start.ExitCode -ne 0) { throw "Falha ao ligar as instâncias: $($start.Text)" }
    }
    Write-Host "  aguardando os nós entrarem no cluster (até $EsperaMinutos min)..."
    if (Wait-OficinaNodes -Esperado $esperado -Minutos $EsperaMinutos) {
        Write-Host "  $esperado nó(s) Ready"
        return
    }

    # Instâncias ligadas que não entram no cluster: o Academy bloqueou o bootstrap do nó. O node group cria outras.
    $ids = @(Get-OficinaNodeInstances -Ambiente $Ambiente -Region $Region | ForEach-Object { $_.Id })
    if ($ids.Count -gt 0) {
        Write-Host "  recriando os nós (as instâncias $($ids -join ', ') não entraram no cluster)"
        $terminate = Invoke-OficinaNative -Command 'aws' -Arguments (@('ec2', 'terminate-instances', '--region', $Region, '--instance-ids') + $ids)
        if ($terminate.ExitCode -ne 0) { throw "Falha ao recriar os nós: $($terminate.Text)" }
    }
    Write-Host "  aguardando os nós novos (até $RecriacaoMinutos min)..."
    if (Wait-OficinaNodes -Esperado $esperado -Minutos $RecriacaoMinutos) {
        Write-Host "  $esperado nó(s) Ready"
        return
    }
    throw "Os nós de oficina-$Ambiente não ficaram Ready. Veja a seção 21.2 do Bootstrap (explicit deny no console da instância) e rode o script de novo."
}

function Wait-OficinaServicos {
    param([Parameter(Mandatory)][string]$Ambiente, [int]$Minutos = 10)

    foreach ($servico in $OficinaServicos) {
        $namespace = $servico.Namespace -f $Ambiente
        $deployment = Invoke-OficinaNative -Command 'kubectl' -Arguments @('get', 'deployment', $servico.Service, '-n', $namespace, '-o', 'name')
        if ($deployment.ExitCode -ne 0) {
            Write-Host "  $($servico.Nome): ainda sem deploy em $namespace"
            continue
        }
        $wait = Invoke-OficinaNative -Command 'kubectl' -Arguments @('wait', '--for=condition=Available', "deployment/$($servico.Service)", '-n', $namespace, "--timeout=$($Minutos)m")
        if ($wait.ExitCode -ne 0) {
            throw "$($servico.Nome) não ficou pronto em $Minutos min. Veja: kubectl get pods -n $namespace"
        }
        Write-Host "  $($servico.Nome): pronto"
    }
}

function Get-OficinaGitHubVar {
    param(
        [Parameter(Mandatory)][string]$Repositorio,
        [Parameter(Mandatory)][string]$Nome,
        [Parameter(Mandatory)][string]$Ambiente
    )

    $result = Invoke-OficinaNative -Command 'gh' -Arguments @('variable', 'get', $Nome, '--repo', "$OficinaOwner/$Repositorio", '--env', $Ambiente)
    if ($result.ExitCode -ne 0) { return $null }
    $result.Text
}

function Get-OficinaLbUrl {
    param([Parameter(Mandatory)][string]$Service, [Parameter(Mandatory)][string]$Namespace)

    $result = Invoke-OficinaNative -Command 'kubectl' -Arguments @('get', 'service', $Service, '-n', $Namespace, '-o', 'jsonpath={.status.loadBalancer.ingress[0].hostname}')
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($result.Text)) { return $null }
    "http://$($result.Text)"
}

function Test-OficinaHealth {
    param([Parameter(Mandatory)][string]$Url)

    try {
        [string](Invoke-RestMethod -Uri "$Url/actuator/health" -TimeoutSec 15 -ErrorAction Stop).status -eq 'UP'
    }
    catch {
        $false
    }
}

function Wait-OficinaHealth {
    param([Parameter(Mandatory)][string]$Ambiente, [int]$Minutos = 10)

    $urls = [ordered]@{}
    foreach ($servico in $OficinaServicos) {
        $url = Get-OficinaLbUrl -Service $servico.Service -Namespace ($servico.Namespace -f $Ambiente)
        if ($null -eq $url) {
            throw "$($servico.Nome) sem LoadBalancer em $($servico.Namespace -f $Ambiente). Confira o deploy do serviço."
        }
        # O DNS de um LoadBalancer novo pode levar alguns minutos para responder.
        $limite = (Get-Date).AddMinutes($Minutos)
        while (-not (Test-OficinaHealth -Url $url)) {
            if ((Get-Date) -gt $limite) {
                throw "$($servico.Nome) não respondeu UP em $url/actuator/health depois de $Minutos min."
            }
            Write-Host "  $($servico.Nome) ainda não está UP; aguardando..."
            Start-Sleep -Seconds 20
        }
        Write-Host "  $($servico.Nome): UP"
        $urls[$servico.Nome] = $url
    }
    $urls['API Gateway'] = Get-OficinaGitHubVar -Repositorio 'fiap-tc-fase4-oficina-os-service' -Nome 'API_GATEWAY_BASE_URL' -Ambiente $Ambiente
    $urls
}

function Get-OficinaPagamentoConfig {
    param([Parameter(Mandatory)][string]$Ambiente)

    $result = Invoke-OficinaNative -Command 'kubectl' -Arguments @('get', 'configmap', 'orcamento-pagamento-config', '-n', "oficina-orcamento-pagamento-$Ambiente", '-o', 'jsonpath={.data.PAYMENT_PROVIDER}|{.data.MERCADOPAGO_NOTIFICATION_URL}')
    if ($result.ExitCode -ne 0) { return $null }
    $partes = $result.Text -split '\|', 2
    $provedor = if ([string]::IsNullOrWhiteSpace($partes[0])) { 'fake' } else { $partes[0].Trim() }
    $webhook = if ($partes.Count -gt 1) { $partes[1].Trim() } else { '' }
    [pscustomobject]@{ Provedor = $provedor; Webhook = $webhook }
}

function Get-OficinaGitHubLogin {
    $result = Invoke-OficinaNative -Command 'gh' -Arguments @('api', 'user', '--jq', '.login')
    if ($result.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($result.Text)) {
        throw "Não foi possível identificar o usuário do gh. Rode gh auth login. $($result.Text)"
    }
    $result.Text
}

function Get-OficinaUltimoRun {
    param(
        [Parameter(Mandatory)][string]$Repositorio,
        [Parameter(Mandatory)][string]$Workflow,
        [Parameter(Mandatory)][string]$Ref,
        [Parameter(Mandatory)][string]$Login
    )

    # Filtra pelo usuário do gh: um dispatch de outra pessoa no mesmo workflow não é confundido com o deste script.
    $result = Invoke-OficinaNative -Command 'gh' -Arguments @('run', 'list', '--repo', "$OficinaOwner/$Repositorio", '--workflow', $Workflow, '--branch', $Ref, '--event', 'workflow_dispatch', '--user', $Login, '--limit', '1', '--json', 'databaseId', '--jq', '.[0].databaseId // 0')
    if ($result.ExitCode -ne 0) {
        throw "Falha ao listar os runs de $Repositorio/$Workflow`: $($result.Text)"
    }
    [long]$result.Text
}

function Invoke-OficinaWorkflow {
    param(
        [Parameter(Mandatory)][string]$Repositorio,
        [Parameter(Mandatory)][string]$Workflow,
        [Parameter(Mandatory)][string]$Ref,
        [string[]]$Campos = @(),
        [int]$TimeoutMinutos = 60
    )

    $login = Get-OficinaGitHubLogin
    $anterior = Get-OficinaUltimoRun -Repositorio $Repositorio -Workflow $Workflow -Ref $Ref -Login $login
    $dispatch = Invoke-OficinaNative -Command 'gh' -Arguments (@('workflow', 'run', $Workflow, '--repo', "$OficinaOwner/$Repositorio", '--ref', $Ref) + $Campos)
    if ($dispatch.ExitCode -ne 0) {
        throw "Falha ao disparar $Repositorio/$Workflow`: $($dispatch.Text)"
    }

    # O run aparece na lista alguns segundos depois do dispatch; ids de run são crescentes.
    $limite = (Get-Date).AddMinutes(2)
    do {
        Start-Sleep -Seconds 5
        $id = Get-OficinaUltimoRun -Repositorio $Repositorio -Workflow $Workflow -Ref $Ref -Login $login
        if ((Get-Date) -gt $limite -and $id -le $anterior) {
            throw "O run de $Repositorio/$Workflow não apareceu. Confira em https://github.com/$OficinaOwner/$Repositorio/actions"
        }
    } while ($id -le $anterior)

    $url = "https://github.com/$OficinaOwner/$Repositorio/actions/runs/$id"
    Write-Host "  run: $url"
    $limite = (Get-Date).AddMinutes($TimeoutMinutos)
    $ultimo = ''
    while ($true) {
        $view = Invoke-OficinaNative -Command 'gh' -Arguments @('run', 'view', "$id", '--repo', "$OficinaOwner/$Repositorio", '--json', 'status,conclusion')
        if ($view.ExitCode -eq 0) {
            $run = $view.Text | ConvertFrom-Json
            if ([string]$run.status -ne $ultimo) {
                Write-Host "  status: $($run.status)"
                $ultimo = [string]$run.status
            }
            if ($ultimo -eq 'completed') {
                if ([string]$run.conclusion -ne 'success') { throw "O run terminou com '$($run.conclusion)'. Veja o log em $url" }
                return $url
            }
        }
        if ((Get-Date) -gt $limite) { throw "O run passou de $TimeoutMinutos min sem terminar. Acompanhe em $url" }
        Start-Sleep -Seconds 20
    }
}
