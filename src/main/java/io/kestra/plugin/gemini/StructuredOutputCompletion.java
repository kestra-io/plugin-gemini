package io.kestra.plugin.gemini;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Generate Structured JSON Output Using the Gemini Client.",
    description = "See [Gemini API about structured output completion](https://ai.google.dev/gemini-api/docs/structured-output) for more information."
)
@Plugin(
    examples = {
        @Example(
            title = "Structured JSON Output Completion using the Gemini Client.",
            full = true,
            code = """
                id: gemini_structured_json_completion
                namespace: company.team

                tasks:
                  - id: gemini_structured_json_completion
                    type: io.kestra.plugin.gemini.StructuredOutputCompletion
                    apiKey: ${{ secrets.GEMINI_API_KEY }}
                    model: "gemini-2.5-flash-preview-05-20"
                    prompt: What are the weather predictions for tomorrow in London?
                    jsonResponseSchema: |
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
        )
    }
)
public class StructuredOutputCompletion extends AbstractGemini implements RunnableTask<StructuredOutputCompletion.Output> {

    private static final String APPLICATION_JSON = "application/json";

    @Schema(title = "Prompt")
    @NotNull
    private Property<String> prompt;

    @Schema(title = "jsonResponseSchema")
    @NotNull
    private Property<String> jsonResponseSchema;

    @Override
    public Output run(RunContext runContext) throws Exception {

        var renderedApiKey = runContext.render(apiKey).as(String.class).orElseThrow();
        var renderedModel = runContext.render(model).as(String.class).orElseThrow();
        var renderedPrompt = runContext.render(prompt).as(String.class).orElseThrow();
        var responseSchema = runContext.render(jsonResponseSchema).as(String.class).orElseThrow();


        try (var client = Client.builder().apiKey(renderedApiKey).build()) {
            // Configure generation settings, including the response schema and MIME type
            final GenerateContentConfig generationConfig = GenerateContentConfig.builder()
                .responseMimeType(APPLICATION_JSON)
                .responseSchema(com.google.genai.types.Schema.fromJson(responseSchema))
                .build();

            final GenerateContentResponse responses = client.models.generateContent(renderedModel, renderedPrompt, generationConfig);

            sendMetrics(runContext, responses.usageMetadata().map(List::of).orElse(List.of()));

            final List<String> predictions = Objects.nonNull(responses.parts()) ?
                responses.parts().stream()
                    .map(Part::text)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList() : List.of();
            return StructuredOutputCompletion.Output.builder()
                .predictions(predictions)
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of text predictions made by the model.")
        private List<String> predictions;
    }
}
