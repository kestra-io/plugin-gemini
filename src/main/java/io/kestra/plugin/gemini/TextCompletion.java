package io.kestra.plugin.gemini;

import com.google.genai.Client;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Complete text using the Gemini Client.",
    description = "See [Gemini API about text completion](https://ai.google.dev/gemini-api/docs/text-generation) for more information."
)
@Plugin(
    examples = {
        @Example(
            title = "Text completion using the Gemini Client.",
            full = true,
            code = """
                id: gemini_text_completion
                namespace: company.team

                tasks:
                  - id: text_completion
                    type: io.kestra.plugin.gemini.TextCompletion
                    apiKey: ${{ secrets.GEMINI_API_KEY }}
                    model: "gemini-2.5-flash-preview-05-20"
                    prompt: What color is the sky? Answer with a unique word and without any punctuation.
                """
        )
    }
)
public class TextCompletion extends AbstractGemini implements RunnableTask<TextCompletion.Output> {

    @Schema(title = "Prompt")
    @NotNull
    private Property<String> prompt;

    @Override
    public Output run(RunContext runContext) throws Exception {

        var renderedApiKey = runContext.render(apiKey).as(String.class).orElseThrow();
        var renderedModel = runContext.render(model).as(String.class).orElseThrow();
        var renderedPrompt = runContext.render(prompt).as(String.class).orElseThrow();

        try (var client = Client.builder().apiKey(renderedApiKey).build()) {
            var response = client.models.generateContent(renderedModel, renderedPrompt, null);

            var candidates = response.candidates().orElse(List.of());

            var metadata = response.usageMetadata().stream().toList();

            sendMetrics(runContext, metadata);

            return TextCompletion.Output.builder()
                .predictions(candidates.stream().map(Prediction::of).toList())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of text predictions made by the model.")
        private List<Prediction> predictions;
    }
}
