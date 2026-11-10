# Bootstrap de uma única vez (homologação ou produção)

Este roteiro sobe um ambiente inteiro da Fase 4 no AWS Academy com **um único script**, o `scripts/provision-environment.ps1`. Para apagar tudo, também há um único script, o `scripts/destroy-environment.ps1`.

O script executa, em ordem, os mesmos passos das seções 7.2 a 15 do [Bootstrap da Fase 4](bootstrap-aws-do-zero-fase4.md). Ele espera cada passo terminar antes de seguir e para no primeiro erro. O Bootstrap continua sendo a referência para entender cada passo e para o troubleshooting.

Todos os comandos são para **Windows PowerShell 5.1**.

## 1. Pré-requisitos (uma vez por computador)

- Preparação permanente da seção 4 do Bootstrap: ferramentas, `gh auth login`, os 6 repositórios clonados lado a lado em `C:\fiap\fase4`, token do HCP, `REPO_SYNC_TOKEN`, SonarCloud e New Relic.
- Na 1ª execução, o `configure-environment.ps1` pede os tokens e as senhas. Nas execuções seguintes, ele usa os valores guardados em `C:\fiap-secrets`.
- Repositórios atualizados:

```powershell
foreach ($r in 'fiap-tc-fase4-oficina-os-service','fiap-tc-fase4-oficina-database-infra') { git -C "C:\fiap\fase4\$r" pull --ff-only }
```

## 2. Subir o ambiente

1. No AWS Academy, clique em **Start Lab** e espere o indicador verde.
2. Vá em **AWS Details → AWS CLI → Show** e copie o bloco `[default]` inteiro.
3. Rode:

```powershell
Set-Location C:\fiap\fase4\fiap-tc-fase4-oficina-os-service
Set-ExecutionPolicy -Scope Process Bypass -Force
.\scripts\provision-environment.ps1
```

O script pergunta qual ambiente subir (`1` para `homolog` ou `2` para `production`) e pede para você digitar o nome dele para confirmar. Depois disso, sobe tudo sem novas perguntas, exceto as da 1ª execução do `configure-environment.ps1`. O processo leva de **1h30 a 2h**, dentro das 4h da sessão do Lab.

Para não ter a pergunta inicial, informe o ambiente direto:

```powershell
.\scripts\provision-environment.ps1 -Environment homolog
.\scripts\provision-environment.ps1 -Environment production
```

## 3. O que o script faz

| Etapa | O que faz | Seção do Bootstrap |
|---|---|---|
| `configure` | lê o `[default]` da área de transferência; grava credenciais, workspaces HCP, Environments, SonarCloud e New Relic | 7.2 |
| `kubernetes` | EKS, add-ons, New Relic e observabilidade | 8 |
| `db-os`, `db-orcamento-pagamento`, `db-diagnostico-reparo` | 2 RDS, DynamoDB e as 3 filas com DLQ, um por vez | 9 |
| `configure-bancos` | leva JDBC, tabela e filas aos serviços e confere `DEPLOY_ENABLED=true` nos 3 | 9 |
| `labrole` | testa se um pod acessa as filas SQS e o DynamoDB | 10 |
| `auth` | Lambdas, API Gateway e Authorizer (1º apply) | 11 |
| `configure-auth` | leva a URL do API Gateway ao Orçamento e Pagamento | 11 |
| `cd-os`, `cd-diagnostico-reparo`, `cd-orcamento-pagamento` | build, imagem e deploy dos 3 serviços no EKS | 12 |
| `auth-rotas` | rotas do API Gateway para os LoadBalancers e webhook (2º apply) | 13 |
| `kubernetes-synthetic` | Synthetic do New Relic com o health final do OS | 14 |
| `configure-final` | atualiza o contexto local com as URLs finais | 14 |
| `health` | espera os 3 serviços responderem `UP` e mostra as URLs | 15 |
| `e2e` (opcional) | coleção Newman e, se pedido, smoke k6 | 17 |

**Diferenças entre os ambientes:**

| | `homolog` | `production` |
|---|---|---|
| Branch dos workflows | `homolog` | `main` |
| Workflow do Kubernetes | `deploy-homolog.yml` | `deploy-production.yml` |
| Perfil dos RDS | sem Multi-AZ, sem deletion protection, sem snapshot final | igual: perfil descartável do AWS Academy |

O script passa `-UseAwsAcademyDisposableProductionProfile` em todas as execuções do `configure-environment.ps1`, inclusive na homologação. Sem esse parâmetro, os RDS de produção voltariam para Multi-AZ e deletion protection no próximo apply.

No final, o script mostra:
- as URLs dos 3 serviços e do API Gateway;
- o link de cada run, que serve como evidência da ordem da subida;
- o comando para destruir o ambiente.

**E2E junto com a subida:**

```powershell
.\scripts\provision-environment.ps1 -Environment homolog -RunE2E                 # Newman
.\scripts\provision-environment.ps1 -Environment homolog -RunE2E -RunLoadTest    # Newman + smoke k6
```

## 4. Se uma etapa falhar

O script para e mostra o comando para retomar, por exemplo:

```powershell
.\scripts\provision-environment.ps1 -Environment homolog -StartAt cd-os
```

- Abra o link do run que falhou, corrija a causa e rode o comando mostrado.
- Ao retomar, o script roda primeiro o `configure-environment.ps1`, que renova as credenciais do Lab. Por isso, **copie o `[default]` de novo antes de retomar**.
- **Lab expirou no meio** (`ExpiredToken`, `voc-cancel-cred`): clique em Start Lab, copie o `[default]` e retome da etapa que falhou.
- **`DEPLOY_ENABLED` de algum serviço ficou `false`:** falta output de um dos bancos. Confira os runs `db-*` e retome com `-StartAt configure-bancos`.
- **Nós do EKS sumiram** (pods `Pending`): siga a seção 21.2 do Bootstrap e retome da etapa que falhou.

## 5. Destruir o ambiente

Também é um único script, que desliga os gates de deploy e destrói tudo na ordem inversa: Auth → namespaces e LoadBalancers → New Relic → bancos e filas → ENIs das Lambdas → EKS e VPC.

```powershell
Set-Location C:\fiap\fase4\fiap-tc-fase4-oficina-os-service
Set-ExecutionPolicy -Scope Process Bypass -Force
.\scripts\configure-environment.ps1 -Environment homolog -UseAwsAcademyDisposableProductionProfile   # lê o [default] copiado
.\scripts\destroy-environment.ps1 -Environment homolog                                               # digite 'homolog' para confirmar
```

Para produção, troque `homolog` por `production` nos 2 comandos. Se uma etapa falhar, retome com `-StartAt <etapa>`, como em `.\scripts\destroy-environment.ps1 -Environment homolog -StartAt lambda-enis`. Os detalhes estão nas seções 22.1 e 26 do Bootstrap.

> **Custos:** não deixe homologação e produção no ar ao mesmo tempo. Destrua o ambiente ao terminar a demonstração.
