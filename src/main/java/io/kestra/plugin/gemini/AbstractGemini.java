package io.kestra.plugin.gemini;

import com.google.genai.types.GenerateContentResponseUsageMetadata;
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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractGemini extends Task {

    @Schema(title = "Gemini API Key")
    @NotNull
    protected Property<String> apiKey;

    @Schema(
        title = "Model",
        description = "Specifies which generative model (e.g., 'gemini-1.5-flash', 'gemini-1.0-pro') to use for the completion."
    )
    @NotNull
    protected Property<String> model;

    protected void sendMetrics(RunContext runContext, List<GenerateContentResponseUsageMetadata> metadata) {
        runContext.metric(Counter.of("candidate.token.count", metadata.stream().mapToInt(m -> m.candidatesTokenCount().orElse(0)).sum()));
        runContext.metric(Counter.of("prompt.token.count", metadata.stream().mapToInt(m -> m.promptTokenCount().orElse(0)).sum()));
        runContext.metric(Counter.of("total.token.count", metadata.stream().mapToInt(m -> m.totalTokenCount().orElse(0)).sum()));
    }
}
