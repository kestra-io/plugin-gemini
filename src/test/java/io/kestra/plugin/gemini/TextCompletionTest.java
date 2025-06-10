package io.kestra.plugin.gemini;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KestraTest
public class TextCompletionTest {

    private static final String GEMINI_API_KEY = System.getenv("GEMINI_API_KEY");

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".*")
    void textCompletion() throws Exception {
        var runContext = runContextFactory.of();
        var textCompletion = TextCompletion.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue("gemini-2.5-flash-preview-05-20"))
            .prompt(Property.ofValue("Where is Tbilisi? Answer in one word without any punctuation."))
            .build();

        var output = textCompletion.run(runContext);

        assertEquals("Georgia", output.getPredictions().getFirst().content());
    }
}
