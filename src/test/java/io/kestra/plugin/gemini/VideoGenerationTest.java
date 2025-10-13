package io.kestra.plugin.gemini;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
public class VideoGenerationTest {

    private static final String GEMINI_API_KEY = System.getenv("GEMINI_API_KEY");
    private static final String DOWNLOAD_VIDEO_PATH = "video.mp4";
    private static final String GCS_BUCKET_LOCATION =  System.getenv("GCS_BUCKET_LOCATION");
    private static final String GCS_PROJECT = System.getenv("GCS_PROJECT");
    private static final String GCS_LOCATION = System.getenv("GCS_LOCATION");

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    @Disabled // costs too much
    void generateVideo_whenValidParametersProvided_shouldGenerateVideo() throws Exception {
        var runContext = runContextFactory.of();
        var videoGeneration = VideoGeneration.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue("veo-2.0-generate-001"))
            .prompt(Property.ofValue("a simple animation of a bouncing ball"))
            .durationInSeconds(Property.ofValue(20))
            .includeAudio(Property.ofValue(false))
            .timeout(Property.ofValue(Duration.ofMinutes(10)))
            .downloadFilePath(Property.ofValue(DOWNLOAD_VIDEO_PATH))
            .build();

        var output = videoGeneration.run(runContext);

        assertThat(output.getVideoUri(), is(notNullValue()));
        assertThat(output.getMetadata(), is(notNullValue()));
    }


    @Test
    @Disabled // costs too much
    void generateVideo_whenUsingVertexAIWithGcsOutput_shouldGenerateVideo() throws Exception {
        var runContext = runContextFactory.of();
        var videoGeneration = VideoGeneration.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue("veo-3.0-generate-preview"))
            .prompt(Property.ofValue("a simple animation of a bouncing ball"))
            .durationInSeconds(Property.ofValue(6))
            .includeAudio(Property.ofValue(false))
            .timeout(Property.ofValue(Duration.ofMinutes(10)))
            .outputGcsUri(Property.ofValue(GCS_BUCKET_LOCATION))
            .vertexAI(Property.ofValue(true))
            .project(Property.ofValue(GCS_PROJECT))
            .location(Property.ofValue(GCS_LOCATION))
            .build();

        var output = videoGeneration.run(runContext);
        assertThat(output.getVideoUri(), is(notNullValue()));
        assertThat(output.getMetadata(), is(notNullValue()));
    }

    @Test
    @Disabled // costs too much
    void generateVideo_whenUsingVertexAIWithAudioAndGcsOutput_shouldGenerateVideoWithAudio() throws Exception {
        var runContext = runContextFactory.of();
        var videoGeneration = VideoGeneration.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue("veo-3.0-generate-preview"))
            .prompt(Property.ofValue("a simple animation of a bouncing ball"))
            .durationInSeconds(Property.ofValue(6))
            .includeAudio(Property.ofValue(true))
            .timeout(Property.ofValue(Duration.ofMinutes(10)))
            .outputGcsUri(Property.ofValue(GCS_BUCKET_LOCATION))
            .vertexAI(Property.ofValue(true))
            .project(Property.ofValue(GCS_PROJECT))
            .location(Property.ofValue(GCS_LOCATION))
            .build();

        var output = videoGeneration.run(runContext);
        assertThat(output.getVideoUri(), is(notNullValue()));
        assertThat(output.getMetadata(), is(notNullValue()));
    }

    @Test
    @Disabled // costs too much
    void generateVideo_whenNegativePromptProvided_shouldGenerateVideo() throws Exception {
        var runContext = runContextFactory.of();
        var videoGeneration = VideoGeneration.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue("veo-2.0-generate-001"))
            .prompt(Property.ofValue("a cat playing in a garden"))
            .negativePrompt(Property.ofValue("barking, dogs"))
            .durationInSeconds(Property.ofValue(5))
            .includeAudio(Property.ofValue(false))
            .timeout(Property.ofValue(Duration.ofMinutes(10)))
            .build();

        var output = videoGeneration.run(runContext);

        assertThat(output.getVideoUri(), is(notNullValue()));
        assertThat(output.getMetadata(), is(notNullValue()));
    }

    @Test
    @Disabled // costs too much
    void generateVideo_whenPromptIsEmpty_shouldThrowException() {
        var runContext = runContextFactory.of();
        var videoGeneration = VideoGeneration.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue("veo-2.0-generate-001"))
            .prompt(Property.ofValue(""))
            .durationInSeconds(Property.ofValue(5))
            .build();

        var exception = assertThrows(Exception.class, () -> videoGeneration.run(runContext));
        assertThat(exception.getMessage(), containsString(" Text to video requires prompt to be set"));
    }

    @Test
    void generateVideo_whenDurationIsInvalid_shouldThrowException() {
        var runContext = runContextFactory.of();
        var videoGeneration = VideoGeneration.builder()
            .apiKey(Property.ofValue("fake-api-key"))
            .model(Property.ofValue("veo-2.0-generate-001"))
            .prompt(Property.ofValue("test prompt"))
            .durationInSeconds(Property.ofValue(0))
            .build();

        var exception = assertThrows(Exception.class, () -> videoGeneration.run(runContext));
        assertThat(exception.getMessage(), containsString("Duration must be between 1 and 60 seconds"));
    }

    @Test
    void generateVideo_whenApiKeyIsInvalid_shouldThrowException() {
        var runContext = runContextFactory.of();
        var videoGeneration = VideoGeneration.builder()
            .apiKey(Property.ofValue("invalid-api-key"))
            .model(Property.ofValue("veo-3.0-generate-preview"))
            .prompt(Property.ofValue("test prompt"))
            .durationInSeconds(Property.ofValue(5))
            .timeout(Property.ofValue(Duration.ofMinutes(1)))
            .build();

        var exception = assertThrows(Exception.class, () -> videoGeneration.run(runContext));
        assertThat(exception.getMessage(), anyOf(
            containsString("API key not valid. Please pass a valid API key")
        ));
    }
}