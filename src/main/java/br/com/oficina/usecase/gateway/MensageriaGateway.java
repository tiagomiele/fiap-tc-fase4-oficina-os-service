package br.com.oficina.usecase.gateway;

import br.com.oficina.usecase.saga.MensagemSaga;

/** Porta de saída da Saga: entrega um comando na fila do serviço de destino. */
public interface MensageriaGateway {

  void enviar(MensagemSaga mensagem);
}
