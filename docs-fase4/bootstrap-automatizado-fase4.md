# Bootstrap automatizado (homologação ou produção)

Este roteiro opera a Fase 4 no AWS Academy só com scripts. Todos eles:
- **perguntam o ambiente num menu** (`1` = `homolog`, `2` = `production`). Para pular o menu, passe `-Environment homolog` ou `-Environment production`;
- **conferem as credenciais do Lab antes de mexer em qualquer coisa.** Se a área de transferência não tiver o bloco `[default]`, ou se ele for de uma sessão antiga, o script avisa e espera você copiar de novo e teclar Enter. Você nunca digita chaves;
- **param no primeiro erro** e mostram o que fazer.

O [Bootstrap da Fase 4](bootstrap-aws-do-zero-fase4.md) continua sendo a referência para entender cada passo e para o troubleshooting.

Todos os comandos são para **Windows PowerShell 5.1**.

| Situação | Script |
|---|---|
| Subir um ambiente do zero | `provision-environment.ps1` (seção 3) |
| Cliquei em **Start Lab** e o ambiente já está no ar (sessão nova, `ExpiredToken`, `voc-cancel-cred`) | `lab-reiniciado.ps1` (seção 4) |
| Validar se o ambiente funciona como especificado | `validar-ambiente.ps1` (seção 5) |
| Ligar ou desligar o Mercado Pago | `provedor-pagamento.ps1` (seção 6) |
| Pagar uma OS no Mercado Pago sandbox | `pagamento-mercadopago.ps1` (seção 6) |
| Destruir o ambiente | `destroy-environment.ps1` (seção 7) |

## 1. Pré-requisitos (uma vez por computador)

- Preparação permanente da seção 4 do Bootstrap: ferramentas, `gh auth login`, os 6 repositórios clonados lado a lado, token do HCP, `REPO_SYNC_TOKEN`, SonarCloud e New Relic.
- Na 1ª execução, o `configure-environment.ps1` (chamado pelos scripts) pede os tokens e as senhas. Nas seguintes, ele usa os valores guardados em `C:\fiap-secrets`.

## 2. Antes de cada script

1. No AWS Academy, clique em **Start Lab** e espere o indicador verde.
2. Vá em **AWS Details → AWS CLI → Show** e copie o bloco `[default]` inteiro.
3. Abra o PowerShell na pasta do OS Service e atualize os clones. Troque o `$Root` pela pasta onde estão os 6 clones:

```powershell
$Root = 'C:\fiap\fase4'   # ex.: 'C:\Users\<você>\OneDrive\Documentos\GitHub'
foreach ($r in 'fiap-tc-fase4-oficina-os-service','fiap-tc-fase4-oficina-database-infra') { git -C (Join-Path $Root $r) pull --ff-only }
Set-Location (Join-Path $Root 'fiap-tc-fase4-oficina-os-service')
Set-ExecutionPolicy -Scope Process Bypass -Force
```

Rode um script por vez, sempre nessa janela.

## 3. Subir o ambiente

```powershell
.\scripts\provision-environment.ps1
```

O script pergunta o ambiente, confere o `[default]` e pede uma confirmação (`S`). Depois sobe tudo sem novas perguntas, exceto as da 1ª execução do `configure-environment.ps1`. Leva de **1h30 a 2h**, dentro das 4h da sessão do Lab. Não copie outra coisa até a 1ª etapa terminar: ela lê o `[default]` da área de transferência.

| Etapa | O que faz | Seção do Bootstrap |
|---|---|---|
| `configure` | grava credenciais, workspaces HCP, Environments, SonarCloud e New Relic | 7.2 |
| `kubernetes` | EKS, add-ons, New Relic e observabilidade | 8 |
| `db-os`, `db-orcamento-pagamento`, `db-diagnostico-reparo` | 2 RDS, DynamoDB e as 3 filas com DLQ, um por vez | 9 |
| `configure-bancos` | leva JDBC, tabela e filas aos serviços e confere `DEPLOY_ENABLED=true` nos 3 | 9 |
| `nos` | confere os nós do EKS; liga ou recria os que não entraram no cluster | 21.2 |
| `labrole` | testa se um pod acessa as filas SQS e o DynamoDB | 10 |
| `auth` | Lambdas, API Gateway e Authorizer (1º apply) | 11 |
| `configure-auth` | leva a URL do API Gateway ao Orçamento e Pagamento | 11 |
| `cd-os`, `cd-diagnostico-reparo`, `cd-orcamento-pagamento` | build, imagem e deploy dos 3 serviços no EKS | 12 |
| `auth-rotas` | rotas do API Gateway para os LoadBalancers e webhook (2º apply) | 13 |
| `kubernetes-synthetic` | Synthetic do New Relic com o health final do OS | 14 |
| `configure-final` | atualiza o contexto local com as URLs finais | 14 |
| `health` | espera os 3 serviços responderem `UP` e mostra as URLs | 15 |
| `e2e` (só com `-RunE2E`) | coleção Newman e, com `-RunLoadTest`, smoke k6 | 17 |

