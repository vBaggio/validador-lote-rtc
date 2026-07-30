package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.infrastructure.tables.HttpsTransport;
import br.com.validadorlote.infrastructure.tables.SafeHttpsClient;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateResult;
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvrsSchemaUpdaterTest {

    @TempDir Path temp;

    @Test
    void keepsTheEmbeddedBaseWhenSvrsOnlyListsAnOlderPackage() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        var updater = updater("PL_010b_NT2025_002_v1.30.zip", SchemaZipExtractorTest.zip(Map.of()), downloads);

        ArtifactUpdateResult result = updater.updateIfNew();

        assertThat(result.updated()).isFalse();
        assertThat(result.detail()).contains("base local mantida");
        assertThat(downloads).hasValue(0);
    }

    @Test
    void installsANewerSvrsPackageAndRecordsTheOfficialDiscoveryAndDownloadUrls() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        var updater = updater("PL_010e_NT2026_002_v1.03.zip", SchemaZipExtractorTest.zip(Map.of()), downloads);

        assertThat(updater.updateIfNew().updated()).isTrue();
        assertThat(new SchemaArtifactStore(temp).isActiveVersion("010e_v1.03")).isTrue();
        assertThat(new SchemaArtifactStore(temp).activeManifestOrNull().sourceUrl())
                .startsWith("https://dfe-portal.svrs.rs.gov.br/NFE/DownloadArquivoEstatico/");
        assertThat(downloads).hasValue(1);
    }

    @Test
    void checkStagesNewReleaseWithoutChangingTheActiveVersion() throws Exception {
        var updater = updater("PL_010e_NT2026_002_v1.03.zip", SchemaZipExtractorTest.zip(Map.of()),
                new AtomicInteger());

        ArtifactCheckResult result = updater.check();

        assertThat(result.status()).isEqualTo(ArtifactCheckResult.Status.UPDATE_AVAILABLE);
        assertThat(result.candidate().version()).isEqualTo("010e_v1.03");
        assertThat(new SchemaArtifactStore(temp).activeManifestOrNull()).isNull();
    }

    @Test
    void applyActivatesExactlyTheCandidateReturnedByCheck() throws Exception {
        var updater = updater("PL_010e_NT2026_002_v1.03.zip", SchemaZipExtractorTest.zip(Map.of()),
                new AtomicInteger());
        var candidate = updater.check().candidate();

        updater.apply(candidate);

        assertThat(new SchemaArtifactStore(temp).activeManifestOrNull().version())
                .isEqualTo(candidate.version());
    }

    @Test
    void applyRejectsACandidateForAnotherArtifact() throws Exception {
        var updater = updater("PL_010e_NT2026_002_v1.03.zip", SchemaZipExtractorTest.zip(Map.of()),
                new AtomicInteger());
        var tableCandidate = new ArtifactUpdateCandidate(ArtifactId.FISCAL_TABLES, "candidate-v2",
                "https://dfe-portal.svrs.rs.gov.br/x", java.time.Instant.EPOCH, "0".repeat(64), "");

        assertThatThrownBy(() -> updater.apply(tableCandidate))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyOfficialDownloadDoesNotInstallOrReplaceAnything() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        var good = updater("PL_010e_NT2026_002_v1.03.zip", SchemaZipExtractorTest.zip(Map.of()), downloads);
        assertThat(good.updateIfNew().updated()).isTrue();
        var empty = updater("PL_010e_NT2026_002_v1.04.zip", new byte[0], downloads);

        assertThatThrownBy(empty::updateIfNew).isInstanceOf(IllegalStateException.class);
        assertThat(new SchemaArtifactStore(temp).isActiveVersion("010e_v1.03")).isTrue();
    }

    private SvrsSchemaUpdater updater(String name, byte[] zip, AtomicInteger downloads) {
        SafeHttpsClient https = new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"), Duration.ofSeconds(1),
                32 * 1024 * 1024, (uri, timeout) -> response(uri, name, zip, downloads));
        return new SvrsSchemaUpdater(https, new SvrsSchemaCatalogParser(), new SchemaZipExtractor(),
                new SchemaArtifactStore(temp), "010e_v1.02");
    }

    private HttpsTransport.Response response(URI uri, String name, byte[] zip, AtomicInteger downloads) {
        if (uri.getPath().contains("DownloadArquivoEstatico")) {
            downloads.incrementAndGet();
            return new HttpsTransport.Response(200, uri, Map.of(), zip);
        }
        String page = "<h1>Schemas</h1><article><time>11/07/2026</time><a onclick=\"download_arquivo_estatico('NFE', 2, '%s')\">ZIP</a></article><h1>Notas Técnicas</h1>"
                .formatted(name);
        return new HttpsTransport.Response(200, uri, Map.of(), page.getBytes(StandardCharsets.UTF_8));
    }
}
