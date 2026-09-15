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

    // Non-OK finish reasons the Gemini SDK surfaces as an IllegalArgumentException
    // instead of an ApiException. These are non-deterministic, model-side refusals
    // (e.g. the prompt happens to match training data) rather than code failures,
    // so the test is skipped instead of failing CI.
    private static final String FINISH_REASON_MESSAGE = "finished unexpectedly with reason";
    private static final Set<String> NON_OK_FINISH_REASONS = Set.of(
        "RECITATION", "SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII"
    );

    private GeminiTestUtils() {
    }

    static <T> T runOrSkipOnTransientError(GeminiCall<T> call) throws Exception {
        try {
            return call.call();
        } catch (ApiException e) {
            if (TRANSIENT_STATUS_CODES.contains(e.code())) {
                Assumptions.abort("Skipping: Gemini returned a transient error " + e.code() + ": " + e.message());
            }
            throw e;
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            if (message != null
                && (message.contains(FINISH_REASON_MESSAGE) || NON_OK_FINISH_REASONS.stream().anyMatch(message::contains))) {
                Assumptions.abort("Skipping: Gemini refused the request with a non-OK finish reason: " + message);
            }
            throw e;
        }
    }

    @FunctionalInterface
    interface GeminiCall<T> {
        T call() throws Exception;
    }
}
