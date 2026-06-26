package io.kestra.plugin.gemini;

import java.util.Set;

import com.google.genai.errors.ApiException;

import org.junit.jupiter.api.Assumptions;

/**
 * Helpers for the live Gemini integration tests.
 */
final class GeminiTestUtils {

    // HTTP status codes Gemini returns for transient, infra-side conditions
    // (overload, rate limiting, gateway errors). Hitting one of these is not a
    // code failure, so the test is skipped instead of failing CI.
    private static final Set<Integer> TRANSIENT_STATUS_CODES = Set.of(429, 500, 502, 503, 504);

    private GeminiTestUtils() {
    }

    /**
     * Runs a live Gemini call. If Gemini returns a transient error (for example
     * 503 "high demand"), the test is aborted and reported as skipped, so CI does
     * not go red on Google's availability blips. Any other error is rethrown.
     */
    static <T> T runOrSkipOnTransientError(GeminiCall<T> call) throws Exception {
        try {
            return call.call();
        } catch (ApiException e) {
            if (TRANSIENT_STATUS_CODES.contains(e.code())) {
                Assumptions.abort("Skipping: Gemini returned a transient error " + e.code() + ": " + e.message());
            }
            throw e;
        }
    }

    @FunctionalInterface
    interface GeminiCall<T> {
        T call() throws Exception;
    }
}
