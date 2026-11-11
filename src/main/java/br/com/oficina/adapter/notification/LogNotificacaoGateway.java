package br.com.oficina.adapter.notification;

import br.com.oficina.usecase.gateway.NotificacaoGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(
    name = "oficina.notificacao.tipo",
    havingValue = "log",
    matchIfMissing = true)
public class LogNotificacaoGateway implements NotificacaoGateway {

  private static final Logger log = LoggerFactory.getLogger(LogNotificacaoGateway.class);

  @Override
  public void enviar(String destinatario, String assunto, String corpo) {
    log.info("Notificacao simulada processada");
  }
}
