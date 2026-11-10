package br.com.oficina.usecase;

import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.model.Placa;
import br.com.oficina.domain.model.Veiculo;
import br.com.oficina.domain.model.VeiculoId;
import br.com.oficina.usecase.gateway.ClienteRepository;
import br.com.oficina.usecase.gateway.VeiculoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VeiculoServiceImpl {

  private static final String ERRO_VEICULO_NAO_CADASTRADO = "VEICULO_NAO_CADASTRADO";
  private static final String MSG_VEICULO_NAO_CADASTRADO = "Veículo não cadastrado";

  private final VeiculoRepository repo;
  private final ClienteRepository clientes;

  public VeiculoServiceImpl(VeiculoRepository repo, ClienteRepository clientes) {
    this.repo = repo;
    this.clientes = clientes;
  }

  @Transactional
  public Veiculo cadastrar(String placa, Long idCliente, String marca, String modelo, int ano) {
    if (clientes.porId(idCliente).isEmpty()) {
      throw new BusinessException("CLIENTE_NAO_CADASTRADO", "Cliente não cadastrado");
    }
    Placa p = Placa.de(placa);
    VeiculoId id = new VeiculoId(p, idCliente);
    if (repo.porId(id).isPresent()) {
      throw new BusinessException("VEICULO_DUPLICADO", "Veículo já cadastrado para esse cliente");
    }
    return repo.salvar(Veiculo.criar(p, idCliente, marca, modelo, ano));
  }

  @Transactional
  public Veiculo atualizar(String placa, Long idCliente, String marca, String modelo, int ano) {
    VeiculoId id = new VeiculoId(Placa.de(placa), idCliente);
    Veiculo v =
        repo.porId(id)
            .orElseThrow(
                () ->
                    new BusinessException(ERRO_VEICULO_NAO_CADASTRADO, MSG_VEICULO_NAO_CADASTRADO));
    v.atualizar(marca, modelo, ano);
    return repo.salvar(v);
  }

  @Transactional
  public void desativar(String placa, Long idCliente) {
    VeiculoId id = new VeiculoId(Placa.de(placa), idCliente);
    Veiculo v =
        repo.porId(id)
            .orElseThrow(
                () ->
                    new BusinessException(ERRO_VEICULO_NAO_CADASTRADO, MSG_VEICULO_NAO_CADASTRADO));
    if (repo.temOsAtiva(id)) {
      throw new BusinessException(
          "VEICULO_COM_OS_ATIVA", "Veículo possui OS ativa e não pode ser desativado");
    }
    v.desativar();
    repo.salvar(v);
  }

  @Transactional(readOnly = true)
  public Veiculo buscar(String placa, Long idCliente) {
    VeiculoId id = new VeiculoId(Placa.de(placa), idCliente);
    return repo.porId(id)
        .orElseThrow(
            () -> new BusinessException(ERRO_VEICULO_NAO_CADASTRADO, MSG_VEICULO_NAO_CADASTRADO));
  }

  @Transactional(readOnly = true)
  public List<Veiculo> porCliente(Long idCliente) {
    return repo.porCliente(idCliente);
  }
}
