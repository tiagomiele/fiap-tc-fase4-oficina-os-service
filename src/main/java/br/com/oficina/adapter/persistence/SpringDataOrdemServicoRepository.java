package br.com.oficina.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataOrdemServicoRepository
    extends JpaRepository<OrdemServicoJpaEntity, String> {

  String STATUS_ATIVOS =
      "('RECEBIDA','EM_DIAGNOSTICO','AGUARDANDO_APROVACAO','EM_EXECUCAO','AGUARDANDO_PAGAMENTO')";

  @Query(
      value =
          "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM ordens_servico "
              + "WHERE id_cliente = :idCliente AND status IN "
              + STATUS_ATIVOS,
      nativeQuery = true)
  boolean existeOsAtivaPorCliente(@Param("idCliente") Long idCliente);

  @Query(
      value =
          "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM ordens_servico "
              + "WHERE id_placa = :placa AND id_cliente = :idCliente AND status IN "
              + STATUS_ATIVOS,
      nativeQuery = true)
  boolean existeOsAtivaPorVeiculo(@Param("placa") String placa, @Param("idCliente") Long idCliente);

  @Query(
      value =
          "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM "
              + "orcamentos_itens_ordem_servico i "
              + "JOIN ordens_servico o ON o.id_ordem_servico = i.id_ordem_servico "
              + "WHERE i.id_servico_sku = :idServicoSku AND i.tipo_item = :tipoItem "
              + "AND i.status <> 'CANCELADO' AND o.status IN "
              + STATUS_ATIVOS,
      nativeQuery = true)
  boolean existeOsAtivaPorServicoSku(
      @Param("idServicoSku") Long idServicoSku, @Param("tipoItem") String tipoItem);
}
