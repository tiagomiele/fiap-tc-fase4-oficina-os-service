[CmdletBinding()]
param(
    [ValidateSet('homolog', 'production')]
    [string]$Environment = 'homolog',
    [string]$RepositoriesRoot,
    [string]$TerraformOrganization = 'oficina-fiap-soat-fase-2',
    [string]$TerraformProject = 'soat-fase4',
    [string]$TerraformProjectId = 'prj-UTzD6p6PqRVEr27C',
    [string]$SonarOrganization = 'tiagomiele',
    [string]$AwsCredentialsFile,
    [string]$SecretsRoot,
    [string]$NotificationSourceEmail = $env:NOTIFICATION_SOURCE_EMAIL,
    [switch]$EnableSesDelivery,
    [switch]$CreateSesIdentity,
    [switch]$ConfigureNewRelic,
    [switch]$DisableObservability,
    [switch]$UseAwsAcademyDisposableProductionProfile,
    [switch]$RequireBackendDeployReady,
    [switch]$SkipGitHub,
    [switch]$SkipBranchProtection,
    [switch]$ConfigureSonarCloud,
    [switch]$ConfigureMercadoPago,
    [switch]$ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:ConfigurationIssues = [Collections.Generic.List[string]]::new()
if ($ConfigureNewRelic -and $DisableObservability) {
    throw 'Use somente um parâmetro: ConfigureNewRelic ou DisableObservability.'
}
if ($CreateSesIdentity -and -not $EnableSesDelivery) {
    throw 'CreateSesIdentity exige EnableSesDelivery. No AWS Academy, omita ambos para usar o modo log.'
}
if ($RequireBackendDeployReady -and $SkipGitHub) {
    throw 'RequireBackendDeployReady não pode ser usado com SkipGitHub.'
}

$RepositoryNames = @{
    Kubernetes         = 'fiap-tc-fase4-oficina-kubernetes-infra'
    Database           = 'fiap-tc-fase4-oficina-database-infra'
    Backend            = 'fiap-tc-fase4-oficina-os-service'
    Auth               = 'fiap-tc-fase4-oficina-auth-serverless'
    OrcamentoPagamento = 'fiap-tc-fase4-oficina-orcamento-pagamento-service'
    DiagnosticoReparo  = 'fiap-tc-fase4-oficina-diagnostico-reparo-service'
}
$ServiceRepositoryKeys = @('Backend', 'OrcamentoPagamento', 'DiagnosticoReparo')
$DatabaseComponents = @('Database', 'DatabaseOrcamentoPagamento', 'DatabaseDiagnosticoReparo')
$RdsComponents = @('Database', 'DatabaseOrcamentoPagamento')
$TerraformComponents = @('Kubernetes') + $DatabaseComponents + @('Auth')
$DatabaseWorkingDirectories = @{
    Database                   = 'os'
    DatabaseOrcamentoPagamento = 'orcamento-pagamento'
    DatabaseDiagnosticoReparo  = 'diagnostico-reparo'
}
# Checks do ci.yml (sem filtro de path) exigidos nas regras de proteção da main e da homolog.
$RequiredStatusChecks = @{
    Kubernetes         = @('Repository validation', 'Terraform infrastructure', 'Terraform observability', 'Kubernetes add-ons validation')
    Database           = @('Repository validation', 'Terraform validation', 'Database integration tests', 'Security validation')
    Auth               = @('Repository validation', 'Java build and tests', 'Terraform validation', 'Security validation')
    Backend            = @('Repository validation', 'Build, test & coverage', 'SonarCloud', 'SBOM (CycloneDX) + Dependency-Track', 'Security scan')
    OrcamentoPagamento = @('Repository validation', 'Build, test & coverage', 'SonarCloud', 'SBOM (CycloneDX) + Dependency-Track', 'Security scan')
    DiagnosticoReparo  = @('Repository validation', 'Build, test & coverage', 'SonarCloud', 'SBOM (CycloneDX) + Dependency-Track', 'Security scan')
}
$GitHubOwner = 'tiagomiele'
# Prefixo fase4: os workspaces da Fase 3 (mesma organização) permanecem intocados.
$WorkspaceNames = @{}
foreach ($entry in @{
    Kubernetes                 = 'kubernetes'
    Database                   = 'db-os'
    DatabaseOrcamentoPagamento = 'db-orcamento-pagamento'
    DatabaseDiagnosticoReparo  = 'db-diagnostico-reparo'
    Auth                       = 'auth'
    NewRelic                   = 'newrelic'
}.GetEnumerator()) {
    $WorkspaceNames[$entry.Key] = @{
        homolog    = "oficina-fase4-$($entry.Value)-homolog"
        production = "oficina-fase4-$($entry.Value)-production"
    }
}

function Assert-Command {
    param([Parameter(Mandatory)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Comando obrigatório não encontrado: $Name"
    }
}

function Assert-TerraformPlatform {
    $versionOutput = @(& terraform version -json 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Não foi possível identificar a plataforma do Terraform: $(($versionOutput | Select-Object -Last 3) -join ' ')"
    }

    try {
        $version = ($versionOutput -join [Environment]::NewLine) | ConvertFrom-Json
    }
    catch {
        throw 'O comando terraform version -json retornou uma resposta inválida.'
    }

    if ($env:OS -eq 'Windows_NT' -and [string]$version.platform -ne 'windows_amd64') {
        throw "Terraform incompatível: plataforma $($version.platform). Instale a edição Windows x64 (windows_amd64) antes de continuar."
    }
}

function Resolve-OpenSslCommand {
    $command = Get-Command openssl -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    $git = Get-Command git -ErrorAction SilentlyContinue
    if ($null -ne $git) {
        $gitRoot = Split-Path (Split-Path $git.Source -Parent) -Parent
        $bundledOpenSsl = Join-Path $gitRoot 'usr\bin\openssl.exe'
        if (Test-Path $bundledOpenSsl) {
            return $bundledOpenSsl
        }
    }

    throw 'OpenSSL não encontrado no PATH nem na instalação do Git for Windows.'
}

function ConvertFrom-SecureText {
    param([Parameter(Mandatory)][Security.SecureString]$Value)

    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Read-SecretText {
    param([Parameter(Mandatory)][string]$Prompt)

    ConvertFrom-SecureText (Read-Host $Prompt -AsSecureString)
}

function New-RandomSecret {
    $bytes = New-Object byte[] 36
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $random.GetBytes($bytes)
    }
    finally {
        $random.Dispose()
    }

    ([Convert]::ToBase64String($bytes).TrimEnd('=') -replace '[+/]', 'A') + '!a9'
}

function Assert-RdsPassword {
    param([Parameter(Mandatory)][string]$Password)

    if ($Password.Length -lt 16 -or $Password.Length -gt 128) {
        throw 'A senha do RDS deve possuir entre 16 e 128 caracteres.'
    }

    $invalidCharacter = $Password.ToCharArray() | Where-Object {
        $code = [int]$_
        $code -lt 33 -or $code -gt 126 -or $_ -in @('/', '@', '"')
    } | Select-Object -First 1
    if ($null -ne $invalidCharacter) {
        throw 'A senha do RDS deve usar somente ASCII imprimível e não pode conter barra, arroba, aspas ou espaço.'
    }
}

function Assert-NotificationSourceEmail {
    param([Parameter(Mandatory)][string]$Email)

    if ($Email.Length -gt 320 -or $Email -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') {
        throw 'O remetente de notificação deve ser um endereço de e-mail válido com até 320 caracteres.'
    }
}

function Get-StoredValue {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Prompt
    )

    if (Test-Path $Path) {
        return (Get-Content $Path -Raw).Trim()
    }

    $value = Read-Host $Prompt
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Prompt não foi informado."
    }
    New-Item -ItemType Directory -Force (Split-Path $Path -Parent) | Out-Null
    Set-Content -Path $Path -Value $value -Encoding UTF8
    $value
}

function Get-StoredSecret {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Prompt,
        [switch]$GenerateWhenEmpty,
        [scriptblock]$Validator,
        [int]$MaxAttempts = 3,
        [string]$Directory = $script:SecretsDirectory
    )

    New-Item -ItemType Directory -Force $Directory | Out-Null
    $path = Join-Path $Directory "$Name.clixml"
    if (Test-Path $path) {
        $stored = ConvertFrom-SecureText (Import-Clixml $path)
        if ($null -ne $Validator) {
            try {
                & $Validator $stored
            }
            catch {
                throw "$($_.Exception.Message) Remova somente $path e reexecute o script para informar ou gerar um valor compatível."
            }
        }
        return $stored
    }

    $promptText = $Prompt
    if ($GenerateWhenEmpty) {
        $promptText = "$Prompt (Enter vazio para gerar uma nova)"
    }

    # O valor só é gravado depois de validado: um valor recusado nunca fica no disco
    # exigindo remoção manual antes da próxima execução.
    $value = $null
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $candidate = Read-SecretText $promptText
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            if (-not $GenerateWhenEmpty) {
                throw "$Name não foi informado."
            }
            $candidate = New-RandomSecret
        }

        if ($null -eq $Validator) {
            $value = $candidate
            break
        }

        try {
            & $Validator $candidate
            $value = $candidate
            break
        }
        catch {
            if ($attempt -eq $MaxAttempts) {
                throw
            }
            Write-Warning "$($_.Exception.Message) Informe outro valor ou pressione Enter para gerar um compatível."
        }
    }

    ConvertTo-SecureString $value -AsPlainText -Force | Export-Clixml $path
    $value
}

# Fase 4 usa credencial própria do projeto soat-fase4 (Team API token).
function Get-TerraformToken {
    Get-StoredSecret -Name 'hcp-terraform-token-fase4' -Prompt "HCP Terraform Team API token com acesso Admin somente ao projeto $TerraformProject" -Directory (Join-Path $SecretsRoot 'shared') -Validator {
        param($candidate)

        $script:HcpHeaders = @{ Authorization = "Bearer $candidate" }
        $script:HcpProject = Get-HcpProject
    }
}

