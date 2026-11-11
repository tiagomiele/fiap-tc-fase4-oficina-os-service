package br.com.oficina.usecase.gateway;

import br.com.oficina.usecase.saga.MensagemSaga;

/** Controle de idempotência por {@code messageId}. */
public interface MensagemProcessadaRepository {

  /** Registra a mensagem e retorna {@code false} quando ela já foi processada antes. */
  boolean registrarSeNova(MensagemSaga mensagem);
}
