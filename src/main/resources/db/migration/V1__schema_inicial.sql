-- ============================================================================
-- V1__schema_inicial.sql — MVP v2 da oficina mecânica
-- Modelo único, reset completo do banco em relação à v1.
-- ============================================================================

-- ---------- identidade ------------------------------------------------------
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    papel VARCHAR(32) NOT NULL CHECK (papel IN ('FUNCIONARIO_DA_OFICINA', 'TECNICO_DA_OFICINA')),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- clientes --------------------------------------------------------
CREATE TABLE clientes (
    id_cliente BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    documento VARCHAR(20) NOT NULL UNIQUE,
    tipo_documento VARCHAR(4) NOT NULL CHECK (tipo_documento IN ('CPF', 'CNPJ')),
    email VARCHAR(255),
    telefone VARCHAR(32),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- veículos (PK composta) -----------------------------------------
CREATE TABLE veiculos (
    id_placa VARCHAR(8) NOT NULL,
    id_cliente BIGINT NOT NULL REFERENCES clientes(id_cliente),
    marca VARCHAR(64) NOT NULL,
    modelo VARCHAR(64) NOT NULL,
    ano INTEGER NOT NULL CHECK (ano BETWEEN 1900 AND 2100),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id_placa, id_cliente)
);
CREATE INDEX idx_veiculos_cliente ON veiculos(id_cliente);

