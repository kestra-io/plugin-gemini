package io.kestra.plugin.gemini;

import com.google.genai.types.*;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractGemini extends Task {

    @Schema(
        title = "Gemini API key",
        description = "Secret used for direct Gemini API calls; render from a secure variable."
    )
    @NotNull
    protected Property<String> apiKey;

    @Schema(
        title = "Generative model",
        description = "Gemini model identifier to call (e.g., `gemini-2.5-flash`, `gemini-1.5-pro`). Must support the requested input type."
    )
    @NotNull
    protected Property<String> model;

    protected void sendMetrics(RunContext runContext, List<GenerateContentResponseUsageMetadata> metadata) {
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
}
