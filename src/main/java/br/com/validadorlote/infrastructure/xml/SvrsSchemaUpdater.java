package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.infrastructure.tables.SafeHttpsClient;
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCandidate;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateResult;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Atualiza somente um pacote 010e mais novo que a base local, descoberto na SVRS. */
public final class SvrsSchemaUpdater {

    public static final URI CATALOG = URI.create("https://dfe-portal.svrs.rs.gov.br/NFe/Documentos");
    private final SafeHttpsClient https;
    private final SvrsSchemaCatalogParser catalog;
    private final SchemaZipExtractor zip;
    private final SchemaArtifactStore store;
    private final String embeddedProfile;

    public SvrsSchemaUpdater(SafeHttpsClient https, SvrsSchemaCatalogParser catalog,
            SchemaZipExtractor zip, SchemaArtifactStore store, String embeddedProfile) {
        this.https = https;
        this.catalog = catalog;
        this.zip = zip;
        this.store = store;
        this.embeddedProfile = embeddedProfile;
    }

    public ArtifactUpdateResult updateIfNew() {
        ArtifactCheckResult checked = check();
        if (checked.status() == ArtifactCheckResult.Status.UP_TO_DATE) {
            return ArtifactUpdateResult.unchanged(checked.detail());
        }
        apply(checked.candidate());
        return ArtifactUpdateResult.updated(checked.detail());
    }

    /** Consulta a SVRS e prepara uma candidata validada, sem alterar a base ativa. */
    public ArtifactCheckResult check() {
        String activeProfile = store.activeManifestOrNull() == null ? embeddedProfile
                : store.activeManifestOrNull().version();
        var release = catalog.newestCompatible(CATALOG, https.getUtf8(CATALOG), activeProfile);
        if (release.isEmpty()) {
            return ArtifactCheckResult.upToDate(
                    "A SVRS não publicou pacote compatível mais novo; base local mantida");
        }
        Path candidate = zip.extract(https.getBytes(release.get().downloadUrl()));
        try {
            ArtifactManifest manifest = store.prepare(candidate, release.get().profile(), release.get().discoveryUrl().toString(),
                    release.get().downloadUrl().toString(), release.get().publishedAt());
            String detail = "Schemas preparados pela SVRS";
            return ArtifactCheckResult.available(new ArtifactUpdateCandidate(ArtifactId.NFE_SCHEMAS,
                    manifest.version(), manifest.sourceUrl(), manifest.publishedAt(), manifest.sha256(), detail),
                    detail);
        } finally {
            delete(candidate);
        }
    }

    /** Ativa somente uma candidata de schemas que já passou pela preparação. */
    public ArtifactManifest apply(ArtifactUpdateCandidate candidate) {
        if (candidate == null || candidate.artifact() != ArtifactId.NFE_SCHEMAS) {
            throw new IllegalArgumentException("Candidata não corresponde aos schemas NF-e");
        }
        return store.activate(candidate.version());
    }

    private void delete(Path root) {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível limpar staging de schemas", e);
        }
    }
}
