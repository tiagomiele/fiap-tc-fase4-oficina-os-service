# OWASP Top 10 — Mapeamento de mitigações no Oficina Backend

> Documento de referência: como cada categoria do **OWASP Top 10 (edição 2021)** está endereçada nas escolhas técnicas e de arquitetura do projeto.

A cobertura é orientada às **práticas de desenvolvimento seguro** aplicadas no código, na configuração da aplicação, no pipeline de CI e na infraestrutura de execução (Docker / Spring profiles). Ferramentas automatizadas — **SBOM CycloneDX** + OWASP **Dependency-Track** (análise contínua de CVEs em dependências) e **Trivy** (filesystem scan) — complementam o quadro, principalmente nas categorias **A06** e **A08**.

## Tabela de cobertura

| ID | Categoria | Mitigação aplicada | Onde no projeto |
|---|---|---|---|
| **A01** | Broken Access Control | Autorização declarativa por endpoint com `@PreAuthorize`; **role hierarchy** (`FUNCIONARIO_DA_OFICINA > TECNICO_DA_OFICINA`); separação clara de perfis em 4 controllers (`Auth`, `Administrativo`, `Tecnico`, `ClientePublico`); **token opaco por OS** para o canal público do cliente, sem trafegar credenciais. | [`SecurityConfig.java`](../../src/main/java/br/com/oficina/config/SecurityConfig.java) · [`AdministrativoOficinaController`](../../src/main/java/br/com/oficina/controller/AdministrativoOficinaController.java) · [`TecnicoOficinaController`](../../src/main/java/br/com/oficina/controller/TecnicoOficinaController.java) · [`ClienteOficinaController`](../../src/main/java/br/com/oficina/controller/ClienteOficinaController.java) |
| **A02** | Cryptographic Failures | Senhas com **BCrypt cost 12** (não armazenamos plaintext); JWT **HMAC-SHA256** com segredo ≥ 256 bits validado no boot; canal HTTP termina sob TLS no ambiente produtivo (responsabilidade do load balancer); senhas e tokens **nunca aparecem em log**. | [`PasswordConfig.java`](../../src/main/java/br/com/oficina/config/PasswordConfig.java) · [`JwtService.java`](../../src/main/java/br/com/oficina/security/JwtService.java) · `application.yml` (`oficina.security.jwt.secret`) |
| **A03** | Injection | Acesso a dados **exclusivamente** via Spring Data JPA com prepared statements (zero SQL string-concat); **Jakarta Validation** em todos os DTOs (`@NotNull`, `@Pattern`, `@Size`); **Value Objects** (`Documento`, `Placa`, `Dinheiro`) validados no construtor; respostas em JSON sanitizado pelo Jackson. | DTOs em `dto/`, VOs em `domain.compartilhado.vo`, repositórios em `domain/*/repository` |
| **A04** | Insecure Design | Arquitetura **DDD** com bounded contexts e invariantes do domínio enforçadas no agregado: estoque ≥ 0, máquina de estados da OS (`RECEBIDA → … → ENTREGUE`), PK composta de NF (`numero + serie + cnpj + data`), placa única por cliente. **ArchUnit** garante que regras de domínio não vazem para a camada web. | [`docs/ddd/context-map.md`](../01-ddd/context-map.md) · `domain.model` · `arquitetura/ArchitectureTest.java` |
| **A05** | Security Misconfiguration | `spring.jpa.hibernate.ddl-auto=validate` (sem auto-DDL em runtime); migrations versionadas via **Flyway** (`V1__schema_inicial.sql`, …); imagem Docker com **usuário não-root** + JRE Alpine; CORS restrito (origens explícitas em produção); `server.error.include-stacktrace=never`; endpoints do Actuator restritos a `health, info, metrics`. | [`application.yml`](../../src/main/resources/application.yml) · [`Dockerfile`](../../Dockerfile) · `db/migration/` |
| **A06** | Vulnerable & Outdated Components | **SBOM CycloneDX** gerado a cada build pelo `cyclonedx-maven-plugin` e enviado ao **OWASP Dependency-Track** (análise contínua, assíncrona, com histórico por versão); **Trivy** filesystem scan em SARIF; OWASP **Dependency-Check** disponível como profile opcional (`./mvnw -Powasp dependency-check:check`) para inspeções pontuais com `failBuildOnCVSS=9`. | [`pom.xml`](../../pom.xml) (`cyclonedx-maven-plugin`) · [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) (jobs `sbom` + `trivy`) · [`docs/security/dependency-track-guide.md`](./dependency-track-guide.md) · [`docs/security/reports/`](./reports/) |
| **A07** | Identification & Authentication Failures | Login via Spring Security com `BCryptPasswordEncoder`; JWT com TTL curto (15 min para access token, 7 dias para refresh); **rate limit** por IP no endpoint de login (`oficina.security.rate-limit.login-per-minute`); mensagens de erro genéricas em falhas de login (sem revelar se o usuário existe); bootstrap admin com senha trocável via `ADMIN_PASSWORD`. | `controller/AuthController.java` · `security/JwtService.java` · `security/RateLimitFilter.java` |
| **A08** | Software & Data Integrity Failures | Build reprodutível com **Maven Wrapper** (`./mvnw`) — versão fixa em `.mvn/wrapper/maven-wrapper.properties`; migrations Flyway garantem que o schema produtivo é idêntico ao dos testes; CI publica artefatos imutáveis (`jacoco-report`, `sbom-cyclonedx`, `trivy-fs-sarif`); SBOM CycloneDX versionado em `docs/security/reports/` para rastreabilidade; imagem Docker construída a partir de `eclipse-temurin:21-jre-alpine`. | `.mvn/wrapper/` · `db/migration/V*__*.sql` · `Dockerfile` · `ci.yml` · [`docs/security/reports/`](./reports/) |
| **A09** | Security Logging & Monitoring Failures | **Logback JSON** no perfil `prod` (logs estruturados, fáceis de ingerir); MDC com `X-Request-Id` por requisição (correlação ponta a ponta); CPF/CNPJ **mascarados** em logs INFO (`***`); endpoints do Actuator (`/health`, `/info`, `/metrics`) expostos para observabilidade; envelope de erro padronizado (`ApiError`) com `code` estável (`USR-001`, `OS-014`, …) para correlação fora do código. | [`logback-spring.xml`](../../src/main/resources/logback-spring.xml) · `web/RequestIdFilter.java` · `web/ApiError.java` |
| **A10** | Server-Side Request Forgery (SSRF) | A aplicação **não realiza chamadas HTTP saídas** baseadas em URLs fornecidas pelo usuário — todas as integrações são internas (banco PostgreSQL, OpenAPI estático). Sem `RestTemplate` / `WebClient` exposto a inputs externos. | (ausência de superfície SSRF) |

