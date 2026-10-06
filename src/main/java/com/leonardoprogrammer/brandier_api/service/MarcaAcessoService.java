package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.PromotorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MarcaAcessoService {

    public record Acesso(Marca marca, boolean gestor) {
    }

    private final MarcaRepository marcaRepository;
    private final PromotorRepository promotorRepository;

    public MarcaAcessoService(MarcaRepository marcaRepository, PromotorRepository promotorRepository) {
        this.marcaRepository = marcaRepository;
        this.promotorRepository = promotorRepository;
    }

    @Transactional(readOnly = true)
    public Acesso exigir(Usuario usuario, UUID marcaId) {
        Marca marca = marcaRepository.findById(marcaId).orElseThrow(MarcaAcessoService::naoEncontrada);

        if (marca.getGestor().getId().equals(usuario.getId())) {
            return new Acesso(marca, true);
        }
        if (promotorRepository.existsByMarcaIdAndUsuarioId(marcaId, usuario.getId())) {
            return new Acesso(marca, false);
        }
        throw naoEncontrada();
    }

    private static BusinessRuleException naoEncontrada() {
        return new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO);
    }
}
