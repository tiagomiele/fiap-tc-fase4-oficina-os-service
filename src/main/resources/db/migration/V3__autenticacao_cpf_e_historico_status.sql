CREATE OR REPLACE FUNCTION oficina_somente_digitos(valor TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
PARALLEL SAFE
AS $$
  SELECT regexp_replace(valor, '[^0-9]', '', 'g')
$$;

ALTER TABLE clientes
  ADD COLUMN documento_normalizado VARCHAR(14)
  GENERATED ALWAYS AS (oficina_somente_digitos(documento)) STORED;

CREATE UNIQUE INDEX ux_clientes_documento_normalizado
  ON clientes(documento_normalizado);

CREATE INDEX idx_clientes_cpf_ativo
  ON clientes(documento_normalizado)
  WHERE tipo_documento = 'CPF' AND ativo = TRUE;

CREATE TABLE historico_status_ordem_servico (
  id BIGSERIAL PRIMARY KEY,
  id_ordem_servico VARCHAR(20) NOT NULL
    REFERENCES ordens_servico(id_ordem_servico) ON DELETE CASCADE,
  status VARCHAR(32) NOT NULL CHECK (status IN (
    'RECEBIDA', 'EM_DIAGNOSTICO', 'AGUARDANDO_APROVACAO',
    'EM_EXECUCAO', 'AGUARDANDO_PAGAMENTO', 'PAGA', 'ENTREGUE', 'CANCELADA')),
  entrada_em TIMESTAMPTZ NOT NULL,
  saida_em TIMESTAMPTZ,
  duracao_milisegundos BIGINT CHECK (duracao_milisegundos IS NULL OR duracao_milisegundos >= 0),
  correlation_id VARCHAR(64) NOT NULL
);

CREATE UNIQUE INDEX ux_historico_os_status_aberto
  ON historico_status_ordem_servico(id_ordem_servico)
  WHERE saida_em IS NULL;

CREATE INDEX idx_historico_os_entrada
  ON historico_status_ordem_servico(id_ordem_servico, entrada_em);

CREATE INDEX idx_historico_status_periodo
  ON historico_status_ordem_servico(status, entrada_em);

INSERT INTO historico_status_ordem_servico (
  id_ordem_servico, status, entrada_em, correlation_id)
SELECT id_ordem_servico, status, criado_em, 'migration-v3'
FROM ordens_servico;
