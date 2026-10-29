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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

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