function Get-HcpApiStatusCode {
    param([Parameter(Mandatory)][Management.Automation.ErrorRecord]$ErrorRecord)

    $responseProperty = $ErrorRecord.Exception.PSObject.Properties['Response']
    if ($null -eq $responseProperty -or $null -eq $responseProperty.Value) {
        return $null
    }

    $statusCode = $responseProperty.Value.StatusCode
    if ($statusCode -is [int]) {
        return $statusCode
    }
    [int]$statusCode
}

function Get-HcpApiErrorDetail {
    param([Parameter(Mandatory)][Management.Automation.ErrorRecord]$ErrorRecord)

    $errorDetailsProperty = $ErrorRecord.PSObject.Properties['ErrorDetails']
    $responseBody = if ($null -eq $errorDetailsProperty -or $null -eq $errorDetailsProperty.Value) {
        ''
    }
    else {
        [string]$errorDetailsProperty.Value.Message
    }
    $responseProperty = $ErrorRecord.Exception.PSObject.Properties['Response']
    if ([string]::IsNullOrWhiteSpace($responseBody) -and $null -ne $responseProperty -and $null -ne $responseProperty.Value) {
        try {
            $response = $responseProperty.Value
            $contentProperty = $response.PSObject.Properties['Content']
            if ($null -ne $contentProperty -and $null -ne $contentProperty.Value) {
                $responseBody = $contentProperty.Value.ReadAsStringAsync().GetAwaiter().GetResult()
            }
            else {
                $responseStream = $response.GetResponseStream()
                if ($null -ne $responseStream) {
                    $reader = [IO.StreamReader]::new($responseStream)
                    try {
                        $responseBody = $reader.ReadToEnd()
                    }
                    finally {
                        $reader.Dispose()
                    }
                }
            }
        }
        catch {
            $responseBody = ''
        }
    }

    if ([string]::IsNullOrWhiteSpace($responseBody)) {
        return [string]$ErrorRecord.Exception.Message
    }

    try {
        $apiError = $responseBody | ConvertFrom-Json
        $errorItems = @($apiError.PSObject.Properties['errors'].Value)
        $details = @($errorItems | ForEach-Object {
            $source = $_.PSObject.Properties['source'].Value
            $pointerValue = if ($null -eq $source) { $null } else { $source.PSObject.Properties['pointer'].Value }
            $pointer = if ([string]::IsNullOrWhiteSpace($pointerValue)) { '' } else { " [$pointerValue]" }
            "$($_.title): $($_.detail)$pointer"
        })
        if ($details.Count -gt 0) {
            return $details -join '; '
        }
    }
    catch {
    }

    ($responseBody -replace '[\r\n]+', ' ').Trim()
}

function Invoke-HcpApi {
    param(
        [Parameter(Mandatory)][ValidateSet('GET', 'POST', 'PATCH', 'DELETE')][string]$Method,
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
        # Bytes UTF-8: o Windows PowerShell 5.1 envia corpo string em ISO-8859-1 e o HCP rejeita acentos com HTTP 400.
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 20 -Compress))
    }

    try {
        Invoke-RestMethod @parameters
    }
    catch {
        $statusCode = Get-HcpApiStatusCode -ErrorRecord $_
        $detail = Get-HcpApiErrorDetail -ErrorRecord $_
        $exception = [InvalidOperationException]::new(
            "Falha no HCP Terraform ao $Context ($Method /api/v2/$Path): $detail",
            $_.Exception
        )
        if ($null -ne $statusCode) {
            $exception.Data['HcpStatusCode'] = $statusCode
        }
        throw $exception
    }
}

function Get-HcpProject {
    $project = (Invoke-HcpApi -Method GET -Path "projects/$TerraformProjectId" -Context "consultar o projeto $TerraformProject ($TerraformProjectId)").data
    # O id é a referência do projeto da Fase 4; o nome vem do HCP só para as mensagens.
    $script:TerraformProject = [string]$project.attributes.name
    $organization = [string]$project.relationships.organization.data.id
    if ($organization -ne $TerraformOrganization) {
        throw "O projeto $TerraformProjectId pertence à organização '$organization', esperado '$TerraformOrganization'."
    }
    $project
}

function Get-HcpWorkspace {
    param(
        [Parameter(Mandatory)][string]$Name,
        [switch]$AllowMissing
    )

    try {
        return (Invoke-HcpApi -Method GET -Path "organizations/$TerraformOrganization/workspaces/$Name" -Context "consultar o workspace $Name").data
    }
    catch {
        if ($AllowMissing -and $_.Exception.Data['HcpStatusCode'] -eq 404) {
            return $null
        }
        throw
    }
}

function Initialize-HcpWorkspace {
    param(
        [Parameter(Mandatory)][object]$Project,
        [Parameter(Mandatory)][string]$Name,
        [string]$WorkingDirectory = ''
    )

    $workspace = Get-HcpWorkspace -Name $Name -AllowMissing
    if ($null -ne $workspace -and [string]$workspace.relationships.project.data.id -ne [string]$Project.id) {
        throw "O workspace $Name pertence a outro projeto ($($workspace.relationships.project.data.id)). A Fase 4 só usa workspaces do projeto $TerraformProject."
    }
    $desiredAttributes = @{
        'execution-mode'    = 'remote'
        'auto-apply'        = $false
        'working-directory' = $WorkingDirectory
    }
    if ($null -eq $workspace) {
        $desiredAttributes.name = $Name
        $body = @{
            data = @{
                type          = 'workspaces'
                attributes    = $desiredAttributes
                relationships = @{
                    project = @{ data = @{ type = 'projects'; id = $Project.id } }
                }
            }
        }
        return (Invoke-HcpApi -Method POST -Path "organizations/$TerraformOrganization/workspaces" -Body $body -Context "criar o workspace $Name").data
    }

    $changedAttributes = @{}
    if ([string]$workspace.attributes.'execution-mode' -ne 'remote') {
        $changedAttributes['execution-mode'] = 'remote'
    }
    if ([bool]$workspace.attributes.'auto-apply') {
        $changedAttributes['auto-apply'] = $false
    }
    if ([string]$workspace.attributes.'working-directory' -ne $WorkingDirectory) {
        $changedAttributes['working-directory'] = $WorkingDirectory
    }
    if ($changedAttributes.Count -eq 0) {
        return $workspace
    }

    $body = @{
        data = @{
            type       = 'workspaces'
            id         = $workspace.id
            attributes = $changedAttributes
        }
    }
    (Invoke-HcpApi -Method PATCH -Path "workspaces/$($workspace.id)" -Body $body -Context "atualizar o workspace $Name").data
}

function Get-HcpWorkspaceVariables {
    param([Parameter(Mandatory)][string]$WorkspaceId)

    @((Invoke-HcpApi -Method GET -Path "workspaces/$WorkspaceId/vars?page%5Bsize%5D=100" -Context "consultar variáveis do workspace $WorkspaceId").data)
}

function Set-HcpWorkspaceVariable {
    param(
        [Parameter(Mandatory)][object]$Workspace,
        [Parameter(Mandatory)][string]$Key,
        [Parameter(Mandatory)][string]$Value,
        [ValidateSet('terraform', 'env')][string]$Category = 'terraform',
        [bool]$Hcl = $false,
        [bool]$Sensitive = $false,
        [string]$Description = 'Gerenciada por scripts/configure-environment.ps1'
    )

    $variables = Get-HcpWorkspaceVariables $Workspace.id
    $matchingVariables = @($variables | Where-Object {
        $_.attributes.key -eq $Key -and $_.attributes.category -eq $Category
    })
    $existing = $matchingVariables | Select-Object -First 1

    $payload = @{
        data = @{
            type       = 'vars'
            attributes = @{
                key         = $Key
                value       = $Value
                description = $Description
                category    = $Category
                hcl         = $Hcl
                sensitive   = $Sensitive
            }
        }
    }

    if ($null -eq $existing) {
        Invoke-HcpApi -Method POST -Path "workspaces/$($Workspace.id)/vars" -Body $payload -Context "criar a variável $Key no workspace $($Workspace.attributes.name)" | Out-Null
        return
    }

    if ([bool]$existing.attributes.sensitive -ne $Sensitive) {
        foreach ($variable in $matchingVariables) {
            Invoke-HcpApi -Method DELETE -Path "workspaces/$($Workspace.id)/vars/$($variable.id)" -Context "recriar a variável $Key no workspace $($Workspace.attributes.name)" | Out-Null
        }
        Invoke-HcpApi -Method POST -Path "workspaces/$($Workspace.id)/vars" -Body $payload -Context "recriar a variável $Key no workspace $($Workspace.attributes.name)" | Out-Null
        return
    }

    $payload.data.id = $existing.id
    if ($Sensitive) {
        $payload.data.attributes = @{ value = $Value }
    }
    else {
        $payload.data.attributes.Remove('sensitive')
    }
    Invoke-HcpApi -Method PATCH -Path "workspaces/$($Workspace.id)/vars/$($existing.id)" -Body $payload -Context "atualizar a variável $Key no workspace $($Workspace.attributes.name)" | Out-Null
    foreach ($duplicateVariable in $matchingVariables | Select-Object -Skip 1) {
        Invoke-HcpApi -Method DELETE -Path "workspaces/$($Workspace.id)/vars/$($duplicateVariable.id)" -Context "remover duplicata da variável $Key no workspace $($Workspace.attributes.name)" | Out-Null
    }
}

function Remove-HcpWorkspaceVariable {
    param(
        [Parameter(Mandatory)][object]$Workspace,
        [Parameter(Mandatory)][string]$Key,
        [ValidateSet('terraform', 'env')][string]$Category = 'terraform'
    )

    $variables = Get-HcpWorkspaceVariables $Workspace.id
    foreach ($variable in $variables | Where-Object {
        $_.attributes.key -eq $Key -and $_.attributes.category -eq $Category
    }) {
        Invoke-HcpApi -Method DELETE -Path "workspaces/$($Workspace.id)/vars/$($variable.id)" -Context "remover a variável $Key do workspace $($Workspace.attributes.name)" | Out-Null
    }
}

