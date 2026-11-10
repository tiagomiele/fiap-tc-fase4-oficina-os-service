package br.com.oficina.adapter.controller;

import br.com.oficina.adapter.dto.LancamentoResponse;
import br.com.oficina.adapter.dto.OrdemServicoResponse;
import br.com.oficina.adapter.dto.TempoMedioPorOsResponse;
import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.enums.TipoItem;
import br.com.oficina.domain.model.Cliente;
import br.com.oficina.domain.model.EstoquePeca;
import br.com.oficina.domain.model.ItemNotaFiscalFornecedor;
import br.com.oficina.domain.model.NotaFiscalFornecedor;
import br.com.oficina.domain.model.OrdemServico;
import br.com.oficina.domain.model.Peca;
import br.com.oficina.domain.model.Servico;
import br.com.oficina.domain.model.Veiculo;
import br.com.oficina.usecase.ClienteServiceImpl;
import br.com.oficina.usecase.EstoqueServiceImpl;
import br.com.oficina.usecase.FinanceiroServiceImpl;
import br.com.oficina.usecase.NotaFiscalFornecedorServiceImpl;
import br.com.oficina.usecase.OrdemServicoServiceImpl;
import br.com.oficina.usecase.PecaServiceImpl;
import br.com.oficina.usecase.ServicoServiceImpl;
import br.com.oficina.usecase.VeiculoServiceImpl;
import br.com.oficina.usecase.gateway.RelatorioGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 02 - AdministrativoOficinaController — "Perfil Administrativo da Oficina".
 *
 * <p>Concentra todos os endpoints de uso do pessoal administrativo (CRUD de catálogos, faturamento,
 * estoque, relatórios, contas e confirmação de pagamento de OS). Todos os endpoints exigem JWT com
 * role {@code FUNCIONARIO_DA_OFICINA}.
 */
@RestController
@PreAuthorize("hasRole('FUNCIONARIO_DA_OFICINA')")
@Tag(
    name = "02-Perfil Administrativo da Oficina",
    description =
        "Endpoints administrativos: serviços, peças, clientes, veículos, notas fiscais de"
            + " fornecedor, estoque, relatórios, contas a pagar/receber e confirmação de"
            + " pagamento de OS.")
public class AdministrativoOficinaController {

  private static final String DESC_NUMERO_OS =
      "Número da Ordem de Serviço no formato OS-MMAAAA-NNNNNN.";
  private static final String EXAMPLE_NUMERO_OS = "OS-042026-000001";
  private static final String MSG_DESATIVACAO_SERVICO =
      "Desativação de serviço realizada com sucesso!";
  private static final String MSG_DESATIVACAO_PECA = "Desativação de peça realizada com sucesso!";
  private static final String MSG_DESATIVACAO_CLIENTE =
      "Desativação de cliente realizada com sucesso!";
  private static final String MSG_DESATIVACAO_VEICULO =
      "Desativação de veículo realizada com sucesso!";
  private static final String MSG_NF_ESTORNADA = "Nota fiscal estornada com sucesso!";

  private final ServicoServiceImpl servicoService;
  private final PecaServiceImpl pecaService;
  private final ClienteServiceImpl clienteService;
  private final VeiculoServiceImpl veiculoService;
  private final NotaFiscalFornecedorServiceImpl nfService;
  private final EstoqueServiceImpl estoqueService;
  private final RelatorioGateway relatorioService;
  private final OrdemServicoServiceImpl osService;
  private final FinanceiroServiceImpl financeiroService;

  public AdministrativoOficinaController(
      ServicoServiceImpl servicoService,
      PecaServiceImpl pecaService,
      ClienteServiceImpl clienteService,
      VeiculoServiceImpl veiculoService,
      NotaFiscalFornecedorServiceImpl nfService,
      EstoqueServiceImpl estoqueService,
      RelatorioGateway relatorioService,
      OrdemServicoServiceImpl osService,
      FinanceiroServiceImpl financeiroService) {
    this.servicoService = servicoService;
    this.pecaService = pecaService;
    this.clienteService = clienteService;
    this.veiculoService = veiculoService;
    this.nfService = nfService;
    this.estoqueService = estoqueService;
    this.relatorioService = relatorioService;
    this.osService = osService;
    this.financeiroService = financeiroService;
  }

  // =====================================================================================
  // Schemas (DTOs)
  // =====================================================================================

  // ----- Serviços -----
  @Schema(
      description = "Dados para cadastro/atualização de um serviço do catálogo.",
      example =
          "{\"nome\":\"Troca de óleo\",\"descricao\":\"Troca de óleo lubrificante e filtro\","
              + "\"precoBase\":100.00}")
  public record ServicoRequest(
      @Schema(description = "Nome do serviço.", example = "Troca de óleo") @NotBlank String nome,
      @Schema(
              description = "Descrição do serviço (opcional).",
              example = "Troca de óleo lubrificante e filtro")
          String descricao,
      @Schema(description = "Preço base em BRL (>= 0).", example = "100.00")
          @NotNull
          @PositiveOrZero
          BigDecimal precoBase) {}

