package io.kestra.plugin.gemini;

import com.google.genai.Client;
import com.google.genai.types.FinishReason;
import com.google.genai.types.Part;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Use Multimodal completion using the Gemini Client.",
    description = "See [Gemini API about multimodal input](https://ai.google.dev/gemini-api/docs/text-generation#multimodal-input) for more information."
)
@Plugin(
    examples = {
        @Example(
            title = "Multimodal completion using the Gemini Client",
            full = true,
            code = """
                id: gemini_multimodal_completion
                namespace: company.team

                inputs:
                  - id: image
                    type: FILE

                tasks:
                  - id: multimodal_completion
                    type: io.kestra.plugin.gemini.MultimodalCompletion
                    apiKey: "{{ secret('GEMINI_API_KEY') }}"
                    model: "gemini-2.5-flash"
                    contents:
                      - content: Can you describe this image?
                      - mimeType: image/jpeg
                        content: "{{ inputs.image }}"
                """
        )
    }
)
public class MultimodalCompletion extends AbstractGemini implements RunnableTask<MultimodalCompletion.Output> {

    @Schema(
        title = "The chat content prompt for the model to respond to"
    )
    @NotEmpty
    private Property<List<Content>> contents;

    @Override
    public Output run(RunContext runContext) throws Exception {

        var renderedApiKey = runContext.render(apiKey).as(String.class).orElseThrow();
        var renderedModel = runContext.render(model).as(String.class).orElseThrow();
        var renderedContents = runContext.render(contents).asList(Content.class);

        try (var client = Client.builder().apiKey(renderedApiKey).build()) {
            var response = client.models.generateContent(renderedModel, renderedContents.stream()
                .map(throwFunction(c -> toGeminiContent(runContext, c))).toList(), null);

            sendMetrics(runContext, response.usageMetadata().map(List::of).orElse(List.of()));

            var finishReason = response.finishReason();
            var safetyRatings = response.candidates().flatMap(candidates -> candidates.getFirst().safetyRatings()).orElse(List.of()).stream()
                .map(safetyRating -> new MultimodalCompletion.SafetyRating(
                    safetyRating.category().toString(),
                    safetyRating.probability().toString(),
                    safetyRating.blocked().orElse(false))
                ).toList();

            var output = Output.builder()
                .finishReason(finishReason.toString())
                .safetyRatings(safetyRatings);

            if (finishReason.knownEnum() == FinishReason.Known.SAFETY) {
                runContext.logger().warn("Content response has been blocked for safety reason");
                output.blocked(true);
            } else if (finishReason.knownEnum() == FinishReason.Known.RECITATION) {
                runContext.logger().warn("Content response has been blocked for recitation reason");
                output.blocked(true);
            } else {
                output.text(response.text());
            }

            return output.build();
        }
    }

    private com.google.genai.types.Content toGeminiContent(RunContext runContext, Content content) throws IllegalVariableEvaluationException {

        var renderedContent = runContext.render(content.content).as(String.class).orElseThrow();
        var renderedMimeType = runContext.render(content.mimeType).as(String.class).orElse(null);
        var renderedRole = runContext.render(content.role).as(String.class).orElse("user");

        if (content.mimeType != null) {
            return com.google.genai.types.Content.builder()
                .parts(List.of(createPart(runContext, renderedContent, renderedMimeType)))
                .role(renderedRole)
                .build();
        }

        return com.google.genai.types.Content.builder().parts(List.of(Part.builder().text(renderedContent).build())).role(renderedRole).build();
    }

    private Part createPart(RunContext runContext, String content, String mimeType) {
        try (var is = runContext.storage().getFile(URI.create(content))) {
            byte[] partBytes = is.readAllBytes();
            return Part.fromBytes(partBytes, mimeType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuperBuilder
    @Value
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The generated response text"
        )
        String text;

        @Schema(
            title = "The response safety ratings"
        )
        List<SafetyRating> safetyRatings;

        @Schema(
            title = "Whether the response has been blocked for safety reasons"
        )
        boolean blocked;

        @Schema(
            title = "The reason the generation has finished"
        )
        String finishReason;

        @Override
        public Optional<State.Type> finalState() {
            return blocked ? Optional.of(State.Type.WARNING) : io.kestra.core.models.tasks.Output.super.finalState();
        }
    }

    @Builder
    @Value
    public static class Content {
        @Schema(
            title = "Mime type of the content, use it only when the content is not text."
        )
        Property<String> mimeType;

        @Schema(
            title = "The content itself, should be a string for text content or a Kestra internal storage URI for other content types.",
            description = "If the content is not text, the `mimeType` property must be set."
        )
        @NotNull
        Property<String> content;

        @Schema(
            title = "The content role, defaults to \"user\"."
        )
        Property<String> role = Property.ofValue("user");
    }

    @Value
    public static class SafetyRating {
        @Schema(
            title = "Safety category."
        )
        String category;

        @Schema(
            title = "Safety rating probability."
        )
        String probability;

        @Schema(
            title = "Whether the response has been blocked for safety reasons."
        )
        boolean blocked;
    }
}
