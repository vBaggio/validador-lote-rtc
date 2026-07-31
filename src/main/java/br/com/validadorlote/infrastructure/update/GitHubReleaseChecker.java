package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.domain.ApplicationRelease;
import br.com.validadorlote.infrastructure.tables.SafeHttpsClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Optional;
import java.util.function.Supplier;

/** Lê somente a última release estável do repositório oficial, sem propagar falhas. */
public final class GitHubReleaseChecker implements Supplier<Optional<ApplicationRelease>> {

    public static final URI LATEST_RELEASE = URI.create(
            "https://api.github.com/repos/vBaggio/validador-lote-rtc/releases/latest");
    private static final String OFFICIAL_PAGE_PREFIX =
            "/vBaggio/validador-lote-rtc/releases/tag/";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final SafeHttpsClient https;

    public GitHubReleaseChecker(SafeHttpsClient https) {
        this.https = https;
    }

    @Override
    public Optional<ApplicationRelease> get() {
        try {
            return parse(https.getUtf8(LATEST_RELEASE));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static Optional<ApplicationRelease> parse(String document) {
        try {
            JsonNode root = JSON.readTree(document);
            if (root == null || !root.isObject() || truthy(root, "draft")
                    || truthy(root, "prerelease")) return Optional.empty();
            JsonNode tag = root.get("tag_name");
            JsonNode page = root.get("html_url");
            if (tag == null || !tag.isTextual() || !tag.asText().matches("v?[0-9]+\\.[0-9]+\\.[0-9]+")
                    || page == null || !page.isTextual()) return Optional.empty();
            URI pageUri = URI.create(page.asText());
            if (!"https".equalsIgnoreCase(pageUri.getScheme())
                    || !"github.com".equalsIgnoreCase(pageUri.getHost())
                    || (pageUri.getPort() != -1 && pageUri.getPort() != 443)
                    || !pageUri.getPath().startsWith(OFFICIAL_PAGE_PREFIX)) return Optional.empty();
            String version = tag.asText().startsWith("v") ? tag.asText().substring(1) : tag.asText();
            return Optional.of(new ApplicationRelease(version, pageUri));
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static boolean truthy(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value == null || !value.isBoolean() || value.booleanValue();
    }
}
