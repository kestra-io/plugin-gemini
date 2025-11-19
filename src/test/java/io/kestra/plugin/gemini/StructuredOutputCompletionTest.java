package io.kestra.plugin.gemini;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@KestraTest
class StructuredOutputCompletionTest {
    private static final String GEMINI_API_KEY = System.getenv("GEMINI_API_KEY");
    private static final String GEMINI_2_5_FLASH_LITE= "gemini-2.5-flash-lite";

    @Inject
    private RunContextFactory runContextFactory;


    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".*")
    void structuredOutputCompletion() throws Exception {
        var runContext = runContextFactory.of();

        var structuredOutputCompletionBuilder = StructuredOutputCompletion.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue(GEMINI_2_5_FLASH_LITE))
            .prompt(Property.ofValue("List a few popular cookie recipes, and include the amounts of ingredients?"))
            .jsonResponseSchema(Property.ofValue(
                """
                    {
                       "type": "ARRAY",
                       "items": {
                         "type": "OBJECT",
                         "properties": {
                           "recipeName": { "type": "STRING" },
                           "ingredients": {
                             "type": "ARRAY",
                             "items": { "type": "STRING" }
                           }
                         },
                         "propertyOrdering": ["recipeName", "ingredients"]
                       }
                     }

                    """
            ))
            .build();

        var output = structuredOutputCompletionBuilder.run(runContext);
        assertThat(output.getPredictions().toString(), containsString("recipeName"));
        assertThat(output.getPredictions().toString(), containsString("ingredients"));
        assertThat(output.getPredictions().toString(), containsString("\"recipeName\":"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".*")
    void structuredOutputCompletion_withSimpleStructure() throws Exception {
        var runContext = runContextFactory.of();

        var structuredOutputCompletionBuilder = StructuredOutputCompletion.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue(GEMINI_2_5_FLASH_LITE))
            .prompt(Property.ofValue("List a few popular cookie recipes, and include the amounts of ingredients?"))
            .jsonResponseSchema(Property.ofValue(
                """
                     {
                        "type": "object",
                        "properties": {
                            "predictions": {
                                "type": "array",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "content": {
                                            "type": "string"
                                        }
                                    }
                                }
                            }
                        }
                    }

                    """
            ))
            .build();

        final var output = structuredOutputCompletionBuilder.run(runContext);
        assertThat(output.getPredictions().toString(), containsString("predictions"));
        assertThat(output.getPredictions().toString(), containsString("content"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".*")
    void structuredOutputCompletion_givenNoJsonResponseStructure_shouldThrowException() throws Exception {
        var runContext = runContextFactory.of();

        var structuredOutputCompletionBuilder = StructuredOutputCompletion.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue(GEMINI_2_5_FLASH_LITE))
            .prompt(Property.ofValue("List a few popular cookie recipes, and include the amounts of ingredients?"))
            .build();


        var exception = Assertions.assertThrows(Exception.class, () -> structuredOutputCompletionBuilder.run(runContext));
        assertThat(exception.getMessage(), containsString("No value present"));
    }


    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".*")
    void structuredOutputCompletion_givenInvalidJsonStructure_shouldThrowException() throws Exception {
        var runContext = runContextFactory.of();
        var structuredOutputCompletionBuilderWithInvalidJson = StructuredOutputCompletion.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue(GEMINI_2_5_FLASH_LITE))
            .prompt(Property.ofValue("List a few popular cookie recipes, and include the amounts of ingredients?"))
            .jsonResponseSchema(Property.ofValue(
                """
                    {
                        "type": "object",
                        "properties": {
                            "predictions": {
                                "type": "array",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "content": {
                                            "type": "string"
                                    }
                                        // Missing closing brace for "properties" and "items"
                            }
                        }
                    """)
            )
            .build();

        var exception = Assertions.assertThrows(Exception.class, () -> structuredOutputCompletionBuilderWithInvalidJson.run(runContext));
        assertThat(exception.getMessage(), containsString("Failed to deserialize the JSON string."));
    }
}