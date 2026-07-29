package br.com.validadorlote.infrastructure.xml;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reconhece somente o perfil 010e de NF-e/NFC-e dentro da seção oficial em uso do Portal. */
public final class PortalSchemaCatalogParser {

    private static final String ACTIVE_HEADING = "VERSÕES OFICIAIS (EM USO)";
    private static final Pattern HEADING = Pattern.compile("(?is)<h[1-6][^>]*>(.*?)</h[1-6]>");
    private static final Pattern ROW = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    private static final Pattern LINK = Pattern.compile(
            "(?is)<a[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>");
    private static final Pattern PROFILE = Pattern.compile("\\b010e_v\\.?(\\d+)\\.(\\d+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("\\b(\\d{2}/\\d{2}/\\d{4})\\b");
    private static final DateTimeFormatter PORTAL_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    /**
     * O perfil 010e é a escolha explícita da aplicação (D-047): o 010d paralelo não é escolhido
     * por ordenação nem por "maior versão". Mais de uma entrada 010e ativa é ambiguidade segura.
     */
    public PortalSchemaRelease parse(URI catalogUrl, String html) {
        String active = activeSection(html);
        List<PortalSchemaRelease> matches = new ArrayList<>();
        Matcher rows = ROW.matcher(active);
        while (rows.find()) {
            String row = rows.group(1);
            Matcher profile = PROFILE.matcher(text(row));
            if (!profile.find()) {
                continue;
            }
            Matcher link = LINK.matcher(row);
            if (!link.find()) {
                throw new IllegalStateException("Pacote 010e oficial sem link de download");
            }
            Matcher date = DATE.matcher(text(row));
            if (!date.find()) {
                throw new IllegalStateException("Pacote 010e oficial sem data de publicação");
            }
            URI download = catalogUrl.resolve(htmlUnescape(link.group(1)));
            Instant publishedAt = LocalDate.parse(date.group(1), PORTAL_DATE)
                    .atStartOfDay().toInstant(ZoneOffset.UTC);
            matches.add(new PortalSchemaRelease(catalogUrl, download,
                    "010e_v" + profile.group(1) + "." + profile.group(2), publishedAt));
        }
        if (matches.isEmpty()) {
            throw new IllegalStateException("Não encontrei o perfil NF-e/NFC-e 010e entre versões oficiais em uso");
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("Há mais de um perfil 010e oficial em uso; seleção ambígua");
        }
        return matches.getFirst();
    }

    private String activeSection(String html) {
        Matcher headings = HEADING.matcher(html);
        int start = -1;
        int end = html.length();
        while (headings.find()) {
            if (start >= 0) {
                end = headings.start();
                break;
            }
            if (ACTIVE_HEADING.equals(normalize(text(headings.group(1))))) {
                start = headings.end();
            }
        }
        if (start < 0) throw new IllegalStateException("Seção de versões oficiais em uso não encontrada");
        return html.substring(start, end);
    }

    private String text(String html) {
        return html.replaceAll("(?is)<[^>]+>", " ").replace("&nbsp;", " ").trim();
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private String htmlUnescape(String href) {
        return href.replace("&amp;", "&");
    }
}