  @Schema(
      description = "Dados de retorno de um serviço.",
      example =
          "{\"idServico\":1,\"nome\":\"Troca de óleo\","
              + "\"descricao\":\"Troca de óleo lubrificante e filtro\","
              + "\"precoBase\":100.00,\"ativo\":true}")
  public record ServicoResponse(
      @Schema(description = "Identificador do serviço.", example = "1") Long idServico,
      @Schema(description = "Nome.", example = "Troca de óleo") String nome,
      @Schema(description = "Descrição.", example = "Troca de óleo lubrificante e filtro")
          String descricao,
      @Schema(description = "Preço base.", example = "100.00") BigDecimal precoBase,
      @Schema(description = "Ativo (false = desativado).", example = "true") boolean ativo) {
    static ServicoResponse de(Servico s) {
      return new ServicoResponse(
          s.getIdServico(), s.getNome(), s.getDescricao(), s.getPrecoBase().valor(), s.isAtivo());
    }
  }

  // ----- Peças -----
  @Schema(
      description = "Dados para cadastro/atualização de uma peça do catálogo.",
      example = "{\"nome\":\"Filtro de óleo\",\"precoVenda\":30.00}")
  public record PecaRequest(
      @Schema(description = "Nome da peça.", example = "Filtro de óleo") @NotBlank String nome,
      @Schema(description = "Preço de venda em BRL (>= 0).", example = "30.00")
          @NotNull
          @PositiveOrZero
          BigDecimal precoVenda) {}

  @Schema(
      description = "Dados de retorno de uma peça.",
      example = "{\"idSku\":1,\"nome\":\"Filtro de óleo\",\"precoVenda\":30.00,\"ativo\":true}")
  public record PecaResponse(
      @Schema(description = "SKU/identificador da peça.", example = "1") Long idSku,
      @Schema(description = "Nome.", example = "Filtro de óleo") String nome,
      @Schema(description = "Preço de venda.", example = "30.00") BigDecimal precoVenda,
      @Schema(description = "Ativo (false = desativado).", example = "true") boolean ativo) {
    static PecaResponse de(Peca p) {
      return new PecaResponse(p.getIdSku(), p.getNome(), p.getPrecoVenda().valor(), p.isAtivo());
    }
  }

  // ----- Clientes -----
  @Schema(
      description = "Dados para cadastro de um novo cliente.",
      example =
          "{\"nome\":\"João Silva\",\"documento\":\"52998224725\","
              + "\"email\":\"joao@ex.com\",\"telefone\":\"11999999999\"}")
  public record ClienteRequest(
      @Schema(description = "Nome completo do cliente.", example = "João Silva") @NotBlank
          String nome,
      @Schema(
              description = "CPF (11 dígitos) ou CNPJ (14 dígitos), só dígitos.",
              example = "52998224725")
          @NotBlank
          String documento,
      @Schema(description = "E-mail de contato.", example = "joao@ex.com") String email,
      @Schema(description = "Telefone com DDD (só dígitos).", example = "11999999999")
          String telefone) {}

  @Schema(
      description = "Dados atualizáveis de um cliente (documento não muda).",
      example =
          "{\"nome\":\"João S. Silva\",\"email\":\"joao.s@ex.com\",\"telefone\":\"11988887777\"}")
  public record AtualizarClienteRequest(
      @Schema(description = "Nome completo.", example = "João S. Silva") @NotBlank String nome,
      @Schema(description = "E-mail.", example = "joao.s@ex.com") String email,
      @Schema(description = "Telefone.", example = "11988887777") String telefone) {}

  @Schema(
      description = "Dados de retorno de um cliente.",
      example =
          "{\"idCliente\":42,\"nome\":\"João Silva\",\"documento\":\"52998224725\","
              + "\"tipoDocumento\":\"CPF\",\"email\":\"joao@ex.com\","
              + "\"telefone\":\"11999999999\",\"ativo\":true}")
  public record ClienteResponse(
      @Schema(description = "Identificador único do cliente.", example = "42") Long idCliente,
      @Schema(description = "Nome.", example = "João Silva") String nome,
      @Schema(description = "Documento (CPF/CNPJ).", example = "52998224725") String documento,
      @Schema(description = "Tipo do documento.", example = "CPF") String tipoDocumento,
      @Schema(description = "E-mail.", example = "joao@ex.com") String email,
      @Schema(description = "Telefone.", example = "11999999999") String telefone,
      @Schema(description = "Ativo (false = desativado).", example = "true") boolean ativo) {
    static ClienteResponse de(Cliente c) {
      return new ClienteResponse(
          c.getIdCliente(),
          c.getNome(),
          c.getDocumento().valor(),
          c.getDocumento().tipo().name(),
          c.getEmail(),
          c.getTelefone(),
          c.isAtivo());
    }
  }