function Set-HcpVariableSetVariables {
    param(
        [Parameter(Mandatory)][string]$VariableSetId,
        [Parameter(Mandatory)][object[]]$Definitions
    )

    $existingVariables = @((Invoke-HcpApi -Method GET -Path "varsets/$VariableSetId/relationships/vars" -Context 'consultar variáveis do Variable Set AWS').data)
    foreach ($definition in $Definitions) {
        $matchingVariables = @($existingVariables | Where-Object {
            $_.attributes.key -eq $definition.attributes.key -and $_.attributes.category -eq $definition.attributes.category
        })
        $payload = @{ data = $definition }
        if ($matchingVariables.Count -eq 0) {
            Invoke-HcpApi -Method POST -Path "varsets/$VariableSetId/relationships/vars" -Body $payload -Context "criar $($definition.attributes.key) no Variable Set AWS" | Out-Null
            continue
        }

        $primaryVariable = $matchingVariables[0]
        if ([bool]$primaryVariable.attributes.sensitive -ne [bool]$definition.attributes.sensitive) {
            foreach ($variable in $matchingVariables) {
                Invoke-HcpApi -Method DELETE -Path "varsets/$VariableSetId/relationships/vars/$($variable.id)" -Context "recriar $($definition.attributes.key) no Variable Set AWS" | Out-Null
            }
            Invoke-HcpApi -Method POST -Path "varsets/$VariableSetId/relationships/vars" -Body $payload -Context "recriar $($definition.attributes.key) no Variable Set AWS" | Out-Null
            continue
        }

        $attributes = if ([bool]$definition.attributes.sensitive) {
            @{ value = $definition.attributes.value }
        }
        else {
            $definition.attributes.Clone()
        }
        $attributes.Remove('sensitive')
        $payload.data = @{
            type       = $definition.type
            id         = $primaryVariable.id
            attributes = $attributes
        }
        Invoke-HcpApi -Method PATCH -Path "varsets/$VariableSetId/relationships/vars/$($primaryVariable.id)" -Body $payload -Context "atualizar $($definition.attributes.key) no Variable Set AWS" | Out-Null
        foreach ($duplicateVariable in $matchingVariables | Select-Object -Skip 1) {
            Invoke-HcpApi -Method DELETE -Path "varsets/$VariableSetId/relationships/vars/$($duplicateVariable.id)" -Context "remover duplicata de $($definition.attributes.key) no Variable Set AWS" | Out-Null
        }
    }
}

function Set-AwsVariableSet {
    param(
        [Parameter(Mandatory)][object]$Project,
        [Parameter(Mandatory)][hashtable]$Credentials
    )

    # Variable Set do próprio projeto, aplicado ao projeto: não depende de permissão na organização.
    $name = 'aws-academy-credentials-fase4'
    $existingSets = @((Invoke-HcpApi -Method GET -Path "projects/$($Project.id)/varsets?page%5Bsize%5D=100" -Context 'consultar Variable Sets do projeto').data)
    $matchingSets = @($existingSets | Where-Object { $_.attributes.name -eq $name })

    $projectRelationships = @(@{ type = 'projects'; id = $Project.id })
    $variables = @(
        @{
            type       = 'vars'
            attributes = @{
                key       = 'AWS_ACCESS_KEY_ID'
                value     = $Credentials.AWS_ACCESS_KEY_ID
                category  = 'env'
                hcl       = $false
                sensitive = $true
            }
        },
        @{
            type       = 'vars'
            attributes = @{
                key       = 'AWS_SECRET_ACCESS_KEY'
                value     = $Credentials.AWS_SECRET_ACCESS_KEY
                category  = 'env'
                hcl       = $false
                sensitive = $true
            }
        },
        @{
            type       = 'vars'
            attributes = @{
                key       = 'AWS_SESSION_TOKEN'
                value     = $Credentials.AWS_SESSION_TOKEN
                category  = 'env'
                hcl       = $false
                sensitive = $true
            }
        }
    )

    $payload = @{
        data = @{
            type          = 'varsets'
            attributes    = @{
                name        = $name
                description = 'Credenciais temporárias compartilhadas do AWS Academy. Gerenciado pelo script central.'
                global      = $false
                priority    = $false
            }
            relationships = @{
                projects = @{ data = $projectRelationships }
                vars     = @{ data = $variables }
            }
        }
    }

    if ($matchingSets.Count -eq 0) {
        $payload.data.relationships.parent = @{
            data = @{
                type = 'projects'
                id   = $Project.id
            }
        }
        Invoke-HcpApi -Method POST -Path "organizations/$TerraformOrganization/varsets" -Body $payload -Context 'criar o Variable Set AWS' | Out-Null
        return
    }

    $primarySet = $matchingSets[0]
    $attributeChanges = @{}
    if ([string]$primarySet.attributes.description -ne [string]$payload.data.attributes.description) {
        $attributeChanges.description = $payload.data.attributes.description
    }
    if ([bool]$primarySet.attributes.global) {
        $attributeChanges.global = $false
    }
    if ([bool]$primarySet.attributes.priority) {
        $attributeChanges.priority = $false
    }
    if ($attributeChanges.Count -gt 0) {
        $metadataPayload = @{
            data = @{
                type       = 'varsets'
                id         = $primarySet.id
                attributes = $attributeChanges
            }
        }
        Invoke-HcpApi -Method PATCH -Path "varsets/$($primarySet.id)" -Body $metadataPayload -Context 'atualizar os metadados do Variable Set AWS' | Out-Null
    }
    $projectPayload = @{ data = $projectRelationships }
    Invoke-HcpApi -Method POST -Path "varsets/$($primarySet.id)/relationships/projects" -Body $projectPayload -Context "associar o Variable Set AWS ao projeto $TerraformProject" | Out-Null
    Set-HcpVariableSetVariables -VariableSetId $primarySet.id -Definitions $variables
    foreach ($duplicateSet in $matchingSets | Select-Object -Skip 1) {
        Invoke-HcpApi -Method DELETE -Path "varsets/$($duplicateSet.id)" -Context 'remover Variable Set AWS duplicado' | Out-Null
    }
}

function Parse-AwsCredentials {
    param([Parameter(Mandatory)][string]$Content)

    $result = @{}
    foreach ($line in $Content -split "`r?`n") {
        if ($line -match '^\s*(aws_access_key_id|aws_secret_access_key|aws_session_token)\s*=\s*(.+?)\s*$') {
            $result[$matches[1].ToUpperInvariant()] = $matches[2]
        }
    }

    if ($result.Count -ne 3) {
        return $null
    }

    $result
}

