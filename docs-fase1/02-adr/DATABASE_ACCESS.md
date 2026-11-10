# Acesso ao banco de dados (PostgreSQL no Docker)

Este documento mostra **como conectar e consultar** o PostgreSQL que sobe junto com a aplicação via `docker compose up`. Cobre 3 caminhos — Adminer (UI no navegador), `psql` dentro do container, cliente externo no host — e lista todas as tabelas criadas pelas migrations Flyway com exemplos de `SELECT`.

## 1. Pré-requisitos

Suba a stack completa a partir da raiz do projeto:

```bash
docker compose up --build -d
```

Confira que os 3 serviços estão saudáveis:

```bash
docker compose ps
```

Saída esperada (status `Up` e `healthy` no `db`):

```
NAME              IMAGE                 STATUS                   PORTS
oficina-adminer   adminer:4             Up                       0.0.0.0:8081->8080/tcp
oficina-app       oficina-backend       Up                       0.0.0.0:8080->8080/tcp
oficina-db        postgres:16-alpine    Up (healthy)             0.0.0.0:5432->5432/tcp
```

## 2. Credenciais e conexão (resumo)

Estes valores são definidos no [`docker-compose.yml`](../../docker-compose.yml) e usados pela aplicação via as variáveis `DB_URL`, `DB_USER`, `DB_PASSWORD`:

| Campo | Valor (default do compose) |
|---|---|
| Host (de dentro da rede do compose) | `db` |
| Host (do seu computador, fora do compose) | `localhost` |
| Porta | `5432` |
| Banco de dados | `oficina` |
| Usuário | `oficina` |
| Senha | `oficina` |
| JDBC URL (usada pela app) | `jdbc:postgresql://db:5432/oficina` |

> Credenciais para ambiente **local de desenvolvimento**. Em produção, troque pelas env vars `DB_URL`, `DB_USER`, `DB_PASSWORD`.

## 3. Opção A — Adminer (UI no navegador, mais simples)

O `adminer` já está subindo pelo docker-compose na porta `8081`.

1. Abra `http://localhost:8081`.
2. Preencha a tela de login:
   - **System**: `PostgreSQL`
   - **Server**: `db` _(é o nome do serviço no compose; o Adminer está na mesma rede do Postgres)_
   - **Username**: `oficina`
   - **Password**: `oficina`
   - **Database**: `oficina`
3. Clique em **Login**. Na barra lateral aparecem todas as tabelas; clique em qualquer uma para ver dados, estrutura e rodar SQL custom via aba **SQL command**.

## 4. Opção B — `psql` dentro do container (sem instalar nada no host)

Se você já está com o compose de pé, tem `psql` pronto dentro do container do Postgres:

```bash
docker compose exec db psql -U oficina -d oficina
```

Não vai pedir senha (há `trust` local no postgres image do compose).

Dentro do prompt `oficina=#` você pode usar os metacomandos do `psql`:

```sql
\dt                    -- lista todas as tabelas
\d clientes            -- descreve colunas, índices e constraints da tabela 'clientes'
\d+ ordens_servico     -- versão detalhada com tamanhos
\du                    -- lista roles/users do banco
\dn                    -- lista schemas
SELECT version();      -- versão do Postgres
\q                     -- sai
```

Para rodar um SQL único sem entrar no REPL:

```bash
docker compose exec db psql -U oficina -d oficina -c "SELECT numero, status FROM ordens_servico ORDER BY criado_em DESC LIMIT 20;"
```

## 5. Opção C — Cliente no host (psql local, DBeaver, pgAdmin, DataGrip…)

A porta `5432` está publicada no host (`ports: "5432:5432"`), então qualquer cliente de Postgres no seu computador consegue conectar:

| Campo | Valor |
|---|---|
| Host | `localhost` |
| Porta | `5432` |
| Banco | `oficina` |
| Usuário | `oficina` |
| Senha | `oficina` |

Exemplo com `psql` instalado localmente:

```bash
PGPASSWORD=oficina psql -h localhost -p 5432 -U oficina -d oficina
```

Com DBeaver/pgAdmin: crie uma nova conexão "PostgreSQL" com os valores acima.

> Se a porta `5432` já estiver em uso na sua máquina (outro Postgres local), troque a publicação no `docker-compose.yml` (ex.: `"55432:5432"`) e use `-p 55432`.

## 6. Tabelas criadas pelas migrations Flyway

As migrations ficam em [`src/main/resources/db/migration/`](../../src/main/resources/db/migration) e rodam automaticamente no boot da app. Para ver o estado atual:

