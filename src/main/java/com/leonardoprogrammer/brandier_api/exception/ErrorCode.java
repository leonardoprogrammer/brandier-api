package com.leonardoprogrammer.brandier_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ErrorCode {

    REQUISICAO_INVALIDA(HttpStatus.BAD_REQUEST, "Requisição inválida", "A requisição não pôde ser processada."),
    VALIDACAO_INVALIDA(HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos estão inválidos."),
    NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Não autenticado", "É necessário estar autenticado para acessar este recurso."),
    TOKEN_ATUALIZACAO_INVALIDO(HttpStatus.UNAUTHORIZED, "Sessão inválida", "O token de atualização é inválido, expirou ou já foi utilizado."),
    ACESSO_NEGADO(HttpStatus.FORBIDDEN, "Acesso negado", "Você não tem permissão para acessar este recurso."),
    CSRF_INVALIDO(HttpStatus.FORBIDDEN, "Requisição não autorizada", "O token CSRF está ausente ou é inválido."),
    EMAIL_NAO_VERIFICADO(HttpStatus.FORBIDDEN, "E-mail não verificado", "Confirme seu e-mail para acessar a conta. Se precisar, reenvie o link de verificação."),
    TOKEN_VERIFICACAO_INVALIDO(HttpStatus.BAD_REQUEST, "Link inválido", "O link de verificação é inválido, expirou ou já foi utilizado."),
    RECURSO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Recurso não encontrado", "O recurso solicitado não foi encontrado."),
    METODO_NAO_PERMITIDO(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido", "O método HTTP não é permitido para este recurso."),
    MIDIA_NAO_SUPORTADA(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de conteúdo não suportado", "O tipo de conteúdo enviado não é suportado."),
    ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Ocorreu um erro inesperado. Tente novamente mais tarde.");

    private final HttpStatus status;
    private final String title;
    private final String message;

    ErrorCode(HttpStatus status, String title, String message) {
        this.status = status;
        this.title = title;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public static ErrorCode fromStatus(HttpStatusCode statusCode) {
        for (ErrorCode code : values()) {
            if (code != VALIDACAO_INVALIDA && code.status.value() == statusCode.value()) {
                return code;
            }
        }
        return statusCode.is4xxClientError() ? REQUISICAO_INVALIDA : ERRO_INTERNO;
    }
}
