package io.kestra.plugin.gemini;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
            .model(Property.ofValue("gemini-2.5-flash-preview-05-20"))
            .messages(Property.ofValue(List.of("What is the capital of Japan? Answer with a unique word and without any punctuation.")))
            .build();

        var output = chatCompletion.run(runContext);

        assertEquals("Tokyo", output.getPredictions().getFirst().content());
    }
}