```sql
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Lista completa das tabelas (além da `flyway_schema_history`):

| Tabela | Origem | Papel no domínio |
|---|---|---|
| `users` | V1 | Funcionários da oficina e técnicos (login JWT) |
| `clientes` | V2 | Clientes pessoa física ou jurídica |
| `veiculos` | V2 | Veículos vinculados a um cliente |
| `servicos` | V3 | Catálogo de serviços (nome, preço base, tempo estimado) |
| `pecas` | V3 | Catálogo de peças com controle de estoque |
| `ordens_servico` | V4 | Ordens de Serviço (agregado raiz) |
| `itens_ordem_servico` | V4 | Itens = orçamento da OS (serviços + peças com preço congelado) |
| `ordens_numero_sequencia` | V4 | Contador atômico por ano para gerar o número `OS-YYYY-NNNNNN` |

### Consultas prontas por bounded context

Cole qualquer bloco dentro do `psql` ou do Adminer:

**Identity / Usuários**
```sql
SELECT id, email, nome, papel, ativo FROM users ORDER BY email;
```

**Clientes**
```sql
SELECT id, documento, tipo_documento, nome, email, telefone, ativo, criado_em
FROM clientes
ORDER BY nome;
```

**Veículos (com dono)**
```sql
SELECT v.placa, v.marca, v.modelo, v.ano, c.documento, c.nome
FROM veiculos v
JOIN clientes c ON c.id = v.cliente_id
ORDER BY c.nome, v.placa;
```

**Catálogo — Serviços**
```sql
SELECT nome, preco_base, tempo_estimado_minutos, ativo
FROM servicos
ORDER BY nome;
```

**Catálogo — Peças e estoque**
```sql
SELECT sku, nome, preco_venda, estoque, ativo
FROM pecas
ORDER BY sku;

-- Apenas peças com estoque baixo
SELECT sku, nome, estoque FROM pecas WHERE estoque < 5 ORDER BY estoque ASC;
```

**Ordens de Serviço — lista administrativa**
```sql
SELECT os.numero,
       os.status,
       c.nome  AS cliente,
       v.placa AS placa,
       os.valor_orcamento,
       os.recebida_em,
       os.entregue_em
FROM ordens_servico os
JOIN clientes c ON c.id = os.cliente_id
JOIN veiculos v ON v.id = os.veiculo_id
ORDER BY os.recebida_em DESC;
```

**Ordem de Serviço — itens / orçamento de uma OS específica**
```sql
SELECT i.tipo_item, i.descricao, i.quantidade, i.preco_unitario,
       (i.quantidade * i.preco_unitario) AS subtotal
FROM itens_ordem_servico i
JOIN ordens_servico os ON os.id = i.ordem_servico_id
WHERE os.numero = 'OS-2026-000001'
ORDER BY i.tipo_item, i.descricao;
```

**Sequência atual do número da OS**
```sql
SELECT ano, ultimo_seq FROM ordens_numero_sequencia ORDER BY ano DESC;
```

**Relatório — tempo médio de execução por serviço**
```sql
SELECT i.referencia_id AS servico_id,
       ROUND(AVG(EXTRACT(EPOCH FROM (os.finalizada_em - os.em_execucao_em)) / 60)::numeric, 2) AS tempo_medio_minutos,
       COUNT(*) AS amostras
FROM ordens_servico os
JOIN itens_ordem_servico i ON i.ordem_servico_id = os.id
WHERE i.tipo_item = 'SERVICO'
  AND os.em_execucao_em IS NOT NULL
  AND os.finalizada_em IS NOT NULL
GROUP BY i.referencia_id
ORDER BY amostras DESC;
```

## 7. Perguntas frequentes

**Preciso de senha para o `psql` dentro do container?**
Não. O `docker compose exec db psql ...` entra como o user `oficina` via socket local, que é `trust` no image oficial.

**Consigo conectar do host sem instalar `psql`?**
Sim — use o Adminer (`http://localhost:8081`) ou qualquer GUI (DBeaver/pgAdmin). A porta `5432` está publicada.

**Como resetar o banco do zero?**
```bash
docker compose down -v
docker compose up --build -d
```
O `-v` apaga o volume `oficina-db-data` (destrutivo). A app recria o schema rodando as migrations Flyway no boot seguinte.

**A app diz "schema validation failed"/"table not found" no boot. E agora?**
Isso acontece se o volume do Postgres está antigo (de outra versão do schema). Rode o `docker compose down -v` acima para limpar e deixar as migrations rodarem do zero.

**Onde fica o usuário admin da API?**
Na tabela `users`, seed feita pela app no primeiro boot. Credenciais default vêm das env vars `ADMIN_EMAIL` (`admin@oficina.local`) e `ADMIN_PASSWORD` (`admin123`). Confira com:
```sql
SELECT email, papel, ativo FROM users;
```