function Get-AwsCredentials {
    $content = $null
    if (-not [string]::IsNullOrWhiteSpace($AwsCredentialsFile)) {
        if (-not (Test-Path $AwsCredentialsFile)) {
            throw "Arquivo de credenciais não encontrado: $AwsCredentialsFile"
        }
        $content = Get-Content $AwsCredentialsFile -Raw
    }
    else {
        try {
            $content = Get-Clipboard -Raw -ErrorAction Stop
        }
        catch {
            $content = $null
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($content)) {
        $parsed = Parse-AwsCredentials $content
        if ($null -ne $parsed) {
            return $parsed
        }
    }

    @{
        AWS_ACCESS_KEY_ID     = Read-Host 'AWS_ACCESS_KEY_ID'
        AWS_SECRET_ACCESS_KEY = Read-SecretText 'AWS_SECRET_ACCESS_KEY'
        AWS_SESSION_TOKEN     = Read-SecretText 'AWS_SESSION_TOKEN'
    }
}

function Set-LocalAwsCredentials {
    param([Parameter(Mandatory)][hashtable]$Credentials)

    $env:AWS_ACCESS_KEY_ID = $Credentials.AWS_ACCESS_KEY_ID
    $env:AWS_SECRET_ACCESS_KEY = $Credentials.AWS_SECRET_ACCESS_KEY
    $env:AWS_SESSION_TOKEN = $Credentials.AWS_SESSION_TOKEN
    $env:AWS_DEFAULT_REGION = 'us-west-2'

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        if (-not $ValidateOnly) {
            $configureCommands = @(
                @('configure', 'set', 'aws_access_key_id', $Credentials.AWS_ACCESS_KEY_ID),
                @('configure', 'set', 'aws_secret_access_key', $Credentials.AWS_SECRET_ACCESS_KEY),
                @('configure', 'set', 'aws_session_token', $Credentials.AWS_SESSION_TOKEN),
                @('configure', 'set', 'region', 'us-west-2')
            )
            foreach ($command in $configureCommands) {
                & aws @command 2>&1 | Out-Null
                if ($LASTEXITCODE -ne 0) {
                    throw "Falha ao executar: aws $($command[0..2] -join ' ')"
                }
            }
        }

        $identityOutput = @(& aws sts get-caller-identity --output json 2>&1)
        $identityExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($identityExitCode -ne 0) {
        throw "As credenciais AWS não foram aceitas: $($identityOutput -join ' ')"
    }

    $identity = ($identityOutput -join [Environment]::NewLine) | ConvertFrom-Json
    Write-Host "AWS validada: conta $($identity.Account), sessão LabRole ativa."
}

function Invoke-Gh {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & gh @Arguments
    if ($LASTEXITCODE -ne 0) {
        # Nunca exibe o valor de secrets na mensagem de erro.
        $displayed = @($Arguments)
        $bodyIndex = [Array]::IndexOf($displayed, '--body')
        if ($displayed[0] -eq 'secret' -and $bodyIndex -ge 0 -and $bodyIndex + 1 -lt $displayed.Count) {
            $displayed[$bodyIndex + 1] = '***'
        }
        throw "Falha no GitHub CLI: gh $($displayed -join ' ')"
    }
}

function Initialize-GitHubEnvironment {
    param(
        [Parameter(Mandatory)][string]$Repository,
        [Parameter(Mandatory)][string]$Name
    )

    Invoke-Gh @('api', '--method', 'PUT', "repos/$GitHubOwner/$Repository/environments/$Name", '--silent')
}

function Set-GitHubVariable {
    param(
        [Parameter(Mandatory)][string]$Repository,
        [Parameter(Mandatory)][string]$EnvironmentName,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value
    )

    Invoke-Gh @('variable', 'set', $Name, '--body', $Value, '--repo', "$GitHubOwner/$Repository", '--env', $EnvironmentName)
}

function Get-GitHubVariable {
    param(
        [Parameter(Mandatory)][string]$Repository,
        [Parameter(Mandatory)][string]$EnvironmentName,
        [Parameter(Mandatory)][string]$Name
    )

    $output = @(& gh variable get $Name --repo "$GitHubOwner/$Repository" --env $EnvironmentName 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Falha ao confirmar a variável $Name no GitHub Environment $EnvironmentName."
    }

    ($output -join [Environment]::NewLine).Trim()
}

function Set-GitHubSecret {
    param(
        [Parameter(Mandatory)][string]$Repository,
        [Parameter(Mandatory)][string]$EnvironmentName,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value
    )

    # --body preserva o valor exato: enviar por pipe acrescenta a quebra de linha do
    # PowerShell ao segredo e o consumidor recebe um valor diferente do armazenado.
    Invoke-Gh @('secret', 'set', $Name, '--body', $Value, '--repo', "$GitHubOwner/$Repository", '--env', $EnvironmentName)
}

function Set-GitHubRepositorySecret {
    param(
        [Parameter(Mandatory)][string]$Repository,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value
    )

    Invoke-Gh @('secret', 'set', $Name, '--body', $Value, '--repo', "$GitHubOwner/$Repository")
}

function Set-GitHubRepositoryVariable {
    param(
        [Parameter(Mandatory)][string]$Repository,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value
    )

    Invoke-Gh @('variable', 'set', $Name, '--body', $Value, '--repo', "$GitHubOwner/$Repository")
}

function Set-DatabaseWorkspaceVariables {
    param([Parameter(Mandatory)][string]$EnvironmentName)

    $repository = $RepositoryNames.Database
    foreach ($entry in @{
        OS                  = 'Database'
        ORCAMENTO_PAGAMENTO = 'DatabaseOrcamentoPagamento'
        DIAGNOSTICO_REPARO  = 'DatabaseDiagnosticoReparo'
    }.GetEnumerator()) {
        foreach ($targetEnvironment in @('homolog', 'production')) {
            $suffix = $targetEnvironment.ToUpperInvariant()
            Set-GitHubVariable -Repository $repository -EnvironmentName $EnvironmentName -Name "TF_WORKSPACE_$($entry.Key)_$suffix" -Value $WorkspaceNames[$entry.Value][$targetEnvironment]
        }
    }
}

function New-BranchRulesetPayload {
    param(
        [Parameter(Mandatory)][string]$Branch,
        [Parameter(Mandatory)][string[]]$RequiredChecks
    )

    $githubActionsIntegrationId = 15368
    [ordered]@{
        name        = "Proteção $Branch"
        target      = 'branch'
        enforcement = 'active'
        conditions  = @{ ref_name = @{ include = @("refs/heads/$Branch"); exclude = @() } }
        rules       = @(
            @{ type = 'deletion' },
            @{ type = 'non_fast_forward' },
            @{
                type       = 'pull_request'
                parameters = @{
                    required_approving_review_count   = 0
                    dismiss_stale_reviews_on_push     = ($Branch -eq 'main')
                    require_code_owner_review         = $false
                    require_last_push_approval        = $false
                    required_review_thread_resolution = $true
                }
            },
            @{
                type       = 'required_status_checks'
                parameters = @{
                    strict_required_status_checks_policy = $true
                    required_status_checks               = @($RequiredChecks | ForEach-Object {
                        @{ context = $_; integration_id = $githubActionsIntegrationId }
                    })
                }
            }
        )
    }
}

function Set-GitHubBranchRuleset {
    param(
        [Parameter(Mandatory)][string]$Repository,
        [Parameter(Mandatory)][string]$Branch,
        [Parameter(Mandatory)][string[]]$RequiredChecks
    )

    $payload = New-BranchRulesetPayload -Branch $Branch -RequiredChecks $RequiredChecks
    # Compara só a parte ASCII do nome ("Proteção" -> "Prote??o"): o Windows PowerShell 5.1 decodifica a saída
    # do gh com a página de código da console, e um nome acentuado não bateria (o POST repetido dá HTTP 422).
    $asciiFilter = '.[] | [(.id | tostring), (.name | explode | map(if . > 127 then 63 else . end) | implode)] | @tsv'
    $rulesetsOutput = @(& gh api "repos/$GitHubOwner/$Repository/rulesets" --jq $asciiFilter 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Falha ao consultar as regras de proteção de $Repository."
    }
    $asciiName = $payload.name -replace '[^\x00-\x7F]', '?'
    $existing = @($rulesetsOutput | ForEach-Object {
            $id, $name = ([string]$_) -split "`t", 2
            if ($name -eq $asciiName) { [pscustomobject]@{ id = $id } }
        }) | Select-Object -First 1

    $file = [IO.Path]::GetTempFileName()
    try {
        [IO.File]::WriteAllText($file, ($payload | ConvertTo-Json -Depth 10), (New-Object Text.UTF8Encoding $false))
        if ($null -eq $existing) {
            Invoke-Gh @('api', '--method', 'POST', "repos/$GitHubOwner/$Repository/rulesets", '--input', $file, '--silent')
        }
        else {
            Invoke-Gh @('api', '--method', 'PUT', "repos/$GitHubOwner/$Repository/rulesets/$($existing.id)", '--input', $file, '--silent')
        }
    }
    finally {
        Remove-Item $file -Force -ErrorAction SilentlyContinue
    }
}

function Get-HcpTerraformOutputs {
    param([Parameter(Mandatory)][object]$Workspace)

    $workspaceName = [string]$Workspace.attributes.name
    if ([string]::IsNullOrWhiteSpace($workspaceName)) {
        $workspaceName = [string]$Workspace.id
    }

    try {
        $outputItems = @((Invoke-HcpApi -Method GET -Path "workspaces/$($Workspace.id)/current-state-version-outputs" -Context "consultar outputs atuais de $workspaceName").data)
    }
    catch {
        if ($_.Exception.Data['HcpStatusCode'] -eq 404) {
            Write-Warning "Workspace $workspaceName ainda não possui outputs no state atual. Reexecute este mesmo script após o primeiro apply."
            return $null
        }

        $message = "Não foi possível ler os outputs do workspace $workspaceName."
        $script:ConfigurationIssues.Add($message)
        Write-Warning "$message Não execute plan/apply antes de corrigir essa leitura."
        return $null
    }

    $outputs = [ordered]@{}
    foreach ($outputItem in $outputItems) {
        $name = [string]$outputItem.attributes.name
        if ([string]::IsNullOrWhiteSpace($name)) {
            continue
        }

        $outputs[$name] = [pscustomobject]@{
            sensitive = [bool]$outputItem.attributes.sensitive
            value     = $outputItem.attributes.value
        }
    }

    [pscustomobject]$outputs
}

function ConvertTo-HclList {
    param([Parameter(Mandatory)][object[]]$Values)

    ConvertTo-Json -InputObject @($Values) -Compress
}

function Get-TerraformOutput {
    param(
        [object]$Outputs,
        [Parameter(Mandatory)][string]$Name
    )

    if ($null -eq $Outputs) {
        return $null
    }
    $property = $Outputs.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        return $null
    }
    if ($null -eq $property.Value.PSObject.Properties['value']) {
        return $null
    }
    $property.Value
}

