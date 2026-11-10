# SBOM versionado — evidência de varredura de dependências

Esta pasta guarda **SBOMs** (Software Bills of Materials) gerados pelo
`cyclonedx-maven-plugin` em momentos significativos do projeto, como
evidência auditavel das dependências analisadas para vulnerabilidades.

## Como o SBOM é gerado

```bash
./mvnw -DskipTests package cyclonedx:makeAggregateBom
```

Saídas em `target/`:

| Arquivo | Formato | Uso |
|---|---|---|
| `bom.xml` | CycloneDX 1.5 (XML) | Upload para Dependency-Track, Snyk, etc. |
| `bom.json` | CycloneDX 1.5 (JSON) | Inspecão programática, diffs entre versões |

## SBOMs versionados

| Arquivo | Data | Componentes | Versão do plugin | Spec CycloneDX |
|---|---|---|---|---|
| [`bom-2026-05-05.xml`](./bom-2026-05-05.xml) / [`.json`](./bom-2026-05-05.json) | 2026-05-05 | 96 | `cyclonedx-maven-plugin:2.8.2` | 1.5 |

## Como atualizar

Após mudanças significativas no `pom.xml` (upgrade de Spring Boot, troca
de driver, nova dependência crítica), regenere o SBOM e versione um novo
arquivo aqui:

```bash
./mvnw -DskipTests package cyclonedx:makeAggregateBom
cp target/bom.xml "docs/security/reports/bom-$(date -u +%Y-%m-%d).xml"
cp target/bom.json "docs/security/reports/bom-$(date -u +%Y-%m-%d).json"
```

E adicione uma linha à tabela acima com a contagem de componentes.

## Como verificar contra CVEs

O SBOM por si só **não contém informações de vulnerabilidade** — é apenas
o inventário. Para a análise de CVEs:

1. **Dependency-Track local** (recomendado): suba via
   `docker compose -f docker-compose.dep-track.yml up -d` e faça upload
   do `bom.xml`. Veja
   [`docs/security/dependency-track-guide.md`](../dependency-track-guide.md).

2. **CLI estática**: ferramentas como
   [`cdxgen`](https://github.com/CycloneDX/cdxgen),
   [`grype`](https://github.com/anchore/grype),
   [`osv-scanner`](https://github.com/google/osv-scanner) consomem o
   `bom.xml` e produzem relatórios offline.

3. **OWASP Dependency-Check (profile)**:
   `./mvnw -Powasp org.owasp:dependency-check-maven:check` faz a análise
   direta sobre o `pom.xml` (sem usar o SBOM, abordagem alternativa).

## Por que versionar o SBOM no repositório?

- **Rastreabilidade**: relacionar exatamente quais versões de quais
  bibliotecas estavam no produto em cada release.
- **Conformidade**: leis americanas (Executive Order 14028, CISA SBOM
  Guidance) e europeias (Cyber Resilience Act) **exigem SBOM** em
  software entregue a entidades governamentais e para infraestrutura
  crítica.
- **Resposta a incidentes**: quando uma CVE crítica nova é divulgada
  (ex.: Log4Shell), o time pode rapidamente identificar versões
  afetadas com `grep` no SBOM.
- **Auditoria de licenças**: o SBOM lista a licença de cada componente,
  útil para revisão jurídica.

## Tamanho dos arquivos

Os SBOMs gerados ocupam ~200-300 KB cada, então versionar múltiplas
gerações no git é barato e prático.
