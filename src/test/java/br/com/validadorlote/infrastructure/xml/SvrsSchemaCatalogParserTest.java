package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class SvrsSchemaCatalogParserTest {

    private static final URI CATALOG = URI.create("https://dfe-portal.svrs.rs.gov.br/NFe/Documentos");

    @Test
    void selectsOnlyANewer010ePackageAndBuildsAnEncodedOfficialDownloadUrl() {
        var release = new SvrsSchemaCatalogParser().newestCompatible(CATALOG, """
                <h1>Manuais</h1><article><time>12/07/2026</time><a onclick="download_arquivo_estatico('NFE', 2, 'PL_010e_NT2026_002_v1.99.zip')">fora</a></article>
                <h1>Schemas</h1><article><time>07/10/2025</time><a onclick="download_arquivo_estatico('NFE', 2, 'PL_010b_NT2025_002_v1.30.zip')">antigo</a></article>
                <article><time>11/07/2026</time><a onclick="download_arquivo_estatico('NFE', 2, 'PL_010e_NT2026_002_v1.03.zip')">novo</a></article>
                <article><time>11/07/2026</time><a onclick="download_arquivo_estatico('NFE', 3, 'NT_2026.002.pdf')">nota</a></article>
                <h1>Notas Técnicas</h1>
                """, "010e_v1.02");

        assertThat(release).isPresent();
        assertThat(release.orElseThrow().profile()).isEqualTo("010e_v1.03");
        assertThat(release.orElseThrow().downloadUrl().toString())
                .contains("sistema=NFE&tipoArquivo=2&nomeArquivo=PL_010e_NT2026_002_v1.03.zip");
        assertThat(new SvrsSchemaCatalogParser().downloadUri("pacote RTC.zip").toString())
                .contains("nomeArquivo=pacote+RTC.zip");
    }

    @Test
    void doesNotTreatAnOlderOrParallelProfileAsAnUpdate() {
        var release = new SvrsSchemaCatalogParser().newestCompatible(CATALOG, """
                <h1>Schemas</h1><article><time>07/10/2025</time><a onclick="download_arquivo_estatico('NFE', 2, 'PL_010b_NT2025_002_v1.30.zip')">antigo</a></article>
                <article><time>10/07/2026</time><a onclick="download_arquivo_estatico('NFE', 2, 'PL_010e_NT2025_002_v1.02.zip')">igual</a></article>
                <h1>Notas Técnicas</h1>
                """, "010e_v1.02");

        assertThat(release).isEmpty();
    }
}
