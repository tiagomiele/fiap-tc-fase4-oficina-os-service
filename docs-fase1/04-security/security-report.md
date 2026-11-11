# Relatório de Análise de Vulnerabilidades

> Este documento consolida a análise de vulnerabilidades do projeto, conforme exigido na Fase 1 do Tech Challenge.

## Referência: OWASP Top 10 (2021)

A postura de segurança do projeto é orientada pelo **OWASP Top 10 (edição 2021)**. O mapeamento detalhado de cada categoria (A01 a A10) com a respectiva mitigação aplicada no código, configuração e pipeline está em [`docs/security/owasp-top10.md`](owasp-top10.md).

## Ferramentas utilizadas

| Ferramenta | Escopo | Como rodar |
|---|---|---|
| **CycloneDX SBOM** (`cyclonedx-maven-plugin`) | Inventário de dependências (input para qualquer scanner) | `./mvnw -DskipTests package cyclonedx:makeAggregateBom` |
| **OWASP Dependency-Track** (servidor) | Análise contínua de CVEs a partir do SBOM | `docker compose -f docker-compose.dep-track.yml up -d` |
| **OWASP Dependency-Check** (profile opcional) | Inspeção pontual local de CVEs em dependências | `./mvnw -Powasp org.owasp:dependency-check-maven:check` |
| **Trivy** (filesystem + imagem) | CVEs no filesystem e na imagem Docker | `trivy fs .` / `trivy image oficina-backend:local` |
| **Gitleaks** (opcional) | Segredos commitados | `gitleaks detect --source .` |

## Execução no CI

O pipeline `.github/workflows/ci.yml` roda automaticamente em cada push/PR:
- Build + testes + JaCoCo (gate 80% no domínio).
- **Geração do SBOM CycloneDX** (`bom.xml` + `bom.json`) publicado como artefato (`sbom-cyclonedx`).
- **Upload do SBOM para Dependency-Track** (opcional, ativado quando os secrets `DEPENDENCY_TRACK_URL` + `DEPENDENCY_TRACK_API_KEY` estão configurados).
- Trivy em filesystem com SARIF publicado no GitHub Advanced Security.

> **Estratégia adotada**: o CI gera o **inventário** das dependências (SBOM, ~10-30s) e delega a **análise** ao Dependency-Track. O servidor processa o SBOM contra o NVD/OSV de forma assíncrona, mantém histórico por versão e não onera o pipeline.
>
> **Setup do servidor**: detalhado em [`docs/security/dependency-track-guide.md`](dependency-track-guide.md). Inclui `docker-compose.dep-track.yml` para uso local e instruções de hospedagem em ambiente compartilhado.
>
> **SBOM versionado**: o `bom.xml` mais recente fica em [`docs/security/reports/`](04-security/reports/) como evidência auditavel da varredura.

## Análise (preencher a cada release)

### Findings (iteração inicial)

| ID | Gravidade | Dependência / Arquivo | Descrição | Mitigação / Status |
|---|---|---|---|---|
| _a preencher_ | _a preencher_ | _a preencher_ | _a preencher_ | _a preencher_ |

### Postura de segurança aplicada

1. **Autenticação**: JWT HMAC-SHA256, segredo ≥ 256 bits validado no boot.
2. **Autorização**: `@PreAuthorize` com roles (`ADMIN`, `ATENDENTE`, `MECANICO`).
3. **Senhas**: hash com BCrypt cost 12.
4. **Validação de entrada**: Jakarta Validation + VOs (`Document`, `Plate`, `Money`, `Stock`).
5. **Erros**: envelope padronizado, sem stack trace, com `code` estável.
6. **Endpoints públicos**: rate limit + token opaco de acompanhamento por OS.
7. **CORS**: restrito (configurar origens antes de produção).
8. **Logs**: sem PII em INFO (CPF/CNPJ mascarados); `X-Request-Id` via MDC.
9. **Dependências**: verificadas no CI; `failBuildOnCVSS=9` no OWASP.
10. **Imagem Docker**: usuário não-root, JRE Alpine, healthcheck configurado.

## Próximos passos

- [ ] Executar o scan inicial e popular a tabela de findings.
- [ ] Revisar configuração de CORS para produção.
- [ ] Avaliar migração para RS256 (JWT) com rotação de chaves.
- [ ] Configurar alerta para `StockBelowThreshold` (observabilidade operacional).