  // ----- Veículos -----
  @Schema(
      description = "Dados para cadastro de um novo veículo (atrelado a um cliente).",
      example =
          "{\"placa\":\"ABC1234\",\"idCliente\":42,\"marca\":\"Fiat\","
              + "\"modelo\":\"Uno\",\"ano\":2020}")
  public record VeiculoRequest(
      @Schema(
              description = "Placa do veículo (Mercosul `ABC1D23` ou antiga `ABC1234`).",
              example = "ABC1234")
          @NotBlank
          String placa,
      @Schema(description = "ID do cliente proprietário.", example = "42") @NotNull Long idCliente,
      @Schema(description = "Marca.", example = "Fiat") @NotBlank String marca,
      @Schema(description = "Modelo.", example = "Uno") @NotBlank String modelo,
      @Schema(description = "Ano de fabricação (>= 1900).", example = "2020") @Min(1900) int ano) {}

  @Schema(
      description = "Dados atualizáveis de um veículo.",
      example = "{\"marca\":\"Fiat\",\"modelo\":\"Uno Way\",\"ano\":2021}")
  public record AtualizarVeiculoRequest(
      @Schema(description = "Marca.", example = "Fiat") @NotBlank String marca,
      @Schema(description = "Modelo.", example = "Uno Way") @NotBlank String modelo,
      @Schema(description = "Ano (>= 1900).", example = "2021") @Min(1900) int ano) {}

  @Schema(
      description = "Dados de retorno de um veículo.",
      example =
          "{\"placa\":\"ABC1234\",\"idCliente\":42,\"marca\":\"Fiat\","
              + "\"modelo\":\"Uno\",\"ano\":2020,\"ativo\":true}")
  public record VeiculoResponse(
      @Schema(description = "Placa.", example = "ABC1234") String placa,
      @Schema(description = "ID do cliente.", example = "42") Long idCliente,
      @Schema(description = "Marca.", example = "Fiat") String marca,
      @Schema(description = "Modelo.", example = "Uno") String modelo,
      @Schema(description = "Ano.", example = "2020") int ano,
      @Schema(description = "Ativo.", example = "true") boolean ativo) {
    static VeiculoResponse de(Veiculo v) {
      return new VeiculoResponse(
          v.getId().placa().valor(),
          v.getId().idCliente(),
          v.getMarca(),
          v.getModelo(),
          v.getAno(),
          v.isAtivo());
    }
  }

  // ----- Notas Fiscais -----
  @Schema(
      description = "Item de uma nota fiscal de fornecedor.",
      example = "{\"idSku\":1,\"quantidade\":10,\"precoUnitario\":18.50}")
  public record ItemNotaRequest(
      @Schema(description = "SKU da peça.", example = "1") @NotNull Long idSku,
      @Schema(description = "Quantidade (> 0).", example = "10") @Positive int quantidade,
      @Schema(description = "Preço unitário (>= 0).", example = "18.50") @NotNull @PositiveOrZero
          BigDecimal precoUnitario) {}

  @Schema(
      description = "Dados para emissão de uma nota fiscal de fornecedor (entrada de estoque).",
      example =
          "{\"numeroNota\":\"123456\",\"serieNota\":\"1\","
              + "\"cnpjFornecedor\":\"12345678000190\","
              + "\"dataEmissao\":\"2026-04-01\",\"nomeFornecedor\":\"Fornecedor ACME\","
              + "\"itens\":[{\"idSku\":1,\"quantidade\":10,\"precoUnitario\":18.50}]}")
  public record NotaFiscalRequest(
      @Schema(description = "Número da NF.", example = "123456") @NotBlank String numeroNota,
      @Schema(description = "Série da NF.", example = "1") @NotBlank String serieNota,
      @Schema(description = "CNPJ do fornecedor (14 dígitos).", example = "12345678000190")
          @NotBlank
          String cnpjFornecedor,
      @Schema(description = "Data de emissão (ISO).", example = "2026-04-01") @NotNull
          LocalDate dataEmissao,
      @Schema(description = "Nome/razão social do fornecedor.", example = "Fornecedor ACME")
          @NotBlank
          String nomeFornecedor,
      @Schema(description = "Itens da nota.") @NotEmpty List<@Valid ItemNotaRequest> itens) {}

