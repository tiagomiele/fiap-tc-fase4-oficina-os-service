# Bootstrap da Oficina Fase 4 na AWS, do zero

Este roteiro sobe a Fase 4 inteira num AWS Academy Learner Lab vazio, desde as ferramentas e credenciais até a validação da Saga. A base é o `docs-fase3/bootstrap-aws-do-zero.md` da Fase 3, adaptado aos 6 repositórios, aos 3 serviços e ao projeto HCP exclusivo da Fase 4.

Todos os comandos são para **Windows PowerShell 5.1**. Rode os blocos na ordem.

> **Atalho:** as seções 7.2 a 15 rodam de uma vez com `scripts/provision-environment.ps1`. Veja o [Bootstrap de uma única vez](bootstrap-automatizado-fase4.md).

## 1. Objetivo e ordem obrigatória

```text
Preparação permanente (uma vez)
→ sessão AWS Academy + configure-environment.ps1
→ Kubernetes/EKS + add-ons + New Relic inicial
→ Bancos e filas: OS (RDS+SQS) → Orçamento e Pagamento (RDS+SQS) → Diagnóstico e Reparo (DynamoDB+SQS)
→ configure-environment.ps1 (leva JDBC, tabela, filas e DEPLOY_ENABLED aos 3 serviços)
→ teste da LabRole (pod → SQS/DynamoDB)
→ Auth/API Gateway (1º apply)
→ configure-environment.ps1 (leva a URL do API Gateway ao Orçamento e Pagamento)
→ CD dos 3 serviços
→ Auth (2º apply: rotas do Orçamento e Pagamento e webhook)
→ Kubernetes (2º run: Synthetic com o health final do OS)
→ validação manual da Saga e E2E
```

Diferenças em relação à Fase 3:

| Fase 3 | Fase 4 |
|---|---|
| 4 repos, 1 backend | 6 repos, 3 serviços (OS, Orçamento e Pagamento, Diagnóstico e Reparo) |
| HCP project `soat-fase3`, `terraform login` | projeto HCP exclusivo `prj-UTzD6p6PqRVEr27C`, Team token próprio |
| GitHub App `SYNC_APP_CLIENT_ID`/`SYNC_APP_PRIVATE_KEY` | token fine-grained `REPO_SYNC_TOKEN` (o script cadastra) |
| 1 RDS | 2 RDS + 1 DynamoDB + 3 filas SQS com DLQ |
| script só no bootstrap | script também depois dos bancos e do 1º Auth, porque nem todo output é sincronizado pelos workflows (seção 25) |

> **Custos:** EKS, 2 RDS, NAT Gateway, 3 LoadBalancers e telemetria consomem o orçamento do Learner Lab. Destrua o ambiente pelos workspaces HCP quando terminar; nunca apague recursos soltos pelo console.

## 2. Regras de segurança

1. Toda a homologação acontece na branch `homolog` e no Environment `homolog`.
2. Rode um workflow por vez e espere terminar verde antes do próximo.
3. Não selecione `main`/`production` durante a homologação.
4. Não envie no chat, prints ou documentos: credenciais AWS, tokens HCP/GitHub/SonarCloud/New Relic/Mercado Pago, senhas, JWTs, chaves RSA ou CPFs reais.
5. Não versione `C:\fiap-secrets`, `.env`, `.pem` nem states Terraform.
6. Não ative Auto Apply nos workspaces HCP.
7. Se um plan mostrar destruição inesperada, cancele o run e pare.
8. Se a sessão AWS expirar, renove pelo script (seção 21) e repita só o workflow que falhou.
9. Não exclua recursos manualmente para corrigir divergências de Terraform.
10. Os workflows de deploy também disparam em `push` para `homolog`/`main`. Faça merges só quando o ambiente estiver pronto para receber o deploy.

## 3. Repositórios e recursos

| Projeto | Repositório | Workflow de deploy |
|---|---|---|
| Kubernetes/EKS/New Relic | `tiagomiele/fiap-tc-fase4-oficina-kubernetes-infra` | `deploy-homolog.yml` / `deploy-production.yml` |
| Bancos e filas | `tiagomiele/fiap-tc-fase4-oficina-database-infra` | `os.yml`, `orcamento-pagamento.yml`, `diagnostico-reparo.yml` |
| Auth/API Gateway/Lambdas | `tiagomiele/fiap-tc-fase4-oficina-auth-serverless` | `terraform-deploy.yml` |
| OS Service (orquestrador da Saga) | `tiagomiele/fiap-tc-fase4-oficina-os-service` | `cd.yml` (+ `e2e.yml`, `bdd.yml`) |
| Orçamento e Pagamento | `tiagomiele/fiap-tc-fase4-oficina-orcamento-pagamento-service` | `cd.yml` |
| Diagnóstico e Reparo | `tiagomiele/fiap-tc-fase4-oficina-diagnostico-reparo-service` | `cd.yml` |

```text
Região AWS:        us-west-2
HCP organização:   oficina-fiap-soat-fase-2
HCP projeto:       prj-UTzD6p6PqRVEr27C  (https://app.terraform.io/app/oficina-fiap-soat-fase-2/projects/prj-UTzD6p6PqRVEr27C)
Cluster homolog:   oficina-homolog
Namespaces:        oficina-homolog | oficina-orcamento-pagamento-homolog | oficina-diagnostico-reparo-homolog
Services k8s:      oficina-app | oficina-orcamento-pagamento | oficina-diagnostico-reparo
Filas (+ DLQ):     os-eventos | orcamento-pagamento-comandos | diagnostico-reparo-comandos (maxReceiveCount 5)
DynamoDB:          oficina-homolog-execucoes (GSI etapa-entradaFilaEm-index)
```

Workspaces HCP (o script cria todos dentro do projeto da Fase 4):

```text
oficina-fase4-kubernetes-homolog              oficina-fase4-kubernetes-production
oficina-fase4-db-os-homolog                   oficina-fase4-db-os-production
oficina-fase4-db-orcamento-pagamento-homolog  oficina-fase4-db-orcamento-pagamento-production
oficina-fase4-db-diagnostico-reparo-homolog   oficina-fase4-db-diagnostico-reparo-production
oficina-fase4-auth-homolog                    oficina-fase4-auth-production
oficina-fase4-newrelic-homolog                oficina-fase4-newrelic-production
```

Variable Set do projeto: `aws-academy-credentials-fase4`.

## 4. Preparação permanente (uma vez por computador/conta)

### 4.1 Ferramentas no Windows

```powershell
winget install --id Git.Git -e
winget install --id GitHub.cli -e
winget install --id Hashicorp.Terraform -e --architecture x64
winget install --id Amazon.AWSCLI -e
winget install --id Kubernetes.kubectl -e
winget install --id Helm.Helm -e
winget install --id EclipseAdoptium.Temurin.21.JDK -e
winget install --id Docker.DockerDesktop -e
```

Feche e abra o PowerShell e confira:

```powershell
git --version; gh --version; terraform version; aws --version
kubectl version --client; helm version; java -version; docker --version

$TerraformInfo = terraform version -json | ConvertFrom-Json
if ($TerraformInfo.platform -ne 'windows_amd64') { throw "Terraform incompatível: $($TerraformInfo.platform)" }

$OpenSsl = (Get-Command openssl -ErrorAction SilentlyContinue).Source
if (-not $OpenSsl) { $OpenSsl = Join-Path $env:ProgramFiles 'Git\usr\bin\openssl.exe' }
if (-not (Test-Path $OpenSsl)) { throw 'OpenSSL não encontrado. Reinstale o Git for Windows.' }
& $OpenSsl version
```

Requisitos: Terraform ≥ 1.6 e < 2.0, kubectl compatível com EKS 1.34. Os runners do GitHub instalam Java, Maven, Docker, Helm e Node sozinhos; as ferramentas locais servem para o script, o diagnóstico e a validação manual.

### 4.2 GitHub

```powershell
gh auth login --hostname github.com --git-protocol https --web
gh auth status --hostname github.com
```

A conta precisa ser admin dos 6 repos; o token do `gh` precisa dos escopos `repo`, `workflow` e `read:org`.

**Não é preciso `terraform login`.** O token da Fase 3 (gravado pelo `terraform login`) foi a causa do HTTP 401. O script da Fase 4 ignora `TFC_TOKEN` e `credentials.tfrc.json`.

### 4.3 Clonar os 6 repositórios lado a lado

O script acha os outros repos pela pasta pai do `fiap-tc-fase4-oficina-os-service`.

```powershell
$Root = 'C:\fiap\fase4'
New-Item -ItemType Directory -Force $Root | Out-Null
$Repositories = 'fiap-tc-fase4-oficina-kubernetes-infra','fiap-tc-fase4-oficina-database-infra','fiap-tc-fase4-oficina-auth-serverless',
                'fiap-tc-fase4-oficina-os-service','fiap-tc-fase4-oficina-orcamento-pagamento-service','fiap-tc-fase4-oficina-diagnostico-reparo-service'

foreach ($Name in $Repositories) {
  $Path = Join-Path $Root $Name
  if (-not (Test-Path (Join-Path $Path '.git'))) {
    gh repo clone "tiagomiele/$Name" $Path
    if ($LASTEXITCODE -ne 0) { throw "Falha ao clonar $Name" }
  }
  $Pendentes = @(git -C $Path status --porcelain)
  if ($Pendentes.Count -gt 0) {
    Write-Host "$Name tem alterações locais:" -ForegroundColor Yellow
    $Pendentes | ForEach-Object { "  $_" }
    throw "Limpe $Name (veja 'Se aparecer alterações locais' abaixo) e rode o bloco de novo."
  }
  git -C $Path fetch origin
  if ($LASTEXITCODE -ne 0) { throw "Falha no fetch de $Name" }
  git -C $Path checkout homolog
  if ($LASTEXITCODE -ne 0) { throw "Falha ao trocar $Name para homolog" }
  git -C $Path pull --ff-only
  if ($LASTEXITCODE -ne 0) { throw "Falha ao atualizar $Name" }
  "{0,-40} {1}" -f $Name, (git -C $Path log -1 --format='%h %s')
}
```

O bloco pode ser rodado de novo a qualquer momento: ele clona o que falta e atualiza o resto na `homolog`.

**Se aparecer alterações locais:** o bloco para antes do `pull`, para não perder trabalho, e lista os arquivos (`M` = alterado, `??` = novo). Entre no repositório mostrado e escolha **uma** das opções:

```powershell
Set-Location C:\fiap\fase4\<repo>   # troque <repo> pelo nome mostrado
git status
```

- **Guardar as alterações:** `git stash push -u -m "antes do bootstrap"` (recupere depois com `git stash pop`).
- **Descartar as alterações:** `git restore .` desfaz os arquivos `M`; `git clean -n -d` mostra os `??` que serão apagados e `git clean -f -d` apaga.

Depois, `git status` deve mostrar `nothing to commit, working tree clean`; rode o bloco de novo.

Caso comum: uma pasta com nome estranho, como `iap?dd-report/`. Provavelmente um comando recebeu `C:\fiap\bdd-report` com as barras interpretadas como escape (`\f` e `\b`). É só um relatório de BDD: descarte com `git clean -f -d`.

### 4.4 HCP Terraform: Team token exclusivo do projeto da Fase 4

