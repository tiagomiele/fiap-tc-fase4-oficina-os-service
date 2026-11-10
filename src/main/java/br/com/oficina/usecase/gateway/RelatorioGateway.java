package br.com.oficina.usecase.gateway;

import java.time.Instant;
import java.util.List;

public interface RelatorioGateway {

  RelatorioResult tempoMedioPorOs();

  record RelatorioResult(double tempoMedioHoras, int totalOrdens, List<OrdemExecutada> ordens) {}

  record OrdemExecutada(
      String numeroOs, Instant inicioExecucao, Instant fimExecucao, double duracaoHoras) {}
}
