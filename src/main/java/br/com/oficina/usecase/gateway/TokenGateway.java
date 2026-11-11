package br.com.oficina.usecase.gateway;

import br.com.oficina.domain.model.User;

public interface TokenGateway {

  String gerar(User user);

  String gerar(User user, long ttlMinutos);

  long ttlSegundos();

  long ttlSegundos(long ttlMinutos);
}
