package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.tables.HttpsTransport;
import br.com.validadorlote.infrastructure.tables.SafeHttpsClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubReleaseCheckerTest {

    @Test
    void acceptsOnlyAnOfficialStableReleaseWithASemanticTag() {
        Optional<br.com.validadorlote.domain.ApplicationRelease> release =
                GitHubReleaseChecker.parse("""
                {"tag_name":"v0.2.0","html_url":"https://github.com/vBaggio/validador-lote-rtc/releases/tag/v0.2.0","draft":false,"prerelease":false}
                """);

        assertThat(release).hasValueSatisfying(value -> {
            assertThat(value.version()).isEqualTo("0.2.0");
            assertThat(value.page()).hasToString("https://github.com/vBaggio/validador-lote-rtc/releases/tag/v0.2.0");
        });
    }

    @Test
    void rejectsInvalidJsonUnstableTagsAndUnofficialPages() {
        assertThat(GitHubReleaseChecker.parse("not json")).isEmpty();
        assertThat(GitHubReleaseChecker.parse("""
                {"tag_name":"v0.2.0-beta","html_url":"https://github.com/vBaggio/validador-lote-rtc/releases/tag/v0.2.0","draft":false,"prerelease":false}
                """)).isEmpty();
        assertThat(GitHubReleaseChecker.parse("""
                {"tag_name":"v0.2.0","html_url":"https://example.invalid/release","draft":false,"prerelease":false}
                """)).isEmpty();
        assertThat(GitHubReleaseChecker.parse("""
                {"tag_name":"v0.2.0","html_url":"https://github.com:444/vBaggio/validador-lote-rtc/releases/tag/v0.2.0","draft":false,"prerelease":false}
                """)).isEmpty();
        assertThat(GitHubReleaseChecker.parse("""
                {"tag_name":"v0.2.0","html_url":"https://github.com/vBaggio/validador-lote-rtc/releases/tag/v0.2.0","draft":false,"prerelease":true}
                """)).isEmpty();
    }

    @Test
    void containsTransportAndHttpFailures() {
        HttpsTransport failing = (uri, timeout) -> { throw new java.io.IOException("offline"); };
        HttpsTransport rejected = (uri, timeout) -> new HttpsTransport.Response(429, uri,
                Map.of(), new byte[0]);

        assertThat(new GitHubReleaseChecker(client(failing)).get()).isEmpty();
        assertThat(new GitHubReleaseChecker(client(rejected)).get()).isEmpty();
    }

    private static SafeHttpsClient client(HttpsTransport transport) {
        return new SafeHttpsClient(java.util.Set.of("api.github.com"), Duration.ofSeconds(3),
                SafeHttpsClient.GITHUB_RELEASE_MAX_BYTES, transport);
    }
}
