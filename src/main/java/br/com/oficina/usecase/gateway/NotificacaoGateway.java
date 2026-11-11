package br.com.oficina.usecase.gateway;

public interface NotificacaoGateway {

  void enviar(String destinatario, String assunto, String corpo);
}
