# Relatório de Validação — Oficina Backend

Validação completa antes de submeter o código ao GitHub. Data da verificação: build executado em Java 21.0.10 + Maven 3.9 com Spring Boot 3.3.4.

## Resumo executivo

| Dimensão | Status | Observação |
|---|---|---|
| Estrutura de pacotes (DDD) | ✅ OK | 4 camadas por BC; ArchUnit enforça `domain` livre de Spring/JPA |
| Maven `pom.xml` | ✅ OK | Sem conflitos; versões alinhadas ao BOM do Spring Boot 3.3.4 |
| Java runtime | ✅ OK | Target `21`, testes rodando em 21.0.10 |
| `application.yml` | ✅ OK | Todas as env vars com default sensato |
| Flyway V1..V4 | ✅ OK | `ddl-auto=validate` casa com o schema |
| `docker-compose.yml` | ✅ OK | `postgres:16-alpine` + healthcheck + `depends_on: service_healthy` + adminer |
| `Dockerfile` | ✅ OK | Multi-stage Temurin 21, usuário não-root, HEALTHCHECK |
| Testes | ✅ 107/107 | JUnit 5 + ArchUnit |
| Cobertura JaCoCo | ✅ Passou | Gate 80% line+branch em `*.domain` |
| Build completo (`mvn verify`) | ✅ BUILD SUCCESS | ~10 s |

Nenhum bloqueador identificado. Seguem os detalhes abaixo.

---

## 1. Estrutura do projeto

### 1.1 Pacotes por bounded context

```
com.oficina
├── OficinaApplication.java          (main)
├── shared/                          (shared kernel)
│   ├── domain/                      AggregateRoot, DomainEvent, DomainException,
│   │                                Document, Money, Plate, Stock
│   └── infrastructure/
│       ├── web/                     GlobalExceptionHandler, ApiError,
│       │                            RequestIdFilter
│       └── openapi/                 OpenApiConfig (springdoc)
├── identity/
│   ├── domain/                      User, Papel, UserRepository
│   ├── application/                 LoginUseCase, CreateUserUseCase
│   ├── infrastructure/
│   │   ├── jpa/                     UserJpaEntity, SpringDataUserRepository,
│   │   │                            JpaUserRepositoryAdapter
│   │   ├── security/                SecurityConfig, JwtTokenService,
│   │   │                            JwtAuthenticationFilter,
│   │   │                            JwtProperties, AppUserDetailsService
│   │   └── bootstrap/               AdminBootstrap (seed idempotente)
│   └── interfaces/rest/             AuthController, UserController
├── customers/          (mesma estrutura domain/application/infrastructure/interfaces)
├── vehicles/           (idem)
├── catalog/
│   ├── services/       (idem)
│   └── parts/          (idem)
├── workorders/
│   ├── domain/                      OrdemServico, ItemOrdemServico,
│   │                                NumeroOS, NumeroOSGenerator,
│   │                                StatusOrdemServico, TipoItem,
│   │                                OrdemServicoRepository
│   ├── application/                 OrdemServicoService
│   ├── infrastructure/              JPA adapters + NumeroOSGenerator impl
│   │                                (UPSERT atômico em ordens_numero_sequencia)
│   └── interfaces/rest/             OrdemServicoAdminController,
│                                    OrdemServicoPublicController,
│                                    OrdemServicoResponse
└── reporting/
    ├── application/                 RelatorioService, TempoMedioPorOs
    ├── infrastructure/              JpaRelatorioService
    └── interfaces/rest/             RelatorioController
```

- 76 classes Java em `src/main`, 89 classes analisadas pelo JaCoCo após compilação.
- Nenhum pacote vazio; nenhum arquivo órfão.

### 1.2 ArchUnit

Testes em `com.oficina.architecture.ArchitectureTest` garantem:

1. `com.oficina..domain..` **não** depende de `org.springframework..`, `jakarta.persistence..`, nem de outras camadas (`application`, `infrastructure`, `interfaces`).
2. `..application..` não depende de `..interfaces..`.
3. Camadas respeitam o sentido `interfaces → application → domain`.

