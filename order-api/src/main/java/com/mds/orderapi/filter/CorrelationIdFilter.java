package com.mds.orderapi.filter;

import com.mds.orderapi.util.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Takes the correlation id from the {@code X-Correlation-Id} header (or generates one), puts it in the logging
 * MDC for the request and echoes it in the response. The outbox stores it so it travels on to the broker.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Binds the correlation id for the duration of the request.
     *
     * @param request  incoming request
     * @param response outgoing response
     * @param chain    remaining filter chain
     * @throws ServletException if a downstream filter fails
     * @throws IOException      if a downstream filter fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String id = CorrelationId.validOrNew(request.getHeader(CorrelationId.HTTP_HEADER));

        try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationId.MDC_KEY, id)) {
            response.setHeader(CorrelationId.HTTP_HEADER, id);
            chain.doFilter(request, response);
        }
    }
}