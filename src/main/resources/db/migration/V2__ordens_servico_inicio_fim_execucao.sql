-- Colunas para cálculo do tempo médio efetivo de execução por serviço.
-- inicio_execucao: preenchido na primeira transição para EM_EXECUCAO (aprovação do 1º orçamento).
-- fim_execucao   : preenchido na primeira transição para AGUARDANDO_PAGAMENTO (fim do último reparo).
-- Nenhum dos dois é sobrescrito após preenchido.
ALTER TABLE ordens_servico
  ADD COLUMN inicio_execucao TIMESTAMP WITH TIME ZONE NULL,
  ADD COLUMN fim_execucao    TIMESTAMP WITH TIME ZONE NULL;
