package com.mds.orderapi.util;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation id kept in the logging MDC so log lines from one request or message can be linked across services.
 */
public final class CorrelationId {

    /** HTTP header carrying the correlation id in requests and responses. */
    public static final String HTTP_HEADER = "X-Correlation-Id";
    /** MDC key under which the correlation id appears in log lines. */
    public static final String MDC_KEY = "correlationId";

    /**
     * Accepted format for incoming ids; anything else is replaced, which also prevents log injection.
     */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {
    }

    /**
     * Returns the correlation id of the request or message being processed.
     *
     * @return the correlation id bound to the current thread, or {@code null}
     */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /**
     * Binds a correlation id to the current thread until the returned handle is closed.
     *
     * @param candidate incoming id; replaced with a new one if missing or invalid
     * @return handle that removes the id when closed
     */
    public static MDC.MDCCloseable bind(String candidate) {
        return MDC.putCloseable(MDC_KEY, validOrNew(candidate));
    }

    /**
     * Validates an incoming correlation id.
     *
     * @param candidate incoming id
     * @return the candidate if it's valid, otherwise a new random UUID
     */
    public static String validOrNew(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }
}