# Análise SCA com Dependency-Track (local)

> Detecção de CVEs nas dependências Maven do projeto via **SBOM CycloneDX + base NVD**.
> Validado em **Windows + PowerShell + Docker Desktop**.

> 🔄 **Esta é a referência rápida (uso recorrente).** Se for a **primeira vez** rodando em um PC novo (sem mirror NVD), use [`dependency-track-do-primeira-utilizacao.md`](dependency-track-do-primeira-utilizacao.md) — o bootstrap inclui ~1h17 de download do NVD.

## Pré-requisitos

- Docker Desktop com **RAM ≥ 6 GB** (Settings → Resources)
- Chave da API do NVD (gratuita): <https://nvd.nist.gov/developers/request-an-api-key>
- Repositório clonado, terminal aberto **na pasta raiz do projeto** (onde estão `docker-compose.dep-track.yml` e `pom.xml`)

> ⚠️ **PONTO IMPORTANTE**: todos os comandos abaixo precisam ser executados a partir da **pasta raiz do projeto** (a aplicação, banco e validações compartilham essa raiz).

---

## Passo a passo (PowerShell)

### 1. Subir o Dependency-Track

```powershell
docker compose -f docker-compose.dep-track.yml up -d
```

Aguarda o servidor ficar pronto:

```powershell
docker compose -f docker-compose.dep-track.yml logs --tail 50 apiserver | findstr "ready"
```

> Esperado: `Dependency-Track is ready`. UI em <http://localhost:9090>.

### 2. Configurar (somente na 1ª execução, persiste no volume)

Login inicial: **`admin`** / **`admin`** → trocar senha.

| Tela | Configuração |
|---|---|
| Administração → Fontes de vulnerabilidade → **NVD** | ☑ Habilitar espelhamento via API · API key colada · campo "Última modificação" **vazio** · **Atualizar** |
| Administração → Analisadores → **interno** | ☑ Habilitar analisador interno · ☑ **Correspondência difusa de CPE com PURL** · **Atualizar** |
| Administração → Access Management → Teams → **Automation** | Confere permissões: `BOM_UPLOAD`, `PROJECT_CREATION_UPLOAD`, `PORTFOLIO_MANAGEMENT`, `VIEW_PORTFOLIO`, `VULNERABILITY_ANALYSIS` · Aba **API Keys** → **+ Create** → copiar a chave gerada (formato `odt_...`) |

> 🔒 **NVD API key**: você cola **a sua chave pessoal** (a que recebeu por email do NIST). É um UUID no formato `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`. Esta a ser incluida dentro de fontes de vulnerabilidade, banco de dados de vulnerabilidades que o Dependency-Track usa para cruzar com as dependências do projeto. O mirror do NVD é feito por máquina (não tem como compartilhar a chave entre pessoas) e a chave é gratuita, então cada um deve usar a sua própria. Se tiver mais de uma chave, pode usar qualquer uma delas — não há limite de uso.
>
> 🔒 **API key do Dependency-Track (`odt_...`)**: é gerada **dentro da sua instalação local**. Cada pessoa que rodar terá uma chave diferente — não tem como reaproveitar a de outra pessoa.

### 3. Disparar mirror NVD

```powershell
docker compose -f docker-compose.dep-track.yml restart apiserver
```

Acompanha o progresso:

```powershell
docker compose -f docker-compose.dep-track.yml logs --tail 200 apiserver | findstr /i "Mirrored Mirroring"
```

**Cenários esperados:**

- **Volume novo (1ª vez no PC)**: aparece `Will mirror all CVEs` e demora **~1h17min** até `Mirroring of ~348451 CVEs completed`
- **Volume reusado** (compose já foi rodado antes nessa máquina): só sync incremental, **~3-5 segundos** com algumas dezenas/centenas de CVEs novos

Exemplo de log (sync incremental):
```
INFO [NistMirrorTask] Starting NIST mirroring task
INFO [NistApiMirrorTask] Mirroring CVEs that were modified since 2026-XX-XXTXX:XX:XXZ
INFO [NistApiMirrorTask] Mirroring of N CVEs completed in PT...S
INFO [EpssMirrorTask] EPSS mirroring complete
```