1. HCP → *Organization settings → Teams → Create a team*: `oficina-fase4`, **sem** permissões de organização.
2. Abra o [projeto da Fase 4](https://app.terraform.io/app/oficina-fiap-soat-fase-2/projects/prj-UTzD6p6PqRVEr27C) → *Settings → Team access → Add team and permissions*: `oficina-fase4` com **Admin** (cria workspaces e gerencia o Variable Set do projeto).
3. *Organization settings → API tokens → Team Tokens → Create a team token*: time `oficina-fase4`, expiração depois da entrega. Copie o token.
4. Limpe credenciais antigas para não haver mistura:

```powershell
Remove-Item Env:\TFC_TOKEN -ErrorAction SilentlyContinue
[Environment]::SetEnvironmentVariable('TFC_TOKEN', $null, 'User')
Remove-Item C:\fiap-secrets\shared\hcp-terraform-token-fase4.clixml -ErrorAction SilentlyContinue
```

5. Teste o token antes do script (não grava nada):

```powershell
$Tok = Read-Host 'Team token HCP' -AsSecureString
$Plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($Tok))
$H = @{ Authorization = "Bearer $Plain" }
$P = (Invoke-RestMethod 'https://app.terraform.io/api/v2/projects/prj-UTzD6p6PqRVEr27C' -Headers $H).data
"$($P.attributes.name) / $($P.relationships.organization.data.id)"   # esperado: <nome do projeto> / oficina-fiap-soat-fase-2
Remove-Variable Tok, Plain, H, P
```

Na 1ª execução do script, o prompt `HCP Terraform Team API token com acesso Admin somente ao projeto ...` pede esse token. O script confere que ele enxerga o `prj-UTzD6p6PqRVEr27C` e grava em `C:\fiap-secrets\shared\hcp-terraform-token-fase4.clixml`. O mesmo token vai para os GitHub Environments como `TF_API_TOKEN`.

Se o plano do HCP não permitir times, use um *User token* (*Account settings → Tokens*) no mesmo prompt: o script continua restrito ao projeto da Fase 4.

### 4.5 GitHub: token de sincronização entre repos (`REPO_SYNC_TOKEN`)

Os deploys do Kubernetes, do OS-database, do Auth e do OS gravam outputs nos GitHub Environments de outros repos. A Fase 4 **não usa a GitHub App da Fase 3**.

1. GitHub → *Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token*.
2. Nome `oficina-fase4-sync`; expiração depois da entrega; *Resource owner* `tiagomiele`.
3. *Only select repositories*: os 6 repos `oficina-*` da Fase 4.
4. *Repository permissions → Environments: Read and write* (*Metadata: Read* entra sozinho).
5. Na 1ª execução, o script pede o token (`GitHub fine-grained token de sincronização`), grava em `C:\fiap-secrets\shared\github-sync-token.clixml` e cadastra o secret no OS, no Auth e no Database.

### 4.6 SonarCloud

Em https://sonarcloud.io → *My Account → Security*, gere um token. O `-ConfigureSonarCloud` pede uma vez, grava em `C:\fiap-secrets\shared\sonarcloud-token.clixml` e cadastra `SONAR_TOKEN` nos 3 serviços. Projetos: `tiagomiele_fiap-tc-fase4-oficina-os-service`, `tiagomiele_fiap-tc-fase4-oficina-orcamento-pagamento-service`, `tiagomiele_fiap-tc-fase4-oficina-diagnostico-reparo-service`.

### 4.7 New Relic

Tenha em mãos, sem colocá-los em documentos: **Account ID**, **User API key** e **Ingest License key**. O `-ConfigureNewRelic` pede uma vez (as chaves em prompt protegido), grava em `C:\fiap-secrets\shared` e liga o APM nos 3 serviços (`NEW_RELIC_ENABLED=true` + `NEW_RELIC_LICENSE_KEY`), além do dashboard, alertas e telemetria do RDS/DLQ.

### 4.8 Mercado Pago (opcional)

Sem credenciais, o Orçamento e Pagamento usa `PAYMENT_PROVIDER=fake`, que é o necessário para o E2E. As credenciais vêm do painel (https://www.mercadopago.com.br/developers/panel/app), na sua aplicação: *Credenciais de teste* (Access Token e Public Key) e *Webhooks → modo de teste* (assinatura secreta). O comprador é a conta de teste `oficina-cliente`. Não cole esses valores em documentos ou no chat. A ativação, a validação e a volta ao `fake` estão na seção 20, que só roda depois do E2E verde.

## 5. Liberar o Learner Lab da Fase 3 (se ela ainda estiver de pé)

O Lab tem limite de EKS/RDS. Se a Fase 3 ainda existir na mesma conta, destrua-a **pelo HCP** (projeto `soat-fase3`, *Settings → Destruction and Deletion → Queue destroy plan*), na ordem `oficina-newrelic-homolog` → `oficina-auth-homolog` → `oficina-database-homolog` → `oficina-kubernetes-homolog`. Os repositórios da Fase 3 não são alterados.

```powershell
aws eks list-clusters --region us-west-2
aws rds describe-db-instances --region us-west-2 --query 'DBInstances[].DBInstanceIdentifier'
```

Os dois devem voltar vazios antes da seção 8.

## 6. Iniciar uma sessão AWS Academy

1. AWS Academy → Learner Lab → **Start Lab**; espere o indicador verde.
2. **AWS Details → AWS CLI → Show** e copie o bloco `[default]` inteiro. O script lê da área de transferência; se não achar, pergunta.
3. Não cole o bloco em chat, arquivo de projeto ou print.
4. Mantenha o Lab ativo durante os deploys (a sessão dura cerca de 4 h).

## 7. Bootstrap de homologação

### 7.1 Validação (não altera nada)

```powershell
Set-Location C:\fiap\fase4\fiap-tc-fase4-oficina-os-service
Set-ExecutionPolicy -Scope Process Bypass -Force
.\scripts\configure-environment.ps1 -Environment homolog -ValidateOnly
```

Esperado:

```text
AWS validada: conta <conta do Lab>, sessão LabRole ativa.
HCP Terraform validado: projeto <nome> (prj-UTzD6p6PqRVEr27C) acessível.
WARNING: Workspaces que serão criados na execução efetiva: ...   (normal na 1ª vez)
Validação concluída para homolog: ferramentas, repositórios, AWS STS, projeto HCP Terraform e GitHub CLI estão acessíveis.
```

### 7.2 Execução real

Com o `[default]` ainda na área de transferência:

```powershell
.\scripts\configure-environment.ps1 -Environment homolog -ConfigureSonarCloud -ConfigureNewRelic
```

Se as regras de proteção (`Proteção main`/`Proteção homolog`) já existem nos 6 repos e o script ainda não tem a correção do [fiap-tc-fase4-oficina-os-service#13](https://github.com/tiagomiele/fiap-tc-fase4-oficina-os-service/pull/13), acrescente `-SkipBranchProtection` (evita o HTTP 422 ao tentar recriá-las):

```powershell
.\scripts\configure-environment.ps1 -Environment homolog -ConfigureSonarCloud -ConfigureNewRelic -SkipBranchProtection
```

Na 1ª vez ele pede, nesta ordem aproximada: token HCP (4.4), senhas dos 2 RDS, secret JWT, senha do admin e chave de notificações (**Enter gera** valores novos), New Relic (4.7), token de sincronização (4.5) e SonarCloud (4.6). No Academy, **não** use `-EnableSesDelivery` nem `-CreateSesIdentity`: as notificações ficam em modo `log`.

O script:
- grava as credenciais AWS no computador, no Variable Set `aws-academy-credentials-fase4` e nos GitHub Environments;
- cria/atualiza os 12 workspaces `oficina-fase4-*` dentro do projeto (recusa workspace que esteja em outro projeto);
- cria os Environments `homolog`, `homolog-plan`, `production`, `production-plan` nos 6 repos, com secrets e variáveis;
- sincroniza as regras de proteção de `main`/`homolog`;
- **não** faz `terraform apply` nem deploy.

Resultado obrigatório no final:

```text
Configuração automática concluída para homolog.
Credenciais AWS: computador local, HCP Terraform e GitHub Environments atualizados.
Contexto não sensível salvo em C:\fiap-secrets\oficina-homolog\environment-context.ps1
```

Antes da infraestrutura existir, avisos de outputs ausentes e `DEPLOY_ENABLED=false` são normais.

Confirme a sessão:

```powershell
$Identity = aws sts get-caller-identity | ConvertFrom-Json
$Identity | Select-Object Account, Arn
if ($Identity.Arn -notmatch ':assumed-role/voclabs/') { throw "Sessão inesperada: $($Identity.Arn). Não prossiga." }
```

A conta do Academy pode mudar ao reiniciar o Lab; compare com *AWS Details* e não fixe Account IDs antigos.

### 7.3 Conferir HCP e GitHub

```powershell
.\scripts\configure-environment.ps1 -Environment homolog -ValidateOnly   # não deve listar workspaces ausentes
foreach ($r in $Repositories) {
  $envs = (gh api "repos/tiagomiele/$r/environments" -q '.environments[].name') -join ', '
  "{0,-40} {1}" -f $r, $envs
}
foreach ($r in 'fiap-tc-fase4-oficina-os-service','fiap-tc-fase4-oficina-auth-serverless','fiap-tc-fase4-oficina-database-infra') {
  "{0,-40} {1}" -f $r, ((gh secret list --repo "tiagomiele/$r" --env homolog) -match '^REPO_SYNC_TOKEN' -join '')
}
```

No HCP, confira os 12 workspaces no projeto da Fase 4, execução remota e **Auto Apply desligado**.

### 7.4 Função auxiliar para disparar e acompanhar workflows

Cole uma vez por janela do PowerShell:

```powershell
function Invoke-Workflow {
  param([Parameter(Mandatory)][string]$Repo, [Parameter(Mandatory)][string]$Workflow, [string]$Ref = 'homolog')
  $full = "tiagomiele/$Repo"
  gh workflow run $Workflow --repo $full --ref $Ref
  if ($LASTEXITCODE -ne 0) { throw "Falha ao disparar $full/$Workflow" }
  Start-Sleep 10
  $id = gh run list --repo $full --workflow $Workflow --branch $Ref --event workflow_dispatch --limit 1 --json databaseId -q '.[0].databaseId'
  Write-Host "Run: https://github.com/$full/actions/runs/$id"
  gh run watch $id --repo $full --exit-status
  if ($LASTEXITCODE -ne 0) { throw "Run $id de $full/$Workflow falhou. Pare e analise o log." }
}
```

Guarde os links dos runs: eles provam a ordem da reconstrução.

## 8. Kubernetes/EKS e observabilidade inicial

```powershell
Invoke-Workflow -Repo fiap-tc-fase4-oficina-kubernetes-infra -Workflow deploy-homolog.yml
```

O run faz: validação da sessão AWS → plan/apply do EKS → **sincroniza VPC, subnets e security group com `oficina-fase4-db-os-homolog`, `oficina-fase4-db-orcamento-pagamento-homolog` e `oficina-fase4-auth-homolog`** → Metrics Server e New Relic `nri-bundle` → rollout → plan/apply da observabilidade (dashboard com a página **Saga** e alertas).

Para avançar:
- cluster `oficina-homolog` criado e nodes `Ready`;
- passo "Sincronizar outputs de rede com Database e Auth" verde;
- add-ons e observabilidade verdes, sem destruição inesperada.

O Synthetic fica desligado neste 1º run, porque o OS ainda não tem LoadBalancer (é ligado na seção 14).

```powershell
aws eks update-kubeconfig --region us-west-2 --name oficina-homolog
kubectl get nodes
```

## 9. Bancos e filas (database-infra)

Os 3 workflows usam o mesmo componente (`terraform-component.yml`). Rode **um por vez**, depois do Kubernetes atual:

```powershell
Invoke-Workflow -Repo fiap-tc-fase4-oficina-database-infra -Workflow os.yml                    # RDS do OS + fila os-eventos/DLQ + telemetria
Invoke-Workflow -Repo fiap-tc-fase4-oficina-database-infra -Workflow orcamento-pagamento.yml   # RDS do O&P + fila orcamento-pagamento-comandos/DLQ
Invoke-Workflow -Repo fiap-tc-fase4-oficina-database-infra -Workflow diagnostico-reparo.yml    # DynamoDB + fila diagnostico-reparo-comandos/DLQ
```

Só o `os.yml` sincroniza sozinho (JDBC do OS para o workspace do Auth e para o Environment do OS). Os outputs dos outros 2 (JDBC do O&P, tabela DynamoDB, URLs das filas para os 3 serviços e `DEPLOY_ENABLED` dos 3) são levados pelo script. **Rode o script agora:**

```powershell
Set-Location C:\fiap\fase4\fiap-tc-fase4-oficina-os-service
.\scripts\configure-environment.ps1 -Environment homolog
```

Confira:

```powershell
aws sqs list-queues --region us-west-2 --queue-name-prefix oficina-homolog- --query 'QueueUrls[]' --output table
aws dynamodb describe-table --region us-west-2 --table-name oficina-homolog-execucoes --query 'Table.TableStatus'
aws rds describe-db-instances --region us-west-2 --query 'DBInstances[].[DBInstanceIdentifier,DBInstanceStatus]' --output table
```

Esperado:
- 3 filas principais e 3 DLQs (mais a DLQ da telemetria);
- tabela `ACTIVE`;
- 2 RDS `available`;
- no script: `Output do RDS sincronizado automaticamente...`, `Gate do Backend confirmado: DEPLOY_ENABLED=true`, `Gate do fiap-tc-fase4-oficina-orcamento-pagamento-service: DEPLOY_ENABLED=true` e `Gate do fiap-tc-fase4-oficina-diagnostico-reparo-service: DEPLOY_ENABLED=true`.

Não altere `DEPLOY_ENABLED` à mão. Se ficar `false`, falta output de algum workspace.

## 10. Teste da LabRole (pod → SQS e DynamoDB)

É o maior risco do Academy: os pods usam a LabRole do node para chegar no SQS e no DynamoDB.

```powershell
Set-Location C:\fiap\fase4\fiap-tc-fase4-oficina-database-infra
.\scripts\test-labrole-access.ps1 -Environment homolog
Set-Location C:\fiap\fase4\fiap-tc-fase4-oficina-os-service
```

Esperado: `LabRole validada: o pod acessa as 3 filas SQS e a tabela DynamoDB.` Se der falha, pare aqui.

## 11. Auth/API Gateway (1º apply)

O deploy do OS exige `AUTH_BASE_URL`, que só existe depois deste apply. Por isso o Auth vem antes dos serviços.

```powershell
Invoke-Workflow -Repo fiap-tc-fase4-oficina-auth-serverless -Workflow terraform-deploy.yml
.\scripts\configure-environment.ps1 -Environment homolog
```

O run empacota a Lambda Java 21, aplica Lambdas, API Gateway, authorizer, SNS/SQS de notificação e **sincroniza `API_GATEWAY_BASE_URL`, `AUTH_BASE_URL` e `NOTIFICATION_ENDPOINT` com o OS**. O script leva também `API_GATEWAY_BASE_URL` ao Orçamento e Pagamento.

Esperado no script: `URLs do API Gateway sincronizadas automaticamente com o backend.` As rotas para o Orçamento e Pagamento ainda não existem; elas entram na seção 13.

## 12. CD dos 3 serviços

Rode um por vez (cada CD: testes → imagem no GHCR → gate `DEPLOY_ENABLED` → ConfigMap/Secret/Deployment/Service no namespace do serviço → rollout → smoke test → captura do LoadBalancer):

```powershell
Invoke-Workflow -Repo fiap-tc-fase4-oficina-os-service -Workflow cd.yml
Invoke-Workflow -Repo fiap-tc-fase4-oficina-diagnostico-reparo-service -Workflow cd.yml
Invoke-Workflow -Repo fiap-tc-fase4-oficina-orcamento-pagamento-service -Workflow cd.yml
```

Sincronizações feitas pelos próprios runs:
- **OS** → `backend_base_url` no workspace do Auth; `health_check_url`/`synthetic_monitor_enabled` no workspace New Relic; `BACKEND_BASE_URL` no OS; `HEALTH_CHECK_URL`/`SYNTHETIC_MONITOR_ENABLED` no Kubernetes.
- **Orçamento e Pagamento** → `orcamento_pagamento_base_url` no workspace do Auth.
- **Diagnóstico e Reparo** → nada (o técnico acessa o LoadBalancer direto).

```powershell
kubectl get pods,svc -n oficina-homolog
kubectl get pods,svc -n oficina-diagnostico-reparo-homolog
kubectl get pods,svc -n oficina-orcamento-pagamento-homolog
```

Esperado: pods `Running 1/1` e cada Service `LoadBalancer` com hostname `*.elb.amazonaws.com`. Cada pod Spring leva 2–3 min para ficar pronto.

## 13. Auth (2º apply: rotas dos serviços e webhook)

```powershell
Invoke-Workflow -Repo fiap-tc-fase4-oficina-auth-serverless -Workflow terraform-deploy.yml
```

Esperado: só atualizações (nenhum destroy) nas integrações; rotas do Orçamento e Pagamento (`GET /orcamentos/{numeroOs}`, `POST .../aprovar`, `POST .../rejeitar`, `GET /pagamentos/{numeroOs}`, `POST .../nova-tentativa`) e `POST /webhooks/mercadopago`, além das do OS (`/consulta/ordens-servico/{numeroOs}/status`, `/ordens-servico/{numeroOs}/historico`) apontando para o LoadBalancer atual; output `mercadopago_webhook_url`.

## 14. Kubernetes (2º run: Synthetic com o health final)

```powershell
Invoke-Workflow -Repo fiap-tc-fase4-oficina-kubernetes-infra -Workflow deploy-homolog.yml
.\scripts\configure-environment.ps1 -Environment homolog    # atualiza o contexto local com as URLs finais
```

Esperado: EKS sem mudanças destrutivas, add-ons saudáveis, Synthetic `oficina-fase4-homolog-healthcheck` criado com `<OS>/actuator/health`.

---

## 15. Validar a saúde técnica

Namespaces e deployments (o CD acrescenta o ambiente ao nome do namespace; com `$Amb = 'production'`, os nomes terminam em `-production`):

| Serviço | Namespace | Deployment / Service |
|---|---|---|
| OS Service | `oficina-homolog` | `oficina-app` |
| Diagnóstico e Reparo | `oficina-diagnostico-reparo-homolog` | `oficina-diagnostico-reparo` |
| Orçamento e Pagamento | `oficina-orcamento-pagamento-homolog` | `oficina-orcamento-pagamento` |

```powershell
$Amb = 'homolog'   # ou 'production'
aws eks update-kubeconfig --region us-west-2 --name "oficina-$Amb"
kubectl config current-context    # tem que terminar em cluster/oficina-<ambiente>
. "C:\fiap-secrets\oficina-$Amb\environment-context.ps1"
function Get-LbUrl($svc, $ns) { 'http://' + (kubectl get svc $svc -n $ns -o jsonpath='{.status.loadBalancer.ingress[0].hostname}') }
$OS = Get-LbUrl 'oficina-app' "oficina-$Amb"
$DR = Get-LbUrl 'oficina-diagnostico-reparo' "oficina-diagnostico-reparo-$Amb"
$OP = Get-LbUrl 'oficina-orcamento-pagamento' "oficina-orcamento-pagamento-$Amb"
$GW = gh variable get API_GATEWAY_BASE_URL --repo tiagomiele/fiap-tc-fase4-oficina-os-service --env $Amb
$OS; $DR; $OP; $GW
if ($OS -eq 'http://' -or $DR -eq 'http://' -or $OP -eq 'http://') { throw "URL vazia: confira o cluster com kubectl config current-context" }
foreach ($u in $OS, $DR, $OP) { Invoke-RestMethod "$u/actuator/health" }

kubectl get deployment,service,hpa,pdb -n "oficina-$Amb"
kubectl rollout status deployment/oficina-app -n "oficina-$Amb" --timeout=120s
```

Esperado: `current-context` terminando em `cluster/oficina-<ambiente>`; 4 URLs preenchidas; os 3 health `UP`; rollout concluído.

> A seção 16 usa `$OS`, `$DR`, `$OP` e `$GW`. Rode a seção 15 e a 16.1 na mesma janela do PowerShell antes da 16.2; em uma janela nova, rode as duas de novo. Depois de subir ou mexer em outro ambiente, o `kubectl` fica apontando para o último cluster usado: por isso a seção começa pelo `update-kubeconfig`.

Swagger (pelos LoadBalancers, não pelo API Gateway):

```powershell
Start-Process "$OS/swagger-ui/index.html"
Start-Process "$DR/swagger-ui.html"
Start-Process "$OP/swagger-ui.html"
```

## 16. Validação funcional da Saga (manual)

### 16.1 Login do funcionário

```powershell
$s = Import-Clixml "C:\fiap-secrets\oficina-$Amb\backend-admin-password.clixml"
$AdminPwd = if ($s -is [securestring]) { [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)) } else { [string]$s }
function J($o) { ,[Text.Encoding]::UTF8.GetBytes(($o | ConvertTo-Json -Depth 6)) }   # a vírgula impede o PS 5.1 de desmontar o byte[]
$CT = 'application/json; charset=utf-8'
$login = Invoke-RestMethod -Method Post "$OS/auth/login" -ContentType $CT -Body (J @{ email='admin@oficina.local'; senha=$AdminPwd; validadeMinutos=240 })
$HF = @{ Authorization = "Bearer $($login.accessToken)" }
```

O JWT dura 15 min; com 401/403 no meio do teste, repita as 2 últimas linhas.

### 16.2 Cadastro: peça com estoque, serviço, cliente e veículo

```powershell
$peca = Invoke-RestMethod -Method Post "$OS/pecas" -Headers $HF -ContentType $CT -Body (J @{ nome='Filtro HML'; precoVenda=40.00 })
Invoke-RestMethod -Method Post "$OS/notas-fiscais-fornecedor" -Headers $HF -ContentType $CT -Body (J @{
  numeroNota="HML$(Get-Random)"; serieNota='1'; cnpjFornecedor='11222333000181'; dataEmissao='2026-11-01'; nomeFornecedor='Fornecedor HML'
  itens=@(@{ idSku=$peca.idSku; quantidade=3; precoUnitario=20.00 }) })
$serv = Invoke-RestMethod -Method Post "$OS/servicos" -Headers $HF -ContentType $CT -Body (J @{ nome='Revisão HML'; descricao='Revisão'; precoBase=150.00 })

function New-CpfTeste {   # CPF fictício com dígitos verificadores válidos
  $d = 1..9 | ForEach-Object { Get-Random -Min 0 -Max 10 }
  foreach ($n in 10, 11) {
    $soma = 0; for ($i = 0; $i -lt $n - 1; $i++) { $soma += $d[$i] * ($n - $i) }
    $r = ($soma * 10) % 11; if ($r -eq 10) { $r = 0 }; $d += $r
  }
  -join $d
}
$Cpf = New-CpfTeste; $Cpf
$cli = Invoke-RestMethod -Method Post "$OS/clientes" -Headers $HF -ContentType $CT -Body (J @{ nome='Cliente HML'; documento=$Cpf; email="hml$Cpf@teste.com"; telefone='11999999999' })
$Placa = "HML$(Get-Random -Min 1000 -Max 9999)"
Invoke-RestMethod -Method Post "$OS/veiculos" -Headers $HF -ContentType $CT -Body (J @{ placa=$Placa; marca='Fiat'; modelo='Uno'; ano=2020; idCliente=$cli.idCliente })
```

### 16.3 Fluxo feliz

```powershell
$Corr = "hml-$(Get-Random)"
$ordem = Invoke-RestMethod -Method Post "$OS/ordens-servico" -Headers ($HF + @{ 'X-Request-Id'=$Corr }) -ContentType $CT -Body (J @{ idCliente=$cli.idCliente; placa=$Placa; descricaoProblema='Barulho' })
$N = $ordem.numero; $N   # não use $os: o PowerShell não diferencia maiúsculas e sobrescreveria $OS
Start-Sleep 5
Invoke-RestMethod "$DR/execucoes/$N" -Headers $HF                                   # NA_FILA_DIAGNOSTICO (comando chegou pelo SQS)

Invoke-RestMethod -Method Post "$DR/execucoes/$N/iniciar-diagnostico" -Headers $HF
Invoke-RestMethod -Method Post "$OS/ordens-servico/$N/servicos" -Headers $HF -ContentType $CT -Body (J @{ idServicoSku=$serv.idServico; quantidade=1 })
Invoke-RestMethod -Method Post "$OS/ordens-servico/$N/pecas"    -Headers $HF -ContentType $CT -Body (J @{ idServicoSku=$peca.idSku; quantidade=1 })
Invoke-RestMethod -Method Post "$DR/execucoes/$N/concluir-diagnostico" -Headers $HF -ContentType $CT -Body (J @{ diagnostico='Troca do filtro' })
Start-Sleep 5
(Invoke-RestMethod "$OS/ordens-servico/$N" -Headers $HF).status                   # AGUARDANDO_APROVACAO

# Cliente: login por CPF no Auth e decisão pelo API Gateway
$tc = Invoke-RestMethod -Method Post "$GW/auth/cpf" -ContentType $CT -Body (J @{ cpf=$Cpf })
$HC = @{ Authorization = "Bearer $($tc.accessToken)" }
Invoke-RestMethod "$GW/orcamentos/$N" -Headers $HC                                   # AGUARDANDO_APROVACAO + itens/valor
Invoke-RestMethod -Method Post "$GW/orcamentos/$N/aprovar" -Headers $HC
Start-Sleep 5
(Invoke-RestMethod "$OS/ordens-servico/$N" -Headers $HF).status                   # EM_EXECUCAO (estoque baixado)

Invoke-RestMethod -Method Post "$DR/execucoes/$N/iniciar-reparo"  -Headers $HF
Invoke-RestMethod -Method Post "$DR/execucoes/$N/concluir-reparo" -Headers $HF
Start-Sleep 5
(Invoke-RestMethod "$OS/ordens-servico/$N" -Headers $HF).status                   # AGUARDANDO_PAGAMENTO
Invoke-RestMethod "$GW/pagamentos/$N" -Headers $HC                                   # PENDENTE

Invoke-RestMethod -Method Post "$OP/pagamentos/$N/simular?aprovado=true" -Headers $HF   # PAYMENT_PROVIDER=fake
Start-Sleep 5
(Invoke-RestMethod "$OS/ordens-servico/$N" -Headers $HF).status                   # PAGA
Invoke-RestMethod "$GW/ordens-servico/$N/historico" -Headers $HC | ConvertTo-Json -Depth 5   # etapas da Saga com correlationId = $Corr
Invoke-RestMethod -Method Post "$OS/ordens-servico/$N/entregar" -Headers $HF      # ENTREGUE (fora da Saga, outro request id)
```

Segurança: com o CPF de **outro** cliente, `GET "$GW/consulta/ordens-servico/$N/status"` deve dar 404; sem token, 401.

### 16.4 Compensações

Antes, rode de novo o 16.2 (peça com 3 unidades, serviço, CPF novo, cliente e veículo). Depois, na mesma janela, cole os blocos abaixo **em ordem**. A ordem importa por causa do estoque: o cenário D consome 2 das 3 peças.

**Preparação** (login do cliente novo e 2 funções auxiliares)

```powershell
$tc = Invoke-RestMethod -Method Post "$GW/auth/cpf" -ContentType $CT -Body (J @{ cpf=$Cpf })
$HC = @{ Authorization = "Bearer $($tc.accessToken)" }

function New-OsAteAprovacao([int]$QtdPeca = 1) {   # abre uma OS e leva até AGUARDANDO_APROVACAO
  $o = Invoke-RestMethod -Method Post "$OS/ordens-servico" -Headers ($HF + @{ 'X-Request-Id'="hml-$(Get-Random)" }) -ContentType $CT -Body (J @{ idCliente=$cli.idCliente; placa=$Placa; descricaoProblema='Teste compensacao' })
  $n = $o.numero
  Start-Sleep 5
  Invoke-RestMethod -Method Post "$DR/execucoes/$n/iniciar-diagnostico" -Headers $HF | Out-Null
  Invoke-RestMethod -Method Post "$OS/ordens-servico/$n/servicos" -Headers $HF -ContentType $CT -Body (J @{ idServicoSku=$serv.idServico; quantidade=1 }) | Out-Null
  if ($QtdPeca -gt 0) { Invoke-RestMethod -Method Post "$OS/ordens-servico/$n/pecas" -Headers $HF -ContentType $CT -Body (J @{ idServicoSku=$peca.idSku; quantidade=$QtdPeca }) | Out-Null }
  Invoke-RestMethod -Method Post "$DR/execucoes/$n/concluir-diagnostico" -Headers $HF -ContentType $CT -Body (J @{ diagnostico='Teste' }) | Out-Null
  Start-Sleep 5
  Write-Host "$n -> $((Invoke-RestMethod "$OS/ordens-servico/$n" -Headers $HF).status)"   # AGUARDANDO_APROVACAO
  $n
}

function Show-Saga($n) {   # status nos 3 serviços
  [pscustomobject]@{
    numero    = $n
    os        = (Invoke-RestMethod "$OS/ordens-servico/$n" -Headers $HF).status
    orcamento = (Invoke-RestMethod "$GW/orcamentos/$n" -Headers $HC).status
    execucao  = (Invoke-RestMethod "$DR/execucoes/$n" -Headers $HF).etapa
  }
}
```

Se o 1º `$GW` der `Internal Server Error` (cold start do Authorizer), repita o comando.

O token do cliente (`$HC`) vale **15 min**. Se o `$GW` responder `401`, renove-o com as 2 primeiras linhas da preparação (`$tc = ...` e `$HC = ...`) e repita o comando; a OS não muda de status.

**A. Rejeitar e cancelar** (1 peça na OS, estoque continua 3)

```powershell
$A = New-OsAteAprovacao 1
Invoke-RestMethod -Method Post "$GW/orcamentos/$A/rejeitar" -Headers $HC -ContentType $CT -Body (J @{ acao='CANCELAR'; motivo='teste cancelar' }) | Out-Null
Start-Sleep 5
Show-Saga $A                                                      # CANCELADA | REJEITADO | CANCELADA
(Invoke-RestMethod "$OS/estoque/$($peca.idSku)" -Headers $HF)     # saldo 3 (intacto)
```

**B. Rejeitar e refazer** (volta ao diagnóstico)

```powershell
$B = New-OsAteAprovacao 0
Invoke-RestMethod -Method Post "$GW/orcamentos/$B/rejeitar" -Headers $HC -ContentType $CT -Body (J @{ acao='REFAZER'; motivo='teste refazer' }) | Out-Null
Start-Sleep 5
Show-Saga $B                                                      # EM_DIAGNOSTICO | REJEITADO | NA_FILA_DIAGNOSTICO
(Invoke-RestMethod "$DR/execucoes/$B" -Headers $HF).retrabalho    # True
```

**C. Pagamento recusado e nova tentativa** (sem peça)

```powershell
$C = New-OsAteAprovacao 0
Invoke-RestMethod -Method Post "$GW/orcamentos/$C/aprovar" -Headers $HC | Out-Null
Start-Sleep 5
Invoke-RestMethod -Method Post "$DR/execucoes/$C/iniciar-reparo"  -Headers $HF | Out-Null
Invoke-RestMethod -Method Post "$DR/execucoes/$C/concluir-reparo" -Headers $HF | Out-Null
Start-Sleep 5
Invoke-RestMethod -Method Post "$OP/pagamentos/$C/simular?aprovado=false" -Headers $HF | Out-Null
Start-Sleep 5
Show-Saga $C                                                      # AGUARDANDO_PAGAMENTO | PAGAMENTO_RECUSADO | REPARO_CONCLUIDO
(Invoke-RestMethod "$GW/pagamentos/$C" -Headers $HC).status       # RECUSADO
(Invoke-RestMethod -Method Post "$GW/pagamentos/$C/nova-tentativa" -Headers $HC).status   # PENDENTE
Invoke-RestMethod -Method Post "$OP/pagamentos/$C/simular?aprovado=true" -Headers $HF | Out-Null
Start-Sleep 5
Show-Saga $C                                                      # PAGA | PAGO | REPARO_CONCLUIDO
```

**D. Estoque insuficiente** (2 OS com 2 peças cada, estoque 3)

```powershell
$D1 = New-OsAteAprovacao 2
$D2 = New-OsAteAprovacao 2
Invoke-RestMethod -Method Post "$GW/orcamentos/$D1/aprovar" -Headers $HC | Out-Null
Start-Sleep 5
Invoke-RestMethod -Method Post "$GW/orcamentos/$D2/aprovar" -Headers $HC | Out-Null
Start-Sleep 5
Show-Saga $D1                                                     # EM_EXECUCAO | APROVADO | NA_FILA_REPARO
Show-Saga $D2                                                     # CANCELADA | CANCELADO | CANCELADA
(Invoke-RestMethod "$OS/estoque/$($peca.idSku)" -Headers $HF)     # saldo 1
```

As OS `$B` e `$D1` ficam abertas de propósito; não atrapalham os próximos passos.

### 16.5 Mensageria, DLQ e DynamoDB

```powershell
foreach ($q in (aws sqs list-queues --region us-west-2 --queue-name-prefix oficina-homolog- --query 'QueueUrls[]' --output text).Split()) {
  $a = aws sqs get-queue-attributes --region us-west-2 --queue-url $q --attribute-names ApproximateNumberOfMessages --query 'Attributes.ApproximateNumberOfMessages' --output text
  "{0,-90} {1}" -f $q, $a
}
aws dynamodb get-item --region us-west-2 --table-name oficina-homolog-execucoes --key "{\`"numeroOs\`":{\`"S\`":\`"$N\`"}}"
kubectl logs -n oficina-homolog deploy/oficina-app --tail=200 | Select-String $Corr
```

Esperado: filas e DLQs com `0` mensagens depois do fluxo; o item da OS no DynamoDB; logs com o `correlationId`.

**Filas em `0` é o esperado.** O consumidor apaga a mensagem assim que a processa, e o SQS não guarda histórico; o conteúdo só fica na DLQ (14 dias), depois de 5 falhas. O que passou pela Saga fica registrado em:

| Onde | O que mostra |
|---|---|
| Banco do OS, `mensagens_processadas` e `historico_status_ordem_servico` | eventos recebidos pelo OS e cada mudança de status, com `correlation_id` |
| Banco do Orçamento e Pagamento, `mensagens_processadas` | comandos recebidos |
| DynamoDB `oficina-homolog-execucoes` | `etapa`, `correlationId` e os últimos 20 `messageId` (`mensagensProcessadas`) |
| New Relic → Logs | cada mensagem publicada e consumida, buscando pelo `correlationId` |

Os bancos PostgreSQL são privados: abra-os no DBeaver pelo túnel do [Database Infra, seção 6.3](https://github.com/tiagomiele/fiap-tc-fase4-oficina-database-infra#63-ver-os-dados-do-rds-dbeaver-ou-psql), que também traz as portas, a senha e os erros comuns.

**`correlation_id`:** é o mesmo em todas as mensagens da mesma OS, enquanto o `message_id` muda a cada mensagem. Ele vem do `X-Request-Id` da abertura da OS: `hml-<número>` nestes testes manuais, `postman-saga-<runId>` no E2E da seção 17 (`runId` = horário do run em milissegundos) e um UUID quando a requisição chega sem o cabeçalho. O prefixo só indica a origem do teste.

**No console da AWS:** selecione antes a região **Estados Unidos (Oregon) us-west-2**, no canto superior direito. Em outra região (por exemplo, Norte da Virgínia) as filas e a tabela não aparecem.
- **SQS:** **Simple Queue Service** → **Filas** → busque `oficina-homolog-`. As colunas **Mensagens disponíveis** e **Mensagens em andamento** ficam nessa lista, que só se atualiza no **⟳**. Dentro da fila, a aba **Monitoramento** mostra os gráficos (atraso de 1 a 5 min).
- **Conteúdo de uma DLQ:** fila `-dlq` → botão **Enviar e receber mensagens** (no topo da página da fila) → **Sondar mensagens** → ID → aba **Corpo**. Não sonde as filas principais com o serviço no ar: cada leitura conta como tentativa, e na 5ª a mensagem vai para a DLQ.
- **DynamoDB:** **Explorar itens** → `oficina-homolog-execucoes` → **Varrer** → **Filtros** (`numeroOs` = número da OS) → **Executar**.

### 16.6 Ver uma mensagem parada na fila (opcional)

Para ver a fila ser alimentada, pare o consumidor do Diagnóstico e Reparo, abra algumas OS e religue-o. Use a mesma janela das seções 15 a 16.2 (`$OS`, `$DR`, `$HF`, `$cli`, `$Placa`).

**1. Pare o consumidor.** Com 0 réplicas o HPA não o religa sozinho.

```powershell
$NsDR = 'oficina-diagnostico-reparo-homolog'
kubectl scale deploy/oficina-diagnostico-reparo -n $NsDR --replicas=0
if ($LASTEXITCODE -ne 0) { throw "Deployment não encontrado em $NsDR. Confira: kubectl get deploy -A" }
do { Start-Sleep 5; $p = kubectl get pods -n $NsDR -l app.kubernetes.io/name=oficina-diagnostico-reparo -o name } while ($p)   # espera o pod sair
```

**2. Abra 5 OS, uma a cada 10 s.** Enquanto o bloco roda, clique em **⟳** na lista de filas: **Mensagens disponíveis** da `oficina-homolog-diagnostico-reparo-comandos` sobe até 5.

```powershell
$Fila = @()
1..5 | ForEach-Object {
  $c = "hml-fila-$(Get-Random)"
  $o = Invoke-RestMethod -Method Post "$OS/ordens-servico" -Headers ($HF + @{ 'X-Request-Id'=$c }) -ContentType $CT -Body (J @{ idCliente=$cli.idCliente; placa=$Placa; descricaoProblema="Teste fila $_" })
  $Fila += $o.numero; "{0}  {1}" -f $o.numero, $c
  Start-Sleep 10
}
$q = aws sqs get-queue-url --region us-west-2 --queue-name oficina-homolog-diagnostico-reparo-comandos --query QueueUrl --output text
aws sqs get-queue-attributes --region us-west-2 --queue-url $q --attribute-names ApproximateNumberOfMessages   # 5
```

Se quiser ver o conteúdo, sonde a fila **uma vez** no console (**Enviar e receber mensagens** → **Sondar mensagens**): aparece `tipo = INICIAR_DIAGNOSTICO`. A mensagem lida fica cerca de 60 s em **Mensagens em andamento** e depois volta.

**3. Religue e veja a fila esvaziar** (em até 1 min depois do pod pronto):

```powershell
kubectl scale deploy/oficina-diagnostico-reparo -n $NsDR --replicas=1
kubectl rollout status deploy/oficina-diagnostico-reparo -n $NsDR --timeout=300s
Start-Sleep 30
$Fila | ForEach-Object { "{0}  {1}" -f $_, (Invoke-RestMethod "$DR/execucoes/$_" -Headers $HF).etapa }   # NA_FILA_DIAGNOSTICO
```

**Não esqueça o passo 3:** com o consumidor parado, toda OS nova fica presa na fila. Essas 5 OS ficam abertas e não atrapalham os próximos passos.

## 17. E2E automatizado (Newman)

O workflow aquece o login por CPF e o Authorizer antes do Newman, para o cold start das Lambdas não estourar o timeout do API Gateway.

Na 1ª vez, o E2E roda sozinho com o merge do [fiap-tc-fase4-oficina-os-service#14](https://github.com/tiagomiele/fiap-tc-fase4-oficina-os-service/pull/14) (push na `homolog`), que também registra o workflow. Sem isso, o `gh workflow run e2e.yml` responde `HTTP 404: workflow e2e.yml not found on the default branch`, porque a `main` ainda não tem workflows. Depois do 1º run, use os comandos abaixo.

```powershell
gh workflow run e2e.yml --repo tiagomiele/fiap-tc-fase4-oficina-os-service --ref homolog -f environment=homolog -f run_load_test=false
Start-Sleep 10
gh run watch (gh run list --repo tiagomiele/fiap-tc-fase4-oficina-os-service --workflow e2e.yml --limit 1 --json databaseId -q '.[0].databaseId') --repo tiagomiele/fiap-tc-fase4-oficina-os-service --exit-status
```

A coleção faz o health dos 3 serviços, a Saga até `PAGA`/`ENTREGUE` (pagamento fake), a segurança do API Gateway e confere o mesmo `correlationId` nas etapas (`postman-saga-<runId>`). As URLs do D&R e do O&P vêm dos LoadBalancers via `kubectl`. Depois, opcionalmente, repita com `-f run_load_test=true` (smoke k6). Resultado no artefato `newman-homolog-<run>`.

O Newman só roda com `PAYMENT_PROVIDER=fake`. A validação com o Mercado Pago real vem depois, na seção 20.

**17.1 E2E em produção**

Use depois da seção 23, com a produção no ar. Antes, confira três coisas:
- **Ambiente no ar:** os nodes e os 3 serviços precisam estar rodando:

  ```powershell
  aws eks update-kubeconfig --region us-west-2 --name oficina-production
  kubectl get nodes                                   # 2 nodes Ready; "No resources found" = seção 21.2
  kubectl get pods -A | Select-String oficina         # todos 1/1 Running, nenhum Pending
  $u = gh variable get BACKEND_BASE_URL --repo tiagomiele/fiap-tc-fase4-oficina-os-service --env production
  Invoke-RestMethod "$u/actuator/health/readiness"    # status UP
  ```
- **Lab ativo e `[default]` copiado:** o workflow usa as credenciais AWS gravadas no Environment `production` do GitHub.
- **Pagamento no `fake`:** `gh variable list --repo tiagomiele/fiap-tc-fase4-oficina-orcamento-pagamento-service --env production` não deve listar `PAYMENT_PROVIDER`, ou ela deve estar igual a `fake`.

Pelo script, que renova as credenciais no GitHub e roda só o E2E (digite `production` na confirmação):

```powershell
Set-Location C:\fiap\fase4\fiap-tc-fase4-oficina-os-service
git pull --ff-only
Set-ExecutionPolicy -Scope Process Bypass -Force
.\scripts\provision-environment.ps1 -Environment production -StartAt e2e                 # Newman
.\scripts\provision-environment.ps1 -Environment production -StartAt e2e -RunLoadTest    # Newman + smoke k6
```

Direto pelo `gh`, só se as credenciais já foram renovadas nesta sessão do Lab (`configure-environment.ps1 -Environment production -UseAwsAcademyDisposableProductionProfile`):

```powershell
gh workflow run e2e.yml --repo tiagomiele/fiap-tc-fase4-oficina-os-service --ref main -f environment=production -f run_load_test=false
Start-Sleep 10
gh run watch (gh run list --repo tiagomiele/fiap-tc-fase4-oficina-os-service --workflow e2e.yml --limit 1 --json databaseId -q '.[0].databaseId') --repo tiagomiele/fiap-tc-fase4-oficina-os-service --exit-status
```

O run termina com `completed with 'success'`, e o resultado fica no artefato `newman-production-<run>`. No banco de produção (túnel na porta `25432`), a OS do teste aparece com o `correlation_id` `postman-saga-<runId>`.

| Sintoma | Causa | O que fazer |
|---|---|---|
| `ExpiredToken` / `InvalidClientTokenId` no passo *Configure AWS Academy credentials* | credenciais do Lab vencidas no GitHub | copie o `[default]` e use o script acima |
| `LoadBalancer sem hostname` | D&R ou O&P sem deploy na produção | rode o CD dos 2 serviços na `main` (seção 23) |
| falha no `/pagamentos/{n}/simular` | `PAYMENT_PROVIDER=mercadopago` na produção | volte ao `fake` como na seção 20.6 |
| `socket hang up` na 1ª requisição (*Health readiness e X-Request-Id*); no PowerShell, `A conexão subjacente estava fechada` | o cluster ficou sem nodes (o Lab parou as EC2) e os pods estão `Pending`; o LoadBalancer fecha a conexão por não ter destino saudável | interrompa o script (`Ctrl+C`), siga a seção 21.2 com `$amb = 'production'` e rode o E2E de novo depois do health `UP` |

## 18. New Relic

Em https://one.newrelic.com, depois de ~10 min de tráfego (16 e 17 já feitos). Para abrir cada tela, use **Find what you need** (Ctrl+K). As consultas NRQL vão em **Query your data** (rodapé). Tudo aqui é só leitura.

**18.1 APM, cluster e telemetria**
- *APM & Services*: os 3 serviços de homolog aparecem com tráfego;
- *Kubernetes*: cluster `oficina-homolog` com os 2 nós;
- telemetria das Lambdas do Auth e do RDS chegando.

**18.2 Dashboard Saga**: *Dashboards → Oficina-fase4-homolog → aba Saga*, com eventos por tipo, compensações, *Mensagens nas DLQs* (`OficinaSqsSample`, a cada 5 min), *Falhas ao processar mensagens (por serviço)* e tabela por `correlationId`.
- **Aprovado:** as séries de DLQ e de falhas estão em **0 agora**, ou seja, no ponto mais à direita do gráfico. Picos antigos são histórico de problemas já resolvidos (ex.: nós fora do cluster) e não reprovam.
- Confirme pela AWS que as DLQs estão vazias neste momento (devem sair 4 linhas com `= 0`):

```powershell
foreach ($q in 'oficina-homolog-os-eventos-dlq','oficina-homolog-orcamento-pagamento-comandos-dlq','oficina-homolog-diagnostico-reparo-comandos-dlq','oficina-homolog-os-rds-newrelic-telemetry-dlq') {
  $url = aws sqs get-queue-url --region us-west-2 --queue-name $q --query QueueUrl --output text
  "$q = " + (aws sqs get-queue-attributes --region us-west-2 --queue-url $url --attribute-names ApproximateNumberOfMessages --query 'Attributes.ApproximateNumberOfMessages' --output text)
}
```

Se alguma DLQ tiver mensagem, não apague: investigue a causa antes (logs do serviço consumidor pelo `correlationId`).

- Rastreio de uma OS pelo `correlationId`:

```sql
SELECT timestamp, namespace_name, message FROM Log WHERE correlationId = '<valor de $Corr>' SINCE 1 hour ago
```

**18.3 Synthetic `oficina-fase4-homolog-healthcheck`**: *Synthetic monitoring → Synthetic monitors → oficina-fase4-homolog-healthcheck*.
- **Aprovado:** bolinha verde ao lado do nome, `0/2 Locations failing` e as últimas checagens de Washington e Portland sem pontos vermelhos. Falhas antigas no *Success rate*/*Checks failed* (ex.: `HTTP Error` de quando os nós estavam fora) não reprovam.
- Confirme pela NRQL (esperado: só a linha `SUCCESS`):

```sql
SELECT latest(result), count(*) FROM SyntheticCheck WHERE monitorName = 'oficina-fase4-homolog-healthcheck' SINCE 1 hour ago FACET result
```

**18.4 Alertas**
- *Alerts → Alert conditions*: existem `saga_dlq_com_mensagens` e `saga_falhas_de_processamento` (busque por `saga`);
- *Alerts → Issues & activity*: **nenhuma issue aberta**. Issue antiga já fechada não reprova.
- Se houver issue aberta, clique nela e veja a *Alert Condition*. Não feche na mão sem saber a causa. Confira o estado atual do cluster:

```powershell
kubectl get hpa -A                                   # REPLICAS abaixo de MAXPODS
kubectl get pods -A --field-selector=status.phase=Pending   # No resources found
```

  Exemplo já resolvido: `hpa no maximo` ficava aberta porque o HPA escalava também por memória, e a JVM ocupa ~90% do request mesmo parada (CPU em 0%). Correção: HPA só por CPU nos 3 serviços. Depois do CD as réplicas descem sozinhas e a issue fecha sozinha.

## 19. Checklist final de homologação

- [ ] sessão AWS Academy ativa durante todos os deploys;
- [ ] bootstrap concluído com `Configuração automática concluída para homolog.`;
- [ ] Kubernetes (1º run) verde e rede sincronizada;
- [ ] 3 workflows de banco verdes; script mostrando os 3 gates `DEPLOY_ENABLED=true`;
- [ ] LabRole validada (pod → SQS e DynamoDB);
- [ ] Auth (1º apply) verde e URLs sincronizadas;
- [ ] 3 CDs verdes, smoke aprovado e LoadBalancers capturados;
- [ ] Auth (2º apply) verde com rotas do O&P e webhook;
- [ ] Kubernetes (2º run) verde com Synthetic;
- [ ] 3 `/actuator/health` `UP`;
- [ ] fluxo feliz até `PAGA`/`ENTREGUE` e 4 compensações conferidas;
- [ ] filas/DLQs vazias e `correlationId` igual nas etapas;
- [ ] E2E Newman verde;
- [ ] Mercado Pago sandbox (20): `APRO` até `PAGA`; `OTHE` até `PAGAMENTO_RECUSADO` e nova tentativa paga (ou, se o sandbox cair em "Ops, ocorreu um erro", recusa coberta pelo `fake` no 16/17), todos com até R$ 10;
- [ ] `PAYMENT_PROVIDER` de volta a `fake` (20.6);
- [ ] New Relic (18): DLQs e falhas em 0 agora, Synthetic `SUCCESS` na última hora, alertas da Saga existentes e nenhuma issue aberta;
- [ ] SonarCloud (19.1): Quality Gate `Passed` e cobertura > 80% nos 3 serviços, branch `homolog`; BDD (19.2) verde com `bdd-report`;
- [ ] nenhuma destruição inesperada e nenhum secret exposto.

### 19.1 SonarCloud

Só os 3 serviços Java têm SonarCloud (Auth, Kubernetes e Database não). Os projetos são privados.

**Como encontrar e validar:**
1. Abra https://sonarcloud.io e clique em **Log in → GitHub** com a conta `tiagomiele`. Sem login, ou logado com outra conta, os links dão **404** (o SonarCloud esconde projeto privado).
2. Avatar (canto superior direito) → organização **tiagomiele** → **Projects**. Aparecem os 3 projetos `tiagomiele_oficina-...-service`. A frase "Your default branch has not been analyzed yet" é normal: a branch padrão do SonarCloud é a `master`, que o CI não analisa; isso **não** reprova.
3. Clique no projeto. Ao lado do título fica o botão de branch (`master ▾`): clique nele e escolha **`homolog`**. O menu lateral da organização não tem *Branches*; se o botão não aparecer, use os links da tabela, que já abrem na `homolog`.
4. Na tela *Branch Summary* da `homolog`, confira:
   - **Quality gate: Passed** (quadrado verde ✓);
   - **Coverage** acima de **80%**;
   - Security, Reliability e Maintainability sem issue aberta (nota A).
5. Repita para os 3 serviços e tire um print de cada um como evidência.

| Serviço | Link (branch `homolog`) |
|---|---|
| OS | https://sonarcloud.io/summary/overall?id=tiagomiele_fiap-tc-fase4-oficina-os-service&branch=homolog |
| Orçamento e Pagamento | https://sonarcloud.io/summary/overall?id=tiagomiele_fiap-tc-fase4-oficina-orcamento-pagamento-service&branch=homolog |
| Diagnóstico e Reparo | https://sonarcloud.io/summary/overall?id=tiagomiele_fiap-tc-fase4-oficina-diagnostico-reparo-service&branch=homolog |

**Resultado na homologação:** os 3 serviços com Quality Gate **Passed** na `homolog` (Diagnóstico e Reparo com 97,7% de cobertura). Além do SonarCloud, o build dos 3 serviços já falha se a cobertura de linhas ou branches ficar abaixo de 80% (JaCoCo), então CI verde também garante os 80%.

> **Plano do SonarCloud:** a organização está num trial do *Team plan*. Tire os prints de evidência antes de ele vencer. Se depois disso o job SonarCloud passar a falhar na `homolog`, tornar os projetos públicos (*Administration → Project visibility*) resolve sem custo.

O link também aparece no log do job **SonarCloud** do CI, na linha `QUALITY GATE STATUS: PASSED - View details on https://sonarcloud.io/dashboard?id=...`. Se o job mostrar só o passo **SonarCloud pendente** (`SONAR_TOKEN não configurado`), a análise não rodou:
1. confira se o projeto existe em *sonarcloud.io → organização tiagomiele → Projects*. Se não existir, crie em **+ → Analyze new project**, marque o repositório, clique em *Set up* e, em *Administration → Analysis Method*, desligue o **Automatic Analysis** (a análise é feita pelo CI);
2. rode o `-ConfigureSonarCloud` (4.6) para cadastrar o `SONAR_TOKEN`;
3. rode o CI de novo, a partir do último run da `homolog`:

```powershell
$repo = 'tiagomiele/fiap-tc-fase4-oficina-diagnostico-reparo-service'   # troque pelo serviço sem análise
$id = gh run list --repo $repo --workflow ci.yml --branch homolog --limit 1 --json databaseId --jq '.[0].databaseId'
gh run rerun $id --repo $repo
gh run watch $id --repo $repo --exit-status
```

### 19.2 BDD da Saga

```powershell
gh run list --repo tiagomiele/fiap-tc-fase4-oficina-os-service --workflow bdd.yml --branch homolog --limit 1   # completed  success
```

No GitHub, abra *fiap-tc-fase4-oficina-os-service → Actions → BDD Saga*, entre no último run da `homolog` e confira o ícone verde ✓ e, em **Artifacts**, o `bdd-report` (relatório do Cucumber). Para baixar e abrir o relatório:

```powershell
$id = gh run list --repo tiagomiele/fiap-tc-fase4-oficina-os-service --workflow bdd.yml --branch homolog --limit 1 --json databaseId --jq '.[0].databaseId'
gh run download $id --repo tiagomiele/fiap-tc-fase4-oficina-os-service --name bdd-report --dir C:iapdd-report
Get-ChildItem C:iapdd-report -Recurse -Filter *.html | Select-Object -First 1 | Invoke-Item
```

O artefato fica disponível por 90 dias. Se ele tiver expirado, rode o BDD de novo: `gh workflow run bdd.yml --repo tiagomiele/fiap-tc-fase4-oficina-os-service --ref homolog`.

## 20. Validação manual do Mercado Pago sandbox (depois do E2E verde)

> **Atalho em produção:** depois de ligar o Mercado Pago (20.1) e cadastrar o webhook (20.2), `.\scripts\pagamento-mercadopago.ps1` cria a OS até `AGUARDANDO_PAGAMENTO` e mostra o link. Pague com o comprador de teste (titular `APRO`) e confira com `.\scripts\pagamento-mercadopago.ps1 -NumeroOs OS-...` (sem `< >`). Se o primeiro pagamento for recusado, pode tentar de novo no mesmo link: a aprovação posterior confirma a OS.

**Quando:** só depois que o run do E2E do passo 17 terminar com sucesso. O `gh run watch` do 17 termina com `✓ ... completed with 'success'`. No GitHub (*Actions → E2E*), o run aparece com o ícone verde ✓. Para conferir depois, rode `gh run list --repo tiagomiele/fiap-tc-fase4-oficina-os-service --workflow e2e.yml --limit 1`, que deve mostrar `completed  success`. Se aparecer `failure` (ícone vermelho ✗), não siga para esta seção: corrija o E2E antes. O E2E e os cenários do 16 usam `PAYMENT_PROVIDER=fake` e o `/pagamentos/{n}/simular`. Com o Mercado Pago ligado, esse endpoint não existe. Por isso a ordem é: 16 → 17 → **20** → voltar ao `fake` (20.6). O Newman não testa o Mercado Pago real, porque no Checkout Pro uma pessoa precisa abrir o link e pagar. Esta seção pode ser repetida quantas vezes quiser.

**Valor máximo de R$ 10 por pagamento:** o valor cobrado é o total do orçamento, ou seja, a soma dos serviços e peças lançados na OS. As OS do 16 custam R$ 150 ou mais. Aqui, cada OS leva só um serviço de **R$ 5,00**, sem peça, e a função do 20.3 para antes de aprovar se o orçamento passar de R$ 10. Uma rodada completa (20.4 + 20.5) aprova 2 pagamentos de R$ 5,00. O pagamento recusado não é cobrado.

**Pré-requisitos**
- credenciais de teste do vendedor (4.8): Access Token, Public Key e assinatura secreta do webhook;
- conta de teste compradora `oficina-cliente` (usuário, senha e código de verificação ficam no card da conta, em *Contas de teste*);
- Learner Lab ativo e, na mesma janela do PowerShell, as variáveis do 15 (`$OS`, `$DR`, `$OP`, `$GW`), o login do 16.1 (`$HF`, `J`, `$CT`) e o cadastro do 16.2 (`$cli`, `$Placa`, `$Cpf`).

### 20.1 Ligar o Mercado Pago

Rode na pasta do clone do OS Service (a seção 5 usa `C:\fiap\fase4` como exemplo; use a sua). O bloco não depende de variáveis de outras seções, então funciona numa janela nova do PowerShell. Antes, copie o bloco `[default]` do AWS Details: o script renova as credenciais AWS e lê esse bloco da área de transferência (sem ele, pede as chaves uma por uma).

```powershell
.\scripts\configure-environment.ps1 -Environment homolog -ConfigureMercadoPago -UseAwsAcademyDisposableProductionProfile   # na 1ª vez pede Access Token, Public Key e assinatura do webhook

$Repo = 'tiagomiele/fiap-tc-fase4-oficina-orcamento-pagamento-service'
gh workflow run cd.yml --repo $Repo --ref homolog
Start-Sleep 10
$id = gh run list --repo $Repo --workflow cd.yml --branch homolog --event workflow_dispatch --limit 1 --json databaseId -q '.[0].databaseId'
Write-Host "Run: https://github.com/$Repo/actions/runs/$id"
gh run watch $id --repo $Repo --exit-status
if ($LASTEXITCODE -ne 0) { throw "CD do Orçamento e Pagamento falhou. Pare e analise o log." }

aws eks update-kubeconfig --region us-west-2 --name oficina-homolog
kubectl get configmap orcamento-pagamento-config -n oficina-orcamento-pagamento-homolog -o jsonpath='{.data.PAYMENT_PROVIDER} {.data.MERCADOPAGO_SANDBOX} {.data.MERCADOPAGO_NOTIFICATION_URL}'
```

Esperado: `mercadopago true <GW>/webhooks/mercadopago`. Se aparecer `fake`, o CD não rodou depois do configure. O `-UseAwsAcademyDisposableProductionProfile` mantém o RDS de produção destruível quando a produção também está no ar; sem produção, ele não muda nada. O `-ConfigureMercadoPago` grava os 3 valores em `C:\fiap-secrets\shared\mercadopago-*.clixml` e reaproveita nas próximas vezes. Se você renovar uma credencial no painel, apague só o arquivo dela, por exemplo `Remove-Item C:\fiap-secrets\shared\mercadopago-access-token.clixml`, e rode o bloco de novo.

### 20.2 Cadastrar o webhook no painel

```powershell
"$GW/webhooks/mercadopago"   # mesma URL do output mercadopago_webhook_url do Auth
```

No painel (https://www.mercadopago.com.br/developers/panel/app → sua aplicação → *Webhooks → Configurar notificações*), no **modo de teste**: cole essa URL no lugar do `https://example.com`, marque só o evento **Pagamentos (legacy)** (é o `type=payment`; não marque *Order*) e salve. Não gere uma assinatura nova. Se gerar, apague `C:\fiap-secrets\shared\mercadopago-webhook-secret.clixml` e refaça o 20.1.

A URL muda sempre que a homologação é recriada (API Gateway novo). Depois de cada recriação, refaça este passo.

### 20.3 Serviço de R$ 5,00 e função auxiliar

As 2 primeiras linhas renovam o token do funcionário (`$HF`), que já pode ter vencido desde o 16.1 (OS Service `403` e Diagnóstico e Reparo `401`).

```powershell
$login = Invoke-RestMethod -Method Post "$OS/auth/login" -ContentType $CT -Body (J @{ email='admin@oficina.local'; senha=$AdminPwd; validadeMinutos=240 })
$HF = @{ Authorization = "Bearer $($login.accessToken)" }
$tc = Invoke-RestMethod -Method Post "$GW/auth/cpf" -ContentType $CT -Body (J @{ cpf=$Cpf })
$HC = @{ Authorization = "Bearer $($tc.accessToken)" }
$servMp = Invoke-RestMethod -Method Post "$OS/servicos" -Headers $HF -ContentType $CT -Body (J @{ nome='Teste Mercado Pago'; descricao='Teste sandbox'; precoBase=5.00 })

function New-OsMercadoPago {   # OS com 1 serviço de R$ 5,00, sem peça, até AGUARDANDO_PAGAMENTO
  $o = Invoke-RestMethod -Method Post "$OS/ordens-servico" -Headers ($HF + @{ 'X-Request-Id'="hml-mp-$(Get-Random)" }) -ContentType $CT -Body (J @{ idCliente=$cli.idCliente; placa=$Placa; descricaoProblema='Teste Mercado Pago' })
  $n = $o.numero
  Start-Sleep 5
  Invoke-RestMethod -Method Post "$DR/execucoes/$n/iniciar-diagnostico" -Headers $HF | Out-Null
  Invoke-RestMethod -Method Post "$OS/ordens-servico/$n/servicos" -Headers $HF -ContentType $CT -Body (J @{ idServicoSku=$servMp.idServico; quantidade=1 }) | Out-Null
  Invoke-RestMethod -Method Post "$DR/execucoes/$n/concluir-diagnostico" -Headers $HF -ContentType $CT -Body (J @{ diagnostico='Teste Mercado Pago' }) | Out-Null
  Start-Sleep 5
  $valor = [decimal](Invoke-RestMethod "$GW/orcamentos/$n" -Headers $HC).valorTotal
  if ($valor -gt 10) { throw "Orçamento da $n em R$ $valor (acima de R$ 10). Não aprovado; confira os itens da OS." }
  Invoke-RestMethod -Method Post "$GW/orcamentos/$n/aprovar" -Headers $HC | Out-Null
  Start-Sleep 5
  Invoke-RestMethod -Method Post "$DR/execucoes/$n/iniciar-reparo"  -Headers $HF | Out-Null
  Invoke-RestMethod -Method Post "$DR/execucoes/$n/concluir-reparo" -Headers $HF | Out-Null
  Start-Sleep 5
  Write-Host "$n -> $((Invoke-RestMethod "$OS/ordens-servico/$n" -Headers $HF).status) (R$ $valor)"   # AGUARDANDO_PAGAMENTO
  $n
}
```

Use também o `Show-Saga` da preparação do 16.4. Se o `$GW` responder `401`, renove o `$HC` com as 2 primeiras linhas acima.

**Dados para o Checkout Pro** (cartões de teste do Mercado Pago Brasil, valem só no sandbox):

| Campo | Valor |
|---|---|
| Cartão Mastercard | `5031 4332 1540 6351` |
| Cartão Visa (alternativo) | `4235 6477 2802 5682` |
| Código de segurança | `123` |
| Validade | `11/30` |
| Nome do titular | `APRO` = aprovado; `OTHE` = recusado |
| CPF do titular | `12345678909` |

### 20.4 Pagamento aprovado (`APRO`)

```powershell
$tc = Invoke-RestMethod -Method Post "$GW/auth/cpf" -ContentType $CT -Body (J @{ cpf=$Cpf })   # token do cliente vale 15 min
$HC = @{ Authorization = "Bearer $($tc.accessToken)" }
$P = New-OsMercadoPago
$pg = Invoke-RestMethod "$GW/pagamentos/$P" -Headers $HC
$pg | Format-List status, provedor, valor, linkPagamento   # PENDENTE | mercadopago | 5.00 | https://sandbox.mercadopago.com.br/...
$pg.linkPagamento | Set-Clipboard
```

1. Abra uma **janela anônima** (Ctrl+Shift+N), cole o link e entre com o **`oficina-cliente`** (o `TESTUSER...` do card, a senha e, se pedir, o código de verificação).
2. Escolha cartão de crédito e use o **Mastercard** da tabela com o titular **`APRO`**. Pague em 1x.
3. Feche a janela e, depois de uns 30 s, confira:

No checkout, o nome do titular é que define o resultado: com outro nome (ex.: `Jose da Silva`) ou sem login do comprador, o sandbox mostra "Ops, ocorreu um erro". O erro é do Checkout do Mercado Pago, não do serviço; a cobrança continua `PENDENTE` e o mesmo link pode ser reaberto.

```powershell
$tc = Invoke-RestMethod -Method Post "$GW/auth/cpf" -ContentType $CT -Body (J @{ cpf=$Cpf })   # token do cliente vale 15 min
$HC = @{ Authorization = "Bearer $($tc.accessToken)" }
Show-Saga $P                                           # PAGA | PAGO | REPARO_CONCLUIDO
Invoke-RestMethod "$GW/pagamentos/$P" -Headers $HC     # APROVADO; comprovante = id do pagamento no Mercado Pago
```

### 20.5 Pagamento recusado (`OTHE`) e nova tentativa

```powershell
$tc = Invoke-RestMethod -Method Post "$GW/auth/cpf" -ContentType $CT -Body (J @{ cpf=$Cpf })   # token do cliente vale 15 min
$HC = @{ Authorization = "Bearer $($tc.accessToken)" }
$R = New-OsMercadoPago
$pg = Invoke-RestMethod "$GW/pagamentos/$R" -Headers $HC
$pg.linkPagamento | Set-Clipboard
```

Na janela anônima, com o `oficina-cliente`, pague com o titular **`OTHE`** (se o Checkout oferecer o cartão salvo do 20.4, escolha *outro cartão* para digitar o nome). O Checkout mostra o pagamento recusado. **Não tente de novo nessa mesma página**: feche a janela, porque a nova tentativa sai pela API.

> **Limitação do sandbox (observada na homologação):** com os titulares de recusa (`OTHE`, `FUND`), o Checkout Pro do sandbox pode cair em "Ops, ocorreu um erro" (URL `.../fatal/...`) sem criar pagamento nenhum: nada aparece em *Atividade* do vendedor nem do comprador, o webhook não é chamado e a cobrança continua `PENDENTE`. Não é erro do serviço. Nesse caso:
> 1. confira com o bloco abaixo (até o `status`): se vier `PENDENTE`, **não** chame o `nova-tentativa` (responde `409 Não é possível ir de PAGAMENTO_PENDENTE para PAGAMENTO_PENDENTE`, porque só existe nova tentativa depois de uma recusa);
> 2. a recusa e a nova tentativa ficam validadas pelo `fake` (cenário de pagamento recusado do 16 e E2E do 17); o Mercado Pago real fica validado pelo 20.4;
> 3. opcional: pague o mesmo link com `APRO` (mais R$ 5) e confira `PAGA` com `Show-Saga $R`; depois siga para o 20.6.

```powershell
Start-Sleep 30
$tc = Invoke-RestMethod -Method Post "$GW/auth/cpf" -ContentType $CT -Body (J @{ cpf=$Cpf })   # token do cliente vale 15 min
$HC = @{ Authorization = "Bearer $($tc.accessToken)" }
Show-Saga $R                                           # AGUARDANDO_PAGAMENTO | PAGAMENTO_RECUSADO | REPARO_CONCLUIDO
(Invoke-RestMethod "$GW/pagamentos/$R" -Headers $HC).status    # RECUSADO
$nt = Invoke-RestMethod -Method Post "$GW/pagamentos/$R/nova-tentativa" -Headers $HC
$nt.status                                             # PENDENTE (cobrança nova, link novo)
$nt.linkPagamento | Set-Clipboard
```

Abra o link novo numa janela anônima, pague com o titular **`APRO`** e confira:

```powershell
Start-Sleep 30
$tc = Invoke-RestMethod -Method Post "$GW/auth/cpf" -ContentType $CT -Body (J @{ cpf=$Cpf })   # token do cliente vale 15 min
$HC = @{ Authorization = "Bearer $($tc.accessToken)" }
Show-Saga $R                                           # PAGA | PAGO | REPARO_CONCLUIDO
Invoke-RestMethod "$GW/ordens-servico/$R/historico" -Headers $HC | ConvertTo-Json -Depth 5   # recusa e, depois, confirmação do pagamento
```

Logs do webhook (opcional):

```powershell
kubectl logs -n oficina-orcamento-pagamento-homolog deploy/oficina-orcamento-pagamento --tail=200 | Select-String -Pattern 'Mercado Pago|Cobran|webhook'
```

**Se der errado**

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| OS fica em `AGUARDANDO_PAGAMENTO` com pagamento `PENDENTE` e nada no log | o webhook não chegou | confira a URL do 20.2 (API Gateway da recriação atual) e o evento *Pagamentos*; no painel, use *Simular notificação* |
| log `Webhook do Mercado Pago com assinatura inválida` (401) | a assinatura do painel não é a do `-ConfigureMercadoPago` | apague `C:\fiap-secrets\shared\mercadopago-webhook-secret.clixml` e refaça o 20.1 |
| log `Mercado Pago recusou ...: HTTP 401 invalid_access_token` | token renovado ou errado | apague `mercadopago-access-token.clixml` e refaça o 20.1 |
| o link abre, mas o Checkout diz que uma das partes é de teste ou não deixa pagar | credencial `APP_USR-` de conta de teste usa o link normal | `gh variable set MERCADOPAGO_SANDBOX --repo tiagomiele/fiap-tc-fase4-oficina-orcamento-pagamento-service --env homolog --body false` e rode o CD do O&P |
| `New-OsMercadoPago` para com "acima de R$ 10" | outro serviço ou peça entrou na OS | não pague; abra outra OS com a função |

### 20.6 Voltar ao `fake` (obrigatório ao terminar)

O `PAYMENT_PROVIDER` fica gravado no GitHub Environment e sobrevive ao destroy. O `configure-environment.ps1` sem `-ConfigureMercadoPago` não volta ao `fake` sozinho. O bloco não depende de variáveis de outras seções, então funciona numa janela nova do PowerShell.

```powershell
$Repo = 'tiagomiele/fiap-tc-fase4-oficina-orcamento-pagamento-service'
gh variable set PAYMENT_PROVIDER --repo $Repo --env homolog --body fake
gh workflow run cd.yml --repo $Repo --ref homolog
Start-Sleep 10
$id = gh run list --repo $Repo --workflow cd.yml --branch homolog --event workflow_dispatch --limit 1 --json databaseId -q '.[0].databaseId'
Write-Host "Run: https://github.com/$Repo/actions/runs/$id"
gh run watch $id --repo $Repo --exit-status
if ($LASTEXITCODE -ne 0) { throw "CD do Orçamento e Pagamento falhou. Pare e analise o log." }

aws eks update-kubeconfig --region us-west-2 --name oficina-homolog
kubectl get configmap orcamento-pagamento-config -n oficina-orcamento-pagamento-homolog -o jsonpath='{.data.PAYMENT_PROVIDER}'   # fake
```

Esperado: `fake`. Se aparecer `mercadopago`, o CD não rodou depois do `gh variable set`. Com o `fake` de volta, o E2E do 17 volta a passar. Para validar o Mercado Pago de novo, repita a partir do 20.1.

## 21. Sessão do Lab expirou ou foi reiniciada (`ExpiredToken`, `voc-cancel-cred`)

A infraestrutura (EKS, RDS, DynamoDB, SQS) e os states no HCP **continuam existindo**. Não recomece do zero: renove as credenciais, confira os nós e repita **só** o passo que falhou.

### 21.1 Renovar a sessão e as credenciais

1. Pare de disparar workflows.
2. Learner Lab → **Start Lab** → espere o indicador verde → **AWS Details → AWS CLI → Show** → copie o bloco `[default]` inteiro.
3. No PowerShell:

```powershell
Set-Location C:\fiap\fase4\fiap-tc-fase4-oficina-os-service
git pull --ff-only
Set-ExecutionPolicy -Scope Process Bypass -Force
.\scripts\configure-environment.ps1 -Environment homolog
```

O script atualiza as credenciais no computador, no Variable Set do HCP e nos GitHub Environments dos 6 repos. Os tokens (HCP, sincronização, SonarCloud, New Relic) já estão salvos e **não** são pedidos de novo. Esperado no fim: `Configuração automática concluída para homolog.`

Confira a linha `AWS validada: conta ...`: tem que ser a **mesma conta** de antes (ex.: `949086004048`). Se for outra, pare: a infraestrutura não está nela.

### 21.2 Conferir se os nós do EKS voltaram

Quando a sessão acaba, o Lab para as instâncias EC2. Na volta, os nós podem não entrar no cluster. Sintomas: pods `Pending` há muito tempo, LoadBalancer que fecha a conexão (`socket hang up` no Newman, `A conexão subjacente estava fechada` no PowerShell) e o `pg-tunnel` que não fica pronto.

Os comandos abaixo valem para os 2 ambientes; escolha na 1ª linha:

```powershell
$amb = 'homolog'                     # ou 'production'
$c   = "oficina-$amb"
aws eks update-kubeconfig --region us-west-2 --name $c
kubectl get nodes
```

| Resultado | O que fazer |
|---|---|
| 2 nós `Ready` | siga para o 21.3 |
| `No resources found` logo após o Start Lab | espere 5 min e rode `kubectl get nodes` de novo |
| `No resources found` depois de 10 min | recrie os nós (abaixo) |

Diagnóstico (só leitura):

```powershell
aws eks describe-nodegroup --region us-west-2 --cluster-name $c --nodegroup-name "$c-nodes" --query 'nodegroup.[status,scalingConfig,health.issues]' --output json
aws ec2 describe-instances --region us-west-2 --filters Name=tag:eks:cluster-name,Values=$c Name=instance-state-name,Values=pending,running,stopped --query 'Reservations[].Instances[].[InstanceId,State.Name,LaunchTime,MetadataOptions.HttpPutResponseHopLimit]' --output table
```

- instâncias `stopped`: ligue-as com `aws ec2 start-instances --region us-west-2 --instance-ids <id1> <id2>` e espere 5 min;
- instâncias `running`, mas sem nós no `kubectl`: veja o motivo no console de uma delas:

```powershell
aws ec2 get-console-output --region us-west-2 --instance-id <id> --latest --output text | Select-String -Pattern 'explicit deny|fatal|Failed' | Select-Object -Last 6
```

Se aparecer `ec2:DescribeInstances with an explicit deny in a service control policy` e `Failed to start nodeadm-config.service`, o Academy bloqueou o bootstrap do nó durante o restart do Lab (acontece logo depois do Start Lab e é temporário). O `nodeadm` não tenta de novo sozinho; **recrie os nós**:

```powershell
$ids = aws ec2 describe-instances --region us-west-2 --filters Name=tag:eks:cluster-name,Values=$c Name=instance-state-name,Values=running,stopped --query 'Reservations[].Instances[].InstanceId' --output text
aws ec2 terminate-instances --region us-west-2 --instance-ids $ids.Split() | Out-Null
Start-Sleep 360
kubectl get nodes      # esperado: 2 nós Ready
```

O node group cria 2 instâncias novas (5–8 min). Os pods dos serviços voltam sozinhos quando os nós ficam `Ready`; confira com `kubectl get pods -A`.

Se depois da recriação o `explicit deny` continuar, o bloqueio é permanente na conta: pare e peça ajuda (é preciso trocar o tipo de AMI dos nós).

### 21.3 Retomar

- Repita **só** o workflow ou passo que falhou (os anteriores verdes continuam válidos).
- Se os serviços já estavam no ar, confira os 3 health (seção 15) antes de seguir; os LoadBalancers mantêm o mesmo hostname.
- A sessão dura cerca de 4 h. Antes de um passo longo (EKS, RDS), confira o tempo restante no Lab; se faltar pouco, renove antes (21.1).

## 22. Resumo operacional mínimo (reconstrução com tudo já preparado)

Os passos 2 a 11 abaixo são executados de uma vez pelo `.\scripts\provision-environment.ps1 -Environment homolog` ([Bootstrap de uma única vez](bootstrap-automatizado-fase4.md)).

```text
1.  Start Lab e copiar [default]
2.  configure-environment.ps1 -Environment homolog -ConfigureSonarCloud -ConfigureNewRelic
3.  Kubernetes deploy-homolog
4.  Database os → orcamento-pagamento → diagnostico-reparo
5.  configure-environment.ps1
6.  test-labrole-access.ps1
7.  Auth terraform-deploy
8.  configure-environment.ps1
9.  CD OS → CD Diagnóstico e Reparo → CD Orçamento e Pagamento
10. Auth terraform-deploy (2ª vez)
11. Kubernetes deploy-homolog (2ª vez) + configure-environment.ps1
12. Validação manual (16) e E2E (17)
13. Mercado Pago sandbox (20.1 a 20.5) e volta ao fake (20.6)
```

### 22.1 Destruir a homologação para recomeçar do zero

**Automático (recomendado):** primeiro renove as credenciais da AWS. No AWS Academy, clique em **Start Lab**, mesmo com o Lab ativo, vá em **AWS Details → AWS CLI → Show** e copie o bloco `[default]`. Logo em seguida, rode o bloco abaixo, na ordem. O script executa as etapas 1 a 8, uma de cada vez, e espera cada uma terminar:

```powershell
Set-Location C:\fiap\fase4\fiap-tc-fase4-oficina-os-service
git pull
Set-ExecutionPolicy -Scope Process Bypass -Force
.\scripts\configure-environment.ps1 -Environment homolog                                          # lê o [default] copiado
aws sqs list-queues --region us-west-2 --queue-name-prefix oficina-homolog- --query 'length(QueueUrls)'   # um número, sem AccessDenied
.\scripts\destroy-environment.ps1 -Environment homolog                                            # pede para digitar 'homolog'
```

Se uma etapa falhar, corrija a causa e retome de onde parou. Rode um comando por vez, sem colar duas vezes na mesma linha:

```powershell
.\scripts\destroy-environment.ps1 -Environment homolog -StartAt lambda-enis
```

O `aws sqs list-queues` serve para confirmar que a credencial vale de verdade. O `aws sts get-caller-identity` continua respondendo mesmo depois que o Lab cancela a credencial (`voc-cancel-cred`), então ele não serve para essa checagem. A etapa `lambda-enis` costuma ficar de 10 a 40 min mostrando `ENI(s) de Lambda ainda na VPC; aguardando...`. Isso é normal: deixe o script esperar.

O script aceita `homolog` e `production` (produção só com o perfil descartável do AWS Academy; o perfil endurecido, com `deletion_protection`, é recusado) e só mexe nos workspaces `oficina-fase4-*-<ambiente>` do projeto da Fase 4. Ele desliga os gates de deploy (`TF_APPLY_ENABLED`, `ENABLE_TERRAFORM_APPLY`, `DEPLOY_ENABLED`) antes de destruir, pula os workspaces que já estão vazios e pode ser rodado de novo sem risco. Etapas, na ordem: `auth` → `namespaces` → `newrelic` → `db-diagnostico-reparo` → `db-orcamento-pagamento` → `db-os` → `lambda-enis` → `kubernetes` → `verificacao`.

A destruição segue a ordem inversa da criação. Os bancos e o Auth usam a VPC do workspace Kubernetes. Os 3 LoadBalancers foram criados pelo Kubernetes, fora do Terraform. Por isso o EKS/VPC é sempre o **último** a ser destruído.

**0. Credenciais.** Start Lab, copie o `[default]` e rode `configure-environment.ps1 -Environment homolog`. O destroy no HCP usa o Variable Set `aws-academy-credentials-fase4`, que precisa estar com credenciais válidas.

**1. Auth/API Gateway:** destrua o workspace `oficina-fase4-auth-homolog`. As Lambdas ficam na VPC, e a AWS leva de 10 a 40 min para liberar as interfaces de rede (ENIs) delas.

**2. Apps no EKS** (remove os 3 LoadBalancers e os security groups deles):

```powershell
aws eks update-kubeconfig --region us-west-2 --name oficina-homolog
kubectl delete namespace oficina-homolog oficina-orcamento-pagamento-homolog oficina-diagnostico-reparo-homolog --wait=true --timeout=10m
aws elb describe-load-balancers --region us-west-2 --query 'LoadBalancerDescriptions[].LoadBalancerName'   # vazio
```

**3. New Relic:** `oficina-fase4-newrelic-homolog` (dashboard, alertas e Synthetic).

**4 a 6. Bancos e filas:** `oficina-fase4-db-diagnostico-reparo-homolog`, depois `oficina-fase4-db-orcamento-pagamento-homolog` e por fim `oficina-fase4-db-os-homolog`. Em homolog, `deletion_protection = false` e `skip_final_snapshot = true`, então não fica snapshot. Cada RDS leva de 5 a 10 min.

**7. Kubernetes/EKS/VPC:** antes, confirme que as ENIs das Lambdas foram liberadas. Depois, destrua o workspace `oficina-fase4-kubernetes-homolog`, que leva de 15 a 20 min.

```powershell
aws ec2 describe-network-interfaces --region us-west-2 --filters "Name=interface-type,Values=lambda" --query 'length(NetworkInterfaces)'   # 0
```

**Como destruir cada workspace:** em `https://app.terraform.io/app/oficina-fiap-soat-fase-2/workspaces/<workspace>/settings/delete`, vá em *Queue destroy plan* e depois em *Confirm & apply*. Espere terminar (`Applied`) antes de passar ao próximo. Não apague os workspaces nem o Variable Set, porque o bootstrap reaproveita os dois.

**8. Conferência final:**

```powershell
aws eks list-clusters --region us-west-2                                                  # sem oficina-homolog
aws rds describe-db-instances --region us-west-2 --query 'DBInstances[].DBInstanceIdentifier'
aws dynamodb list-tables --region us-west-2                                               # sem oficina-homolog-execucoes
aws sqs list-queues --region us-west-2 --queue-name-prefix oficina-homolog-               # vazio
```

**Troubleshooting do destroy**
- **O destroy do Kubernetes falha em subnet ou security group com `DependencyViolation`:** ainda há um LoadBalancer ou uma ENI de Lambda na VPC. Repita o passo 2, espere as ENIs e faça um novo *Queue destroy plan*. Não apague recursos pelo console.
- **O run de destroy falha com `ExpiredToken` ou `voc-cancel-cred`:** a sessão do Lab expirou no meio. Renove as credenciais (passo 0) e faça um novo *Queue destroy plan* no mesmo workspace. O destroy continua de onde parou.
- **O destroy do Auth (ou outro) falha no plan com `AccessDenied ... voc-cancel-cred`:** o Variable Set do HCP está com credenciais de uma sessão antiga do Lab. Renove as credenciais (Start Lab, copiar `[default]`, `configure-environment.ps1`) e retome com `-StartAt <etapa>`.
- **O destroy do New Relic falha no plan com `408 response returned`:** foi um timeout da API do New Relic. Retome com `-StartAt newrelic`. Se o erro se repetir, espere uns 5 min antes de tentar de novo.
- **No novo apply, `ResourceAlreadyExistsException` em um log group do CloudWatch:** o log group foi recriado pela AWS depois do destroy. Apague-o (`aws logs delete-log-group --region us-west-2 --log-group-name <nome>`) e rode o workflow de novo.

**Para recriar:** comece pela seção 6 (sessão AWS Academy) e siga o resumo da seção 22. A seção 4 (preparação permanente) não precisa ser repetida.

## 23. Produção (somente depois da homologação aprovada)

Mesma cadeia, com `main` e o Environment `production`:

```text
Kubernetes deploy-production.yml → Database (3, ref main) → script → Auth (main) → script
→ CD dos 3 (main) → Auth (main) de novo → Kubernetes deploy-production.yml de novo → E2E environment=production
```

1. Nova sessão do Lab e, **antes de qualquer merge na `main`** (cada merge já dispara o deploy de produção e falharia sem o Environment configurado):

```powershell
Set-Location C:\fiap\fase4\fiap-tc-fase4-oficina-os-service
git pull --ff-only
Set-ExecutionPolicy -Scope Process Bypass -Force
.\scripts\configure-environment.ps1 -Environment production -ValidateOnly
# Academy: perfil descartável (RDS igual à homologação: sem Multi-AZ, sem deletion protection, sem snapshot final)
.\scripts\configure-environment.ps1 -Environment production -ConfigureSonarCloud -ConfigureNewRelic -UseAwsAcademyDisposableProductionProfile
# Produção endurecida (Multi-AZ, deletion protection, snapshot final; não é destruída pelo destroy-environment.ps1):
# .\scripts\configure-environment.ps1 -Environment production -ConfigureSonarCloud -ConfigureNewRelic
```

   **Atenção:** toda execução do script, até com `-Environment homolog`, regrava as variáveis dos workspaces de produção. Se você escolheu o perfil descartável, use `-UseAwsAcademyDisposableProductionProfile` em **todas** as execuções; sem ele, os RDS de produção voltam para Multi-AZ, deletion protection e snapshot final no próximo apply.

   O `-ValidateOnly` não grava credenciais: o `aws` do terminal só deixa de dar `ExpiredToken` depois da execução real. Em seguida confirme que a conta está livre (as duas listas vazias ou só com recursos de produção):

```powershell
aws eks list-clusters --region us-west-2
aws rds describe-db-instances --region us-west-2 --query 'DBInstances[].DBInstanceIdentifier'
```

2. PRs `homolog → main` nos 6 repos, revisados; merge **um por vez**, na ordem Kubernetes → Database → Auth → OS → Diagnóstico e Reparo → Orçamento e Pagamento, rodando o script (`-Environment production -UseAwsAcademyDisposableProductionProfile`) depois dos bancos e depois do 1º Auth, como na homologação.
3. Se quiser aprovação humana, em cada repo *Settings → Environments → production → Required reviewers* (o script não configura revisores).
4. Use `Invoke-Workflow ... -Ref main` (e `deploy-production.yml` no Kubernetes). Em produção prefira health, Swagger e operações de leitura; finalize com o E2E `environment=production` (seção 17.1).

Atenção ao orçamento do Lab: homolog e produção juntos dobram EKS, RDS e LoadBalancers.

## 24. Critérios de parada e troubleshooting

Não avance se:
- `aws sts get-caller-identity` falha ou não é `assumed-role/voclabs/`;
- algum run termina vermelho/cancelado ou o plan mostra destroy inesperado;
- o Kubernetes não produz VPC, subnets privadas e security group;
- algum banco não produz `jdbc_url`/`table_name`/`queue_url`;
- os 3 gates `DEPLOY_ENABLED` não ficam `true`;
- a LabRole falha no teste da seção 10;
- algum serviço falha em rollout, smoke ou LoadBalancer;
- o 2º apply do Auth não publica as rotas do O&P.

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| HCP `401`/`404` no projeto `prj-UTzD6p6PqRVEr27C` | token errado/expirado ou time sem Admin no projeto | 4.4; apagar `C:\fiap-secrets\shared\hcp-terraform-token-fase4.clixml` e rodar o script |
| `O workspace ... pertence a outro projeto` | workspace com o mesmo nome fora do projeto da Fase 4 | mover para o projeto da Fase 4 pela UI ou apagar; o script não reutiliza |
| HCP `400` ao criar o Variable Set | script antigo enviando acentos em ISO-8859-1 (PowerShell 5.1) | `git pull` na `homolog` (correção do [#9](https://github.com/tiagomiele/fiap-tc-fase4-oficina-os-service/pull/9)) e rodar de novo |
| `Secret names must not start with GITHUB_` | script antigo com `GITHUB_SYNC_TOKEN` | `git pull` na `homolog` (secret agora é `REPO_SYNC_TOKEN`) e rodar de novo |
| `Where-Object : A propriedade 'name' não foi encontrada` nas regras de proteção | script antigo no PowerShell 5.1 | `git pull` na `homolog` (correção do [#11](https://github.com/tiagomiele/fiap-tc-fase4-oficina-os-service/pull/11)) |
| `gh api --method POST .../rulesets` → `HTTP 422` | regra já existe e o 5.1 leu o nome com acento errado | rodar com `-SkipBranchProtection` até o merge do [#13](https://github.com/tiagomiele/fiap-tc-fase4-oficina-os-service/pull/13); depois, sem o parâmetro |
| `ExpiredToken` / `voc-cancel-cred` | Lab expirou | seção 21 |
| `kubectl get nodes` → `No resources found` depois do Start Lab | nós parados ou bootstrap bloqueado pelo Academy (`explicit deny` no console da instância) | seção 21.2: recriar os nós |
| Pods `Pending` há muito tempo, `socket hang up` no E2E ou `A conexão subjacente estava fechada` no health | cluster sem nodes | `kubectl get nodes`; se vazio, seção 21.2 com o `$amb` do ambiente |
| `TF_APPLY_ENABLED`/`ENABLE_TERRAFORM_APPLY` diferente de `true` | Environment incompleto | rodar o script e conferir o Environment `homolog` |
| `Deploy bloqueado: DEPLOY_ENABLED` | script não rodou depois dos bancos | seção 9 e repetir o CD |
| `Configure no GitHub Environment homolog: ...` | variável/secret faltando | rodar o script; se for `REPO_SYNC_TOKEN`, refazer 4.5 |
| Sincronização 401/403 entre repos | `REPO_SYNC_TOKEN` expirado ou sem *Environments: Read and write* nos 6 repos | gerar outro, apagar `github-sync-token.clixml`, rodar o script |
| Pod `ImagePullBackOff` | pacote GHCR privado | GitHub → Packages → *Change visibility: Public* |
| Pod `CrashLoopBackOff` no OS/O&P | JDBC, senha ou security group | `kubectl logs -n <ns> deploy/<nome> --previous`; script e redeploy |
| Comando não chega no D&R/O&P | URL de fila ou LabRole | seção 10; `kubectl logs` procurando `Falha ao processar` |
| Mensagem na DLQ | 5 falhas seguidas no consumidor | `aws sqs receive-message --queue-url <dlq>` e analisar o log do serviço |
| API Gateway 503 com o serviço saudável | integração aponta para LoadBalancer antigo | repetir o Auth depois do CD |
| Rotas do cliente 404 no gateway | Auth sem o 2º apply | seção 13 |
| 403 no gateway | JWT expirado (15 min) ou cache do authorizer (5 min) | refazer `POST /auth/cpf` |
| Synthetic ausente | Kubernetes não foi rodado de novo | seção 14 |
| Observabilidade: `Monitor name already exists` | monitor da Fase 3 com o mesmo nome na conta New Relic | já corrigido na `homolog` ([kubernetes-infra#5](https://github.com/tiagomiele/fiap-tc-fase4-oficina-kubernetes-infra/pull/5)): o monitor da Fase 4 é `oficina-fase4-homolog-healthcheck`; rode o `deploy-homolog.yml` de novo |
| `$GW/...` devolve `{"message":"Internal Server Error"}` na 1ª chamada autenticada | cold start do Lambda Authorizer (Java + agente New Relic) passou dos 10 s do API Gateway; o log de acesso `/aws/apigateway/oficina-auth-homolog` mostra `responseLatency = 10001` e `integrationStatus = -` | repita a chamada; corrigido com o Authorizer em 1024 MB ([auth-serverless#4](https://github.com/tiagomiele/fiap-tc-fase4-oficina-auth-serverless/pull/4)) |
| Log do serviço: `Invalid character found in method name [0x16 0x03 ...]` | cliente da internet tentando HTTPS (TLS) no LoadBalancer HTTP público | inofensivo (nível INFO); ignore |
| OS Service `403` e Diagnóstico e Reparo `401` em chamadas com `$HF` que antes funcionavam | token do funcionário expirou (padrão 60 min) | refaça o login do 16.1 (já pede `validadeMinutos=240`) e recomece o cenário com uma OS nova |
| `$GW/...` responde `401` em chamadas que antes funcionavam | token do cliente (`$HC`) expirou (`jwt_ttl_seconds = 900`, 15 min) | refaça o login por CPF (`$tc = Invoke-RestMethod -Method Post "$GW/auth/cpf" ...` e `$HC = ...`) e repita a chamada |
| New Relic sem dados | janela de ingestão | gerar tráfego e esperar 5–15 min |
| Console da AWS não mostra as filas SQS nem a tabela DynamoDB | região errada no console | selecione **Estados Unidos (Oregon) us-west-2** no canto superior direito |
| Fila sempre com **Mensagens disponíveis** = `0` | comportamento normal: o consumidor processa e apaga a mensagem em segundos | 16.5 (onde ver o histórico) e 16.6 (cenário com a mensagem parada) |
| `namespaces "oficina-diagnostico-reparo" not found` | namespace sem o sufixo do ambiente | use `oficina-diagnostico-reparo-homolog` (tabela da seção 15) |
| `namespaces "oficina-production" not found` | `kubectl` apontando para o cluster de homologação | `aws eks update-kubeconfig --region us-west-2 --name oficina-production` |
| `namespaces "oficina-homolog" not found` ou URL `http://` vazia | `kubectl` apontando para o cluster de produção | `aws eks update-kubeconfig --region us-west-2 --name oficina-homolog` |
| `pg-tunnel` em `Pending`, DBeaver com senha recusada ou `Connection refused` | nodes cheios, senha antiga ou conexão de outro ambiente | [Database Infra, seção 6.3](https://github.com/tiagomiele/fiap-tc-fase4-oficina-database-infra#63-ver-os-dados-do-rds-dbeaver-ou-psql) |

Tempos típicos: EKS 20–40 min; cada RDS 10–25 min; DynamoDB 2–5 min; Auth 5–15 min; cada CD 8–15 min; Kubernetes (2º run) 5–20 min. Total: 2h30 a 3h30.

## 25. Matriz de sincronização

| Produtor | Output | Consumidor | Quem sincroniza |
|---|---|---|---|
| Kubernetes | VPC, subnets privadas, security group | HCP `db-os`, `db-orcamento-pagamento`, `auth` | workflow `deploy-homolog.yml` |
| Database OS | JDBC do OS | HCP `auth` + Environment do OS | workflow `os.yml` |
| Database O&P | JDBC do O&P | Environment do O&P | **script** |
| Database D&R | tabela DynamoDB | Environment do D&R | **script** |
| 3 bancos | URLs das 3 filas | Environments dos 3 serviços | **script** |
| EKS + bancos | `DEPLOY_ENABLED=true` | Environments dos 3 serviços | **script** |
| Auth | API Gateway, `AUTH_BASE_URL`, notificações | Environment do OS | workflow `terraform-deploy.yml` |
| Auth | `API_GATEWAY_BASE_URL` | Environment do O&P | **script** |
| OS | LoadBalancer e health | HCP `auth` e `newrelic`; Environments do OS e do Kubernetes | CD do OS |
| Orçamento e Pagamento | LoadBalancer | HCP `auth` (`orcamento_pagamento_base_url`) | CD do O&P |

Por isso há 2 reaplicações (Auth e Kubernetes) e o script roda depois dos bancos e do 1º Auth.

Não incluído nesta versão: os scripts de conexão DBeaver da Fase 3 (`CONECTAR-DBEAVER-*.ps1`) não existem na Fase 4.

## 26. Destruir a infraestrutura para economizar créditos AWS

Faça isso sempre ao terminar uma demonstração. EKS, NAT Gateway, 2 RDS e 3 LoadBalancers cobram por hora, mesmo sem uso.

1. **Renove a sessão:** Start Lab → *AWS Details → AWS CLI → Show* → copie o `[default]`.
2. **Destrua, um ambiente por vez:**

```powershell
Set-Location C:\fiap\fase4\fiap-tc-fase4-oficina-os-service
git pull
Set-ExecutionPolicy -Scope Process Bypass -Force
# Homologação
.\scripts\configure-environment.ps1 -Environment homolog
.\scripts\destroy-environment.ps1 -Environment homolog                # digite 'homolog' para confirmar
# Produção (só o perfil descartável)
.\scripts\configure-environment.ps1 -Environment production -UseAwsAcademyDisposableProductionProfile
.\scripts\destroy-environment.ps1 -Environment production             # digite 'production' para confirmar
```

3. **Se uma etapa falhar** (token expirado, `408` do New Relic, ENI presa): corrija a causa e retome da etapa, por exemplo `.\scripts\destroy-environment.ps1 -Environment production -StartAt lambda-enis`. Detalhes e troubleshooting em 22.1.
4. **Confira que não sobrou nada** (troque `homolog` por `production` se for o caso):

```powershell
aws eks list-clusters --region us-west-2
aws rds describe-db-instances --region us-west-2 --query 'DBInstances[].DBInstanceIdentifier'
aws dynamodb list-tables --region us-west-2
aws sqs list-queues --region us-west-2 --queue-name-prefix oficina-homolog-
aws elb describe-load-balancers --region us-west-2 --query 'LoadBalancerDescriptions[].LoadBalancerName'
aws ec2 describe-nat-gateways --region us-west-2 --filter Name=state,Values=available --query 'NatGateways[].NatGatewayId'
```

Todas as listas devem voltar vazias. Os workspaces HCP, variáveis, secrets e o código continuam; para subir de novo, siga a seção 22 (homologação) ou 23 (produção).