  @Schema(
      description = "Item da nota fiscal (retorno).",
      example = "{\"idSku\":1,\"quantidade\":10,\"precoUnitario\":18.50}")
  public record ItemNotaResponse(
      @Schema(description = "SKU.", example = "1") Long idSku,
      @Schema(description = "Quantidade.", example = "10") int quantidade,
      @Schema(description = "Preço unitário.", example = "18.50") BigDecimal precoUnitario) {}

  @Schema(
      description = "Retorno de uma nota fiscal de fornecedor.",
      example =
          "{\"numeroNota\":\"123456\",\"serieNota\":\"1\","
              + "\"cnpjFornecedor\":\"12345678000190\","
              + "\"dataEmissao\":\"2026-04-01\",\"nomeFornecedor\":\"Fornecedor ACME\","
              + "\"valorTotal\":185.00,\"estornada\":false,"
              + "\"itens\":[{\"idSku\":1,\"quantidade\":10,\"precoUnitario\":18.50}]}")
  public record NotaFiscalResponse(
      @Schema(description = "Número da NF.", example = "123456") String numeroNota,
      @Schema(description = "Série.", example = "1") String serieNota,
      @Schema(description = "CNPJ do fornecedor.", example = "12345678000190")
          String cnpjFornecedor,
      @Schema(description = "Data de emissão.", example = "2026-04-01") LocalDate dataEmissao,
      @Schema(description = "Nome do fornecedor.", example = "Fornecedor ACME")
          String nomeFornecedor,
      @Schema(description = "Valor total.", example = "185.00") BigDecimal valorTotal,
      @Schema(description = "Indica se a NF foi estornada.", example = "false") boolean estornada,
      @Schema(description = "Itens da nota.") List<ItemNotaResponse> itens) {
    static NotaFiscalResponse de(NotaFiscalFornecedor nf) {
      List<ItemNotaResponse> itens =
          nf.getItens().stream()
              .map(
                  (ItemNotaFiscalFornecedor it) ->
                      new ItemNotaResponse(
                          it.getIdSku(), it.getQuantidade(), it.getPrecoUnitario().valor()))
              .toList();
      return new NotaFiscalResponse(
          nf.getId().numeroNota(),
          nf.getId().serieNota(),
          nf.getId().cnpjFornecedor(),
          nf.getId().dataEmissao(),
          nf.getNomeFornecedor(),
          nf.getValorTotal().valor(),
          nf.isEstornada(),
          itens);
    }
  }

  // ----- Estoque -----
  @Schema(
      description = "Saldo de estoque de uma peça.",
      example = "{\"idSku\":1,\"quantidade\":42,\"dataHora\":\"2026-04-12T13:45:00Z\"}")
  public record EstoqueResponse(
      @Schema(description = "SKU.", example = "1") Long idSku,
      @Schema(description = "Quantidade em estoque.", example = "42") int quantidade,
      @Schema(description = "Momento da consulta.", example = "2026-04-12T13:45:00Z")
          Instant dataHora) {
    static EstoqueResponse de(EstoquePeca e) {
      return new EstoqueResponse(e.getIdSku(), e.getQuantidade(), e.getDataHora());
    }
  }

  // ----- OS abrir -----
  @Schema(
      description =
          "Dados para abertura de uma OS. Pode incluir serviços e peças na mesma requisição.",
      example =
          "{\"idCliente\":42,\"placa\":\"ABC1234\","
              + "\"descricaoProblema\":\"Barulho na suspensão dianteira\","
              + "\"itens\":[{\"idServicoSku\":1,\"tipo\":\"SERVICO\",\"quantidade\":1},"
              + "{\"idServicoSku\":2,\"tipo\":\"PECA\",\"quantidade\":2,\"precoUnitario\":45.00}]}")
  public record AbrirOsRequest(
      @Schema(description = "ID do cliente.", example = "42") @NotNull Long idCliente,
      @Schema(
              description = "Placa do veículo (Mercosul `ABC1D23` ou antiga `ABC1234`).",
              example = "ABC1234")
          @NotBlank
          String placa,
      @Schema(
              description = "Descrição do problema relatado pelo cliente.",
              example = "Barulho na suspensão dianteira")
          String descricaoProblema,
      @Schema(description = "Itens (serviços e peças) a incluir na OS. Opcional.")
          List<ItemAberturaRequest> itens) {}

  @Schema(
      description =
          "Dados para criação de uma OS apenas com dados básicos (sem serviços, peças ou"
              + " orçamento), iniciando em status RECEBIDA.",
      example =
          "{\"idCliente\":42,\"placa\":\"ABC1234\","
              + "\"descricaoProblema\":\"Barulho na suspensão dianteira\"}")
  public record AbrirOsRecebidaRequest(
      @Schema(description = "ID do cliente.", example = "42") @NotNull Long idCliente,
      @Schema(
              description = "Placa do veículo (Mercosul `ABC1D23` ou antiga `ABC1234`).",
              example = "ABC1234")
          @NotBlank
          String placa,
      @Schema(
              description = "Descrição do problema relatado pelo cliente.",
              example = "Barulho na suspensão dianteira")
          String descricaoProblema) {}

