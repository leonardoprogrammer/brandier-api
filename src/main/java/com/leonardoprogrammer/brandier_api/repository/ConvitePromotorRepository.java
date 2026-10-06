package com.leonardoprogrammer.brandier_api.repository;

import com.leonardoprogrammer.brandier_api.enums.StatusConvite;
import com.leonardoprogrammer.brandier_api.model.entity.ConvitePromotor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConvitePromotorRepository extends JpaRepository<ConvitePromotor, UUID> {

    Optional<ConvitePromotor> findByTokenHash(String tokenHash);

    @EntityGraph(attributePaths = {"marca", "marca.gestor"})
    List<ConvitePromotor> findAllByEmailCanonicoAndStatusAndExpiraEmAfterOrderByCriadoEmDesc(String emailCanonico,
                                                                                              StatusConvite status,
                                                                                              Instant agora);

    List<ConvitePromotor> findAllByMarcaIdOrderByCriadoEmDesc(UUID marcaId);

    Optional<ConvitePromotor> findByIdAndMarcaId(UUID id, UUID marcaId);

    long countByMarcaIdAndStatusAndExpiraEmAfter(UUID marcaId, StatusConvite status, Instant agora);

    boolean existsByMarcaIdAndEmailCanonicoAndStatusAndExpiraEmAfter(UUID marcaId, String emailCanonico,
                                                                      StatusConvite status, Instant agora);

    @Modifying
    @Query("update ConvitePromotor c set c.status = :novo, c.atualizadoEm = :agora "
            + "where c.id = :id and c.status = :pendente and c.expiraEm > :agora")
    int responderSePendente(@Param("id") UUID id,
                            @Param("novo") StatusConvite novo,
                            @Param("pendente") StatusConvite pendente,
                            @Param("agora") Instant agora);

    @Modifying
    @Query("delete from ConvitePromotor c where c.expiraEm < :limite")
    int deleteExpired(@Param("limite") Instant limite);
}
