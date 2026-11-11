# Observabilidade com New Relic

## Cobertura

| Componente | Integração |
|---|---|
| Spring Boot | New Relic Java Agent e API de eventos customizados |
| EKS | Helm chart `nri-bundle` |
| Lambda | New Relic Lambda integration/extension |
| API Gateway | integração AWS e métricas do CloudWatch |
| Logs | JSON em `stdout`, coletados e correlacionados |
| Uptime | monitor sintético sobre healthcheck público |

## Correlação

Cada requisição deve preservar ou gerar `X-Request-Id`. O New Relic adicionará `trace.id` e `span.id`; os três identificadores devem estar presentes nos logs JSON da aplicação.

Dados pessoais não serão enviados. CPF deve ser omitido ou mascarado.

## Eventos de negócio

A aplicação publicará eventos sem dados sensíveis para permitir consultas NRQL:

- `OrdemServicoCriada`;
- `OrdemServicoStatusAlterado`;
- `OrdemServicoProcessamentoFalhou`;
- `IntegracaoExternaFalhou`.

Atributos previstos: identificador técnico da OS, status anterior, status novo, duração no status, ambiente, operação e código de erro.

## Dashboard obrigatório

- volume diário de ordens de serviço;
- tempo médio em diagnóstico, execução e finalização;
- latência média e percentil 95 das APIs;
- taxa de respostas 5xx;
- falhas nas integrações;
- CPU e memória por pod;
- réplicas desejadas e disponíveis;
- uptime do healthcheck.

## Alertas

- healthcheck indisponível;
- taxa de erro da API acima do limite;
- latência P95 elevada;
- falha no processamento de OS;
- pod indisponível ou reiniciando repetidamente;
- CPU ou memória próxima do limite;
- falha recorrente de Lambda ou integração.

## Segredos

`NEW_RELIC_LICENSE_KEY` e demais credenciais serão armazenados nos ambientes do GitHub/HCP Terraform e aplicados como Secrets Kubernetes. Nenhuma chave será versionada.