  @Schema(
      description = "Novo status de destino para a alteração genérica (contingência).",
      example = "{\"status\":\"EM_DIAGNOSTICO\"}")
  public record AlterarStatusRequest(
      @Schema(
              description =
                  "Status de destino. Valores: RECEBIDA, EM_DIAGNOSTICO, AGUARDANDO_APROVACAO,"
                      + " EM_EXECUCAO, AGUARDANDO_PAGAMENTO, ENTREGUE, CANCELADA.",
              example = "EM_DIAGNOSTICO")
          @NotNull
          StatusOrdemServico status) {}

  @Schema(description = "Item (serviço ou peça) para incluir na abertura da OS.")
  public record ItemAberturaRequest(
      @Schema(description = "ID do serviço ou SKU da peça.", example = "1") @NotNull
          Long idServicoSku,
      @Schema(
              description = "Tipo: SERVICO ou PECA.",
              example = "SERVICO",
              allowableValues = {"SERVICO", "PECA"})
          @NotNull
          TipoItem tipo,
      @Schema(description = "Quantidade (>= 1).", example = "1") @NotNull @Min(1)
          Integer quantidade,
      @Schema(
              description = "Preço unitário (opcional, usa preço do catálogo se omitido).",
              example = "100.00")
          BigDecimal precoUnitario) {}

  // =====================================================================================
  // 01 - Serviços
  // =====================================================================================

  @Operation(summary = "02.1.01 - Cadastrar serviço", description = "Cria um serviço no catálogo.")
  @PostMapping("/servicos")
  public ResponseEntity<ServicoResponse> criarServico(@Valid @RequestBody ServicoRequest req) {
    Servico s = servicoService.cadastrar(req.nome(), req.descricao(), req.precoBase());
    return ResponseEntity.status(HttpStatus.CREATED).body(ServicoResponse.de(s));
  }

  @Operation(summary = "02.1.02 - Atualizar serviço")
  @PutMapping("/servicos/{id}")
  public ServicoResponse atualizarServico(
      @Parameter(name = "id", description = "ID do serviço.", example = "1") @PathVariable Long id,
      @Valid @RequestBody ServicoRequest req) {
    return ServicoResponse.de(
        servicoService.atualizar(id, req.nome(), req.descricao(), req.precoBase()));
  }

  @Operation(summary = "02.1.03 - Buscar serviço por ID")
  @GetMapping("/servicos/{id}")
  public ServicoResponse buscarServico(
      @Parameter(name = "id", description = "ID do serviço.", example = "1") @PathVariable
          Long id) {
    return ServicoResponse.de(servicoService.buscar(id));
  }

  @Operation(summary = "02.1.04 - Listar serviços")
  @GetMapping("/servicos")
  public List<ServicoResponse> listarServicos() {
    return servicoService.listar().stream().map(ServicoResponse::de).toList();
  }

  @Operation(summary = "02.1.05 - Desativar serviço")
  @DeleteMapping("/servicos/{id}")
  public Map<String, String> desativarServico(
      @Parameter(name = "id", description = "ID do serviço.", example = "1") @PathVariable
          Long id) {
    servicoService.desativar(id);
    return Map.of("mensagem", MSG_DESATIVACAO_SERVICO);
  }

  // =====================================================================================
  // 02 - Peças
  // =====================================================================================

  @Operation(summary = "02.2.01 - Cadastrar peça")
  @PostMapping("/pecas")
  public ResponseEntity<PecaResponse> criarPeca(@Valid @RequestBody PecaRequest req) {
    Peca p = pecaService.cadastrar(req.nome(), req.precoVenda());
    return ResponseEntity.status(HttpStatus.CREATED).body(PecaResponse.de(p));
  }

  @Operation(summary = "02.2.02 - Atualizar peça")
  @PutMapping("/pecas/{id}")
  public PecaResponse atualizarPeca(
      @Parameter(name = "id", description = "SKU da peça.", example = "1") @PathVariable Long id,
      @Valid @RequestBody PecaRequest req) {
    return PecaResponse.de(pecaService.atualizar(id, req.nome(), req.precoVenda()));
  }

  @Operation(summary = "02.2.03 - Buscar peça por SKU")
  @GetMapping("/pecas/{id}")
  public PecaResponse buscarPeca(
      @Parameter(name = "id", description = "SKU da peça.", example = "1") @PathVariable Long id) {
    return PecaResponse.de(pecaService.buscar(id));
  }

