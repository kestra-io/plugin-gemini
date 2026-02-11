package io.kestra.plugin.gemini;

import com.google.genai.Client;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.GenerateVideosConfig;
import com.google.genai.types.GenerateVideosOperation;
import com.google.genai.types.GenerateVideosSource;
import com.google.genai.types.GeneratedVideo;
import com.google.genai.types.Video;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Generate video with Veo via Gemini",
    description = "Creates a 1–60s video with Google's Veo 3 model through the Gemini API. Defaults: 10s duration, 5m timeout, audio off. Use Vertex AI with project/location and a GCS output URI; otherwise the file downloads locally. See [Veo 3 documentation](https://developers.googleblog.com/en/veo-3-now-available-gemini-api/) for more information."
)
@Plugin(
    examples = {
        @Example(
            title = "Generate a video of a golden retriever playing in sunflowers.",
            full = true,
            code = """
                id: gemini_video_generation
                namespace: company.ai

                tasks:
                  - id: generate_video
                    type: io.kestra.plugin.gemini.VideoGeneration
                    apiKey: "{{ secret('GEMINI_API_KEY') }}"
                    model: "veo-3.0-generate-preview"
                    prompt: "a close-up shot of a golden retriever playing in a field of sunflowers"
                    negativePrompt: "barking, woofing"
                    duration: 5
                """
        ),
        @Example(
            title = "Generate video using Vertex AI with audio enabled.",
            full = true,
            code = """
                id: gemini_vertex_video_with_audio
                namespace: company.ai

                tasks:
                  - id: generate_vertex_video
                    type: io.kestra.plugin.gemini.VideoGeneration
                    model: "veo-3.0-generate-preview"
                    prompt: "a serene mountain landscape with flowing rivers and birds chirping"
                    duration: 8
                    includeAudio: true
                    vertexAI: true
                    project: "{{ secret('GCP_PROJECT_ID') }}"
                    location: "us-central1"
                    outputGcsUri: "gs://my-bucket/videos/"
                """
        )
    }
)
public class VideoGeneration extends AbstractGemini implements RunnableTask<VideoGeneration.Output> {

    private static final String FILE_NAME_TEMPLATE = "genai_video_{date}.mp4";
    private static final int DEFAULT_DURATION_SECONDS = 10;
    private static final int MIN_DURATION_SECONDS = 1;
    private static final int MAX_DURATION_SECONDS = 60;
    private static final int DEFAULT_POLL_INTERVAL_MS = 1_000;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    @Schema(
        title = "Video prompt",
        description = "Text description of the video to generate."
    )
    @NotNull
    private Property<String> prompt;

    @Schema(
        title = "Negative prompt",
        description = "Text describing what should not appear in the video."
    )
    private Property<String> negativePrompt;

    @Schema(
        title = "Video duration in seconds",
        description = "Duration of the generated video in seconds. Range 1–60; defaults to 10."
    )
    @Builder.Default
    private Property<Integer> durationInSeconds = Property.ofValue(DEFAULT_DURATION_SECONDS);

    @Schema(
        title = "Include audio generation",
        description = "Whether to generate synchronized audio for the video. Default is false; only supported by audio-capable models."
    )
    @Builder.Default
    private Property<Boolean> includeAudio = Property.ofValue(false);

    @Schema(
        title = "Request timeout",
        description = "Timeout for the video generation request. Defaults to 5 minutes; polling occurs every second."
    )
    @Builder.Default
    private Property<Duration> timeout = Property.ofValue(DEFAULT_TIMEOUT);

    @Schema(
        title = "Seed value",
        description = "Optional seed to make video generation deterministic; random when absent."
    )
    @Nullable
    private Property<Integer> seed;

    @Schema(
        title = "Number of videos to generate",
        description = "Number of videos to request. Defaults to 1."
    )
    @Builder.Default
    private Property<Integer> numberOfVideos = Property.ofValue(1);


    @Schema(
        title = "Use Vertex AI",
        description = "Whether to route requests through Vertex AI. Requires project and location; output must use a GCS URI."
    )
    @Builder.Default
    private Property<Boolean> vertexAI = Property.ofValue(false);

    @Schema(
        title = "GCS Output URI",
        description = "Google Cloud Storage URI for the generated video (e.g., gs://bucket/path/). Required when vertexAI is true."
    )
    @Nullable
    private Property<String> outputGcsUri;

    @Schema(
        title = "Project ID",
        description = "Google Cloud project ID used when vertexAI is true."
    )
    @Nullable
    private Property<String> project;

    @Schema(
        title = "Location",
        description = "Google Cloud region for Vertex AI, for example `us-central1`."
    )
    @Nullable
    private Property<String> location;

    @Schema(
        title = "File download path",
        description = "Local file name or path for the downloaded video when not using Vertex AI. Defaults to genai_video_{timestamp}.mp4 in the working directory."
    )
    @Nullable
    private Property<String> downloadFilePath;

