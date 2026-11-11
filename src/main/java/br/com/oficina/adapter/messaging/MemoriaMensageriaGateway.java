package br.com.oficina.adapter.messaging;

import br.com.oficina.usecase.gateway.MensageriaGateway;
import br.com.oficina.usecase.saga.MensagemSaga;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Mensageria local (sem SQS): registra os comandos em log e mantém os últimos em memória. */
@Component
@Primary
@ConditionalOnProperty(
    name = "oficina.mensageria.tipo",
    havingValue = "memoria",
    matchIfMissing = true)
public class MemoriaMensageriaGateway implements MensageriaGateway {

  static final int LIMITE = 200;

  private static final Logger log = LoggerFactory.getLogger(MemoriaMensageriaGateway.class);

  private final List<MensagemSaga> enviadas = new ArrayList<>();

  @Override
  public synchronized void enviar(MensagemSaga mensagem) {
    MensagemSaga comCorrelacao = CorrelationIds.aplicar(mensagem);
    if (enviadas.size() == LIMITE) {
      enviadas.remove(0);
    }
    enviadas.add(comCorrelacao);
    log.info(
        "Comando {} da OS {} registrado em memória para {}",
        comCorrelacao.tipo(),
        comCorrelacao.numeroOs(),
        comCorrelacao.tipo().destino());
  }

  public synchronized List<MensagemSaga> enviadas() {
    return List.copyOf(enviadas);
  }

  public synchronized List<MensagemSaga> enviadasPara(String numeroOs) {
    return enviadas.stream().filter(m -> m.numeroOs().equals(numeroOs)).toList();
  }
}
