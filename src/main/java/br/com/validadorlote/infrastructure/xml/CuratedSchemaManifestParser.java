package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.util.regex.Pattern;

/** Lê o contrato do canal sem tolerar extensões ou valores ambíguos. */
public final class CuratedSchemaManifestParser {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final Pattern RELEASE_VERSION = Pattern.compile(
            "[A-Za-z0-9](?:[A-Za-z0-9._-]{0,98}[A-Za-z0-9])?");
    private static final Pattern APP_VERSION = Pattern.compile(
            "(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public CuratedSchemaChannelManifest parse(byte[] document) {
        final JsonNode root;
        try {
            root = MAPPER.readTree(document);
        } catch (IOException | RuntimeException e) {
            throw ArtifactUpdateException.invalidContent(
                    "Manifesto do canal de schemas inválido", e);
        }
        validateJsonTypes(root);

        final CuratedSchemaChannelManifest manifest;
        try {
            manifest = MAPPER.treeToValue(root, CuratedSchemaChannelManifest.class);
        } catch (IOException | RuntimeException e) {
            throw ArtifactUpdateException.invalidContent(
                    "Manifesto do canal de schemas inválido", e);
        }
        validate(manifest);
        return manifest;
    }

    private static void validateJsonTypes(JsonNode root) {
        if (root == null || !root.isObject()
                || !isIntegral(root, "format")
                || !isText(root, "keyId")
                || !isText(root, "signature")) {
            throw invalidManifest();
        }
        JsonNode signed = root.get("signed");
        if (signed == null || !signed.isObject()
                || !isText(signed, "artifact")
                || !isIntegral(signed, "releaseSequence")
                || !isText(signed, "version")
                || !isText(signed, "publishedAt")
                || !isText(signed, "minimumAppVersion")
                || !isText(signed, "zipUrl")
                || !isText(signed, "zipSha256")) {
            throw invalidManifest();
        }
        JsonNode provenance = signed.get("sourceProvenance");
        if (provenance == null || !provenance.isArray()) throw invalidManifest();
        for (JsonNode source : provenance) {
            if (!source.isObject()
                    || !isText(source, "name")
                    || !isText(source, "url")
                    || !isText(source, "revision")) {
                throw invalidManifest();
            }
        }
    }

    private static boolean isIntegral(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isIntegralNumber();
    }

    private static boolean isText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual();
    }

    public byte[] canonicalSignedBytes(CuratedSchemaChannelManifest manifest) {
        if (manifest == null || manifest.signed() == null) {
            throw ArtifactUpdateException.invalidContent(
                    "Conteúdo assinado do manifesto de schemas ausente");
        }
        try {
            return MAPPER.writeValueAsBytes(manifest.signed());
        } catch (IOException | RuntimeException e) {
            throw ArtifactUpdateException.invalidContent(
                    "Não foi possível serializar o conteúdo assinado do manifesto de schemas", e);
        }
    }

    private static void validate(CuratedSchemaChannelManifest manifest) {
        if (manifest == null || manifest.format() != 1
                || !matches(IDENTIFIER, manifest.keyId())
                || manifest.signature() == null || manifest.signature().isBlank()
                || manifest.signed() == null) {
            throw invalidManifest();
        }

        CuratedSchemaChannelManifest.SignedRelease signed = manifest.signed();
        if (signed.artifact() == null
                || signed.releaseSequence() <= 0
                || !matches(RELEASE_VERSION, signed.version())
                || signed.publishedAt() == null
                || !isComparableAppVersion(signed.minimumAppVersion())
                || !isHttps(signed.zipUrl())
                || !matches(SHA_256, signed.zipSha256())
                || signed.sourceProvenance() == null
                || signed.sourceProvenance().isEmpty()) {
            throw invalidManifest();
        }

        for (CuratedSchemaChannelManifest.SourceProvenance provenance
                : signed.sourceProvenance()) {
            if (provenance == null
                    || provenance.name() == null || provenance.name().isBlank()
                    || provenance.url() == null || !provenance.url().isAbsolute()
                    || provenance.revision() == null || provenance.revision().isBlank()) {
                throw invalidManifest();
            }
        }
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private static boolean isComparableAppVersion(String value) {
        if (value == null || value.length() > 100 || !APP_VERSION.matcher(value).matches()) {
            return false;
        }
        try {
            for (String component : value.split("\\.")) Integer.parseInt(component);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isHttps(URI uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && uri.isAbsolute() && uri.getHost() != null && !uri.getHost().isBlank();
    }

    private static ArtifactUpdateException invalidManifest() {
        return ArtifactUpdateException.invalidContent(
                "Manifesto do canal de schemas contém dados inválidos");
    }
}
