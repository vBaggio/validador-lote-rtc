package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.infrastructure.tables.SafeHttpsClient;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/** Adquire schemas exclusivamente do Portal Nacional; falhas são devolvidas ao coordenador. */
public final class PortalSchemaUpdater {

    public static final URI CATALOG = URI.create(
            "https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=BMPFMBoln3w%3D");
    private final SafeHttpsClient https;
    private final PortalSchemaCatalogParser catalog;
    private final SchemaZipExtractor zip;
    private final SchemaArtifactStore store;

    public PortalSchemaUpdater(SafeHttpsClient https, PortalSchemaCatalogParser catalog,
            SchemaZipExtractor zip, SchemaArtifactStore store) {
        this.https = https;
        this.catalog = catalog;
        this.zip = zip;
        this.store = store;
    }

    /** @return {@code true} quando uma nova base foi instalada; {@code false} se a versão já é ativa. */
    public boolean updateIfNew() {
        PortalSchemaRelease release = catalog.parse(CATALOG, https.getUtf8(CATALOG));
        if (store.isActiveVersion(release.profile())) return false;
        Path candidate = zip.extract(https.getBytes(release.downloadUrl()));
        try {
            store.install(candidate, release.profile(), release.discoveryUrl().toString(),
                    release.downloadUrl().toString(), release.publishedAt());
            return true;
        } finally {
            delete(candidate);
        }
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