  @Operation(summary = "02.2.04 - Listar peças")
  @GetMapping("/pecas")
  public List<PecaResponse> listarPecas() {
    return pecaService.listar().stream().map(PecaResponse::de).toList();
  }

  @Operation(summary = "02.2.05 - Desativar peça")
  @DeleteMapping("/pecas/{id}")
  public Map<String, String> desativarPeca(
      @Parameter(name = "id", description = "SKU da peça.", example = "1") @PathVariable Long id) {
    pecaService.desativar(id);
    return Map.of("mensagem", MSG_DESATIVACAO_PECA);
  }

  // =====================================================================================
  // 03 - Clientes
  // =====================================================================================

  @Operation(summary = "02.3.01 - Cadastrar cliente")
  @PostMapping("/clientes")
  public ResponseEntity<ClienteResponse> criarCliente(@Valid @RequestBody ClienteRequest req) {
    Cliente c = clienteService.cadastrar(req.nome(), req.documento(), req.email(), req.telefone());
    return ResponseEntity.status(HttpStatus.CREATED).body(ClienteResponse.de(c));
  }

  @Operation(summary = "02.3.02 - Atualizar cliente (nome/email/telefone)")
  @PutMapping("/clientes/{idCliente}")
  public ClienteResponse atualizarCliente(
      @Parameter(name = "idCliente", description = "ID do cliente.", example = "42")
          @PathVariable("idCliente")
          Long id,
      @Valid @RequestBody AtualizarClienteRequest req) {
    return ClienteResponse.de(
        clienteService.atualizar(id, req.nome(), req.email(), req.telefone()));
  }

  @Operation(summary = "02.3.03 - Buscar cliente por ID")
  @GetMapping("/clientes/{idCliente}")
  public ClienteResponse buscarCliente(
      @Parameter(name = "idCliente", description = "ID do cliente.", example = "42")
          @PathVariable("idCliente")
          Long id) {
    return ClienteResponse.de(clienteService.buscar(id));
  }

  @Operation(summary = "02.3.04 - Listar clientes")
  @GetMapping("/clientes")
  public List<ClienteResponse> listarClientes() {
    return clienteService.listar().stream().map(ClienteResponse::de).toList();
  }

  @Operation(summary = "02.3.05 - Desativar cliente")
  @DeleteMapping("/clientes/{idCliente}")
  public Map<String, String> desativarCliente(
      @Parameter(name = "idCliente", description = "ID do cliente.", example = "42")
          @PathVariable("idCliente")
          Long id) {
    clienteService.desativar(id);
    return Map.of("mensagem", MSG_DESATIVACAO_CLIENTE);
  }

  // =====================================================================================
  // 04 - Veículos
  // =====================================================================================

  @Operation(summary = "02.4.01 - Cadastrar veículo")
  @PostMapping("/veiculos")
  public ResponseEntity<VeiculoResponse> criarVeiculo(@Valid @RequestBody VeiculoRequest req) {
    Veiculo v =
        veiculoService.cadastrar(
            req.placa(), req.idCliente(), req.marca(), req.modelo(), req.ano());
    return ResponseEntity.status(HttpStatus.CREATED).body(VeiculoResponse.de(v));
  }

  @Operation(summary = "02.4.02 - Atualizar veículo")
  @PutMapping("/veiculos/{placa}/cliente/{idCliente}")
  public VeiculoResponse atualizarVeiculo(
      @Parameter(name = "placa", description = "Placa do veículo.", example = "ABC1234")
          @PathVariable
          String placa,
      @Parameter(name = "idCliente", description = "ID do cliente.", example = "42") @PathVariable
          Long idCliente,
      @Valid @RequestBody AtualizarVeiculoRequest req) {
    return VeiculoResponse.de(
        veiculoService.atualizar(placa, idCliente, req.marca(), req.modelo(), req.ano()));
  }

  @Operation(summary = "02.4.03 - Listar veículos do cliente")
  @GetMapping("/veiculos/cliente/{idCliente}")
  public List<VeiculoResponse> listarVeiculosPorCliente(
      @Parameter(name = "idCliente", description = "ID do cliente.", example = "42") @PathVariable
          Long idCliente) {
    return veiculoService.porCliente(idCliente).stream().map(VeiculoResponse::de).toList();
  }

  @Operation(summary = "02.4.04 - Desativar veículo")
  @DeleteMapping("/veiculos/{placa}/cliente/{idCliente}")
  public Map<String, String> desativarVeiculo(
      @Parameter(name = "placa", description = "Placa do veículo.", example = "ABC1234")
          @PathVariable
          String placa,
      @Parameter(name = "idCliente", description = "ID do cliente.", example = "42") @PathVariable
          Long idCliente) {
    veiculoService.desativar(placa, idCliente);
    return Map.of("mensagem", MSG_DESATIVACAO_VEICULO);
  }

