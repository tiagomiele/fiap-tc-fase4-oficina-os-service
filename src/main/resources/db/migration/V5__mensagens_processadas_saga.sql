-- Idempotência dos eventos da Saga: cada messageId do SQS é processado uma única vez.
CREATE TABLE mensagens_processadas (
  message_id      VARCHAR(100) PRIMARY KEY,
  tipo            VARCHAR(60)  NOT NULL,
  numero_os       VARCHAR(30)  NOT NULL,
  correlation_id  VARCHAR(100),
  processada_em   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_mensagens_processadas_os ON mensagens_processadas(numero_os);
