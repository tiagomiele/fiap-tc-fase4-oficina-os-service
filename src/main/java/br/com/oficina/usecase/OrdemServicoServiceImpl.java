package br.com.oficina.usecase;

import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.enums.TipoItem;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.model.Cliente;
import br.com.oficina.domain.model.Dinheiro;
import br.com.oficina.domain.model.HistoricoStatusOrdemServico;
import br.com.oficina.domain.model.ItemOrcamento;
import br.com.oficina.domain.model.NumeroOS;
import br.com.oficina.domain.model.OrdemServico;
import br.com.oficina.domain.model.Peca;
import br.com.oficina.domain.model.Placa;
import br.com.oficina.domain.model.Servico;
import br.com.oficina.domain.model.VeiculoId;
import br.com.oficina.usecase.gateway.ClienteRepository;
import br.com.oficina.usecase.gateway.HistoricoStatusOrdemServicoRepository;
import br.com.oficina.usecase.gateway.MensageriaGateway;
import br.com.oficina.usecase.gateway.NotificacaoGateway;
import br.com.oficina.usecase.gateway.NumeroOSGenerator;
import br.com.oficina.usecase.gateway.OrdemServicoRepository;
import br.com.oficina.usecase.gateway.PecaRepository;
import br.com.oficina.usecase.gateway.ServicoRepository;
import br.com.oficina.usecase.gateway.VeiculoRepository;
import br.com.oficina.usecase.saga.MensagemSaga;
import br.com.oficina.usecase.saga.TipoMensagemSaga;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdemServicoServiceImpl {

  public record ItemAbertura(
      Long idServicoSku, TipoItem tipo, int quantidade, java.math.BigDecimal precoUnitario) {}

  private final OrdemServicoRepository repo;
  private final NumeroOSGenerator numerador;
  private final ClienteRepository clientes;
  private final VeiculoRepository veiculos;
  private final ServicoRepository servicos;
  private final PecaRepository pecas;
  private final EstoqueServiceImpl estoque;
  private final FinanceiroServiceImpl financeiro;
  private final NotificacaoGateway notificacao;
  private final HistoricoStatusOrdemServicoRepository historico;
  private final MensageriaGateway mensageria;

  public OrdemServicoServiceImpl(
      OrdemServicoRepository repo,
      NumeroOSGenerator numerador,
      ClienteRepository clientes,
      VeiculoRepository veiculos,
      ServicoRepository servicos,
      PecaRepository pecas,
      EstoqueServiceImpl estoque,
      FinanceiroServiceImpl financeiro,
      NotificacaoGateway notificacao,
      HistoricoStatusOrdemServicoRepository historico,
      MensageriaGateway mensageria) {
    this.repo = repo;
    this.numerador = numerador;
    this.clientes = clientes;
    this.veiculos = veiculos;
    this.servicos = servicos;
    this.pecas = pecas;
    this.estoque = estoque;
    this.financeiro = financeiro;
    this.notificacao = notificacao;
    this.historico = historico;
    this.mensageria = mensageria;
  }

  @Transactional
  public OrdemServico abrir(
      Long idCliente, String placaRaw, String descricaoProblema, List<ItemAbertura> itens) {
    OrdemServico os = abrir(idCliente, placaRaw, descricaoProblema);
    if (itens == null || itens.isEmpty()) {
      return os;
    }
    String numero = os.getNumero().valor();
    for (ItemAbertura item : itens) {
      if (item.tipo() == TipoItem.SERVICO) {
        os = adicionarServico(numero, item.idServicoSku(), item.quantidade(), item.precoUnitario());
      } else {
        os = adicionarPeca(numero, item.idServicoSku(), item.quantidade(), item.precoUnitario());
      }
    }
    return os;
  }

  @Transactional
  public OrdemServico abrir(Long idCliente, String placaRaw, String descricaoProblema) {
    Cliente cliente =
        clientes
            .porId(idCliente)
            .orElseThrow(
                () -> new BusinessException("CLIENTE_NAO_CADASTRADO", "Cliente não cadastrado"));
    if (!cliente.isAtivo()) {
      throw new BusinessException("CLIENTE_INATIVO", "Cliente inativo");
    }
    Placa placa = Placa.de(placaRaw);
    VeiculoId vid = new VeiculoId(placa, idCliente);
    if (veiculos.porId(vid).isEmpty()) {
      throw new BusinessException(
          "VEICULO_NAO_CADASTRADO", "Veículo não cadastrado para o cliente");
    }
    NumeroOS numero = numerador.proximo();
    OrdemServico os = repo.salvar(OrdemServico.abrir(numero, idCliente, placa, descricaoProblema));
    mensageria.enviar(
        MensagemSaga.comando(
            TipoMensagemSaga.INICIAR_DIAGNOSTICO,
            numero.valor(),
            MensagemSaga.dados(
                "idCliente", idCliente,
                "placa", placa.valor(),
                "descricaoProblema", descricaoProblema)));
    return os;
  }

  @Transactional
  public OrdemServico adicionarServico(
      String numero, Long idServico, int quantidade, java.math.BigDecimal precoUnitarioOverride) {
    Servico s =
        servicos
            .porId(idServico)
            .orElseThrow(
                () -> new BusinessException("SERVICO_NAO_CADASTRADO", "Serviço não cadastrado"));
    if (!s.isAtivo()) {
      throw new BusinessException("SERVICO_INATIVO", "Serviço inativo");
    }
    OrdemServico os = carregar(numero);
    Dinheiro preco =
        precoUnitarioOverride == null ? s.getPrecoBase() : Dinheiro.de(precoUnitarioOverride);
    os.adicionarServico(idServico, s.getNome(), quantidade, preco);
    return repo.salvar(os);
  }

  @Transactional
  public OrdemServico adicionarPeca(
      String numero, Long idSku, int quantidade, java.math.BigDecimal precoUnitarioOverride) {
    Peca p =
        pecas
            .porSku(idSku)
            .orElseThrow(() -> new BusinessException("PECA_NAO_CADASTRADA", "Peça não cadastrada"));
    if (!p.isAtivo()) {
      throw new BusinessException("PECA_INATIVA", "Peça inativa");
    }
    OrdemServico os = carregar(numero);
    Dinheiro preco =
        precoUnitarioOverride == null ? p.getPrecoVenda() : Dinheiro.de(precoUnitarioOverride);
    if (estoque.saldo(idSku) < quantidade) {
      throw new BusinessException("ESTOQUE_INSUFICIENTE", "Estoque insuficiente");
    }
    os.adicionarPeca(idSku, p.getNome(), quantidade, preco);
    return repo.salvar(os);
  }

  @Transactional
  public OrdemServico enviarParaAprovacao(String numero) {
    OrdemServico os = carregar(numero);
    os.enviarParaAprovacao();
    OrdemServico salva = repo.salvar(os);
    notificarCliente(salva);
    return salva;
  }

  @Transactional
  public OrdemServico aprovar(String numero) {
    OrdemServico os = carregar(numero);
    os.aprovar();
    reservarPecas(numero, os);
    OrdemServico salva = repo.salvar(os);
    notificarCliente(salva);
    return salva;
  }

  @Transactional
  public OrdemServico rejeitarCancelar(String numero, String motivo) {
    OrdemServico os = carregar(numero);
    os.rejeitarCancelar(motivo);
    OrdemServico salva = repo.salvar(os);
    notificarCliente(salva);
    return salva;
  }

  @Transactional
  public OrdemServico rejeitarRefazer(String numero, String motivo) {
    OrdemServico os = carregar(numero);
    os.rejeitarRefazer(motivo);
    OrdemServico salva = repo.salvar(os);
    notificarCliente(salva);
    return salva;
  }

  @Transactional
  public OrdemServico concluirReparo(String numero) {
    OrdemServico os = carregar(numero);
    os.concluirReparo();
    OrdemServico salva = repo.salvar(os);
    notificarCliente(salva);
    return salva;
  }

  @Transactional
  public OrdemServico confirmarPagamento(String numero, String comprovante) {
    OrdemServico os = carregar(numero);
    os.confirmarPagamento(comprovante);
    OrdemServico salva = repo.salvar(os);
    financeiro.lancarContaAReceberOS(
        salva.getValorTotalConserto(), numero, "Pagamento OS " + numero);
    notificarCliente(salva);
    return salva;
  }

  @Transactional
  public OrdemServico entregar(String numero) {
    OrdemServico os = carregar(numero);
    os.entregar();
    OrdemServico salva = repo.salvar(os);
    notificarCliente(salva);
    return salva;
  }

  @Transactional
  public OrdemServico cancelarEmDiagnostico(String numero, String motivo) {
    OrdemServico os = carregar(numero);
    os.cancelarEmDiagnostico(motivo);
    OrdemServico salva = repo.salvar(os);
    mensageria.enviar(
        MensagemSaga.comando(
            TipoMensagemSaga.CANCELAR_EXECUCAO, numero, MensagemSaga.dados("motivo", motivo)));
    notificarCliente(salva);
    return salva;
  }

  @Transactional
  public OrdemServico alterarStatus(String numero, StatusOrdemServico novoStatus) {
    OrdemServico os = carregar(numero);
    os.alterarStatus(novoStatus);
    OrdemServico salva = repo.salvar(os);
    notificarCliente(salva);
    return salva;
  }

  /** Confere se o saldo atual cobre as peças do orçamento em aprovação (passo da Saga). */
  @Transactional(readOnly = true)
  public boolean estoqueSuficiente(String numero) {
    return pecasDoOrcamentoAtual(carregar(numero)).entrySet().stream()
        .allMatch(e -> estoque.saldo(e.getKey()) >= e.getValue());
  }

  /** Compensação de pagamento recusado: a OS segue aguardando pagamento e o cliente é avisado. */
  @Transactional(readOnly = true)
  public OrdemServico notificarPagamentoRecusado(String numero, String motivo) {
    OrdemServico os = carregar(numero);
    String detalhe = motivo == null || motivo.isBlank() ? "" : " Motivo: " + motivo + ".";
    notificar(
        os,
        "OS " + numero + " — pagamento recusado",
        "o pagamento da sua Ordem de Serviço "
            + numero
            + " foi recusado."
            + detalhe
            + " Você pode tentar novamente.");
    return os;
  }

  @Transactional(readOnly = true)
  public OrdemServico consultar(String numero) {
    return carregar(numero);
  }

  @Transactional(readOnly = true)
  public OrdemServico consultarDoCliente(String numero, Long idCliente) {
    OrdemServico os = carregar(numero);
    exigirProprietario(os, idCliente);
    return os;
  }

  @Transactional(readOnly = true)
  public List<HistoricoStatusOrdemServico> historicoDoCliente(String numero, Long idCliente) {
    exigirProprietario(carregar(numero), idCliente);
    return historico.porOrdemServico(numero);
  }

  @Transactional(readOnly = true)
  public List<OrdemServico> listar() {
    return repo.listar();
  }

  @Transactional(readOnly = true)
  public List<OrdemServico> listarAtivas() {
    return repo.listar().stream()
        .filter(os -> os.getStatus().visivelNaListagem())
        .sorted(
            java.util.Comparator.comparingInt(
                    (OrdemServico os) -> os.getStatus().getPrioridadeListagem())
                .thenComparing(OrdemServico::getCriadoEm))
        .toList();
  }

  private void exigirProprietario(OrdemServico os, Long idCliente) {
    if (idCliente == null || !Objects.equals(os.getIdCliente(), idCliente)) {
      throw new BusinessException("OS_NAO_ENCONTRADA", "Ordem de serviço não encontrada");
    }
  }

  private OrdemServico carregar(String numero) {
    return repo.porNumero(NumeroOS.de(numero))
        .orElseThrow(
            () -> new BusinessException("OS_NAO_ENCONTRADA", "Ordem de serviço não encontrada"));
  }

  private void notificarCliente(OrdemServico os) {
    notificar(
        os,
        "OS " + os.getNumero().valor() + " — " + os.getStatus(),
        "sua Ordem de Serviço "
            + os.getNumero().valor()
            + " teve o status atualizado para: "
            + os.getStatus()
            + ".");
  }

  private void notificar(OrdemServico os, String assunto, String mensagem) {
    clientes
        .porId(os.getIdCliente())
        .filter(c -> c.getEmail() != null && !c.getEmail().isBlank())
        .ifPresent(
            c ->
                notificacao.enviar(
                    c.getEmail(), assunto, "Prezado(a) " + c.getNome() + ", " + mensagem));
  }

  private void reservarPecas(String numero, OrdemServico os) {
    for (ItemOrcamento it : os.orcamentoAtual().getItens()) {
      if (it.getTipoItem() == TipoItem.PECA) {
        estoque.consumoPorOrcamento(
            it.getIdServicoSku(),
            it.getQuantidade(),
            numero,
            it.getIdOrcamento(),
            it.getIdOrcamentoItem());
      }
    }
  }

  private static Map<Long, Integer> pecasDoOrcamentoAtual(OrdemServico os) {
    return os.orcamentoAtual().getItens().stream()
        .filter(it -> it.getTipoItem() == TipoItem.PECA)
        .collect(
            Collectors.groupingBy(
                ItemOrcamento::getIdServicoSku,
                Collectors.summingInt(ItemOrcamento::getQuantidade)));
  }
}