## Ferramentas OWASP utilizadas no pipeline

| Ferramenta | Categoria do Top 10 alvo | Quando roda | Saída |
|---|---|---|---|
| **CycloneDX SBOM** (`cyclonedx-maven-plugin`) | A06, A08 | A cada PR no GitHub Actions (~10-30s) | `target/bom.xml` + `target/bom.json` (artefato `sbom-cyclonedx`) |
| **OWASP Dependency-Track** (servidor) | A06 (análise contínua) | Async, ao receber o SBOM | Dashboard com CVEs por componente, histórico por versão |
| **Trivy filesystem scan** | A06, A05 | A cada PR no GitHub Actions | SARIF publicado em "Code scanning" + artefato `trivy-fs.sarif` |
| **OWASP Dependency-Check** (profile opcional) | A06 (inspeção local) | `./mvnw -Powasp dependency-check:check` | HTML, JSON, SARIF (`target/dependency-check-report.*`) |
| **`failBuildOnCVSS=9`** (no profile owasp) | gate de A06 | Quando o profile owasp é executado | Build falha se houver CVE com score ≥ 9.0 (crítica) |
| **Suppression file** (opcional) | A06 com falsos positivos | Sob demanda | [`owasp-suppressions.xml`](../../owasp-suppressions.xml) |

## Outras iniciativas OWASP de referência

A Aula 5 cita projetos da OWASP que **não foram incorporados ao MVP**, mas que ficam como referência conhecida do time:

| Projeto OWASP | Status no projeto | Justificativa |
|---|---|---|
| **OWASP Top 10** | ✓ Mapeado neste documento | Catálogo de referência aplicado |
| **OWASP Dependency-Track** (servidor com SBOM CycloneDX) | ✓ **Adotado** — stack via [`docker-compose.dep-track.yml`](../../docker-compose.dep-track.yml); CI envia o SBOM automaticamente quando os secrets `DEPENDENCY_TRACK_URL` + `DEPENDENCY_TRACK_API_KEY` estão configurados | Modelo SBOM-first com análise assíncrona, sem onerar o CI |
| **OWASP Dependency-Check** | ✓ Disponível como profile Maven opcional (`-Powasp`) | Mantido para inspeções locais; análise contínua é responsabilidade do Dependency-Track |
| **OWASP ZAP** (scanner DAST) | Não integrado ao CI | Pode ser executado manualmente contra `http://localhost:8080` para validações ad-hoc |
| **OWASP WebGoat / Juice Shop** | Não aplicável | Aplicações vulneráveis para treinamento, não fazem parte do produto |
| **OWASP Web Security Testing Guide** | Referência consultada | Inspirou a postura de testes de autenticação, autorização e validação de entrada |

## Como rodar localmente

```bash
# Gerar SBOM CycloneDX (rapido, ~10-30s) — gera target/bom.xml + target/bom.json
./mvnw -DskipTests package cyclonedx:makeAggregateBom

# Subir o servidor Dependency-Track local (UI em http://localhost:9090)
docker compose -f docker-compose.dep-track.yml up -d

# Enviar o SBOM para o servidor (com a chave gerada na UI)
curl -X POST http://localhost:9091/api/v1/bom \
  -H "X-Api-Key: $DTRACK_API_KEY" \
  -F "autoCreate=true" \
  -F "projectName=oficina-backend" \
  -F "projectVersion=0.1.0-SNAPSHOT" \
  -F "bom=@target/bom.xml"

# Inspecao pontual com OWASP Dependency-Check (profile opcional)
./mvnw -Powasp org.owasp:dependency-check-maven:check

# Scan de filesystem com Trivy (requer Trivy instalado)
trivy fs .

# Scan da imagem Docker
docker build -t oficina-backend:local .
trivy image oficina-backend:local
```

Guia detalhado: [`dependency-track-guide.md`](./dependency-track-guide.md).

## Critérios de revisão

Este mapeamento deve ser revisado quando:

- Houver atualização do **OWASP Top 10** (próxima edição esperada para 2025).
- Forem adicionados **endpoints com chamadas HTTP saídas** (impacta A10).
- For introduzido um **mecanismo de upload de arquivos** (impacta A03 e A05).
- For habilitada **federação de identidade / SSO** (impacta A02 e A07).
- For implementado um **sistema de auditoria** completo (reforça A09).
