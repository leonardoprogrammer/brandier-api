package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.enums.StatusConvite;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.dto.VinculoPromotorDTO;
import com.leonardoprogrammer.brandier_api.model.entity.ConvitePromotor;
import com.leonardoprogrammer.brandier_api.model.entity.Promotor;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.ConvitePromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.PromotorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PromotorService {

    public record PromotoresDaMarca(List<Promotor> promotores, List<ConvitePromotor> convites, Instant agora,
                                    int usadas, int limite) {
    }

    private final PromotorRepository promotorRepository;
    private final ConvitePromotorRepository conviteRepository;
    private final MarcaRepository marcaRepository;
    private final LimitsProperties limits;
    private final Clock clock;

    public PromotorService(PromotorRepository promotorRepository,
                           ConvitePromotorRepository conviteRepository,
                           MarcaRepository marcaRepository,
                           LimitsProperties limits,
                           Clock clock) {
        this.promotorRepository = promotorRepository;
        this.conviteRepository = conviteRepository;
        this.marcaRepository = marcaRepository;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PromotoresDaMarca listarDaMarca(Usuario gestor, UUID marcaId) {
        marcaRepository.findByIdAndGestorId(marcaId, gestor.getId()).orElseThrow(PromotorService::naoEncontrado);
        Instant agora = Instant.now(clock);

        List<Promotor> promotores = promotorRepository.listarPorMarca(marcaId);
        List<ConvitePromotor> convites = conviteRepository.findAllByMarcaIdOrderByCriadoEmDesc(marcaId);
        long pendentes = convites.stream().filter(c -> c.statusEm(agora) == StatusConvite.PENDENTE).count();

        return new PromotoresDaMarca(promotores, convites, agora, (int) (promotores.size() + pendentes),
                limits.maxPromotersPerBrand());
    }

    @Transactional
    public void remover(Usuario gestor, UUID marcaId, UUID promotorId) {
        marcaRepository.findByIdAndGestorIdForUpdate(marcaId, gestor.getId()).orElseThrow(PromotorService::naoEncontrado);
        Promotor promotor = promotorRepository.findByIdAndMarcaId(promotorId, marcaId)
                .orElseThrow(PromotorService::naoEncontrado);
        promotorRepository.delete(promotor);
    }

    @Transactional(readOnly = true)
    public List<VinculoPromotorDTO> meusVinculos(Usuario usuario) {
        return promotorRepository.listarPorUsuario(usuario.getId()).stream().map(VinculoPromotorDTO::de).toList();
    }

    @Transactional
    public void sair(Usuario usuario, UUID marcaId) {
        Promotor promotor = promotorRepository.findByMarcaIdAndUsuarioId(marcaId, usuario.getId())
                .orElseThrow(PromotorService::naoEncontrado);
        promotorRepository.delete(promotor);
    }

    private static BusinessRuleException naoEncontrado() {
        return new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO);
    }
}