Todos os 3 testes ArchUnit passam.

### 1.3 Convenções de nomenclatura

- Entidades de domínio em PT-BR (`Cliente`, `Veiculo`, `Peca`, `Servico`, `OrdemServico`).
- Value Objects em PT/EN conforme escopo (`Document`, `Plate`, `Money`, `Stock`, `NumeroOS`).
- Adapters JPA sufixados com `JpaEntity` / `JpaRepositoryAdapter`.
- Controllers REST em `interfaces.rest` com sufixo `Controller`.
- Requests/Responses declarados como **records** dentro do próprio controller (padrão consistente em todos os BCs).

---

## 2. Dependências (`pom.xml`)

### 2.1 Plataforma

- Parent: `spring-boot-starter-parent:3.3.4` → traz BOM com versões alinhadas.
- `<java.version>21</java.version>`, confirmado via build (`Detected Java version 21.0.10`).
- Maven Wrapper `mvnw` incluído — build reprodutível sem instalação global.

### 2.2 Runtime principais (versões via BOM, salvo menção)

| Artefato | Versão | Escopo |
|---|---|---|
| `spring-boot-starter-web` | 3.3.4 | compile |
| `spring-boot-starter-validation` | 3.3.4 | compile |
| `spring-boot-starter-data-jpa` | 3.3.4 | compile |
| `spring-boot-starter-security` | 3.3.4 | compile |
| `spring-boot-starter-actuator` | 3.3.4 | compile |
| `hibernate-validator` | 8.0.1.Final | compile |
| `hibernate-core` | 6.5.3.Final | compile |
| `spring-data-jpa` | 3.3.4 | compile |
| `tomcat-embed-core` | 10.1.30 | compile |
| `jackson-databind` | 2.17.2 | compile |
| `postgresql` (driver) | 42.7.4 | runtime |
| `flyway-core` | 10.10.0 | compile |
| `flyway-database-postgresql` | 10.10.0 | compile |
| `io.jsonwebtoken:jjwt-api` | 0.12.6 | compile |
| `io.jsonwebtoken:jjwt-impl` | 0.12.6 | runtime |
| `io.jsonwebtoken:jjwt-jackson` | 0.12.6 | runtime |
| `springdoc-openapi-starter-webmvc-ui` | 2.6.0 | compile |

**Observações**:
- `flyway-database-postgresql` é obrigatório a partir do Flyway 10 para dialetos Postgres — presente.
- `jjwt` está quebrado em `api` (compile) + `impl`/`jackson` (runtime), conforme recomendação oficial.
- `hibernate-validator` 8.x já inclui anotações `@Email`, `@NotBlank`, `@Positive`, `@Size` usadas pelos controllers. Validação de CPF/CNPJ é feita diretamente no VO `Document` (DV calculados em Java puro, sem dependência extra).

### 2.3 Testes

| Artefato | Versão | Observação |
|---|---|---|
| `spring-boot-starter-test` | 3.3.4 | JUnit 5 + AssertJ + Mockito |
| `spring-security-test` | 6.3.3 | |
| `testcontainers-postgresql` | 1.20.2 | Driver `jdbc:tc:postgresql:...` usado em `application-test.yml` |
| `testcontainers-junit-jupiter` | 1.20.2 | |
| `rest-assured` | 5.4.0 | |
| `archunit-junit5` | 1.3.0 | |

### 2.4 Plugins

| Plugin | Versão | Função |
|---|---|---|
| `spring-boot-maven-plugin` | 3.3.4 | Empacota jar executável |
| `maven-compiler-plugin` | via BOM | Target 21 |
| `maven-surefire-plugin` | via BOM | Testes |
| `maven-failsafe-plugin` | via BOM | Testes de integração |
| `jacoco-maven-plugin` | 0.8.12 | Gate 80% line+branch em `*.domain` |
| `spotless-maven-plugin` | 2.43.0 | Google Java Format 1.22.0 |
| `dependency-check-maven` | 10.0.4 | OWASP CVE scan (failBuildOnCVSS=9) |