function Get-KubernetesBackendUrl {
    param(
        [Parameter(Mandatory)][string]$EnvironmentName,
        [Parameter(Mandatory)][string]$ClusterName
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $awsExitCode = 1
    $kubectlExitCode = 1
    $hostname = $null
    try {
        $ErrorActionPreference = 'Continue'
        & aws eks update-kubeconfig --region us-west-2 --name $ClusterName 2>$null | Out-Null
        $awsExitCode = $LASTEXITCODE
        if ($awsExitCode -eq 0) {
            $namespace = "oficina-$EnvironmentName"
            $hostname = & kubectl get service oficina-app -n $namespace -o 'jsonpath={.status.loadBalancer.ingress[0].hostname}' 2>$null
            $kubectlExitCode = $LASTEXITCODE
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($awsExitCode -ne 0 -or $kubectlExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($hostname)) {
        return $null
    }
    "http://$hostname"
}

function Get-NewRelicLayerVersion {
    param(
        [string]$Region = 'us-west-2',
        [string]$Runtime = 'java21',
        [string]$Architecture = 'arm64',
        [string]$LayerName = 'NewRelicAgentJavaARM64-slim'
    )

    $uri = "https://$Region.layers.newrelic-external.com/get-layers?CompatibleRuntime=$Runtime&CompatibleArchitecture=$Architecture"
    try {
        $response = Invoke-RestMethod -Method GET -Uri $uri -ErrorAction Stop
    }
    catch {
        throw "Não foi possível consultar as camadas públicas do New Relic: $($_.Exception.Message)"
    }

    $layer = @($response.Layers | Where-Object { [string]$_.LayerName -eq $LayerName }) | Select-Object -First 1
    if ($null -eq $layer) {
        throw "A camada $LayerName não foi encontrada para $Runtime/$Architecture em $Region."
    }

    $version = [string]$layer.LatestMatchingVersion.Version
    if ($version -notmatch '^\d+$') {
        throw "A API pública do New Relic retornou uma versão inválida para $LayerName."
    }

    [int]$version
}

function Write-EnvironmentContext {
    param(
        [Parameter(Mandatory)][Collections.IDictionary]$Values,
        [Parameter(Mandatory)][string]$Path
    )

    $lines = foreach ($entry in $Values.GetEnumerator()) {
        if ($entry.Value -is [Array]) {
            $items = @($entry.Value | ForEach-Object { "'$(($_ -replace "'", "''"))'" })
            '$' + $entry.Key + ' = @(' + ($items -join ', ') + ')'
        }
        elseif ($entry.Value -is [ValueType] -and $entry.Value -isnot [string]) {
            '$' + $entry.Key + ' = ' + $entry.Value
        }
        else {
            '$' + $entry.Key + " = '$(($entry.Value -replace "'", "''"))'"
        }
    }

    Set-Content -Path $Path -Value $lines -Encoding UTF8
}

if ([string]::IsNullOrWhiteSpace($RepositoriesRoot)) {
    $backendRoot = Split-Path $PSScriptRoot -Parent
    $RepositoriesRoot = Split-Path $backendRoot -Parent
}
if ([string]::IsNullOrWhiteSpace($SecretsRoot)) {
    $SecretsRoot = if ($env:OS -eq 'Windows_NT') {
        'C:\fiap-secrets'
    }
    else {
        Join-Path $HOME '.oficina-secrets'
    }
}
$script:SecretsDirectory = Join-Path $SecretsRoot "oficina-$Environment"
if (-not $ValidateOnly) {
    New-Item -ItemType Directory -Force $script:SecretsDirectory | Out-Null
}
$contextPath = Join-Path $script:SecretsDirectory 'environment-context.ps1'
$contextValues = [ordered]@{
    environment    = $Environment
    eksClusterName = "oficina-$Environment"
}

Assert-Command terraform
Assert-TerraformPlatform
Assert-Command aws
Assert-Command kubectl
$script:OpenSslCommand = Resolve-OpenSslCommand
if (-not $SkipGitHub) {
    Assert-Command gh
    Invoke-Gh @('auth', 'status')
}

$repositoryPaths = @{}
foreach ($entry in $RepositoryNames.GetEnumerator()) {
    $path = Join-Path $RepositoriesRoot $entry.Value
    if (-not (Test-Path $path)) {
        throw "Repositório não encontrado: $path"
    }
    $repositoryPaths[$entry.Key] = $path
}

$credentials = Get-AwsCredentials
Set-LocalAwsCredentials $credentials

$terraformToken = Get-TerraformToken
$script:HcpHeaders = @{ Authorization = "Bearer $terraformToken" }

$hcpWorkspaces = @{}
$awsWorkspaces = @()
Write-Host "HCP Terraform validado: projeto $TerraformProject ($TerraformProjectId) acessível."
if ($ValidateOnly) {
    $missingWorkspaces = @()
    foreach ($component in $TerraformComponents + @('NewRelic')) {
        foreach ($targetEnvironment in @('homolog', 'production')) {
            $workspaceName = $WorkspaceNames[$component][$targetEnvironment]
            if ($null -eq (Get-HcpWorkspace -Name $workspaceName -AllowMissing)) {
                $missingWorkspaces += $workspaceName
            }
        }
    }
    if ($missingWorkspaces.Count -gt 0) {
        Write-Warning "Workspaces que serão criados na execução efetiva: $($missingWorkspaces -join ', ')"
    }
    Write-Host "Validação concluída para ${Environment}: ferramentas, repositórios, AWS STS, projeto HCP Terraform e GitHub CLI estão acessíveis."
    Write-Host 'Nenhum arquivo de credencial, Variable Set, workspace ou GitHub Environment foi alterado.'
    return
}

Write-Host 'Sincronizando projeto e workspaces no HCP Terraform...'
$hcpProject = $script:HcpProject
foreach ($component in $TerraformComponents + @('NewRelic')) {
    foreach ($targetEnvironment in @('homolog', 'production')) {
        $workspaceName = $WorkspaceNames[$component][$targetEnvironment]
        $workingDirectory = if ($component -eq 'NewRelic') {
            'observability/newrelic'
        }
        elseif ($DatabaseWorkingDirectories.ContainsKey($component)) {
            $DatabaseWorkingDirectories[$component]
        }
        else {
            ''
        }
        $workspace = Initialize-HcpWorkspace -Project $hcpProject -Name $workspaceName -WorkingDirectory $workingDirectory
        $hcpWorkspaces[$workspaceName] = $workspace
        if ($component -ne 'NewRelic') {
            $awsWorkspaces += $workspace
        }
    }
}
Write-Host 'Sincronizando credenciais temporárias no Variable Set compartilhado...'
Set-AwsVariableSet -Project $hcpProject -Credentials $credentials
foreach ($workspace in $awsWorkspaces) {
    foreach ($key in @('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN')) {
        Remove-HcpWorkspaceVariable -Workspace $workspace -Key $key -Category env
    }
}

$redundantVariables = @{
    Kubernetes = @('aws_region', 'cluster_version', 'lab_role_arn')
    Database   = @('aws_region', 'db_name', 'db_username', 'db_engine_version', 'db_instance_class', 'multi_az', 'deletion_protection', 'skip_final_snapshot')
    DatabaseOrcamentoPagamento = @('aws_region', 'db_name', 'db_username', 'db_engine_version', 'db_instance_class', 'multi_az', 'deletion_protection', 'skip_final_snapshot')
    DatabaseDiagnosticoReparo  = @('aws_region')
    Auth       = @('aws_region', 'lab_role_arn', 'jwt_issuer', 'jwt_audience', 'jwt_ttl_seconds')
}
foreach ($component in $TerraformComponents) {
    foreach ($targetEnvironment in @('homolog', 'production')) {
        $workspace = $hcpWorkspaces[$WorkspaceNames[$component][$targetEnvironment]]
        foreach ($key in $redundantVariables[$component]) {
            Remove-HcpWorkspaceVariable -Workspace $workspace -Key $key
        }
        Set-HcpWorkspaceVariable -Workspace $workspace -Key environment -Value $targetEnvironment
        if ($component -in $RdsComponents) {
            $isProduction = $targetEnvironment -eq 'production'
            $hardenedProduction = $isProduction -and -not $UseAwsAcademyDisposableProductionProfile
            $backupRetentionDays = if ($isProduction) { '14' } else { '7' }
            $multiAz = if ($hardenedProduction) { 'true' } else { 'false' }
            $deletionProtection = if ($hardenedProduction) { 'true' } else { 'false' }
            $skipFinalSnapshot = if ($hardenedProduction) { 'false' } else { 'true' }

            Set-HcpWorkspaceVariable -Workspace $workspace -Key backup_retention_days -Value $backupRetentionDays -Hcl $true
            Set-HcpWorkspaceVariable -Workspace $workspace -Key multi_az -Value $multiAz -Hcl $true
            Set-HcpWorkspaceVariable -Workspace $workspace -Key deletion_protection -Value $deletionProtection -Hcl $true
            Set-HcpWorkspaceVariable -Workspace $workspace -Key skip_final_snapshot -Value $skipFinalSnapshot -Hcl $true
            if ($isProduction) {
                Set-HcpWorkspaceVariable -Workspace $workspace -Key final_snapshot_identifier -Value "oficina-$($DatabaseWorkingDirectories[$component])-production-db-final"
            }
        }
    }
}

Write-Host 'Sincronizando secrets permanentes dos workspaces...'
$dbPassword = Get-StoredSecret -Name 'database-password' -Prompt 'Senha atual do RDS' -GenerateWhenEmpty -Validator {
    param($candidate)

    Assert-RdsPassword -Password $candidate
}
$orcamentoPagamentoDbPassword = Get-StoredSecret -Name 'orcamento-pagamento-database-password' -Prompt 'Senha atual do RDS do Orçamento e Pagamento' -GenerateWhenEmpty -Validator {
    param($candidate)

    Assert-RdsPassword -Password $candidate
}
$appJwtSecret = Get-StoredSecret -Name 'backend-jwt-secret' -Prompt 'Secret HMAC administrativo atual do backend' -GenerateWhenEmpty
$adminPassword = Get-StoredSecret -Name 'backend-admin-password' -Prompt 'Senha atual do administrador do backend' -GenerateWhenEmpty
$notificationApiKey = Get-StoredSecret -Name 'notification-api-key' -Prompt 'Chave técnica atual das notificações (Enter vazio para gerar)' -GenerateWhenEmpty
$notificationSourceEmailPath = Join-Path $script:SecretsDirectory 'notification-source-email.txt'
if ($EnableSesDelivery) {
    if ([string]::IsNullOrWhiteSpace($NotificationSourceEmail)) {
        $NotificationSourceEmail = Get-StoredValue -Path $notificationSourceEmailPath -Prompt 'E-mail remetente verificado no Amazon SES'
    }
    Assert-NotificationSourceEmail -Email $NotificationSourceEmail
    Set-Content -Path $notificationSourceEmailPath -Value $NotificationSourceEmail -Encoding UTF8
}
else {
    $NotificationSourceEmail = 'nao-responder@example.invalid'
}

$privateKeyPath = Join-Path $script:SecretsDirectory 'jwt-private.pem'
$publicKeyPath = Join-Path $script:SecretsDirectory 'jwt-public.pem'
if (-not (Test-Path $privateKeyPath) -or -not (Test-Path $publicKeyPath)) {
    & $script:OpenSslCommand genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $privateKeyPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Falha ao gerar a chave privada RSA.'
    }
    & $script:OpenSslCommand rsa -pubout -in $privateKeyPath -out $publicKeyPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Falha ao gerar a chave pública RSA.'
    }
}
$jwtPrivateKey = Get-Content $privateKeyPath -Raw
$jwtPublicKey = Get-Content $publicKeyPath -Raw

$databaseWorkspace = $hcpWorkspaces[$WorkspaceNames.Database[$Environment]]
$authWorkspace = $hcpWorkspaces[$WorkspaceNames.Auth[$Environment]]
$orcamentoPagamentoDatabaseWorkspace = $hcpWorkspaces[$WorkspaceNames.DatabaseOrcamentoPagamento[$Environment]]
$diagnosticoReparoDatabaseWorkspace = $hcpWorkspaces[$WorkspaceNames.DatabaseDiagnosticoReparo[$Environment]]
Set-HcpWorkspaceVariable -Workspace $databaseWorkspace -Key db_password -Value $dbPassword -Sensitive $true
Set-HcpWorkspaceVariable -Workspace $orcamentoPagamentoDatabaseWorkspace -Key db_password -Value $orcamentoPagamentoDbPassword -Sensitive $true
Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key jwt_private_key -Value $jwtPrivateKey -Sensitive $true
Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key jwt_public_key -Value $jwtPublicKey
Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key notification_api_key -Value $notificationApiKey -Sensitive $true
$notificationDeliveryMode = if ($EnableSesDelivery) { 'ses' } else { 'log' }
Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key notification_delivery_mode -Value $notificationDeliveryMode
Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key notification_source_email -Value $NotificationSourceEmail
$createSesIdentityValue = if ($CreateSesIdentity) { 'true' } else { 'false' }
Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key notification_create_ses_identity -Value $createSesIdentityValue -Hcl $true

if (-not $SkipGitHub) {
    foreach ($targetEnvironment in @('homolog', 'production')) {
        $planEnvironment = "$targetEnvironment-plan"
        foreach ($component in @('Kubernetes', 'Database', 'Auth')) {
            $repository = $RepositoryNames[$component]
            Initialize-GitHubEnvironment -Repository $repository -Name $planEnvironment
            if ($component -eq 'Database') {
                Set-DatabaseWorkspaceVariables -EnvironmentName $planEnvironment
            }
            Set-GitHubSecret -Repository $repository -EnvironmentName $planEnvironment -Name TF_API_TOKEN -Value $terraformToken
            Set-GitHubVariable -Repository $repository -EnvironmentName $planEnvironment -Name TF_CLOUD_ORGANIZATION -Value $TerraformOrganization
            Set-GitHubVariable -Repository $repository -EnvironmentName $planEnvironment -Name TF_WORKSPACE_HOMOLOG -Value $WorkspaceNames[$component].homolog
            Set-GitHubVariable -Repository $repository -EnvironmentName $planEnvironment -Name TF_WORKSPACE_PRODUCTION -Value $WorkspaceNames[$component].production
        }

        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $planEnvironment -Name TF_WORKSPACE_OBSERVABILITY_HOMOLOG -Value $WorkspaceNames.NewRelic.homolog
        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $planEnvironment -Name TF_WORKSPACE_OBSERVABILITY_PRODUCTION -Value $WorkspaceNames.NewRelic.production

        foreach ($repository in $RepositoryNames.Values) {
            Initialize-GitHubEnvironment -Repository $repository -Name $targetEnvironment
            Set-GitHubSecret -Repository $repository -EnvironmentName $targetEnvironment -Name AWS_ACCESS_KEY_ID -Value $credentials.AWS_ACCESS_KEY_ID
            Set-GitHubSecret -Repository $repository -EnvironmentName $targetEnvironment -Name AWS_SECRET_ACCESS_KEY -Value $credentials.AWS_SECRET_ACCESS_KEY
            Set-GitHubSecret -Repository $repository -EnvironmentName $targetEnvironment -Name AWS_SESSION_TOKEN -Value $credentials.AWS_SESSION_TOKEN
            Set-GitHubVariable -Repository $repository -EnvironmentName $targetEnvironment -Name AWS_REGION -Value 'us-west-2'
        }

        Set-DatabaseWorkspaceVariables -EnvironmentName $targetEnvironment
        foreach ($component in @('Kubernetes', 'Database', 'Auth')) {
            $repository = $RepositoryNames[$component]
            Set-GitHubSecret -Repository $repository -EnvironmentName $targetEnvironment -Name TF_API_TOKEN -Value $terraformToken
            Set-GitHubVariable -Repository $repository -EnvironmentName $targetEnvironment -Name TF_CLOUD_ORGANIZATION -Value $TerraformOrganization
            Set-GitHubVariable -Repository $repository -EnvironmentName $targetEnvironment -Name TF_WORKSPACE_HOMOLOG -Value $WorkspaceNames[$component].homolog
            Set-GitHubVariable -Repository $repository -EnvironmentName $targetEnvironment -Name TF_WORKSPACE_PRODUCTION -Value $WorkspaceNames[$component].production
        }

        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $targetEnvironment -Name TF_WORKSPACE_OBSERVABILITY_HOMOLOG -Value $WorkspaceNames.NewRelic.homolog
        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $targetEnvironment -Name TF_WORKSPACE_OBSERVABILITY_PRODUCTION -Value $WorkspaceNames.NewRelic.production
        Set-GitHubSecret -Repository $RepositoryNames.Backend -EnvironmentName $targetEnvironment -Name TF_API_TOKEN -Value $terraformToken
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $targetEnvironment -Name TF_CLOUD_ORGANIZATION -Value $TerraformOrganization
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $targetEnvironment -Name TF_WORKSPACE_AUTH -Value $WorkspaceNames.Auth[$targetEnvironment]
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $targetEnvironment -Name TF_WORKSPACE_OBSERVABILITY -Value $WorkspaceNames.NewRelic[$targetEnvironment]
        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $targetEnvironment -Name CLUSTER_NAME -Value "oficina-$targetEnvironment"
        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $targetEnvironment -Name APP_NAMESPACE -Value "oficina-$targetEnvironment"
        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $targetEnvironment -Name SYNTHETIC_MONITOR_ENABLED -Value 'false'
        # Só o ambiente configurado ganha apply: o outro pode estar destruído, e um merge na branch dele recriaria recursos órfãos.
        if ($targetEnvironment -eq $Environment) {
            Set-GitHubVariable -Repository $RepositoryNames.Database -EnvironmentName $targetEnvironment -Name ENABLE_TERRAFORM_APPLY -Value 'true'
            Set-GitHubVariable -Repository $RepositoryNames.Auth -EnvironmentName $targetEnvironment -Name TF_APPLY_ENABLED -Value 'true'
        }

        foreach ($serviceKey in $ServiceRepositoryKeys) {
            Set-GitHubVariable -Repository $RepositoryNames[$serviceKey] -EnvironmentName $targetEnvironment -Name EKS_CLUSTER_NAME -Value "oficina-$targetEnvironment"
        }
        Set-GitHubVariable -Repository $RepositoryNames.OrcamentoPagamento -EnvironmentName $targetEnvironment -Name APP_DB_USER -Value 'oficina_admin'
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $targetEnvironment -Name APP_DB_USER -Value 'oficina_admin'
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $targetEnvironment -Name SERVERLESS_JWT_ISSUER -Value 'fiap-tc-fase4-oficina-auth-serverless'
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $targetEnvironment -Name SERVERLESS_JWT_AUDIENCE -Value 'oficina-backend'
        Set-GitHubVariable -Repository $RepositoryNames.OrcamentoPagamento -EnvironmentName $targetEnvironment -Name SERVERLESS_JWT_ISSUER -Value 'fiap-tc-fase4-oficina-auth-serverless'
        Set-GitHubVariable -Repository $RepositoryNames.OrcamentoPagamento -EnvironmentName $targetEnvironment -Name SERVERLESS_JWT_AUDIENCE -Value 'oficina-backend'
        # O deploy do Orçamento e Pagamento envia a URL do LoadBalancer ao workspace do Auth.
        Set-GitHubSecret -Repository $RepositoryNames.OrcamentoPagamento -EnvironmentName $targetEnvironment -Name TF_API_TOKEN -Value $terraformToken
        Set-GitHubVariable -Repository $RepositoryNames.OrcamentoPagamento -EnvironmentName $targetEnvironment -Name TF_CLOUD_ORGANIZATION -Value $TerraformOrganization
        Set-GitHubVariable -Repository $RepositoryNames.OrcamentoPagamento -EnvironmentName $targetEnvironment -Name TF_WORKSPACE_AUTH -Value $WorkspaceNames.Auth[$targetEnvironment]
    }

    Set-GitHubSecret -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name APP_DB_PASSWORD -Value $dbPassword
    Set-GitHubSecret -Repository $RepositoryNames.OrcamentoPagamento -EnvironmentName $Environment -Name APP_DB_PASSWORD -Value $orcamentoPagamentoDbPassword
    Set-GitHubSecret -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name APP_JWT_SECRET -Value $appJwtSecret
    # Os 3 serviços validam os mesmos tokens: funcionário/técnico (JWT_SECRET) e cliente (chave pública).
    foreach ($serviceKey in @('OrcamentoPagamento', 'DiagnosticoReparo')) {
        Set-GitHubSecret -Repository $RepositoryNames[$serviceKey] -EnvironmentName $Environment -Name APP_JWT_SECRET -Value $appJwtSecret
        Set-GitHubSecret -Repository $RepositoryNames[$serviceKey] -EnvironmentName $Environment -Name SERVERLESS_JWT_PUBLIC_KEY -Value $jwtPublicKey
    }
    Set-GitHubSecret -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name APP_ADMIN_PASSWORD -Value $adminPassword
    Set-GitHubSecret -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name SERVERLESS_JWT_PUBLIC_KEY -Value $jwtPublicKey
    Set-GitHubSecret -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name NOTIFICATION_API_KEY -Value $notificationApiKey
}

$kubernetesReady = $false
$databaseReady = $false
$kubernetesOutputs = Get-HcpTerraformOutputs -Workspace $hcpWorkspaces[$WorkspaceNames.Kubernetes[$Environment]]
$vpcOutput = Get-TerraformOutput -Outputs $kubernetesOutputs -Name vpc_id
$privateSubnetsOutput = Get-TerraformOutput -Outputs $kubernetesOutputs -Name private_subnet_ids
$eksSecurityGroupOutput = Get-TerraformOutput -Outputs $kubernetesOutputs -Name eks_cluster_security_group_id
if ($null -ne $vpcOutput -and $null -ne $privateSubnetsOutput -and $null -ne $eksSecurityGroupOutput) {
    $vpcId = [string]$vpcOutput.value
    $privateSubnetIds = @($privateSubnetsOutput.value)
    $eksSecurityGroupId = [string]$eksSecurityGroupOutput.value
    $contextValues['vpcId'] = $vpcId
    $contextValues['privateSubnetIds'] = $privateSubnetIds
    $contextValues['eksSecurityGroupId'] = $eksSecurityGroupId

    Set-HcpWorkspaceVariable -Workspace $databaseWorkspace -Key vpc_id -Value $vpcId
    Set-HcpWorkspaceVariable -Workspace $databaseWorkspace -Key private_subnet_ids -Value (ConvertTo-HclList -Values $privateSubnetIds) -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $databaseWorkspace -Key allowed_security_group_ids -Value (ConvertTo-HclList -Values @($eksSecurityGroupId)) -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $orcamentoPagamentoDatabaseWorkspace -Key vpc_id -Value $vpcId
    Set-HcpWorkspaceVariable -Workspace $orcamentoPagamentoDatabaseWorkspace -Key private_subnet_ids -Value (ConvertTo-HclList -Values $privateSubnetIds) -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $orcamentoPagamentoDatabaseWorkspace -Key allowed_security_group_ids -Value (ConvertTo-HclList -Values @($eksSecurityGroupId)) -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key private_subnet_ids -Value (ConvertTo-HclList -Values $privateSubnetIds) -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key lambda_security_group_id -Value $eksSecurityGroupId
    $kubernetesReady = $true

    Write-Host 'Outputs do Kubernetes sincronizados automaticamente com banco e autenticação.'
}
else {
    Write-Warning 'Kubernetes ainda não possui todos os outputs. Reexecute este mesmo script após o apply do cluster.'
}

$databaseOutputs = Get-HcpTerraformOutputs -Workspace $hcpWorkspaces[$WorkspaceNames.Database[$Environment]]
$jdbcOutput = Get-TerraformOutput -Outputs $databaseOutputs -Name jdbc_url
if ($null -ne $jdbcOutput) {
    $jdbcUrl = [string]$jdbcOutput.value
    $contextValues['jdbcUrl'] = $jdbcUrl
    $databaseEndpointOutput = Get-TerraformOutput -Outputs $databaseOutputs -Name database_endpoint
    $databasePortOutput = Get-TerraformOutput -Outputs $databaseOutputs -Name database_port
    $databaseNameOutput = Get-TerraformOutput -Outputs $databaseOutputs -Name database_name
    if ($null -ne $databaseEndpointOutput) {
        $contextValues['dbHost'] = [string]$databaseEndpointOutput.value
    }
    if ($null -ne $databasePortOutput) {
        $contextValues['dbPort'] = [int]$databasePortOutput.value
    }
    if ($null -ne $databaseNameOutput) {
        $contextValues['dbName'] = [string]$databaseNameOutput.value
    }

    if (-not $SkipGitHub) {
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name APP_DB_URL -Value $jdbcUrl
    }
    $databaseReady = $true
    Write-Host 'Output do RDS sincronizado automaticamente com o backend.'
}
else {
    Write-Warning 'RDS ainda não possui output. Reexecute este mesmo script após o apply do banco.'
}

$orcamentoPagamentoOutputs = Get-HcpTerraformOutputs -Workspace $orcamentoPagamentoDatabaseWorkspace
$orcamentoPagamentoJdbcOutput = Get-TerraformOutput -Outputs $orcamentoPagamentoOutputs -Name jdbc_url
if ($null -ne $orcamentoPagamentoJdbcOutput -and -not $SkipGitHub) {
    Set-GitHubVariable -Repository $RepositoryNames.OrcamentoPagamento -EnvironmentName $Environment -Name APP_DB_URL -Value ([string]$orcamentoPagamentoJdbcOutput.value)
}
$diagnosticoReparoOutputs = Get-HcpTerraformOutputs -Workspace $diagnosticoReparoDatabaseWorkspace
$dynamoTableOutput = Get-TerraformOutput -Outputs $diagnosticoReparoOutputs -Name table_name
if ($null -ne $dynamoTableOutput -and -not $SkipGitHub) {
    Set-GitHubVariable -Repository $RepositoryNames.DiagnosticoReparo -EnvironmentName $Environment -Name DYNAMODB_TABLE_NAME -Value ([string]$dynamoTableOutput.value)
}
$queueOutputs = [ordered]@{
    SQS_OS_EVENTOS_URL                  = $databaseOutputs
    SQS_ORCAMENTO_PAGAMENTO_COMANDOS_URL = $orcamentoPagamentoOutputs
    SQS_DIAGNOSTICO_REPARO_COMANDOS_URL  = $diagnosticoReparoOutputs
}
foreach ($queue in $queueOutputs.GetEnumerator()) {
    $queueUrlOutput = Get-TerraformOutput -Outputs $queue.Value -Name queue_url
    if ($null -eq $queueUrlOutput) {
        Write-Warning "$($queue.Key) ainda não possui output queue_url. Reexecute este mesmo script após o apply do banco."
        continue
    }
    if (-not $SkipGitHub) {
        foreach ($serviceKey in $ServiceRepositoryKeys) {
            Set-GitHubVariable -Repository $RepositoryNames[$serviceKey] -EnvironmentName $Environment -Name $queue.Key -Value ([string]$queueUrlOutput.value)
        }
    }
}

$backendDeployReady = $kubernetesReady -and $databaseReady
if (-not $SkipGitHub) {
    $deployEnabledValue = if ($backendDeployReady) { 'true' } else { 'false' }
    Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name DEPLOY_ENABLED -Value $deployEnabledValue
    $persistedDeployEnabled = Get-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name DEPLOY_ENABLED
    if ($persistedDeployEnabled -ne $deployEnabledValue) {
        throw "GitHub Environment não confirmou DEPLOY_ENABLED=$deployEnabledValue; valor lido: $persistedDeployEnabled"
    }

    Write-Host "Gate do Backend confirmado: DEPLOY_ENABLED=$persistedDeployEnabled."

    $serviceDeployReady = @{
        OrcamentoPagamento = $kubernetesReady -and ($null -ne $orcamentoPagamentoJdbcOutput)
        DiagnosticoReparo  = $kubernetesReady -and ($null -ne $dynamoTableOutput)
    }
    foreach ($entry in $serviceDeployReady.GetEnumerator()) {
        $value = if ($entry.Value) { 'true' } else { 'false' }
        Set-GitHubVariable -Repository $RepositoryNames[$entry.Key] -EnvironmentName $Environment -Name DEPLOY_ENABLED -Value $value
        Write-Host "Gate do $($RepositoryNames[$entry.Key]): DEPLOY_ENABLED=$value."
    }
}
if ($backendDeployReady) {
    Write-Host 'Deploy do backend habilitado: outputs de EKS e RDS estão disponíveis.'
}
elseif ($RequireBackendDeployReady) {
    $missingBackendDependencies = @()
    if (-not $kubernetesReady) { $missingBackendDependencies += 'outputs do Kubernetes' }
    if (-not $databaseReady) { $missingBackendDependencies += 'output JDBC do RDS' }
    $script:ConfigurationIssues.Add("Deploy do Backend bloqueado: faltam $($missingBackendDependencies -join ' e ').")
}

$backendUrl = if ($kubernetesReady) {
    Get-KubernetesBackendUrl -EnvironmentName $Environment -ClusterName $contextValues.eksClusterName
}
else {
    $null
}
if (-not [string]::IsNullOrWhiteSpace($backendUrl)) {
    $contextValues['backendUrl'] = $backendUrl
    Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key backend_base_url -Value $backendUrl
    if (-not $SkipGitHub) {
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name BACKEND_BASE_URL -Value $backendUrl
        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $Environment -Name HEALTH_CHECK_URL -Value "$backendUrl/actuator/health"
        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $Environment -Name SYNTHETIC_MONITOR_ENABLED -Value 'true'
    }
    $newRelicAccountPath = Join-Path (Join-Path $SecretsRoot 'shared') 'newrelic-account-id.txt'
    if (Test-Path $newRelicAccountPath) {
        $existingNewRelicWorkspace = Get-HcpWorkspace $WorkspaceNames.NewRelic[$Environment]
        Set-HcpWorkspaceVariable -Workspace $existingNewRelicWorkspace -Key health_check_url -Value "$backendUrl/actuator/health"
        Set-HcpWorkspaceVariable -Workspace $existingNewRelicWorkspace -Key synthetic_monitor_enabled -Value 'true' -Hcl $true
    }
    Write-Host 'URL do LoadBalancer sincronizada automaticamente.'
}
else {
    Write-Warning 'LoadBalancer do backend ainda não está disponível. O workflow do Backend fará a sincronização após o deploy.'
}

$authOutputs = Get-HcpTerraformOutputs -Workspace $hcpWorkspaces[$WorkspaceNames.Auth[$Environment]]
$apiBaseOutput = Get-TerraformOutput -Outputs $authOutputs -Name api_base_url
if ($null -ne $apiBaseOutput) {
    $apiBaseUrl = [string]$apiBaseOutput.value
    $contextValues['apiBase'] = $apiBaseUrl
    $cpfAuthenticationOutput = Get-TerraformOutput -Outputs $authOutputs -Name cpf_authentication_url
    if ($null -ne $cpfAuthenticationOutput) {
        $contextValues['authUrl'] = [string]$cpfAuthenticationOutput.value
    }
    if (-not $SkipGitHub) {
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name API_GATEWAY_BASE_URL -Value $apiBaseUrl
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name AUTH_BASE_URL -Value $apiBaseUrl
        Set-GitHubVariable -Repository $RepositoryNames.OrcamentoPagamento -EnvironmentName $Environment -Name API_GATEWAY_BASE_URL -Value $apiBaseUrl
    }
    $notificationEndpointOutput = Get-TerraformOutput -Outputs $authOutputs -Name notification_endpoint
    if ($null -ne $notificationEndpointOutput -and -not $SkipGitHub) {
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name NOTIFICATION_ENDPOINT -Value ([string]$notificationEndpointOutput.value)
    }
    Write-Host 'URLs do API Gateway sincronizadas automaticamente com o backend.'
}

if ($DisableObservability) {
    $newRelicWorkspace = $hcpWorkspaces[$WorkspaceNames.NewRelic[$Environment]]
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key observability_enabled -Value 'false' -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key synthetic_monitor_enabled -Value 'false' -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key notification_enabled -Value 'false' -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key newrelic_instrumentation_enabled -Value 'false' -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key newrelic_log_forwarding_enabled -Value 'false' -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $databaseWorkspace -Key rds_newrelic_telemetry_enabled -Value 'false' -Hcl $true
    if (-not $SkipGitHub) {
        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $Environment -Name SYNTHETIC_MONITOR_ENABLED -Value 'false'
        foreach ($serviceKey in $ServiceRepositoryKeys) {
            Set-GitHubVariable -Repository $RepositoryNames[$serviceKey] -EnvironmentName $Environment -Name NEW_RELIC_ENABLED -Value 'false'
        }
    }
}

if ($ConfigureNewRelic) {
    $sharedSecretsDirectory = Join-Path $SecretsRoot 'shared'
    $accountId = Get-StoredValue -Path (Join-Path $sharedSecretsDirectory 'newrelic-account-id.txt') -Prompt 'New Relic Account ID'
    if ($accountId -notmatch '^\d+$') {
        throw 'New Relic Account ID deve ser numérico.'
    }
    $apiKey = Get-StoredSecret -Name 'newrelic-api-key' -Prompt 'New Relic User API key' -Directory $sharedSecretsDirectory
    $licenseKey = Get-StoredSecret -Name 'newrelic-license-key' -Prompt 'New Relic License key' -Directory $sharedSecretsDirectory
    $newRelicWorkspace = Get-HcpWorkspace $WorkspaceNames.NewRelic[$Environment]
    $newRelicLayerVersion = Get-NewRelicLayerVersion

    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key environment -Value $Environment
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key newrelic_account_id -Value $accountId -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key newrelic_api_key -Value $apiKey -Sensitive $true
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key observability_enabled -Value 'true' -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key cluster_name -Value "oficina-$Environment"
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key apm_application_name -Value "fiap-tc-fase4-oficina-os-service-$Environment"
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key kubernetes_namespace -Value "oficina-$Environment"
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key api_gateway_name -Value "oficina-auth-$Environment-http-api"
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key lambda_function_names -Value (ConvertTo-HclList -Values @("oficina-auth-$Environment-login", "oficina-auth-$Environment-authorizer", "oficina-auth-$Environment-notification-ingress", "oficina-auth-$Environment-notification-delivery")) -Hcl $true
    $syntheticMonitorEnabled = if ([string]::IsNullOrWhiteSpace($backendUrl)) { 'false' } else { 'true' }
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key synthetic_monitor_enabled -Value $syntheticMonitorEnabled -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key notification_enabled -Value 'false' -Hcl $true
    if (-not [string]::IsNullOrWhiteSpace($backendUrl)) {
        Set-HcpWorkspaceVariable -Workspace $newRelicWorkspace -Key health_check_url -Value "$backendUrl/actuator/health"
    }

    Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key newrelic_account_id -Value $accountId
    Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key newrelic_license_key -Value $licenseKey -Sensitive $true
    Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key newrelic_layer_version -Value $newRelicLayerVersion -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key newrelic_instrumentation_enabled -Value 'true' -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $authWorkspace -Key newrelic_log_forwarding_enabled -Value 'true' -Hcl $true

    Set-HcpWorkspaceVariable -Workspace $databaseWorkspace -Key newrelic_account_id -Value $accountId -Hcl $true
    Set-HcpWorkspaceVariable -Workspace $databaseWorkspace -Key newrelic_license_key -Value $licenseKey -Sensitive $true
    Set-HcpWorkspaceVariable -Workspace $databaseWorkspace -Key newrelic_region -Value 'US'
    Set-HcpWorkspaceVariable -Workspace $databaseWorkspace -Key rds_newrelic_telemetry_enabled -Value 'true' -Hcl $true

    if (-not $SkipGitHub) {
        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $Environment -Name NEW_RELIC_ACCOUNT_ID -Value $accountId
        Set-GitHubSecret -Repository $RepositoryNames.Kubernetes -EnvironmentName $Environment -Name NEW_RELIC_API_KEY -Value $apiKey
        Set-GitHubSecret -Repository $RepositoryNames.Kubernetes -EnvironmentName $Environment -Name NEW_RELIC_LICENSE_KEY -Value $licenseKey
        Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $Environment -Name SYNTHETIC_MONITOR_ENABLED -Value $syntheticMonitorEnabled
        if (-not [string]::IsNullOrWhiteSpace($backendUrl)) {
            Set-GitHubVariable -Repository $RepositoryNames.Kubernetes -EnvironmentName $Environment -Name HEALTH_CHECK_URL -Value "$backendUrl/actuator/health"
        }
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name NEW_RELIC_ENABLED -Value 'true'
        Set-GitHubVariable -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name NEW_RELIC_APP_NAME -Value "fiap-tc-fase4-oficina-os-service-$Environment"
        Set-GitHubSecret -Repository $RepositoryNames.Backend -EnvironmentName $Environment -Name NEW_RELIC_LICENSE_KEY -Value $licenseKey
        foreach ($serviceKey in @('OrcamentoPagamento', 'DiagnosticoReparo')) {
            Set-GitHubVariable -Repository $RepositoryNames[$serviceKey] -EnvironmentName $Environment -Name NEW_RELIC_ENABLED -Value 'true'
            Set-GitHubSecret -Repository $RepositoryNames[$serviceKey] -EnvironmentName $Environment -Name NEW_RELIC_LICENSE_KEY -Value $licenseKey
        }
    }
}

# Os deploys do OS, do Auth e do OS-database gravam outputs nos GitHub Environments de outros repos.
# Fase 4 usa um token fine-grained (Environments: Read and write nos 6 repos).
if (-not $SkipGitHub) {
    $githubSyncToken = Get-StoredSecret -Name 'github-sync-token' -Prompt 'GitHub fine-grained token de sincronização (Environments: Read and write nos 6 repos da Fase 4)' -Directory (Join-Path $SecretsRoot 'shared')
    foreach ($syncSourceKey in @('Backend', 'Auth', 'Database')) {
        Set-GitHubRepositorySecret -Repository $RepositoryNames[$syncSourceKey] -Name REPO_SYNC_TOKEN -Value $githubSyncToken
    }
    Write-Host 'REPO_SYNC_TOKEN configurado no OS Service, no Auth e no Database.'
}

if ($ConfigureSonarCloud -and -not $SkipGitHub) {
    $sonarToken = Get-StoredSecret -Name 'sonarcloud-token' -Prompt 'SonarCloud token' -Directory (Join-Path $SecretsRoot 'shared')
    foreach ($serviceKey in $ServiceRepositoryKeys) {
        Set-GitHubRepositorySecret -Repository $RepositoryNames[$serviceKey] -Name SONAR_TOKEN -Value $sonarToken
        Set-GitHubRepositoryVariable -Repository $RepositoryNames[$serviceKey] -Name SONAR_ORGANIZATION -Value $SonarOrganization
    }
    Write-Host 'SonarCloud configurado nos 3 serviços.'
}

if ($ConfigureMercadoPago -and -not $SkipGitHub) {
    $mercadoPagoDirectory = Join-Path $SecretsRoot 'shared'
    $mercadoPagoAccessToken = Get-StoredSecret -Name 'mercadopago-access-token' -Prompt 'Mercado Pago Access Token (sandbox)' -Directory $mercadoPagoDirectory
    $mercadoPagoPublicKey = Get-StoredSecret -Name 'mercadopago-public-key' -Prompt 'Mercado Pago Public Key (sandbox)' -Directory $mercadoPagoDirectory
    $mercadoPagoWebhookSecret = Get-StoredSecret -Name 'mercadopago-webhook-secret' -Prompt 'Mercado Pago assinatura secreta do webhook' -Directory $mercadoPagoDirectory
    $paymentRepository = $RepositoryNames.OrcamentoPagamento
    Set-GitHubSecret -Repository $paymentRepository -EnvironmentName $Environment -Name MERCADOPAGO_ACCESS_TOKEN -Value $mercadoPagoAccessToken
    Set-GitHubSecret -Repository $paymentRepository -EnvironmentName $Environment -Name MERCADOPAGO_WEBHOOK_SECRET -Value $mercadoPagoWebhookSecret
    Set-GitHubVariable -Repository $paymentRepository -EnvironmentName $Environment -Name MERCADOPAGO_PUBLIC_KEY -Value $mercadoPagoPublicKey
    Set-GitHubVariable -Repository $paymentRepository -EnvironmentName $Environment -Name PAYMENT_PROVIDER -Value 'mercadopago'
    Write-Host "Mercado Pago configurado no Orçamento e Pagamento ($Environment)."
}

if (-not $SkipGitHub -and -not $SkipBranchProtection) {
    foreach ($entry in $RepositoryNames.GetEnumerator()) {
        foreach ($branch in @('main', 'homolog')) {
            Set-GitHubBranchRuleset -Repository $entry.Value -Branch $branch -RequiredChecks $RequiredStatusChecks[$entry.Key]
        }
    }
    Write-Host 'Regras de proteção da main e da homolog sincronizadas nos 6 repositórios.'
}

Write-EnvironmentContext -Values $contextValues -Path $contextPath

Write-Host ''
if ($script:ConfigurationIssues.Count -gt 0) {
    Write-Warning "A configuração terminou com $($script:ConfigurationIssues.Count) pendência(s) técnica(s):"
    foreach ($issue in $script:ConfigurationIssues) {
        Write-Warning "- $issue"
    }
    throw 'Configuração incompleta. Corrija as pendências acima e reexecute este mesmo comando antes de qualquer plan/apply.'
}

Write-Host "Configuração automática concluída para $Environment."
Write-Host 'Credenciais AWS: computador local, HCP Terraform e GitHub Environments atualizados.'
Write-Host "Contexto não sensível salvo em $contextPath"
Write-Host 'Os workflows de cada projeto sincronizam automaticamente os outputs gerados nos próximos deploys.'