  // =====================================================================================
  // 05 - Notas Fiscais de Fornecedor
  // =====================================================================================

  @Operation(
      summary = "02.5.01 - Emitir nota fiscal de fornecedor",
      description = "Registra a NF e dá entrada nos itens no estoque.")
  @PostMapping("/notas-fiscais-fornecedor")
  public ResponseEntity<NotaFiscalResponse> criarNF(@Valid @RequestBody NotaFiscalRequest req) {
    List<NotaFiscalFornecedorServiceImpl.ItemEntrada> itens =
        req.itens().stream()
            .map(
                i ->
                    new NotaFiscalFornecedorServiceImpl.ItemEntrada(
                        i.idSku(), i.quantidade(), i.precoUnitario()))
            .toList();
    NotaFiscalFornecedor nf =
        nfService.registrar(
            req.numeroNota(),
            req.serieNota(),
            req.cnpjFornecedor(),
            req.dataEmissao(),
            req.nomeFornecedor(),
            itens);
    return ResponseEntity.status(HttpStatus.CREATED).body(NotaFiscalResponse.de(nf));
  }

  @Operation(summary = "02.5.02 - Listar notas fiscais")
  @GetMapping("/notas-fiscais-fornecedor")
  public List<NotaFiscalResponse> listarNFs() {
    return nfService.listar().stream().map(NotaFiscalResponse::de).toList();
  }

  @Operation(summary = "02.5.03 - Buscar nota fiscal por chave composta")
  @GetMapping("/notas-fiscais-fornecedor/{numero}/{serie}/{cnpj}/{data}")
  public NotaFiscalResponse buscarNF(
      @Parameter(name = "numero", description = "Número da NF.", example = "123456") @PathVariable
          String numero,
      @Parameter(name = "serie", description = "Série da NF.", example = "1") @PathVariable
          String serie,
      @Parameter(
              name = "cnpj",
              description = "CNPJ do fornecedor (14 dígitos).",
              example = "12345678000190")
          @PathVariable
          String cnpj,
      @Parameter(name = "data", description = "Data de emissão (ISO).", example = "2026-04-01")
          @PathVariable
          LocalDate data) {
    return NotaFiscalResponse.de(nfService.buscar(numero, serie, cnpj, data));
  }

  @Operation(summary = "02.5.04 - Estornar nota fiscal (devolução de estoque)")
  @DeleteMapping("/notas-fiscais-fornecedor/{numero}/{serie}/{cnpj}/{data}")
  public Map<String, String> estornarNF(
      @Parameter(name = "numero", description = "Número da NF.", example = "123456") @PathVariable
          String numero,
      @Parameter(name = "serie", description = "Série da NF.", example = "1") @PathVariable
          String serie,
      @Parameter(
              name = "cnpj",
              description = "CNPJ do fornecedor (14 dígitos).",
              example = "12345678000190")
          @PathVariable
          String cnpj,
      @Parameter(name = "data", description = "Data de emissão (ISO).", example = "2026-04-01")
          @PathVariable
          LocalDate data) {
    nfService.estornar(numero, serie, cnpj, data);
    return Map.of("mensagem", MSG_NF_ESTORNADA);
  }

  // =====================================================================================
  // 06 - Ordens de Serviço (admin) - abrir
  // =====================================================================================

  @Operation(
      summary = "02.6.01 - Abrir OS",
      description =
          "Gera o número no formato OS-MMAAAA-NNNNNN e inicia o fluxo em status RECEBIDA."
              + " Se itens forem informados, já adiciona serviços e peças ao orçamento"
              + " (transição automática para EM_DIAGNOSTICO).")
  @PostMapping("/ordens-servico")
  public ResponseEntity<OrdemServicoResponse> abrirOs(@Valid @RequestBody AbrirOsRequest req) {
    List<OrdemServicoServiceImpl.ItemAbertura> itens =
        req.itens() == null
            ? List.of()
            : req.itens().stream()
                .map(
                    i ->
                        new OrdemServicoServiceImpl.ItemAbertura(
                            i.idServicoSku(), i.tipo(), i.quantidade(), i.precoUnitario()))
                .toList();
    OrdemServico os = osService.abrir(req.idCliente(), req.placa(), req.descricaoProblema(), itens);
    return ResponseEntity.status(HttpStatus.CREATED).body(OrdemServicoResponse.de(os));
  }