-- ---------- catálogo de serviços -------------------------------------------
CREATE TABLE servicos (
    id_servico BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    descricao VARCHAR(500),
    preco_base NUMERIC(12, 2) NOT NULL CHECK (preco_base >= 0),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- catálogo de peças (sem coluna de estoque) ----------------------
CREATE TABLE pecas (
    id_sku BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    preco_venda NUMERIC(12, 2) NOT NULL CHECK (preco_venda >= 0),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- estoque (saldo atual) ------------------------------------------
CREATE TABLE estoque_pecas (
    id_sku BIGINT PRIMARY KEY REFERENCES pecas(id_sku),
    quantidade INTEGER NOT NULL DEFAULT 0 CHECK (quantidade >= 0),
    data_hora TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ---------- estoque (histórico de movimentação) ----------------------------
CREATE TABLE movimentacao_estoque_pecas (
    id BIGSERIAL PRIMARY KEY,
    id_sku BIGINT NOT NULL REFERENCES pecas(id_sku),
    quantidade INTEGER NOT NULL CHECK (quantidade <> 0),
    origem VARCHAR(32) NOT NULL CHECK (origem IN (
        'ENTRADA_NF', 'ESTORNO_NF', 'CONSUMO_ORCAMENTO', 'DEVOLUCAO_ORCAMENTO')),
    numero_nota VARCHAR(30),
    serie_nota VARCHAR(10),
    cnpj_fornecedor VARCHAR(20),
    data_emissao DATE,
    id_ordem_servico VARCHAR(20),
    id_orcamento INTEGER,
    id_orcamento_item INTEGER,
    data_hora TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_mov_estoque_sku ON movimentacao_estoque_pecas(id_sku);
CREATE INDEX idx_mov_estoque_os ON movimentacao_estoque_pecas(id_ordem_servico);

-- ---------- suprimentos: NF fornecedor -------------------------------------
CREATE TABLE notas_fiscais_fornecedor (
    numero_nota VARCHAR(30) NOT NULL,
    serie_nota VARCHAR(10) NOT NULL,
    cnpj_fornecedor VARCHAR(20) NOT NULL,
    data_emissao DATE NOT NULL,
    nome_fornecedor VARCHAR(255) NOT NULL,
    valor_total NUMERIC(14, 2) NOT NULL CHECK (valor_total >= 0),
    estornada BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (numero_nota, serie_nota, cnpj_fornecedor, data_emissao)
);

CREATE TABLE itens_nota_fiscal_fornecedor (
    numero_nota VARCHAR(30) NOT NULL,
    serie_nota VARCHAR(10) NOT NULL,
    cnpj_fornecedor VARCHAR(20) NOT NULL,
    data_emissao DATE NOT NULL,
    id_sku BIGINT NOT NULL REFERENCES pecas(id_sku),
    quantidade INTEGER NOT NULL CHECK (quantidade > 0),
    preco_unitario NUMERIC(12, 2) NOT NULL CHECK (preco_unitario >= 0),
    PRIMARY KEY (numero_nota, serie_nota, cnpj_fornecedor, data_emissao, id_sku),
    FOREIGN KEY (numero_nota, serie_nota, cnpj_fornecedor, data_emissao)
        REFERENCES notas_fiscais_fornecedor(numero_nota, serie_nota, cnpj_fornecedor, data_emissao)
);

-- ---------- numerador da OS (sequência mensal) -----------------------------
CREATE TABLE numero_os_sequencia (
    mes INTEGER NOT NULL CHECK (mes BETWEEN 1 AND 12),
    ano INTEGER NOT NULL CHECK (ano BETWEEN 2020 AND 2100),
    ultimo_numero INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (mes, ano)
);

-- ---------- ordens de serviço ----------------------------------------------
CREATE TABLE ordens_servico (
    id_ordem_servico VARCHAR(20) PRIMARY KEY,
    id_cliente BIGINT NOT NULL REFERENCES clientes(id_cliente),
    id_placa VARCHAR(8) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'RECEBIDA', 'EM_DIAGNOSTICO', 'AGUARDANDO_APROVACAO',
        'EM_EXECUCAO', 'AGUARDANDO_PAGAMENTO', 'PAGA', 'ENTREGUE', 'CANCELADA')),
    descricao_problema VARCHAR(1000),
    valor_total_conserto NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (valor_total_conserto >= 0),
    motivo_rejeicao VARCHAR(500),
    comprovante_pagamento VARCHAR(255),
    orcamento_atual INTEGER NOT NULL DEFAULT 1 CHECK (orcamento_atual > 0),
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (id_placa, id_cliente) REFERENCES veiculos(id_placa, id_cliente)
);
CREATE INDEX idx_os_cliente ON ordens_servico(id_cliente);
CREATE INDEX idx_os_status ON ordens_servico(status);

-- ---------- itens de orçamento (status por item, invariante por orçamento) -
CREATE TABLE orcamentos_itens_ordem_servico (
    id_ordem_servico VARCHAR(20) NOT NULL REFERENCES ordens_servico(id_ordem_servico),
    id_orcamento INTEGER NOT NULL CHECK (id_orcamento > 0),
    id_orcamento_item INTEGER NOT NULL CHECK (id_orcamento_item > 0),
    tipo_item VARCHAR(16) NOT NULL CHECK (tipo_item IN ('SERVICO', 'PECA')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('EM_ABERTO', 'FINALIZADO', 'CANCELADO')),
    id_servico_sku BIGINT NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    quantidade INTEGER NOT NULL CHECK (quantidade > 0),
    preco_unitario NUMERIC(12, 2) NOT NULL CHECK (preco_unitario >= 0),
    PRIMARY KEY (id_ordem_servico, id_orcamento, id_orcamento_item)
);
CREATE INDEX idx_itens_orc_os ON orcamentos_itens_ordem_servico(id_ordem_servico, id_orcamento);

-- ---------- conta corrente unificada (financeiro) --------------------------
CREATE TABLE conta_corrente_oficina (
    id BIGSERIAL PRIMARY KEY,
    tipo VARCHAR(32) NOT NULL CHECK (tipo IN ('CONTAS_A_PAGAR', 'CONTAS_A_RECEBER')),
    origem VARCHAR(32) NOT NULL CHECK (origem IN ('NF_FORNECEDOR', 'OS_PAGAMENTO')),
    valor NUMERIC(14, 2) NOT NULL CHECK (valor >= 0),
    data_lancamento DATE NOT NULL DEFAULT CURRENT_DATE,
    descricao VARCHAR(500),
    numero_nota VARCHAR(30),
    serie_nota VARCHAR(10),
    cnpj_fornecedor VARCHAR(20),
    data_emissao DATE,
    id_ordem_servico VARCHAR(20),
    estornado BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cc_tipo ON conta_corrente_oficina(tipo);
CREATE INDEX idx_cc_origem ON conta_corrente_oficina(origem);
