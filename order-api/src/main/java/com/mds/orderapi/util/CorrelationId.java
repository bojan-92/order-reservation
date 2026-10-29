package com.mds.orderapi.util;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {

    public static final String HTTP_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /**
     * log injection protection
     */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static MDC.MDCCloseable bind(String candidate) {
        return MDC.putCloseable(MDC_KEY, validOrNew(candidate));
    }

    public static String validOrNew(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }
}