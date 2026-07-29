package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;

import java.net.URI;
import java.time.Instant;

/** Obtém uma candidata SVRS, normaliza e a delega ao armazenamento transacional. */
public final class SvrsTableUpdater {

    public static final URI SOURCE = URI.create(
            "https://dfe-portal.svrs.rs.gov.br/DFE/TabelaClassificacaoTributaria");

    private final SafeHttpsClient https;
    private final SvrsTableExtractor extractor;
    private final SvrsTableNormalizer normalizer;
    private final FiscalTableArtifactStore store;

    public SvrsTableUpdater(SafeHttpsClient https, SvrsTableExtractor extractor,
            SvrsTableNormalizer normalizer, FiscalTableArtifactStore store) {
        this.https = https;
        this.extractor = extractor;
        this.normalizer = normalizer;
        this.store = store;
    }

    public ArtifactManifest update() {
        JsonNode raw = extractor.extract(https.getUtf8(SOURCE));
        byte[] candidate = normalizer.normalize(raw);
        String version = "svrs-" + FiscalTableArtifactStore.sha256(candidate).substring(0, 12);
        return store.install(candidate, version, SOURCE.toString(), Instant.now());
    }

    /** @return {@code true} se instalou base nova; consulta idêntica não troca {@code current}. */
    public boolean updateIfNew() {
        JsonNode raw = extractor.extract(https.getUtf8(SOURCE));
        byte[] candidate = normalizer.normalize(raw);
        String version = "svrs-" + FiscalTableArtifactStore.sha256(candidate).substring(0, 12);
        if (store.isActiveVersion(version)) return false;
        store.install(candidate, version, SOURCE.toString(), Instant.now());
        return true;
    }
}
