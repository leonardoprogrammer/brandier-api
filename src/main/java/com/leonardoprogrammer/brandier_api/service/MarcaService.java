package com.leonardoprogrammer.brandier_api.service;

import com.leonardoprogrammer.brandier_api.config.LimitsProperties;
import com.leonardoprogrammer.brandier_api.exception.BusinessRuleException;
import com.leonardoprogrammer.brandier_api.exception.ErrorCode;
import com.leonardoprogrammer.brandier_api.model.entity.Marca;
import com.leonardoprogrammer.brandier_api.model.entity.Usuario;
import com.leonardoprogrammer.brandier_api.repository.MarcaRepository;
import com.leonardoprogrammer.brandier_api.repository.ReposicaoRepository;
import com.leonardoprogrammer.brandier_api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MarcaService {

    private final MarcaRepository marcaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReposicaoRepository reposicaoRepository;
    private final LimitsProperties limits;

    public MarcaService(MarcaRepository marcaRepository,
                        UsuarioRepository usuarioRepository,
                        ReposicaoRepository reposicaoRepository,
                        LimitsProperties limits) {
        this.marcaRepository = marcaRepository;
        this.usuarioRepository = usuarioRepository;
        this.reposicaoRepository = reposicaoRepository;
        this.limits = limits;
    }

    public int limiteDeMarcas() {
        return limits.maxBrandsPerManager();
    }

    @Transactional(readOnly = true)
    public List<Marca> listar(Usuario gestor) {
        return marcaRepository.findAllByGestorIdOrderByNomeAsc(gestor.getId());
    }

    @Transactional(readOnly = true)
    public Marca buscar(Usuario gestor, UUID id) {
        return marcaRepository.findByIdAndGestorId(id, gestor.getId()).orElseThrow(MarcaService::naoEncontrada);
    }

    @Transactional
    public Marca criar(Usuario gestor, String nome, String descricao) {
        usuarioRepository.findByIdForUpdate(gestor.getId()).orElseThrow(MarcaService::naoEncontrada);

        if (marcaRepository.countByGestorId(gestor.getId()) >= limits.maxBrandsPerManager()) {
            throw new BusinessRuleException(ErrorCode.LIMITE_MARCAS_ATINGIDO);
        }

        String nomeNormalizado = normalizarNome(nome);
        if (marcaRepository.existsByGestorIdAndNomeIgnoreCase(gestor.getId(), nomeNormalizado)) {
            throw new BusinessRuleException(ErrorCode.MARCA_NOME_DUPLICADO);
        }

        Marca marca = new Marca();
        marca.setGestor(gestor);
        marca.setNome(nomeNormalizado);
        marca.setDescricao(normalizarDescricao(descricao));
        return marcaRepository.save(marca);
    }

    @Transactional
    public Marca atualizar(Usuario gestor, UUID id, String nome, String descricao) {
        Marca marca = buscar(gestor, id);

        String nomeNormalizado = normalizarNome(nome);
        if (marcaRepository.existsByGestorIdAndNomeIgnoreCaseAndIdNot(gestor.getId(), nomeNormalizado, id)) {
            throw new BusinessRuleException(ErrorCode.MARCA_NOME_DUPLICADO);
        }

        marca.setNome(nomeNormalizado);
        marca.setDescricao(normalizarDescricao(descricao));
        return marcaRepository.save(marca);
    }

    @Transactional
    public void excluir(Usuario gestor, UUID id) {
        inativarOuExcluir(buscar(gestor, id));
    }

    @Transactional
    public void excluirTodasDoGestor(UUID gestorId) {
        marcaRepository.findAllByGestorIdOrderByNomeAsc(gestorId).forEach(this::inativarOuExcluir);
    }

    private void inativarOuExcluir(Marca marca) {
        if (reposicaoRepository.existsByMarcaId(marca.getId())) {
            marca.setAtiva(false);
            marcaRepository.save(marca);
            return;
        }
        marcaRepository.delete(marca);
    }

    private static String normalizarNome(String nome) {
        return nome.trim();
    }

    private static String normalizarDescricao(String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return null;
        }
        return descricao.trim();
    }

    private static BusinessRuleException naoEncontrada() {
        return new BusinessRuleException(ErrorCode.RECURSO_NAO_ENCONTRADO);
    }
}