### 4. Setar API key na sessão atual

```powershell
$env:DTRACK_API_KEY = "<COLE_AQUI_A_API_KEY_odt_GERADA_NO_PASSO_2>"
```

> O env var só vive nesta janela do PowerShell. Em uma nova aba/sessão, repete o comando.

### 5. Gerar SBOM

```powershell
./mvnw -DskipTests package
```

> Saída: `target/bom.xml` (~96 componentes para o estado atual do projeto, formato CycloneDX 1.5).

### 6. Upload do SBOM

```powershell
curl.exe -X POST http://localhost:9091/api/v1/bom `
  -H "X-Api-Key: $env:DTRACK_API_KEY" `
  -F "autoCreate=true" `
  -F "projectName=oficina-backend" `
  -F "projectVersion=0.1.0-SNAPSHOT" `
  -F "bom=@target/bom.xml"
```

Resposta: `{"token":"<uuid-de-processamento>"}`. Após ~1 min, abre <http://localhost:9090/projects> → projeto `oficina-backend / 0.1.0-SNAPSHOT` com a contagem de CVEs encontrados nos círculos no topo.

---

## Análise SAST com SonarQube (mesma sessão)

> 🔄 **ALTERADO**: `docker run` solto trocado por `docker compose` (consistente com o resto do projeto e com volume persistente).

### 1. Subir o SonarQube

```powershell
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
```

UI em <http://localhost:9000> (login inicial: `admin/admin`, será pedido para trocar a senha).

### 2. Criar projeto e gerar token

`Projects → Create Project → Local` → nome `oficina-backend` → gerar **Project Analysis Token** → copiar a chave (formato `sqp_...`).

> 🔒 **Token pessoal**: cada pessoa que rodar precisa gerar o seu próprio token na sua instalação local. Não há reaproveitamento entre máquinas.

### 3. Rodar análise (PowerShell — env vars)

> 🔄 **ALTERADO**: token hardcoded substituído por placeholder.

```powershell
$env:SONAR_HOST_URL = "http://localhost:9000"
$env:SONAR_TOKEN = "<COLE_AQUI_O_TOKEN_sqp_GERADO_NO_PASSO_2>"
./mvnw clean verify sonar:sonar
```

> ⚠️ **Nunca** use `\` para quebrar linhas no PowerShell — só funciona em Bash. Use **env vars** (acima) ou backtick `` ` `` com aspas em cada `-D...`.

Resultado em <http://localhost:9000/dashboard?id=oficina-backend>.

---

## Comandos de manutenção

| Operação | Comando | Preserva mirror? |
|---|---|---|
| Reiniciar serviço | `docker compose -f docker-compose.dep-track.yml restart apiserver` | ✅ |
| Parar (mantém volume) | `docker compose -f docker-compose.dep-track.yml down` | ✅ |
| Subir novamente | `docker compose -f docker-compose.dep-track.yml up -d` | ✅ |
| **Resetar tudo** | `docker compose -f docker-compose.dep-track.yml down -v` | ❌ apaga 1h17 de mirror |

---

## Troubleshooting

| Sintoma | Causa | Solução |
|---|---|---|
| Login falha após troca de senha | Senha esquecida (não há recuperação no DT 4.x) | `down -v` + `up -d` (volta `admin/admin`, perde mirror) |
| Dashboard com **0 CVEs** após upload | NVD vazio ou Internal Analyzer sem fuzzy PURL | Conferir passo 2 (NVD com data vazia + restart) e passo 2 (Analisador interno marcado) |
| `Not authorized` no `mvn sonar:sonar` | PowerShell não passou o token | Usar env vars (passo 3 do SonarQube) |
| `OssIndexAnalysisTask: 401 Unauthorized` no log | OSS Index sem credencial | Ignorável — análise pelo NVD/Internal não depende disso |
| Aviso `volume already exists but was created for project X` | Outro `docker compose` em pasta diferente está reusando o volume nomeado `oficina-dtrack-data` | Esperado: o volume é compartilhado e o mirror é reaproveitado |

---
