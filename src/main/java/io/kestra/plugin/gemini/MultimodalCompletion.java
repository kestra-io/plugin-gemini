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
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.File;
import java.io.FileOutputStream;
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
    @NotNull
    private Property<List<Content>> contents;

    @Override
    public Output run(RunContext runContext) throws Exception {

        var rApiKey = runContext.render(apiKey).as(String.class).orElseThrow();
        var rModel = runContext.render(model).as(String.class).orElseThrow();
        var rContents = runContext.render(contents).asList(Content.class);

        try (var client = Client.builder().apiKey(rApiKey).build()) {
            var response = client.models.generateContent(
                rModel,
                rContents.stream().map(throwFunction(c -> toGeminiContent(runContext, c))).toList(),
                null
            );

            sendMetrics(runContext, response.usageMetadata().map(List::of).orElse(List.of()));

            var finishReason = response.finishReason();
            var safetyRatings = response.candidates()
                .flatMap(candidates -> candidates.getFirst().safetyRatings())
                .orElse(List.of()).stream()
                .map(safetyRating -> new SafetyRating(
                    safetyRating.category().toString(),
                    safetyRating.probability().toString(),
                    safetyRating.blocked().orElse(false))
                ).toList();

            var images = new java.util.ArrayList<GeneratedImage>();
            var parts = response.parts();

            if (parts != null) {
                for (var p : parts) {
                    var blobOpt = p.inlineData();
                    if (blobOpt.isPresent()) {
                        var blob = blobOpt.get();
                        var mime = blob.mimeType().orElse("");
                        var data = blob.data().orElse(null);
                        if (mime != null && mime.startsWith("image/") && data != null) {
                            try {
                                String ext = guessExtension(mime);
                                File tempFile = runContext.workingDir().createTempFile(ext).toFile();

                                try (var fos = new FileOutputStream(tempFile)) {
                                    fos.write(data);
                                }
                                URI stored = runContext.storage().putFile(tempFile);
                                images.add(GeneratedImage.builder()
                                    .mimeType(mime)
                                    .uri(stored)
                                    .build());
                            } catch (IOException io) {
                                runContext.logger().warn("Failed to persist generated image: {}", io.getMessage());
                            }
                        }
                    }
                }
            }

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
                if (!images.isEmpty()) {
                    output.images(images);
                }
            }

            return output.build();
        }
    }

    private static String guessExtension(String mime) {
        if (mime.equalsIgnoreCase("image/jpeg") || mime.equalsIgnoreCase("image/jpg")) return ".jpg";
        if (mime.equalsIgnoreCase("image/png")) return ".png";
        if (mime.equalsIgnoreCase("image/webp")) return ".webp";
        if (mime.equalsIgnoreCase("image/gif")) return ".gif";
        return ".img";
    }

    private com.google.genai.types.Content toGeminiContent(RunContext runContext, Content content) throws IllegalVariableEvaluationException {

        var rContent = runContext.render(content.content).as(String.class).orElseThrow();
        var rMimeType = runContext.render(content.mimeType).as(String.class).orElse(null);
        var rRole = runContext.render(content.role).as(String.class).orElse("user");

        if (content.mimeType != null) {
            return com.google.genai.types.Content.builder()
                .parts(List.of(createPart(runContext, rContent, rMimeType)))
                .role(rRole)
                .build();
        }

        return com.google.genai.types.Content.builder()
            .parts(List.of(Part.builder().text(rContent).build()))
            .role(rRole)
            .build();
    }

    private Part createPart(RunContext runContext, String content, String mimeType) {
        try (var is = runContext.storage().getFile(URI.create(content))) {
            byte[] bytes = is.readAllBytes();
            return Part.fromBytes(bytes, mimeType);
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
            title = "Generated images stored in Kestra and exposed as URIs",
            description = "When using image-generating/editing models like gemini-2.5-flash-image-preview, this field contains one or more Kestra storage URIs."
        )
        List<GeneratedImage> images;

        @Schema(title = "The response safety ratings")
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
    @Getter
    @EqualsAndHashCode
    public static class GeneratedImage {
        @Schema(title = "IANA mime type of the image, e.g. image/jpeg")
        private final String mimeType;

        @Schema(title = "Kestra storage URI of the image")
        private final URI uri;

        @Override
        public String toString() {
            return uri.toString();
        }
    }

    @Builder
    @Value
    public static class Content {
        Property<String> mimeType;

        @NotNull
        Property<String> content;

        Property<String> role = Property.ofValue("user");
    }

    @Value
    public static class SafetyRating {
        String category;
        String probability;
        boolean blocked;
    }
}
