package io.kestra.plugin.gemini;

import com.google.genai.Client;
import com.google.genai.types.*;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Optional;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Complete a chat using the Gemini Client.",
    description = "See https://github.com/googleapis/java-genai for more information."
)
@Plugin(
    examples = {
        @Example(
            title = "Chat completion using the Gemini Client.",
            full = true,
            code = """
                id: gemini_chat_completion
                namespace: company.team

                tasks:
                  - id: chat_completion
                    type: io.kestra.plugin.gemini.ChatCompletion
                    apiKey: your_api_key
                    model: "gemini-2.5-flash-preview-05-20"
                    messages:
                      - "What is the capital of Japan? Answer with a unique word and without any punctuation."
                      - "Who are you? Answer concisely."
                """
        )
    }
)
public class ChatCompletion extends Task implements RunnableTask<ChatCompletion.Output> {

    @Schema(title = "Gemini API Key")
    @NotNull
    private Property<String> apiKey;

    @Schema(
        title = "Model",
        description = "Specifies which generative model (e.g., 'gemini-1.5-flash', 'gemini-1.0-pro') to use for the completion."
    )
    @NotNull
    private Property<String> model;

    @Schema(title = "Messages")
    @NotEmpty
    private Property<List<String>> messages;

    @Override
    public Output run(RunContext runContext) throws Exception {

        var renderedApiKey = runContext.render(apiKey).as(String.class).orElseThrow();
        var renderedModel = runContext.render(model).as(String.class).orElseThrow();
        var renderedMessages = runContext.render(messages).asList(String.class);

        try (var client = Client.builder().apiKey(renderedApiKey).build()) {
            var chat = client.chats.create(renderedModel);

            var responses = renderedMessages.stream()
                .map(throwFunction(chat::sendMessage))
                .toList();

            var candidates = responses.stream()
                .map(GenerateContentResponse::candidates)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(List::stream)
                .toList();

            var metadata = responses.stream().map(GenerateContentResponse::usageMetadata).filter(Optional::isPresent).map(Optional::get).toList();

            sendMetrics(runContext, metadata);

            return Output.builder()
                .predictions(candidates.stream().map(Prediction::of).toList())
                .build();
        }
    }

    private void sendMetrics(RunContext runContext, List<GenerateContentResponseUsageMetadata> metadata) {
        runContext.metric(Counter.of("candidate.token.count", metadata.stream().mapToInt(m -> m.candidatesTokenCount().orElse(0)).sum()));
        runContext.metric(Counter.of("prompt.token.count", metadata.stream().mapToInt(m -> m.promptTokenCount().orElse(0)).sum()));
        runContext.metric(Counter.of("total.token.count", metadata.stream().mapToInt(m -> m.totalTokenCount().orElse(0)).sum()));
    }

    public record Prediction(Optional<List<SafetyRating>> safetyRatings, CitationMetadata citationMetadata,
                             String content) {
        public static Prediction of(Candidate candidate) {
            return new Prediction(candidate.safetyRatings(),
                candidate.citationMetadata().orElse(null),
                candidate.content().map(Content::text).orElse("")
            );
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of text predictions made by the model.")
        private List<Prediction> predictions;
    }
}
