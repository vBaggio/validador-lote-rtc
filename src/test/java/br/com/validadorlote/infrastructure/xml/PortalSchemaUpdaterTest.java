package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.infrastructure.tables.HttpsTransport;
import br.com.validadorlote.infrastructure.tables.SafeHttpsClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortalSchemaUpdaterTest {

    @TempDir Path temp;

    @Test
    void installsTheOfficialActiveReleaseAndRecordsDiscoveryAndDownloadUrls() throws Exception {
        byte[] zip = SchemaZipExtractorTest.zip(Map.of());
        PortalSchemaUpdater updater = updater(zip);

        assertThat(updater.updateIfNew()).isTrue();
        assertThat(updater.updateIfNew()).isFalse();

        Path manifest = temp.resolve("artifacts/NFE_SCHEMAS/versions/010e_v1.02/manifest.properties");
        Properties properties = new Properties();
        try (var input = Files.newInputStream(manifest)) { properties.load(input); }
        assertThat(properties.getProperty("discoveryUrl")).startsWith("https://www.nfe.fazenda.gov.br");
        assertThat(properties.getProperty("sourceUrl")).startsWith("https://www.nfe.fazenda.gov.br");
    }

    @Test
    void hostileDownloadDoesNotReplaceThePreviousActiveRelease() throws Exception {
        PortalSchemaUpdater good = updater(SchemaZipExtractorTest.zip(Map.of()));
        assertThat(good.updateIfNew()).isTrue();
        PortalSchemaUpdater hostile = updater(SchemaZipExtractorTest.zip(Map.of("../escape.xsd", "x".getBytes())),
                "010e_v1.03");

        assertThatThrownBy(hostile::updateIfNew).isInstanceOf(IllegalStateException.class);
        assertThat(new SchemaArtifactStore(temp).isActiveVersion("010e_v1.02")).isTrue();
    }

    private PortalSchemaUpdater updater(byte[] zip) {
        return updater(zip, "010e_v1.02");
    }

    private PortalSchemaUpdater updater(byte[] zip, String profile) {
        SafeHttpsClient https = new SafeHttpsClient(Set.of("www.nfe.fazenda.gov.br"),
                Duration.ofSeconds(1), 32 * 1024 * 1024, (uri, timeout) -> response(uri, zip, profile));
        return new PortalSchemaUpdater(https, new PortalSchemaCatalogParser(), new SchemaZipExtractor(),
                new SchemaArtifactStore(temp));
    }

    private HttpsTransport.Response response(URI uri, byte[] zip, String profile) {
        byte[] body = uri.getPath().contains("download") ? zip : ("""
                <h2>VERSÕES OFICIAIS (em uso)</h2><table>
                <tr><td>NF-e/NFC-e %s</td><td>10/07/2026</td>
                <td><a href="download/010e.zip">ZIP</a></td></tr></table>
                """.formatted(profile)).getBytes(StandardCharsets.UTF_8);
        return new HttpsTransport.Response(200, uri, Map.of(), body);
    }
}