  @Operation(
      summary = "02.6.02 - Abrir OS (Recebida)",
      description =
          "Cria a OS apenas com dados básicos (sem serviços, peças ou orçamento), no status"
              + " RECEBIDA. Não confundir com 02.6.01, que pode já"
              + " incluir itens no orçamento.")
  @PostMapping("/ordens-servico/recebida")
  public ResponseEntity<OrdemServicoResponse> abrirOsRecebida(
      @Valid @RequestBody AbrirOsRecebidaRequest req) {
    OrdemServico os = osService.abrir(req.idCliente(), req.placa(), req.descricaoProblema());
    return ResponseEntity.status(HttpStatus.CREATED).body(OrdemServicoResponse.de(os));
  }

  @Operation(
      summary = "02.6.03 - Alterar status da OS (contingência)",
      description =
          "Função de contingência do perfil administrativo para alterar o status da OS,"
              + " respeitando as regras: RECEBIDA (somente sem orçamento vinculado);"
              + " EM_DIAGNOSTICO e AGUARDANDO_APROVACAO (permitidos); EM_EXECUCAO (somente com"
              + " serviço ou peça vinculada); AGUARDANDO_PAGAMENTO e ENTREGUE (somente com o"
              + " último orçamento encerrado); CANCELADA (somente com todos os orçamentos"
              + " cancelados).")
  @PatchMapping("/ordens-servico/{numeroOs}/status")
  public OrdemServicoResponse alterarStatusOs(
      @Parameter(name = "numeroOs", description = DESC_NUMERO_OS, example = EXAMPLE_NUMERO_OS)
          @PathVariable("numeroOs")
          String numero,
      @Valid @RequestBody AlterarStatusRequest req) {
    return OrdemServicoResponse.de(osService.alterarStatus(numero, req.status()));
  }

  // =====================================================================================
  // 07 - Estoque
  // =====================================================================================

  @Operation(summary = "02.7.01 - Listar saldo de estoque de todas as peças")
  @GetMapping("/estoque")
  public List<EstoqueResponse> listarEstoque() {
    return estoqueService.listar().stream().map(EstoqueResponse::de).toList();
  }

  @Operation(summary = "02.7.02 - Consultar saldo de estoque de uma peça")
  @GetMapping("/estoque/{idSku}")
  public EstoqueResponse saldoEstoque(
      @Parameter(name = "idSku", description = "SKU da peça.", example = "1") @PathVariable
          Long idSku) {
    return new EstoqueResponse(idSku, estoqueService.saldo(idSku), Instant.now());
  }

  // =====================================================================================
  // 08 - Relatórios
  // =====================================================================================

  @Operation(
      summary = "02.8.01 - Tempo médio por execução de OS (em horas)",
      description =
          "Retorna a duração efetiva (em horas) de cada Ordem de Serviço encerrada e a média"
              + " geral. Considera apenas OS com ambos os timestamps preenchidos:"
              + " `inicio_execucao` (1ª transição para EM_EXECUCAO, aprovação do orçamento)"
              + " e `fim_execucao` (1ª transição para AGUARDANDO_PAGAMENTO, fim do reparo)."
              + " Cálculo por OS: (fim_execucao - inicio_execucao) em horas decimais."
              + " Cálculo da média: AVG das durações. Sem OS encerradas, retorna 0.0.")
  @GetMapping("/relatorios/tempo-medio-por-os")
  public TempoMedioPorOsResponse tempoMedioPorOs() {
    RelatorioGateway.RelatorioResult r = relatorioService.tempoMedioPorOs();
    return TempoMedioPorOsResponse.de(r);
  }

  @Operation(
      summary = "02.8.02 - Relatório de OS por status",
      description =
          "Retorna as OS em andamento agrupadas por status na ordem: EM_EXECUCAO,"
              + " AGUARDANDO_APROVACAO, EM_DIAGNOSTICO e RECEBIDA. Dentro de cada status, as OS"
              + " são ordenadas pela data de inclusão (da mais antiga para a mais recente)."
              + " Exclui PAGA, ENTREGUE e CANCELADA.")
  @GetMapping("/relatorios/os-por-status")
  public List<OrdemServicoResponse> relatorioOsPorStatus() {
    return osService.listarAtivas().stream().map(OrdemServicoResponse::de).toList();
  }

  // =====================================================================================
  // 09 - Contas a Receber
  // =====================================================================================

  @Operation(summary = "02.9.01 - Listar contas a receber (clientes)")
  @GetMapping("/contas-a-receber")
  public List<LancamentoResponse> listarContasAReceber() {
    return financeiroService.contasAReceber().stream().map(LancamentoResponse::de).toList();
  }

  // =====================================================================================
  // 10 - Contas a Pagar
  // =====================================================================================

  @Operation(summary = "02.10.01 - Listar contas a pagar (fornecedores)")
  @GetMapping("/contas-a-pagar")
  public List<LancamentoResponse> listarContasAPagar() {
    return financeiroService.contasAPagar().stream().map(LancamentoResponse::de).toList();
  }
}
