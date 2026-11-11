# 📚 Documentações — Oficina Mecânica - Backend

---

## 🗂️ Organização da Estrutura de Pastas para as Documentações do Projeto

```
docs/
├── 01-ddd/          🎨 Modelagem de domínio (DDD, Domain Storytelling, Event Storming)
├── 02-adr/          🏛️ Decisões de arquitetura (ADRs)
├── 03-api/          🔌 Documentação técnica da API
├── 04-security/     🛡️ Análise de segurança e vulnerabilidades
└── 05-evidencias/   ✅ Relatórios de validação (testes, cobertura, build)
```

---

## 1️⃣ DDD — Modelagem de domínio · [`01-ddd/`](./01-ddd/)

Como descobrimos, modelamos e documentamos o **domínio da oficina mecânica** antes de codificar.

| Artefato | Conteúdo |
|---|---|
| 📜 [Domain Storytelling](./01-ddd/01-1-stortelling/) | 4 jornadas em formato `.egn` (Egon.io) — fluxo principal + 3 exceções. PNGs renderizados disponíveis ao lado de cada `.egn` |
| 🎯 [Event Storming](./01-ddd/01-2-eventstorming/01-2-eventstorming.jpg) | Quadro do Miro com eventos / comandos / agregados / políticas |
| 📗 [Linguagem Ubíqua](./01-ddd/01-3-linguagem_ubiquoa_oficina_mecanica.docx) | Glossário oficial (.docx · ABNT) compartilhado entre negócio e técnico |
| 🗺️ [Bounded Contexts](./01-ddd/bounded-context-oficina-mecanica.png) | Mapa de contextos da oficina mecânica |
| ⚙️ [Máquina de Estados da OS](./01-ddd/work-order-status-machine.md) | Diagrama Mermaid + tabela de transições |

### Como abrir os arquivos `.egn`

Os arquivos `.egn` são do **Egon.io** (Domain Storytelling Modeler). Pra visualizar com animação:

1. Acesse <https://egon.io/app/>
2. Clique em **Import** (ícone na barra superior)
3. Selecione o `.egn` baixado do GitHub

> 💡 Para visualização rápida sem importar, use as imagens `.png` que estão na mesma pasta.

## Como usar no Miro

Os eventos e comandos do Event Storming podem ser usados de 3 formas:
1. Importados diretamente do Miro usando o plugin de integração com GitHub, ou
2. Renderizados (GitHub já renderiza nativamente) e inseridos como imagem, ou
3. Usados como **storyboard** para recriar o Event Storming visualmente com stickies coloridos:
  - 🟧 Laranja = evento de domínio
  - 🟦 Azul = comando
  - 🟨 Amarelo = agregado
  - 🟪 Roxo = política / reação
  - 🟩 Verde = read model / view

Link compartilhado com quadro no miro:

> **Miro board**: https://miro.com/welcomeonboard/SUFWT0NLUGt4MHgxZ01qN2J5UHdoeDNYbmNaZTZEVjRYUUZ5TWNTMjRUV2JNajVhcGxDZUliek8yMEFRbEEwQWNpaWhnL2M2dUJaNEhvaDhXWCszRmw2ZHRnSEkydHJHNVZJUEZFYU5NQ21jK0lZZC9pSTFGZ2pvOFp3bForbVJBS2NFMDFkcUNFSnM0d3FEN050ekl3PT0hdjE=?share_link_id=201223511673

---

## 2️⃣ ADR — Decisões de arquitetura · [`02-adr/`](./02-adr/)

Cada decisão técnica significativa é registrada como **ADR** (Architecture Decision Record).

| ADR | Tema                                                                                            |
|---|-------------------------------------------------------------------------------------------------|
| [ADR 0001 — versão `.docx`](./02-adr/ADR_0001_Banco_Relacional_PostgreSQL.docx) | Justificativa da utilização do banco de dados relacional PostgreSQL.                            |
| [Acesso ao banco de dados](./02-adr/DATABASE_ACCESS.md) | Como conectar ao Postgres via Adminer · `psql` · cliente externo + tabelas e exemplos de SELECT |

---

## 3️⃣ Aplicação — Documentação da API backend - Oficina Mecânica· [`03-api/`](./03-api/)

| Documento | Conteúdo |
|---|---|
| [API.md](./03-api/API.md) | Catálogo detalhado de **todos os endpoints REST** — complementa o Swagger UI |

> 💡 A documentação interativa está sempre disponível em <http://localhost:8080/swagger-ui.html> com a aplicação rodando. Maiores detalhes, consulte o documento [README.md](../README.md) na raiz do projeto.

---

## 4️⃣ Segurança · [`04-security/`](./04-security/)

