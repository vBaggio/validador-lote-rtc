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
    private static final Pattern WINDOWS_UPGRADE_UUID = Pattern.compile(
            "def\\s+windowsUpgradeUuid\\s*=\\s*'([0-9a-f-]{36})'");

    @Test
    void packagedRuntimeIncludesHttpClientModule() throws IOException {
        String buildScript = Files.readString(Path.of("build.gradle"));
        Matcher modules = JPACKAGE_MODULES.matcher(buildScript);

        assertThat(modules.find()).isTrue();
        assertThat(Arrays.stream(modules.group(1).split(",")).map(String::trim))
                .contains("java.net.http");
    }

    @Test
    void windowsInstallerKeepsAStableUpgradeIdentityAndReleaseVersionComesFromCiTag() throws IOException {
        String buildScript = Files.readString(Path.of("build.gradle"));
        String workflow = Files.readString(Path.of(".github/workflows/release.yml"));
        Matcher upgradeUuid = WINDOWS_UPGRADE_UUID.matcher(buildScript);

        assertThat(upgradeUuid.find()).isTrue();
        assertThat(upgradeUuid.group(1)).matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        assertThat(buildScript).contains("'--win-upgrade-uuid', windowsUpgradeUuid");
        assertThat(workflow).contains("app_version=\"${GITHUB_REF_NAME#v}\"")
                .contains("-PappVersion=\"$app_version\"");
    }
}