| | `homolog` | `production` |
|---|---|---|
| Branch dos workflows | `homolog` | `main` |
| Workflow do Kubernetes | `deploy-homolog.yml` | `deploy-production.yml` |
| Perfil dos RDS | sem Multi-AZ, sem deletion protection, sem snapshot final | igual: perfil descartável do AWS Academy |

No final, o script mostra as URLs dos 3 serviços e do API Gateway, o link de cada run (evidência da ordem da subida) e o próximo passo: a validação da seção 5.

**Se uma etapa falhar,** o script mostra o comando para retomar, por exemplo `.\scripts\provision-environment.ps1 -Environment homolog -StartAt cd-os`. Corrija a causa pelo link do run e, se o Lab tiver expirado, faça a seção 2 de novo antes de retomar. Ao retomar, o script renova as credenciais e, depois da etapa `nos`, confere os nós antes de seguir.

> **Custos:** não deixe homologação e produção no ar ao mesmo tempo. Destrua o ambiente ao terminar a demonstração (seção 7).

## 4. Depois de um Start Lab (ambiente já no ar)

Quando a sessão do Lab acaba, a infraestrutura continua existindo, mas as credenciais mudam e o Lab para as instâncias EC2 dos nós. Faça a seção 2 e rode:

```powershell
.\scripts\lab-reiniciado.ps1
```

| Passo | O que faz |
|---|---|
| 1. credenciais | espera o `[default]` válido e renova as credenciais no computador, no HCP e nos GitHub Environments dos 6 repos |
| 2. nós do EKS | liga as instâncias paradas; se elas não entrarem no cluster em 5 min, recria os nós (até 12 min) |
| 3. serviços | espera os 3 deployments ficarem disponíveis |
| 4. health | confere os 3 `UP` e mostra as URLs e o provedor de pagamento |

Esperado no fim: `Ambiente '<ambiente>' pronto para uso.` Se o ambiente escolhido não estiver no ar, o script para **sem alterar nada** e mostra o comando para subir. Assim, os gates de deploy de um ambiente destruído não são religados por engano.

## 5. Validar se o processo funcionou

```powershell
.\scripts\validar-ambiente.ps1             # Newman + smoke k6
.\scripts\validar-ambiente.ps1 -SemCarga   # só o Newman
```

O script não altera a infraestrutura. Ele para e diz o que fazer se faltar algo: credenciais vencidas ou cluster sem nós (rode o `lab-reiniciado.ps1`), ou pagamento fora do `fake` (rode o `provedor-pagamento.ps1 -Provedor fake`). No fim, mostra um resumo com `[OK]` ou `[ATENÇÃO]` em cada item:

| Especificação | Como o script comprova | Onde ver a evidência |
|---|---|---|
| 3 microsserviços independentes no EKS | 3 deployments disponíveis e `/actuator/health` `UP` | URLs no resumo |
| Saga orquestrada pelo OS Service, do diagnóstico ao reparo e ao pagamento, até `ENTREGUE` | E2E Newman no GitHub Actions | link do run; artefato `newman-<ambiente>-<run>` |
| Autenticação serverless: login por CPF, JWT, API Gateway e Authorizer | grupo *Autenticação serverless e segurança* do Newman (sem token, token adulterado, outro cliente) | mesmo run |
| Rastreabilidade: mesmo `correlationId` em todas as etapas | passo *Histórico final correlacionado* do Newman | mesmo run |
| Compensações: estoque insuficiente, orçamento rejeitado, refazer diagnóstico, pagamento recusado e nova tentativa | último run verde do BDD da Saga na branch do ambiente | link do run; artefato `bdd-report` |
| Mensageria SQS com DLQ | nenhuma mensagem nas 3 DLQs depois do E2E | resumo do script |
| Carga | smoke k6 com 0% de falha | mesmo run do E2E |

Validações complementares, feitas à mão no [Bootstrap](bootstrap-aws-do-zero-fase4.md):
- Saga passo a passo, com as 4 compensações e o histórico: seção 16;
- New Relic, com APM dos 3 serviços, cluster, DLQs e Synthetic: seção 18;
- SonarCloud e cobertura acima de 80%: seção 19.1;
- Mercado Pago real: seção 6 abaixo.

## 6. Mercado Pago sandbox (opcional)

Use as credenciais **de teste** do vendedor (seção 4.8 do Bootstrap). Faça um ambiente por vez, porque o painel aceita uma URL de webhook por vez.

