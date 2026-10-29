package com.mds.orderapi.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps exceptions to RFC 9457 problem responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Unknown order id.
     *
     * @param ex the exception
     * @return 404 problem
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleNotFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Order not found", ex.getMessage());
    }

    /**
     * Two requests created the same {@code orderId} at the same time and this one lost the primary-key race.
     * The client can retry and will get the stored order.
     *
     * @param ex the exception
     * @return 409 problem
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Concurrent request",
                "Order is being created by a concurrent request, retry the request");
    }

    /**
     * Builds a problem response.
     *
     * @param status HTTP status
     * @param title  short summary
     * @param detail explanation for the client
     * @return the problem detail
     */
    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}