package com.leonardoprogrammer.brandier_api.model.dto;

import java.util.UUID;

public record AgregadoDTO(UUID id, String nome, Long reposicoes, Long unidades) {
}
