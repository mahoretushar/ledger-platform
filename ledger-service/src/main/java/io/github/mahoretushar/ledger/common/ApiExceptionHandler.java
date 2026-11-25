package io.github.mahoretushar.ledger.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain errors to RFC 9457 problem+json responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException ex) {
        HttpStatus status = "IDEMPOTENCY_KEY_REUSED".equals(ex.getCode())
                ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_CONTENT;
        return problem(status, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "Resource changed, retry the request");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(code);
        pd.setProperty("code", code);
        return pd;
    }
}