**1. Ligar:**

```powershell
.\scripts\provedor-pagamento.ps1   # escolha o ambiente e o provedor 1 (mercadopago)
```

Na 1ª vez, o script pede o Access Token, a Public Key e a assinatura do webhook, e guarda os 3 em `C:\fiap-secrets\shared`. Ele roda o CD do Orçamento e Pagamento e, no fim, mostra a **URL do webhook**. No painel do Mercado Pago (https://www.mercadopago.com.br/developers/panel/app → sua aplicação → *Webhooks → Configurar notificações*, modo de teste), cole essa URL, marque só **Pagamentos (legacy)** e salve. Não gere uma assinatura nova.

**2. Pagar:**

```powershell
.\scripts\pagamento-mercadopago.ps1
```

O script cria uma OS de R$ 5,00 e leva até `AGUARDANDO_PAGAMENTO` pela Saga. Depois, copia o link de pagamento para a área de transferência e espera. Abra o link numa janela anônima, entre com o comprador de teste e pague com:

| Campo | Valor |
|---|---|
| Cartão Mastercard | `5031 4332 1540 6351` |
| Código de segurança / validade | `123` / `11/30` |
| Nome do titular | `APRO` (aprovado) |
| CPF do titular | `12345678909` |

Volte ao PowerShell e tecle Enter. Esperado: `Mercado Pago validado em '<ambiente>': o webhook chegou e a Saga marcou a OS ... como PAGA.` Para conferir depois uma OS já paga: `.\scripts\pagamento-mercadopago.ps1 -NumeroOs OS-...`.

**3. Voltar ao fake (obrigatório ao terminar):**

```powershell
.\scripts\provedor-pagamento.ps1   # escolha o ambiente e o provedor 2 (fake)
```

Sem isso, o E2E do ambiente falha, porque o pagamento simulado só existe no `fake`. Com o ambiente fora do ar, o script só grava `PAYMENT_PROVIDER=fake`, e a próxima subida já vem no `fake`. A recusa com o titular `OTHE` e a nova tentativa ficam na seção 20.5 do Bootstrap (manual).

## 7. Destruir o ambiente

Faça a seção 2 e rode:

```powershell
.\scripts\destroy-environment.ps1   # digite o nome do ambiente para confirmar
```

O script renova as credenciais sozinho, desliga os gates de deploy e destrói tudo na ordem inversa: Auth → namespaces e LoadBalancers → New Relic → bancos e filas → ENIs das Lambdas → EKS e VPC. A etapa `lambda-enis` costuma levar de 10 a 40 min: deixe o script esperar. Se uma etapa falhar, retome com o comando mostrado, por exemplo `.\scripts\destroy-environment.ps1 -Environment homolog -StartAt lambda-enis`. Os detalhes estão nas seções 22.1 e 26 do Bootstrap.

## 8. Se algo falhar

| Mensagem | O que fazer |
|---|---|
| `A área de transferência não tem o bloco [default]` ou `...está vencido ou cancelado` | seção 2 (Start Lab e copiar o `[default]`); volte ao PowerShell e tecle Enter |
| `Credenciais AWS vencidas neste computador` | `.\scripts\lab-reiniciado.ps1` e rode o script de novo |
| `O ambiente '<ambiente>' não está no ar` | `.\scripts\provision-environment.ps1` |
| `Os nós de oficina-<ambiente> não ficaram Ready` | o Academy bloqueou o bootstrap do nó; Bootstrap 21.2 (procure `explicit deny`) |
| `DEPLOY_ENABLED ... está 'false'` | falta output de um banco: confira os runs `db-*` e retome com `-StartAt configure-bancos` |
| `O run terminou com 'failure'` | abra o link mostrado, corrija e retome com o comando mostrado |
| `Pagamento em 'mercadopago'` na validação | `.\scripts\provedor-pagamento.ps1 -Provedor fake` |
| `[ATENÇÃO] há mensagem em DLQ` | Bootstrap 16.5 e 16.6 (a 16.6 deixa uma mensagem de propósito) |

## 9. Parâmetros para pular os menus

| Script | Exemplo |
|---|---|
| `provision-environment.ps1` | `-Environment production -RunE2E -RunLoadTest`; retomar: `-StartAt cd-os`; clones em outra pasta: `-RepositoriesRoot 'D:\clones'` |
| `lab-reiniciado.ps1` | `-Environment production` |
| `validar-ambiente.ps1` | `-Environment homolog -SemCarga` |
| `provedor-pagamento.ps1` | `-Environment production -Provedor fake` |
| `pagamento-mercadopago.ps1` | `-Environment production -NumeroOs OS-112026-000001` |
| `destroy-environment.ps1` | `-Environment homolog -StartAt lambda-enis` |