    @Override
    public Output run(RunContext runContext) throws Exception {

        var rModel = runContext.render(model).as(String.class).orElseThrow();
        var rPrompt = runContext.render(prompt).as(String.class).orElseThrow();
        int rDuration = runContext.render(durationInSeconds).as(Integer.class).orElse(DEFAULT_DURATION_SECONDS);
        var rTimeOut = runContext.render(timeout).as(Duration.class).orElse(DEFAULT_TIMEOUT);
        var rVertexAI = runContext.render(vertexAI).as(Boolean.class).orElse(false);
        var rOutputGcsUri = runContext.render(outputGcsUri).as(String.class).orElse(null);
        var rDownloadFilePath = runContext.render(downloadFilePath)
            .as(String.class)
            .orElse(FILE_NAME_TEMPLATE.replace("{date}", String.valueOf(System.currentTimeMillis())));

        validateInputParameters(rDuration, rDownloadFilePath, rOutputGcsUri, rVertexAI);

        runContext.logger().info("Starting video generation with prompt: {}", rPrompt);

        try (var client = toClient(runContext)) {

            var config = toGenerateVideosConfig(runContext);
            var videoSource = toGenerateVideoSource(rPrompt);

            var generateVideosOperation = client.models.generateVideos(rModel, videoSource, config);
            var operation = pollUntilComplete(client, generateVideosOperation, rTimeOut, runContext);
            var generatedVideo = extractGeneratedVideo(operation);

            validateGeneratedVideo(generatedVideo);

            if (!client.vertexAI()) {
                downloadVideoFile(client, generatedVideo, runContext, rDownloadFilePath);
            }

            return Output.builder()
                .videoUri(generatedVideo.uri())
                .mimeType(generatedVideo.mimeType())
                .metadata(generateVideosOperation.metadata()).build();
        }
    }

    private void validateInputParameters(int duration, String downloadFilePath, String outputGcsUri, boolean isVertexAI) {
        if (duration < MIN_DURATION_SECONDS || duration > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException("Duration must be between " + MIN_DURATION_SECONDS + " and " + MAX_DURATION_SECONDS + " seconds");
        }

        if (isVertexAI && StringUtils.isEmpty(outputGcsUri)) {
            throw new IllegalArgumentException("outputGcsUri is required when using Vertex AI.");
        }

        if (!isVertexAI && StringUtils.isEmpty(downloadFilePath)) {
            throw new IllegalArgumentException("downloadFilePath is required when not using Vertex AI.");
        }
    }

    private Client toClient(final RunContext runContext) throws IllegalVariableEvaluationException {
        var rApiKey = runContext.render(apiKey).as(String.class).orElseThrow();
        var rVertexAI = runContext.render(vertexAI).as(Boolean.class).orElse(false);
        var clientBuilder = Client.builder().vertexAI(rVertexAI);

        if (rVertexAI) {
            clientBuilder.project(runContext.render(project).as(String.class).orElseThrow())
                .location(runContext.render(location).as(String.class).orElseThrow());
        } else {
            clientBuilder.apiKey(rApiKey);
        }

        return clientBuilder.build();
    }

    private GenerateVideosConfig toGenerateVideosConfig(final RunContext runContext) throws IllegalVariableEvaluationException {
        var configBuilder = GenerateVideosConfig.builder()
            .numberOfVideos(runContext.render(numberOfVideos).as(Integer.class).orElse(1));
        runContext.render(seed).as(Integer.class).ifPresent(configBuilder::seed);
        runContext.render(negativePrompt).as(String.class)
            .filter(StringUtils::isNotBlank)
            .ifPresent(configBuilder::negativePrompt);

        var isVertexEnabled = runContext.render(vertexAI).as(Boolean.class).orElse(false);
        if (isVertexEnabled) {
            configureForVertexAI(runContext, configBuilder);
        }
        return configBuilder.build();
    }

    private void configureForVertexAI(final RunContext runContext, GenerateVideosConfig.Builder configBuilder) throws IllegalVariableEvaluationException {
        runContext.render(durationInSeconds).as(Integer.class).ifPresent(configBuilder::durationSeconds);
        runContext.render(includeAudio).as(Boolean.class).ifPresent(configBuilder::generateAudio);
        runContext.render(outputGcsUri).as(String.class).ifPresent(configBuilder::outputGcsUri);
    }

    private GenerateVideosSource toGenerateVideoSource(final String rPrompt) {
        return GenerateVideosSource.builder()
            .prompt(rPrompt)
            .build();
    }

    private GenerateVideosOperation pollUntilComplete(Client client, GenerateVideosOperation operation, final Duration timeoutDuration, final RunContext runContext) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeoutDuration.toMillis();

        while (!operation.done().orElse(false)) {
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                throw new RuntimeException("Video generation timed out after " + timeoutDuration.toMinutes() + " minutes.");
            }
            Thread.sleep(DEFAULT_POLL_INTERVAL_MS);
            runContext.logger().info("Waiting for operation to complete...");
            operation = client.operations.getVideosOperation(operation, null);
        }
        return operation;
    }

    private Video extractGeneratedVideo(GenerateVideosOperation operation) {
        return operation.response()
            .orElseThrow(() -> new RuntimeException("No video was generated. Possible reasons: content policy violations or model limitations. Error ::" +operation.error()))
            .generatedVideos()
            .flatMap(videos -> videos.stream().findFirst())
            .flatMap(GeneratedVideo::video)
            .orElseThrow(() -> new RuntimeException("No video generated"));
    }

    private void validateGeneratedVideo(Video video) {
        if (video.uri().isEmpty()) {
            throw new RuntimeException("Generated video URI is null or empty");
        }
    }

    private void downloadVideoFile(final Client client, final Video videoFile, final RunContext runContext, final String rDownloadFilePath) {
        try {
            client.files.download(videoFile, rDownloadFilePath, null);
        } catch (final GenAiIOException e) {
            runContext.logger().error("An error occurred while downloading the video: {}", e.getMessage());
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Video URI",
            description = "URI of the generated video file."
        )
        private Optional<String> videoUri;

        @Schema(
            title = "MIME type",
            description = "MIME type of the generated video (e.g., video/mp4)."
        )
        private Optional<String> mimeType;

        @Schema(
            title = "Metadata",
            description = "Optional metadata related to the video generation process (e.g., model version, GCS path, etc.)."
        )
        private Optional<Map<String, Object>> metadata;

    }
}
