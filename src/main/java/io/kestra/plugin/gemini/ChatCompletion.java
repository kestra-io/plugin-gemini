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
    description = "See [Gemini API about text completion](https://ai.google.dev/gemini-api/docs/text-generation) for more information."
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
                    apiKey: ${{ secrets.GEMINI_API_KEY }}
                    model: "gemini-2.5-flash-preview-05-20"
                    messages:
                      - "What is the capital of Japan? Answer with a unique word and without any punctuation."
                      - "Who are you? Answer concisely."
                """
        )
    }
)
public class ChatCompletion extends AbstractGemini implements RunnableTask<ChatCompletion.Output> {

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
