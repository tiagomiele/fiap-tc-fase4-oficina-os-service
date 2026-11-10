# Evolução do banco de dados

## Escolha do PostgreSQL

O PostgreSQL será mantido porque o domínio possui relacionamentos transacionais entre clientes, veículos, ordens, orçamentos, estoque e financeiro. Integridade referencial, transações ACID, constraints e índices relacionais são necessários para manter consistência.

## Ajustes da Semana 3

### Autenticação por CPF

- índice único para documento normalizado;
- consulta eficiente por documento e status ativo;
- proibição de CPF em logs e métricas;
- revisão das constraints de documento.

### Histórico de status

A migration V3 cria uma estrutura de histórico para medir diagnóstico, execução e finalização, contendo:

- Ordem de Serviço;
- status;
- entrada no status;
- saída do status;
- duração calculada;
- identificador de correlação.

As transições permanecerão controladas pelo domínio e serão gravadas na mesma transação da OS.

### Índices candidatos

- cliente por documento normalizado e ativo;
- OS por status e data de criação;
- OS por cliente;
- histórico por OS e data;
- histórico por status e período;
- movimentações de estoque por SKU e data.

A escolha final será confirmada por consultas reais e planos de execução, evitando índices desnecessários.

## Responsabilidades

- o repositório de banco provisiona RDS, rede, segurança, backup e parâmetros;
- o repositório da aplicação mantém as migrações Flyway e a evolução do schema;
- nenhuma senha ou string completa de conexão será exposta em outputs públicos.

## Entregáveis posteriores

- diagrama entidade-relacionamento atualizado;
- evidências de integridade e desempenho em RDS;
- revisão dos índices com planos de execução reais.
