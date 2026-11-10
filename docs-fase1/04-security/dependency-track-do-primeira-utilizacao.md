# Dependency-Track local — guia de instalação **do zero** (PC novo)

> Cenário: você está em um **PC novo** (ou outro colaborador), sem nada instalado. Vai subir o Dependency-Track local, baixar todos os ~348 mil CVEs do NVD, e analisar o SBOM do projeto.
>
> **Tempo estimado**: ~1h30 (mirror NVD = 1h17 + setup = 13 min).
> **Plataforma**: Windows + PowerShell + Docker Desktop.

---

## Sumário

- [0. Pré-requisitos do PC](#0-pré-requisitos-do-pc)
- [1. Clonar o repositório](#1-clonar-o-repositório)
- [2. Subir o Dependency-Track](#2-subir-o-dependency-track)
- [3. Login e troca de senha](#3-login-e-troca-de-senha)
- [4. Configurar NVD REST API](#4-configurar-nvd-rest-api)
- [5. Habilitar Internal Analyzer com fuzzy PURL](#5-habilitar-internal-analyzer-com-fuzzy-purl)
- [6. Criar API key do team Automation](#6-criar-api-key-do-team-automation)
- [7. Disparar e aguardar mirror NVD (~1h17min)](#7-disparar-e-aguardar-mirror-nvd-1h17min)
- [8. Setar API key na sessão do PowerShell](#8-setar-api-key-na-sessão-do-powershell)
- [9. Gerar o SBOM do projeto](#9-gerar-o-sbom-do-projeto)
- [10. Upload do SBOM](#10-upload-do-sbom)
- [11. Ver resultado na UI](#11-ver-resultado-na-ui)
- [12. Comandos de manutenção](#12-comandos-de-manutenção)
- [Troubleshooting](#troubleshooting)
- [Referência rápida (cola e executa)](#referência-rápida-cola-e-executa)

---

## 0. Pré-requisitos do PC

Instala, nessa ordem:

### a) Docker Desktop

- Download: <https://www.docker.com/products/docker-desktop>
- Após instalar, abre o app e vai em **Settings → Resources**:
  - **Memory**: ≥ **6 GB** (o apiserver pede 4 GB; com folga pra OS é mais seguro)
  - **CPUs**: ≥ 2
- No Windows: garante que o **WSL 2** está habilitado (o instalador do Docker Desktop guia)

### b) Git

- Download: <https://git-scm.com/download/win>
- Após instalar, no PowerShell:
  ```powershell
  git --version
  ```
  Tem que retornar algo como `git version 2.x`.

### c) Java 21 (LTS)

- Necessário pra rodar o `mvnw` (gerar o SBOM).
- Download: <https://adoptium.net/temurin/releases/?version=21>
- Após instalar:
  ```powershell
  java -version
  ```
  Esperado: `openjdk version "21.x"`.

### d) NVD API Key

- Solicita gratuita em: <https://nvd.nist.gov/developers/request-an-api-key>
- Chega por email em ~1 minuto. **Anota essa chave** — você vai colar no Dep-Track no passo 4.
- Sem ela, o mirror leva **horas** (rate limit de 5 req/30s vs 50 req/30s com chave).

---

## 1. Clonar o repositório

```powershell
cd C:\
mkdir projetos
cd C:\projetos
git clone https://github.com/seu-usuario/seu-projeto.git
cd seu-projeto
```

> Se você já tem o repo em outro caminho, só use `cd <seu-caminho>` e pula esse passo.

Verifica que os arquivos certos estão presentes:

```powershell
ls docker-compose.dep-track.yml, pom.xml
```

Os dois têm que aparecer.

---

## 2. Subir o Dependency-Track

```powershell
docker compose -f docker-compose.dep-track.yml up -d
```

Na primeira vez, o Docker baixa as imagens (~1 GB total, ~3 min). Aguarda até aparecer:

```
✔ Container oficina-dtrack-apiserver Started
✔ Container oficina-dtrack-frontend  Started
```

Espera ~2 min até o servidor ficar pronto:

```powershell
docker compose -f docker-compose.dep-track.yml logs --tail 50 apiserver | findstr "ready"
```

Esperado:
```
oficina-dtrack-apiserver  | ... INFO [AlpineServlet] Dependency-Track is ready
```

Abre no navegador: <http://localhost:9090>

---

## 3. Login e troca de senha

1. Login: **`admin`** / **`admin`**
2. Sistema força troca de senha → cria uma nova
3. **⚠️ Anota a senha** (não há recuperação automática no Dep-Track 4.x)

---

## 4. Configurar NVD REST API

**Administração** (engrenagem ⚙️ no menu lateral) → **Fontes de vulnerabilidade** → aba **National Vulnerability Database (NVD)**.

| Campo | Valor |
|---|---|
| Habilitar espelhamento via API | ☑ marcado (azul com ✓) |
| API key | Sua chave do passo 0d (ex: `c666d2ax-2008-40a0-b30e-c922cc48a857`) |
| Última modificação (UTC) — **data** | **(deixa VAZIO)** |
| Última modificação (UTC) — **hora** | **(deixa VAZIO)** |

> **⚠️ Crítico**: o campo "Última modificação" precisa estar **vazio** pra forçar o mirror cheio. Se tiver qualquer data, ele só faz incremental e a análise vai ficar zerada.

Clica **Atualizar**.

---

## 5. Habilitar Internal Analyzer com fuzzy PURL

**Administração** → **Analisadores** → aba **interno**.

Marca os 4 checkboxes (todos azuis com ✓):
- ☑ Habilitar analisador interno
- ☑ **Habilite a correspondência difusa de CPE em componentes que possuem um URL de pacote (PURL) definido** ← este é o crítico
- ☑ (e mais 2 que vêm marcados por padrão)

Clica **Atualizar**.

> Sem o **fuzzy PURL matching** o Dep-Track não consegue cruzar `pkg:maven/...` (formato dos componentes do SBOM Maven) com `cpe:2.3:a:...` (formato dos CVEs do NVD). Resultado: análise sempre retorna zero.

---

## 6. Criar API key do team Automation

**Administração** → **Access Management** → **Teams** → seleciona **Automation**.

### a) Confere as permissões (marca as que faltarem):

- `BOM_UPLOAD`
- `PORTFOLIO_MANAGEMENT`
- `PROJECT_CREATION_UPLOAD`
- `VIEW_PORTFOLIO`
- `VULNERABILITY_ANALYSIS`

### b) Vai na aba **API Keys** → clica **+ Create**.

Aparece uma nova chave no formato `odt_...`. **Copia ela inteira** (botão de copiar ao lado).

> Anota essa chave em algum lugar seguro — ela só é mostrada uma vez completa.

---

## 7. Disparar e aguardar mirror NVD (~1h17min)

### Reinicia o apiserver (pra aplicar a config do NVD):

```powershell
docker compose -f docker-compose.dep-track.yml restart apiserver
```

Aguarda ~30s.

### Acompanha o progresso (a cada 10-15 min):

```powershell
docker compose -f docker-compose.dep-track.yml logs --tail 200 apiserver | findstr /i "Mirrored Mirroring"
```

Esperado (sequência de ~1h17):
```
NistApiMirrorTask: CVEs were not previously mirrored via NVD API; Will mirror all CVEs
NistApiMirrorTask: Mirrored 2000/348451 CVEs (0%)
NistApiMirrorTask: Mirrored 50000/348451 CVEs (14%)
NistApiMirrorTask: Mirrored 100000/348451 CVEs (28%)
... (continua) ...
NistApiMirrorTask: Mirrored 348000/348451 CVEs (99%)
NistApiMirrorTask: Mirroring of 348451 CVEs completed in PT1H17M…S
EpssMirrorTask: EPSS mirroring complete
```

### O que NÃO se preocupar durante o mirror:

- Avisos `[NistMirrorTask] Encountered retryable exception` → ruído de uma task **legada** que tenta feeds JSON antigos do NVD. Ignorável.
- Avisos `[OssIndexAnalysisTask] HTTP Status : 401` → falta token do OSS Index. Ignorável.

### Importante:

**Pode fechar o PowerShell** durante a espera. O mirror roda **dentro do container Docker**, não na sua sessão do terminal. Quando voltar, é só rodar de novo o comando de progresso pra ver onde está.

---

## 8. Setar API key na sessão do PowerShell

Depois que o mirror terminar, abre o PowerShell, vai pra pasta do projeto, e seta a API key copiada no passo 6:

```powershell
cd C:\projetos\oficina-mecanica

$env:DTRACK_API_KEY = "odt_cole_aqui_a_chave_copiada_no_passo_6"
```

> O env var **só vive nessa janela** do PowerShell. Se abrir uma nova aba, repete.

---

## 9. Gerar o SBOM do projeto

```powershell
./mvnw -DskipTests package
```

Na primeira vez, o Maven baixa as dependências (~5 min). Depois disso, fica em cache local em `C:\Users\<seu-user>\.m2\repository`.

Esperado no final:
```
[INFO] BUILD SUCCESS
```

Verifica o SBOM gerado:

```powershell
ls target\bom.xml, target\bom.json
```

Os dois aparecem (96 componentes, formato CycloneDX 1.5).

---

## 10. Upload do SBOM

```powershell
curl.exe -X POST http://localhost:9091/api/v1/bom `
  -H "X-Api-Key: $env:DTRACK_API_KEY" `
  -F "autoCreate=true" `
  -F "projectName=oficina-backend" `
  -F "projectVersion=0.1.0-SNAPSHOT" `
  -F "bom=@target/bom.xml"
```

Resposta esperada:
```json
{"token":"e5be0685-ced0-4ae3-8efa-f90054e88647"}
```

> Esse `token` é só pra acompanhar o processamento — ele **NÃO** é uma API key.

### Acompanha a análise (opcional, ~1-2 min):

```powershell
docker compose -f docker-compose.dep-track.yml logs --tail 50 apiserver | findstr /i "BomUpload Analysis"
```

Esperado:
```
BomUploadProcessingTask: Processing CycloneDX BOM uploaded to project: ...
BomUploadProcessingTask: Processed 96 components and 0 services
InternalAnalysisTask: Analyzing 96 component(s)
InternalAnalysisTask: Internal analysis complete
VulnerabilityAnalysisTask: ... complete
```

---

## 11. Ver resultado na UI

1. Abre <http://localhost:9090/projects>
2. Clica em **oficina-backend / 0.1.0-SNAPSHOT**
3. **5 círculos no topo direito** se populam:
  - 🔴 **Crítico**: 0
  - 🟠 **Alto**: 7
  - 🟡 **Médio**: 2
  - 🟢 **Baixo**: 0
  - ⚪ **Não atribuído**: 0
4. Aba **Componentes**: 96
5. Aba **Vulnerabilidades de auditoria**: lista os 9 CVEs com CVSS, descrição e versão de correção

> Pode levar 1-2 min após o upload pra UI atualizar — dá **F5** se demorar.

---

## 12. Comandos de manutenção

| Operação | Comando | Preserva mirror? |
|---|---|---|
| Reiniciar serviço | `docker compose -f docker-compose.dep-track.yml restart apiserver` | ✅ |
| Parar (mantém volume) | `docker compose -f docker-compose.dep-track.yml down` | ✅ |
| Subir novamente | `docker compose -f docker-compose.dep-track.yml up -d` | ✅ |
| **Resetar TUDO** (perde mirror, espera 1h17 de novo) | `docker compose -f docker-compose.dep-track.yml down -v` | ❌ |

### Validar que o mirror está completo via API:

```powershell
curl.exe http://localhost:9091/api/v1/metrics/vulnerability -H "X-Api-Key: $env:DTRACK_API_KEY"
```

Retorna JSON com totais. Se vier ~348K cadastrados, mirror tá completo.

---

## Troubleshooting

| Sintoma | Causa | Solução |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker Desktop não está rodando | Abrir o Docker Desktop e aguardar 1 min |
| `Max Memory: 1.x GB` no log | Docker Desktop com pouca RAM | Aumentar pra 6 GB em Settings → Resources |
| Mirror para no `Mirrored 0/348451 CVEs` por mais de 5 min | API key inválida ou rate-limited | Conferir chave em Admin → Fontes de vulnerabilidade → NVD |
| Mirror saiu em 3-5 segundos com **dezenas** de CVEs (não centenas de milhares) | Campo "Última modificação" não foi apagado → sync incremental, não cheio | Apagar campo (passo 4), restart apiserver |
| Dashboard mostra **0 CVEs** após upload com 96 componentes | Internal Analyzer fuzzy PURL desligado, OU mirror incompleto | Conferir passo 5 (fuzzy PURL marcado) e validar mirror via API (passo 12) |
| Login admin/admin falha | Senha já foi trocada (passo 3) — você esqueceu a nova | `down -v` + `up -d` (reseta volume e perde mirror) |
| `OssIndexAnalysisTask: 401 Unauthorized` no log | OSS Index sem credencial (anônimo é limitado) | Ignorar — análise pelo NVD/Internal não depende disso |
| `Not authorized` no `mvn sonar:sonar` (SonarQube) | PowerShell não passou o token via `-D` | Usar env vars: `$env:SONAR_TOKEN = "..."` antes de rodar |
| `\` na linha do PowerShell dá erro | `\` é continuação do **Bash**, não PowerShell | Usar `` ` `` (backtick) para continuar linhas no PowerShell |

---

## Após o setup inicial

Próximas vezes que precisar analisar uma nova versão do projeto:

1. Pula direto pro passo 9 (gerar SBOM) e 10 (upload)
2. O mirror NVD é mantido no volume e atualiza automaticamente em background a cada hora (sync incremental, segundos)
3. Use o mesmo `projectName` + `projectVersion` pra atualizar in-place, ou mude a versão pra criar um histórico