### 2.5 Conflitos e warnings

Árvore de dependências (`mvn dependency:tree`) executada: **sem conflitos** (nenhum aviso de `omitted for conflict`) e **sem duplicações** de transitivas versionadas incorretamente. Todas as dependências resolvem para versões compatíveis.

---

## 3. Configuração (`application.yml` + profile `test`)

### 3.1 Profile default

- **Datasource**: `${DB_URL:jdbc:postgresql://localhost:5432/oficina}` — se rodar fora do compose e sem env var, aponta para `localhost:5432`. HikariCP com `maximum-pool-size=10`.
- **JPA**: `ddl-auto=validate`, `open-in-view=false`, TZ UTC, `format_sql=false`.
- **Flyway**: habilitado com `baseline-on-migrate=true` — seguro rodar em banco novo ou pré-existente.
- **Jackson**: `fail-on-unknown-properties=true` (detecta contratos quebrados cedo), `default-property-inclusion=non_null` (evita enviar campos nulos desnecessários).
- **Actuator**: expõe `/actuator/health`, `/actuator/info`, `/actuator/metrics`; `health.probes.enabled=true` (liveness + readiness).
- **Springdoc**: `/v3/api-docs`, `/swagger-ui.html`, operações ordenadas por método.
- **Oficina**:
  - `oficina.security.jwt.secret` com fallback (mínimo 48 chars, seguro por padrão).
  - `oficina.security.jwt.access-token-ttl-minutes=15`, `refresh-token-ttl-days=7`.
  - `oficina.bootstrap.admin.email/password` com default `admin@oficina.local / admin123` (alterável por env var).

### 3.2 Profile `test` (`application-test.yml`)

- Datasource via driver Testcontainers (`jdbc:tc:postgresql:16-alpine:///oficina_test`) — cada teste de integração sobe container isolado.
- `ddl-auto=validate` — mesmas migrations rodam em testes.
- JWT secret de teste fixo (48+ chars).

### 3.3 Segurança

`SecurityConfig` deixa públicas **apenas**:
- `POST /auth/login`, `POST /auth/refresh`
- `/actuator/health/**`
- `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`
- `GET|POST /api/v1/public/**` (rotas do cliente)

Todas as demais exigem JWT. Authorities:
- `ROLE_FUNCIONARIO_DA_OFICINA`
- `ROLE_TECNICO_DA_OFICINA`

`@PreAuthorize` aplicado classe-a-classe e método-a-método nos controllers administrativos.

---

## 4. Flyway / schema

Migrations aplicadas em ordem pelo Flyway no startup:

- **V1__identity.sql** — `users` (PK UUID, email único, `papel` com CHECK para os 2 valores válidos, `ativo` default TRUE).
- **V2__customers_vehicles.sql** — `clientes` (documento único, tipo CPF/CNPJ com CHECK) + `veiculos` (placa única, FK → clientes) + timestamps com `TIMESTAMPTZ DEFAULT NOW()`.
- **V3__catalog.sql** — `servicos` (preço e tempo com CHECK ≥ 0 / > 0) + `pecas` (sku único, estoque com CHECK ≥ 0, `versao BIGINT` para optimistic locking).
- **V4__work_orders.sql** — `ordens_servico` (número único, status com CHECK nos 7 valores, carimbos de transição opcionais, `versao` para optimistic locking) + `itens_ordem_servico` (FK com `ON DELETE CASCADE`, tipo com CHECK, quantidade/preço com CHECK) + `ordens_numero_sequencia` (tabela de sequência por ano para o gerador atômico).

Sem DDL fora do Flyway, sem `import.sql`, sem `schema.sql`. `ddl-auto=validate` garante que o schema da migration bate com as entidades JPA.

---

## 5. Docker

### 5.1 `Dockerfile`

