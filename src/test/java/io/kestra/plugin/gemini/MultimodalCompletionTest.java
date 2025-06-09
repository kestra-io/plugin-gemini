package io.kestra.plugin.gemini;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@KestraTest
public class MultimodalCompletionTest {

    private static final String GEMINI_API_KEY = System.getenv("GEMINI_API_KEY");

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".*")
    void multimodalCompletion() throws Exception {

        var fileName = "generated-image.jpg";
        var file = new File(Objects.requireNonNull(MultimodalCompletionTest.class
            .getClassLoader().getResource(fileName)).toURI());

        var uri = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/" + fileName),
            new FileInputStream(file)
        );

        var runContext = runContextFactory.of(Map.of(
            "inputs", Map.of("image", uri)
        ));

        var multimodalCompletion = MultimodalCompletion.builder()
            .apiKey(Property.ofValue(GEMINI_API_KEY))
            .model(Property.ofValue("gemini-2.5-flash-preview-05-20"))
            .contents(Property.ofValue(List.of(
                MultimodalCompletion.Content.builder()
                    .content(Property.ofValue("Can you describe this image?"))
                    .build(),
                MultimodalCompletion.Content.builder()
                    .content(Property.ofExpression("{{ inputs.image }}"))
                    .mimeType(Property.ofValue("image/jpeg"))
                    .build()
            )))
            .build();

        var output = multimodalCompletion.run(runContext);

        assertThat(output.getText(), containsString("kitten"));
    }
}
