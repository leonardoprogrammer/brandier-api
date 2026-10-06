package com.leonardoprogrammer.brandier_api.exception;

import com.leonardoprogrammer.brandier_api.ratelimit.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private record FieldError(String campo, String mensagem) {
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException ex) {
        ProblemDetail problem = Problems.create(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex) {
        long seconds = Math.max(1, (long) Math.ceil(ex.getRetryAfter().toMillis() / 1000.0));
        ProblemDetail problem = Problems.create(ErrorCode.MUITAS_TENTATIVAS,
                "Muitas tentativas. Tente novamente em " + describeWait(seconds) + ".");
        problem.setProperty("tentarNovamenteEmSegundos", seconds);
        return ResponseEntity.status(problem.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = Problems.create(ErrorCode.ACESSO_NEGADO);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        ProblemDetail problem = Problems.create(ErrorCode.NAO_AUTENTICADO);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        ProblemDetail problem = Problems.create(ErrorCode.ERRO_INTERNO);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static String describeWait(long seconds) {
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " segundo" : " segundos");
        }
        long minutes = (seconds + 59) / 60;
        return minutes + (minutes == 1 ? " minuto" : " minutos");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = Problems.create(ErrorCode.VALIDACAO_INVALIDA);
        problem.setProperty("erros", errors);
        return ResponseEntity.status(problem.getStatus()).headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        Object responseBody = body;
        if (responseBody == null && ex instanceof ErrorResponse errorResponse) {
            responseBody = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }
        if (responseBody instanceof ProblemDetail problem) {
            ErrorCode code = ErrorCode.fromStatus(statusCode);
            problem.setTitle(code.getTitle());
            problem.setDetail(code.getMessage());
            problem.setProperty(Problems.CODE_PROPERTY, code.name());
        }
        return super.handleExceptionInternal(ex, responseBody, headers, statusCode, request);
    }
}