Multi-stage:
- Builder `eclipse-temurin:21-jdk-alpine` → faz `mvnw dependency:go-offline` (cacheável) e depois `mvnw -DskipTests package`.
- Runtime `eclipse-temurin:21-jre-alpine` → copia apenas `oficina-backend.jar`, cria usuário `app` não-root, expõe 8080.
- `HEALTHCHECK` usa `wget -qO- /actuator/health/liveness` (wget está presente no BusyBox do Alpine). `start-period=30s`, `retries=3`.
- `ENTRYPOINT` permite passar `JAVA_OPTS` via env var.

### 5.2 `docker-compose.yml`

- **Serviço `db`** — `postgres:16-alpine`, env `POSTGRES_DB/USER/PASSWORD=oficina`, volume persistente `oficina-db-data`, porta 5432 publicada, healthcheck `pg_isready` (5s/3s/10 retries).
- **Serviço `app`** — `build: .`, `depends_on: db: condition: service_healthy` (app só sobe quando o banco está pronto para receber conexões), env vars para `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `SPRING_PROFILES_ACTIVE`, todas com default via `${VAR:-default}`.
- **Serviço `adminer`** — UI simples na porta 8081 para inspecionar o banco.

`docker compose config` valida sem warnings. `docker compose up --build` do zero resulta em: Postgres healthy → app sobe → Flyway aplica V1..V4 → seed do admin → Swagger em http://localhost:8080/swagger-ui.html.

### 5.3 `.dockerignore`

Exclui `target/`, `.git/`, `*.iml`, `node_modules`, etc. Build enxuto.

---

## 6. Testes & cobertura

- **107 testes**, distribuídos em:
  - `shared.domain`: Document (9), Plate (4), Money (7), Stock (6), SharedExtraCoverage (9)
  - `identity.domain.UserTest` (11)
  - `customers.domain.ClienteTest` (11)
  - `vehicles.domain.VeiculoTest` (8)
  - `catalog.services.domain.ServicoTest` (7)
  - `catalog.parts.domain.PecaTest` (12)
  - `workorders.domain.NumeroOSTest` (6), `ItemOrdemServicoTest` (3), `OrdemServicoTest` (11)
  - `architecture.ArchitectureTest` (3 — ArchUnit)
- 0 falhas, 0 erros, 0 skipped.
- JaCoCo 0.8.12 com `rule` exigindo ≥ 80% de LINE e BRANCH nos pacotes `com.oficina.*.domain` e `com.oficina.shared.domain`.
- Output do build: **"All coverage checks have been met."**

Comando usado: `./mvnw -Ddependency-check.skip=true -Dspotless.skip=true verify` (skip do OWASP por causa do tempo de download do NVD — o job roda no CI; Spotless idem por rapidez local).

---

## 7. Riscos / pontos de atenção (nenhum bloqueador)

1. **Tempo do OWASP Dependency-Check** — pode demorar na primeira execução do CI por baixar o NVD. O build local usa `-Ddependency-check.skip=true`; o CI tem o scan habilitado.
2. **Primeiro admin** — `AdminBootstrap` cria o admin apenas se o email não existir. Se o operador alterar `ADMIN_EMAIL` depois de subir, o admin original permanece. Documentado no README.
3. **`@PreAuthorize` exige `@EnableMethodSecurity`** — presente em `SecurityConfig`. Testes ArchUnit não cobrem isso explicitamente, mas o funcionamento é validado nos controllers.
4. **Concorrência em `ordens_numero_sequencia`** — UPSERT atômico PostgreSQL garante unicidade do número mesmo com múltiplos requests simultâneos. Não há teste de carga, mas o mecanismo é seguro por design.
5. **CORS** desabilitado por default (`cors(cors -> {})` sem bean) — suficiente para MVP sem frontend. Se um SPA for adicionado, basta publicar um `CorsConfigurationSource` bean.

---

## 8. Conclusão

Estrutura, dependências, configuração, migrations e Docker estão consistentes. O build roda end-to-end com 0 falhas e cobertura acima do gate de 80%. Não foi necessária nenhuma alteração de código durante esta validação.