Análise de vulnerabilidades em 3 frentes: **OWASP Top 10** · **SCA** (CVEs em dependências) · **SBOM**.

### 🛡️ Mapeamento e relatórios

| Documento | Quando ler |
|---|---|
| [`owasp-top10.md`](./04-security/owasp-top10.md) | Como cada categoria do OWASP Top 10 (2021) é endereçada no projeto |
| [`security-report.md`](./04-security/security-report.md) | Relatório consolidado de segurança (entrega FIAP) |

### 📦 Dependency-Track (SCA)

| Documento | Quando ler                                                                    |
|---|-------------------------------------------------------------------------------|
| [`dependency-track-do-primeira-utilizacao.md`](./04-security/dependency-track-do-primeira-utilizacao.md) | Bootstrap completo, inclui mirror NVD (~1h17 min na primeira vez)             |
| [`dependency-track-readme.md`](./04-security/dependency-track-readme.md) | **Uso recorrente** — depois do bootstrap, ~3-5 segundos de mirror incremental |

### 📋 SBOM versionado · [`reports/`](./04-security/reports/README.md)

SBOMs CycloneDX **versionados no repo** como evidência auditável das dependências analisadas.

---

## 5️⃣ Evidências de Validações· [`05-evidencias/`](./05-evidencias/)

| Documento | Conteúdo |
|---|---|
| [VALIDATION_REPORT.md](./05-evidencias/VALIDATION_REPORT.md) | Relatório completo: build · 100 testes · cobertura JaCoCo · ArchUnit · gate Sonar |
| [Evidencia-analise-de-vulnerabilidades-SCA-00.pdf](./05-evidencias/Evidencia-analise-de-vulnerabilidades-SCA-00.pdf) | Análise SCA - Relatório de vulnerabilidades (parte 1) |
| [Evidencia-analise-de-vulnerabilidades-SCA-01.pdf](./05-evidencias/Evidencia-analise-de-vulnerabilidades-SCA-01.pdf) | Análise SCA - Relatório de vulnerabilidades (parte 2) |
| [Evidencia-analise-de-vulnerabilidades-SCA-02.pdf](./05-evidencias/Evidencia-analise-de-vulnerabilidades-SCA-02.pdf) | Análise SCA - Relatório de vulnerabilidades (parte 3) |
| [Evidencia-analise-de-vulnerabilidades-SCA-03.pdf](./05-evidencias/Evidencia-analise-de-vulnerabilidades-SCA-03.pdf) | Análise SCA - Relatório de vulnerabilidades (parte 4) |
| [Evidencia-analise-de-vulnerabilidades-SCA-04.pdf](./05-evidencias/Evidencia-analise-de-vulnerabilidades-SCA-04.pdf) | Análise SCA - Relatório de vulnerabilidades (parte 5) |
| [Evidencia-analise-de-vulnerabilidades-SCA-05.pdf](./05-evidencias/Evidencia-analise-de-vulnerabilidades-SCA-05.pdf) | Análise SCA - Relatório de vulnerabilidades (parte 6) |
| [Evidencia-analise-de-vulnerabilidades-SCA-06.pdf](./05-evidencias/Evidencia-analise-de-vulnerabilidades-SCA-06.pdf) | Análise SCA - Relatório de vulnerabilidades (parte 7) |
| [Evidencia-Analises-SonarQube-01.pdf](./05-evidencias/Evidencia-Analises-SonarQube-01.pdf) | Análise estática SonarQube (parte 1) |
| [Evidencia-Analises-SonarQube-02.pdf](./05-evidencias/Evidencia-Analises-SonarQube-02.pdf) | Análise estática SonarQube (parte 2) |
| [Evidencia-Swagger-UI-projeto-oficina-01.pdf](./05-evidencias/Evidencia-Swagger-UI-projeto-oficina-01.pdf) | Documentação Swagger UI (parte 1) |
| [Evidencia-Swagger-UI-projeto-oficina-02.pdf](./05-evidencias/Evidencia-Swagger-UI-projeto-oficina-02.pdf) | Documentação Swagger UI (parte 2) |
| [Evidencia-Tabelas-Relacional-PostgreSQL-Oficina.pdf](./05-evidencias/Evidencia-Tabelas-Relacional-PostgreSQL-Oficina.pdf) | Relatório das tabelas do banco de dados PostgreSQL |

---

## ✏️ Convenções

- Documentos numerados com prefixo `NN-` para deixar a ordem de leitura explícita
- Diagramas em **Mermaid** (renderizados nativamente pelo GitHub) ou **`.png`** quando exportados de outras ferramentas
- SBOMs em formato CycloneDX 1.5 (XML + JSON)

