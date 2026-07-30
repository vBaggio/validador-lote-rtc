package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateCandidate;

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

    /** Consulta a tabela pública e prepara uma candidata normalizada, sem alterar a ativa. */
    public ArtifactCheckResult check() {
        JsonNode raw = extractor.extract(https.getUtf8(SOURCE));
        byte[] candidate = normalizer.normalize(raw);
        String version = "svrs-" + FiscalTableArtifactStore.sha256(candidate).substring(0, 12);
        if (store.isActiveVersion(version)) {
            return ArtifactCheckResult.upToDate("Tabela fiscal já está atualizada");
        }
        ArtifactManifest manifest = store.prepare(candidate, version, SOURCE.toString(), Instant.now());
        String detail = "Tabela fiscal preparada pela SVRS";
        return ArtifactCheckResult.available(new ArtifactUpdateCandidate(ArtifactId.FISCAL_TABLES,
                manifest.version(), manifest.sourceUrl(), manifest.publishedAt(), manifest.sha256(), detail),
                detail);
    }

    /** Ativa somente uma candidata de tabela que já passou pela preparação. */
    public ArtifactManifest apply(ArtifactUpdateCandidate candidate) {
        if (candidate == null || candidate.artifact() != ArtifactId.FISCAL_TABLES) {
            throw new IllegalArgumentException("Candidata não corresponde à tabela fiscal");
        }
        return store.activate(candidate.version());
    }
}
