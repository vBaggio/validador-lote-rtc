package br.com.validadorlote.infrastructure.tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Consulta às tabelas oficiais embarcadas. Toda busca é por código <b>e data do fato gerador</b>:
 * validar um documento de agosto contra a vigência de dezembro daria veredito errado.
 */
public final class FiscalTables {

    private final Map<String, CstEntry> csts;
    private final Map<String, ClassTribEntry> classificacoes;
    private final TablesManifest manifest;

    private FiscalTables(Map<String, CstEntry> csts, Map<String, ClassTribEntry> classificacoes) {
        this.csts = csts;
        this.classificacoes = classificacoes;
        this.manifest = new TablesManifest();
    }

    public static FiscalTables load() {
        try (InputStream in = FiscalTables.class.getResourceAsStream("/tables/cst-cclasstrib.json")) {
            if (in == null) {
                throw new IllegalStateException(
                        "Tabelas ausentes no classpath — rode ./gradlew updateFiscalTables");
            }
            JsonNode raiz = new ObjectMapper().readTree(in);
            Map<String, CstEntry> csts = new HashMap<>();
            Map<String, ClassTribEntry> cts = new HashMap<>();
            for (JsonNode c : raiz) {
                String cst = c.path("cst").asText();
                csts.put(cst, new CstEntry(cst, c.path("nome").asText(),
                        c.path("exigeGrupo").asBoolean(), c.path("exigeReducao").asBoolean(),
                        c.path("permiteDiferimento").asBoolean(),
                        data(c, "iniVig"), data(c, "fimVig")));
                for (JsonNode ct : c.path("classificacoes")) {
                    String codigo = ct.path("codigo").asText();
                    cts.put(codigo, new ClassTribEntry(codigo, ct.path("nome").asText(), cst,
                            ct.path("nfe").asBoolean(), ct.path("nfce").asBoolean(),
                            decimal(ct, "percRedIbs"), decimal(ct, "percRedCbs"),
                            data(ct, "iniVig"), data(ct, "fimVig")));
                }
            }
            return new FiscalTables(csts, cts);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static LocalDate data(JsonNode no, String campo) {
        String v = no.path(campo).asText(null);
        if (v == null || v.isBlank() || "null".equals(v) || v.length() < 10) {
            return null;
        }
        return LocalDate.parse(v.substring(0, 10));
    }

    private static BigDecimal decimal(JsonNode no, String campo) {
        return no.path(campo).isMissingNode() || no.path(campo).isNull()
                ? null : no.path(campo).decimalValue();
    }

    public Optional<CstEntry> cst(String codigo, LocalDate data) {
        return Optional.ofNullable(csts.get(codigo)).filter(c -> c.vigenteEm(data));
    }

    public Optional<ClassTribEntry> classTrib(String codigo, LocalDate data) {
        return Optional.ofNullable(classificacoes.get(codigo)).filter(c -> c.vigenteEm(data));
    }

    public String provenance() {
        return manifest.describe();
    }
}
