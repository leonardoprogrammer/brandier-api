package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Loja;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.LojaRepository;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LojaService {

    private final LojaRepository lojaRepository;
    private final MarcaRepository marcaRepository;
    private final LimitsProperties limits;

    public LojaService(LojaRepository lojaRepository,
                       MarcaRepository marcaRepository,
                       LimitsProperties limits) {
        this.lojaRepository = lojaRepository;
        this.marcaRepository = marcaRepository;
        this.limits = limits;
    }

    public int limiteDeLojas() {
        return limits.maxStoresPerBrand();
    }

    @Transactional(readOnly = true)
    public List<Loja> listar(Usuario gestor, UUID marcaId) {
        buscarMarca(gestor, marcaId);
        return lojaRepository.findAllByMarcaIdOrderByNomeAsc(marcaId);
    }

    @Transactional(readOnly = true)
    public Loja buscar(Usuario gestor, UUID marcaId, UUID id) {
        buscarMarca(gestor, marcaId);
        return buscarLoja(marcaId, id);
    }

    @Transactional
    public Loja criar(Usuario gestor, UUID marcaId, String nome, String cidade, String uf, String endereco) {
        Marca marca = marcaRepository.findByIdAndGestorIdForUpdate(marcaId, gestor.getId())
                .orElseThrow(LojaService::naoEncontrada);

        if (lojaRepository.countByMarcaId(marcaId) >= limits.maxStoresPerBrand()) {
            throw new BusinessRuleException(ErrorCode.LIMITE_LOJAS_ATINGIDO);
        }

        String nomeNormalizado = nome.trim();
        if (lojaRepository.existsByMarcaIdAndNomeIgnoreCase(marcaId, nomeNormalizado)) {
            throw new BusinessRuleException(ErrorCode.LOJA_NOME_DUPLICADO);
        }

        Loja loja = new Loja();
        loja.setMarca(marca);
        preencher(loja, nomeNormalizado, cidade, uf, endereco);
        return lojaRepository.save(loja);
    }

    @Transactional
    public Loja atualizar(Usuario gestor, UUID marcaId, UUID id, String nome, String cidade, String uf, String endereco) {
        buscarMarca(gestor, marcaId);
        Loja loja = buscarLoja(marcaId, id);

        String nomeNormalizado = nome.trim();
        if (lojaRepository.existsByMarcaIdAndNomeIgnoreCaseAndIdNot(marcaId, nomeNormalizado, id)) {
            throw new BusinessRuleException(ErrorCode.LOJA_NOME_DUPLICADO);
        }

        preencher(loja, nomeNormalizado, cidade, uf, endereco);
        return lojaRepository.save(loja);
    }

    @Transactional
    public void excluir(Usuario gestor, UUID marcaId, UUID id) {
        buscarMarca(gestor, marcaId);
        lojaRepository.delete(buscarLoja(marcaId, id));
    }

    private Marca buscarMarca(Usuario gestor, UUID marcaId) {
        return marcaRepository.findByIdAndGestorId(marcaId, gestor.getId()).orElseThrow(LojaService::naoEncontrada);
    }

    private Loja buscarLoja(UUID marcaId, UUID id) {
        return lojaRepository.findByIdAndMarcaId(id, marcaId).orElseThrow(LojaService::naoEncontrada);
    }

    private static void preencher(Loja loja, String nome, String cidade, String uf, String endereco) {
        loja.setNome(nome);
        loja.setCidade(cidade.trim());
        loja.setUf(uf.trim().toUpperCase(Locale.ROOT));
        loja.setEndereco(endereco == null || endereco.isBlank() ? null : endereco.trim());
    }

    private static BusinessRuleException naoEncontrada() {
        return new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO);
    }
}
