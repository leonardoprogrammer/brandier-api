package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.email.EmailCanonicalizer;
import com.leonardoprogrammer.brandier_api.enums.StatusConvite;
import com.leonardoprogrammer.brandier_api.event.ConviteEmailRequested;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.dto.ConviteRecebidoDTO;
import com.leonardoprogrammer.brandier_api.model.dto.VinculoPromotorDTO;
import com.leonardoprogrammer.brandier_api.model.entity.ConvitePromotor;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Promotor;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.ConvitePromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.PromotorRepository;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import com.leonardoprogrammer.brandier_api.security.OpaqueTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class ConviteService {

    private final ConvitePromotorRepository conviteRepository;
    private final PromotorRepository promotorRepository;
    private final MarcaRepository marcaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LimitsProperties limits;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration lifetime;

    public ConviteService(ConvitePromotorRepository conviteRepository,
                          PromotorRepository promotorRepository,
                          MarcaRepository marcaRepository,
                          UsuarioRepository usuarioRepository,
                          LimitsProperties limits,
                          ApplicationEventPublisher eventPublisher,
                          Clock clock,
                          @Value("${brandier.invitation.expiration-days}") long lifetimeDays) {
        this.conviteRepository = conviteRepository;
        this.promotorRepository = promotorRepository;
        this.marcaRepository = marcaRepository;
        this.usuarioRepository = usuarioRepository;
        this.limits = limits;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.lifetime = Duration.ofDays(lifetimeDays);
    }

    @Transactional
    public ConvitePromotor convidar(Usuario gestor, UUID marcaId, String email) {
        Marca marca = marcaRepository.findByIdAndGestorIdForUpdate(marcaId, gestor.getId())
                .orElseThrow(ConviteService::naoEncontrado);
        Instant agora = Instant.now(clock);

        String emailNormalizado = EmailCanonicalizer.normalize(email);
        String emailCanonico = EmailCanonicalizer.canonicalize(emailNormalizado);

        if (emailCanonico.equals(gestor.getEmailCanonico())) {
            throw new BusinessRuleException(ErrorCode.CONVITE_PROPRIO_EMAIL);
        }

        boolean jaEhPromotor = usuarioRepository.findByEmailCanonico(emailCanonico)
                .map(usuario -> promotorRepository.existsByMarcaIdAndUsuarioId(marcaId, usuario.getId()))
                .orElse(false);
        if (jaEhPromotor) {
            throw new BusinessRuleException(ErrorCode.PROMOTOR_JA_VINCULADO);
        }

        if (conviteRepository.existsByMarcaIdAndEmailCanonicoAndStatusAndExpiraEmAfter(
                marcaId, emailCanonico, StatusConvite.PENDENTE, agora)) {
            throw new BusinessRuleException(ErrorCode.CONVITE_JA_PENDENTE);
        }

        long ocupadas = promotorRepository.countByMarcaId(marcaId)
                + conviteRepository.countByMarcaIdAndStatusAndExpiraEmAfter(marcaId, StatusConvite.PENDENTE, agora);
        if (ocupadas >= limits.maxPromotersPerBrand()) {
            throw new BusinessRuleException(ErrorCode.LIMITE_PROMOTORES_ATINGIDO);
        }

        String token = OpaqueTokens.generate();
        ConvitePromotor convite = new ConvitePromotor();
        convite.setMarca(marca);
        convite.setEmail(emailNormalizado);
        convite.setEmailCanonico(emailCanonico);
        convite.setTokenHash(OpaqueTokens.hash(token));
        convite.setStatus(StatusConvite.PENDENTE);
        convite.setCriadoEm(agora);
        convite.setExpiraEm(agora.plus(lifetime));
        ConvitePromotor salvo = conviteRepository.save(convite);

        eventPublisher.publishEvent(new ConviteEmailRequested(emailNormalizado, gestor.getNome(), marca.getNome(), token));
        return salvo;
    }

    @Transactional
    public void cancelar(Usuario gestor, UUID marcaId, UUID conviteId) {
        marcaRepository.findByIdAndGestorId(marcaId, gestor.getId()).orElseThrow(ConviteService::naoEncontrado);
        ConvitePromotor convite = conviteRepository.findByIdAndMarcaId(conviteId, marcaId)
                .orElseThrow(ConviteService::naoEncontrado);
        conviteRepository.delete(convite);
    }

    @Transactional(readOnly = true)
    public ConviteRecebidoDTO consultar(Usuario usuario, String token) {
        return ConviteRecebidoDTO.de(buscarValido(usuario, token, Instant.now(clock)));
    }

    @Transactional
    public VinculoPromotorDTO aceitar(Usuario usuario, String token) {
        Instant agora = Instant.now(clock);
        ConvitePromotor convite = buscarValido(usuario, token, agora);

        if (conviteRepository.responderSePendente(convite.getId(), StatusConvite.ACEITO, StatusConvite.PENDENTE, agora) == 0) {
            throw conviteInvalido();
        }

        Marca marca = convite.getMarca();
        Promotor promotor = promotorRepository.findByMarcaIdAndUsuarioId(marca.getId(), usuario.getId()).orElseGet(() -> {
            Promotor novo = new Promotor();
            novo.setMarca(marca);
            novo.setUsuario(usuario);
            novo.setEntrouEm(agora);
            return promotorRepository.save(novo);
        });
        return VinculoPromotorDTO.de(promotor);
    }

    @Transactional
    public void recusar(Usuario usuario, String token) {
        Instant agora = Instant.now(clock);
        ConvitePromotor convite = buscarValido(usuario, token, agora);

        if (conviteRepository.responderSePendente(convite.getId(), StatusConvite.RECUSADO, StatusConvite.PENDENTE, agora) == 0) {
            throw conviteInvalido();
        }
    }

    private ConvitePromotor buscarValido(Usuario usuario, String token, Instant agora) {
        ConvitePromotor convite = conviteRepository.findByTokenHash(OpaqueTokens.hash(token))
                .orElseThrow(ConviteService::conviteInvalido);

        boolean valido = convite.getStatus() == StatusConvite.PENDENTE
                && convite.getExpiraEm().isAfter(agora)
                && convite.getEmailCanonico().equals(usuario.getEmailCanonico())
                && !convite.getMarca().getGestor().getId().equals(usuario.getId());
        if (!valido) {
            throw conviteInvalido();
        }
        return convite;
    }

    private static BusinessRuleException conviteInvalido() {
        return new BusinessRuleException(ErrorCode.TOKEN_CONVITE_INVALIDO);
    }

    private static BusinessRuleException naoEncontrado() {
        return new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO);
    }
}
