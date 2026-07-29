package br.com.validadorlote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class JpackageRuntimeConfigurationTest {

    private static final Pattern JPACKAGE_MODULES = Pattern.compile(
            "def\\s+jpackageModules\\s*=\\s*'([^']*)'");

    @Test
    void packagedRuntimeIncludesHttpClientModule() throws IOException {
        String buildScript = Files.readString(Path.of("build.gradle"));
        Matcher modules = JPACKAGE_MODULES.matcher(buildScript);

        assertThat(modules.find()).isTrue();
        assertThat(Arrays.stream(modules.group(1).split(",")).map(String::trim))
                .contains("java.net.http");
    }
}
