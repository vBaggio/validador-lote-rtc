package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortalSchemaCatalogParserTest {

    private static final URI CATALOG = URI.create("https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx");

    @Test
    void selectsOnly010eInsideTheOfficialActiveSectionNotHistoricalOrParallelProfiles() {
        String html = """
                <h2>VERSÕES OFICIAIS (em uso)</h2><table>
                <tr><td>NF-e/NFC-e 010e_v1.02</td><td>10/07/2026</td><td><a href="download/010e.zip">baixar</a></td></tr>
                <tr><td>NF-e/NFC-e 010d_v1.99</td><td>11/07/2026</td><td><a href="download/010d.zip">baixar</a></td></tr>
                </table><h2>VERSÕES HISTÓRICAS</h2><table>
                <tr><td>NF-e/NFC-e 010e_v9.99</td><td>01/01/2027</td><td><a href="old.zip">baixar</a></td></tr>
                </table>
                """;

        PortalSchemaRelease release = new PortalSchemaCatalogParser().parse(CATALOG, html);

        assertThat(release.profile()).isEqualTo("010e_v1.02");
        assertThat(release.downloadUrl()).isEqualTo(CATALOG.resolve("download/010e.zip"));
        assertThat(release.publishedAt()).isEqualTo("2026-07-10T00:00:00Z");
    }

    @Test
    void normalizesTheOfficialProfileSpellingWithADotAfterV() {
        String officialFixture = """
                <h2>VERSÕES OFICIAIS (em uso)</h2><table>
                <tr><td>NF-e/NFC-e 010e_v.1.02</td><td>10/07/2026</td>
                <td><a href="download/010e.zip">baixar</a></td></tr></table>
                """;

        assertThat(new PortalSchemaCatalogParser().parse(CATALOG, officialFixture).profile())
                .isEqualTo("010e_v1.02");
    }

    @Test
    void refusesAmbiguousOrUnrecognizedActiveSection() {
        String ambiguous = """
                <h2>VERSÕES OFICIAIS (em uso)</h2><table>
                <tr><td>NF-e 010e_v1.01 10/07/2026</td><td><a href="a.zip">a</a></td></tr>
                <tr><td>NF-e 010e_v1.02 11/07/2026</td><td><a href="b.zip">b</a></td></tr>
                </table>
                """;

        assertThatThrownBy(() -> new PortalSchemaCatalogParser().parse(CATALOG, ambiguous))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ambígua");
        assertThatThrownBy(() -> new PortalSchemaCatalogParser().parse(CATALOG, "<h2>Histórico</h2>"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Seção");
    }
}
