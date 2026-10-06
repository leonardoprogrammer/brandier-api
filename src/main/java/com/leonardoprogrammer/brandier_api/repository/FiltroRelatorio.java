package com.leonardoprogrammer.brandier_api.repository;

import java.time.Instant;
import java.util.UUID;

public record FiltroRelatorio(UUID marcaId, UUID lojaId, UUID autorId, UUID produtoId, Instant de, Instant ate) {
}
