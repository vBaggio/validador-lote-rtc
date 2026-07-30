package br.com.validadorlote.infrastructure.xml;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lê somente pacotes NF-e/NFC-e publicados no catálogo público da SVRS. */
public final class SvrsSchemaCatalogParser {

    public static final URI DOWNLOAD = URI.create(
            "https://dfe-portal.svrs.rs.gov.br/NFE/DownloadArquivoEstatico/");
    private static final Pattern HEADING = Pattern.compile("(?is)<h1[^>]*>(.*?)</h1>");
    private static final Pattern ARTICLE = Pattern.compile("(?is)<article[^>]*>(.*?)</article>");
    private static final Pattern DATE = Pattern.compile("\\b(\\d{2}/\\d{2}/\\d{4})\\b");
    private static final Pattern DOWNLOAD_CALL = Pattern.compile(
            "(?is)download_arquivo_estatico\\(\\s*['\\\"]NFE['\\\"]\\s*,\\s*2\\s*,\\s*['\\\"]([^'\\\"]+\\.zip)['\\\"]\\s*\\)");
    private static final Pattern PROFILE = Pattern.compile(
            "(?i)\\bPL_(010e)_.*?_v(\\d+)\\.(\\d+)\\.zip\\b");
    private static final DateTimeFormatter SVRS_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    /**
     * Retorna somente um perfil 010e estritamente mais novo. Outros perfis não são ordenáveis
     * contra a base do produto e, portanto, não podem provocar uma troca automática.
     */
    public Optional<SvrsSchemaRelease> newestCompatible(URI catalog, String html, String activeProfile) {
        Version active = Version.parse(activeProfile);
        List<SvrsSchemaRelease> matches = new ArrayList<>();
        Matcher articles = ARTICLE.matcher(schemasSection(html));
        while (articles.find()) {
            String article = articles.group(1);
            Matcher download = DOWNLOAD_CALL.matcher(article);
            if (!download.find()) continue;
            String name = htmlUnescape(download.group(1));
            Matcher profile = PROFILE.matcher(name);
            if (!profile.matches()) continue;
            Version candidate = new Version(profile.group(1).toLowerCase(),
                    Integer.parseInt(profile.group(2)), Integer.parseInt(profile.group(3)));
            if (candidate.compareTo(active) <= 0) continue;
            Matcher date = DATE.matcher(text(article));
            if (!date.find()) throw new IllegalStateException("Pacote de schemas SVRS sem data de publicação");
            matches.add(new SvrsSchemaRelease(catalog, downloadUri(name), candidate.toString(),
                    LocalDate.parse(date.group(1), SVRS_DATE).atStartOfDay().toInstant(ZoneOffset.UTC)));
        }
        if (matches.isEmpty()) return Optional.empty();
        matches.sort(Comparator.comparing(SvrsSchemaRelease::profile, (left, right) ->
                Version.parse(left).compareTo(Version.parse(right))).reversed());
        if (matches.size() > 1 && matches.getFirst().profile().equals(matches.get(1).profile())) {
            throw new IllegalStateException("Há mais de um pacote SVRS para o mesmo perfil");
        }
        return Optional.of(matches.getFirst());
    }

    URI downloadUri(String publishedName) {
        return URI.create(DOWNLOAD + "?sistema=NFE&tipoArquivo=2&nomeArquivo="
                + URLEncoder.encode(publishedName, StandardCharsets.UTF_8));
    }

    private String schemasSection(String html) {
        Matcher headings = HEADING.matcher(html);
        int start = -1;
        int end = html.length();
        while (headings.find()) {
            if (start >= 0) {
                end = headings.start();
                break;
            }
            if ("SCHEMAS".equals(text(headings.group(1)).trim().toUpperCase(java.util.Locale.ROOT))) {
                start = headings.end();
            }
        }
        if (start < 0) throw new IllegalStateException("Seção de schemas da SVRS não encontrada");
        return html.substring(start, end);
    }

    private String text(String html) {
        return html.replaceAll("(?is)<[^>]+>", " ").replace("&nbsp;", " ");
    }

    private String htmlUnescape(String value) {
        return value.replace("&amp;", "&").replace("&#231;", "ç").replace("&#227;", "ã");
    }

    private record Version(String family, int major, int minor) implements Comparable<Version> {
        static Version parse(String profile) {
            Matcher matcher = Pattern.compile("(?i)^(010e)_v(\\d+)\\.(\\d+)$").matcher(profile);
            if (!matcher.matches()) throw new IllegalArgumentException("Perfil de schema não suportado: " + profile);
            return new Version(matcher.group(1).toLowerCase(), Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        }

        @Override public int compareTo(Version other) {
            int familyComparison = family.compareTo(other.family);
            if (familyComparison != 0) return familyComparison;
            int majorComparison = Integer.compare(major, other.major);
            return majorComparison != 0 ? majorComparison : Integer.compare(minor, other.minor);
        }

        @Override public String toString() { return family + "_v" + major + "." + String.format("%02d", minor); }
    }
}
