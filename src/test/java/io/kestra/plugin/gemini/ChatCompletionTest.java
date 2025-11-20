package io.kestra.plugin.gemini;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.text.Normalizer;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
public class ChatCompletionTest {

    private static final String GEMINI_API_KEY = System.getenv("GEMINI_API_KEY");

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".*")
    void chatCompletion() throws Exception {
        var runContext = runContextFactory.of();
        var chatCompletion = ChatCompletion.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue("gemini-2.5-flash-lite"))
            .messages(Property.ofValue(List.of(
                new ChatCompletion.ChatMessage(
                    ChatCompletion.ChatMessageType.USER,
                    "What is the capital of Japan in 2025? Answer with a single unique word and without any punctuation."
                )
            )))
            .build();

        var output = chatCompletion.run(runContext);

        var content = Normalizer.normalize(output.getPredictions().getFirst().content(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        assertTrue(Stream.of("Tokyo", "Edo", "Tokio").anyMatch(content::contains));
    }
}
