package com.leonardoprogrammer.brandier_api.exception;

import org.springframework.http.ProblemDetail;

public final class Problems {

    public static final String CODE_PROPERTY = "codigo";

    private Problems() {
    }

    public static ProblemDetail create(ErrorCode code) {
        return create(code, code.getMessage());
    }

    public static ProblemDetail create(ErrorCode code, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.getStatus(), message);
        problem.setTitle(code.getTitle());
        problem.setProperty(CODE_PROPERTY, code.name());
        return problem;
    }
}
