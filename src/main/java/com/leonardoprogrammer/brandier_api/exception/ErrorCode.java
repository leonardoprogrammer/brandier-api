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
    MUITAS_TENTATIVAS(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas", "Muitas tentativas. Tente novamente mais tarde."),
    TOKEN_REDEFINICAO_INVALIDO(HttpStatus.BAD_REQUEST, "Link inválido", "O link de redefinição de senha é inválido, expirou ou já foi utilizado."),
    EMAIL_DESCARTAVEL(HttpStatus.BAD_REQUEST, "E-mail não aceito", "E-mails temporários ou descartáveis não são aceitos. Use um e-mail permanente."),
    TOKEN_VERIFICACAO_INVALIDO(HttpStatus.BAD_REQUEST, "Link inválido", "O link de verificação é inválido, expirou ou já foi utilizado."),
    CONFLITO(HttpStatus.CONFLICT, "Conflito", "A operação conflita com o estado atual do recurso."),
    LIMITE_MARCAS_ATINGIDO(HttpStatus.CONFLICT, "Limite de marcas atingido", "Você já atingiu o limite de marcas desta conta."),
    MARCA_NOME_DUPLICADO(HttpStatus.CONFLICT, "Marca já existe", "Você já tem uma marca com esse nome."),
    LIMITE_LOJAS_ATINGIDO(HttpStatus.CONFLICT, "Limite de lojas atingido", "Esta marca já atingiu o limite de lojas."),
    LOJA_NOME_DUPLICADO(HttpStatus.CONFLICT, "Loja já existe", "Esta marca já tem uma loja com esse nome."),
    LIMITE_PRODUTOS_ATINGIDO(HttpStatus.CONFLICT, "Limite de produtos atingido", "Esta marca já atingiu o limite de produtos do catálogo."),
    PRODUTO_NOME_DUPLICADO(HttpStatus.CONFLICT, "Produto já existe", "Esta marca já tem um produto com esse nome."),
    LIMITE_PROMOTORES_ATINGIDO(HttpStatus.CONFLICT, "Limite de promotores atingido", "Esta marca já atingiu o limite de promotores (contando convites pendentes)."),
    CONVITE_JA_PENDENTE(HttpStatus.CONFLICT, "Convite já enviado", "Já existe um convite pendente para este e-mail nesta marca."),
    PROMOTOR_JA_VINCULADO(HttpStatus.CONFLICT, "Promotor já vinculado", "Esta pessoa já é promotora desta marca."),
    CONVITE_PROPRIO_EMAIL(HttpStatus.BAD_REQUEST, "Convite inválido", "Você não pode convidar o seu próprio e-mail: o gestor já pode registrar reposições na marca."),
    TOKEN_CONVITE_INVALIDO(HttpStatus.BAD_REQUEST, "Convite inválido", "O convite é inválido, expirou, já foi respondido ou foi enviado para outro e-mail."),
    LOJA_COM_REPOSICOES(HttpStatus.CONFLICT, "Loja com reposições", "Esta loja já tem reposições registradas e não pode ser excluída."),
    MARCA_INATIVA(HttpStatus.CONFLICT, "Marca inativa", "Esta marca foi encerrada porque a conta do gestor foi excluída e não aceita novas reposições."),
    REPOSICAO_SEM_ITENS(HttpStatus.BAD_REQUEST, "Reposição sem itens", "Informe ao menos um produto na reposição."),
    QUANTIDADE_INVALIDA(HttpStatus.BAD_REQUEST, "Quantidade inválida", "A quantidade de cada produto deve ser maior que zero."),
    PRODUTO_REPETIDO(HttpStatus.BAD_REQUEST, "Produto repetido", "O mesmo produto não pode aparecer duas vezes na mesma reposição."),
    PRODUTO_INVALIDO(HttpStatus.BAD_REQUEST, "Produto inválido", "Um ou mais produtos não pertencem a esta marca ou estão inativos."),
    DATA_REPOSICAO_FUTURA(HttpStatus.BAD_REQUEST, "Data inválida", "A data da reposição não pode estar no futuro."),
    JANELA_EDICAO_ENCERRADA(HttpStatus.FORBIDDEN, "Prazo de edição encerrado", "O prazo para alterar ou excluir esta reposição já terminou. Peça ao gestor da marca."),
    RELATORIO_MUITO_GRANDE(HttpStatus.BAD_REQUEST, "Relatório muito grande", "O relatório tem registros demais para exportar. Refine os filtros, por exemplo com um período menor."),
    PERIODO_INVALIDO(HttpStatus.BAD_REQUEST, "Período inválido", "Informe um período em que a data inicial vem antes da final e com no máximo 366 dias."),
    LIMITE_CONTAS_ATINGIDO(HttpStatus.CONFLICT, "Cadastros fechados", "O limite de contas desta demonstração foi atingido. Tente novamente mais tarde."),
    CAPTCHA_INVALIDO(HttpStatus.BAD_REQUEST, "Verificação de segurança inválida", "Não foi possível validar a verificação de segurança. Tente novamente."),
    SENHA_INCORRETA(HttpStatus.FORBIDDEN, "Senha incorreta", "A senha informada está incorreta."),
    SENHA_NAO_DEFINIDA(HttpStatus.FORBIDDEN, "Senha não definida", "Esta conta ainda não tem uma senha definida. Entre com o Google ou solicite uma em \"Esqueci minha senha\"."),
    GOOGLE_TOKEN_INVALIDO(HttpStatus.BAD_REQUEST, "Login com Google inválido", "Não foi possível validar o login com o Google. Tente novamente."),
    GOOGLE_AUTH_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE, "Login com Google indisponível", "O login com o Google não está disponível neste momento."),
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
