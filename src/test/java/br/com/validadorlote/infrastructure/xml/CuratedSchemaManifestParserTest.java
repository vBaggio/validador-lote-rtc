package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.infrastructure.update.ArtifactFailureKind;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CuratedSchemaManifestParserTest {

    private static final String KEY_ID = "test-key-2026";
    private static final String SIGNATURE_MARKER = "${SIGNATURE}";
    private final ObjectMapper json = new ObjectMapper();
    private CuratedSchemaManifestParser parser;
    private Ed25519ManifestVerifier verifier;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() {
        keyPair = generateKeyPair();
        parser = new CuratedSchemaManifestParser();
        verifier = new Ed25519ManifestVerifier(Map.of(KEY_ID,
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())));
    }

    @Test
    void acceptsOnlyAWellFormedManifestWhoseSignatureMatchesItsCanonicalSignedPayload() {
        var manifest = parser.parse(signedFixture("valid-manifest.json"));

        verifier.verify(manifest.keyId(), parser.canonicalSignedBytes(manifest), manifest.signature());

        assertThat(manifest.signed().releaseSequence()).isEqualTo(7L);
        assertThat(manifest.signed().zipSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsAChangedSignedFieldEvenWhenTheOuterJsonIsValid() {
        var manifest = parser.parse(signedFixture("invalid-signature.json"));

        assertInvalidContent(() -> verifier.verify(manifest.keyId(),
                parser.canonicalSignedBytes(manifest), manifest.signature()));
    }

    @Test
    void rejectsUnknownFieldsAtEveryContractLevel() {
        List<byte[]> documents = List.of(
                mutate(root -> root.put("algorithm", "Ed25519")),
                mutate(root -> ((ObjectNode) root.get("signed")).put("channel", "stable")),
                mutate(root -> ((ObjectNode) root.path("signed").path("sourceProvenance").get(0))
                        .put("branch", "trunk")));

        documents.forEach(document -> assertInvalidContent(() -> parser.parse(document)));
    }

    @Test
    void rejectsMissingOrNullRequiredFields() {
        List<Consumer<ObjectNode>> mutations = List.of(
                root -> root.remove("format"),
                root -> root.putNull("keyId"),
                root -> root.remove("signed"),
                root -> root.putNull("signature"),
                root -> ((ObjectNode) root.get("signed")).putNull("artifact"),
                root -> ((ObjectNode) root.get("signed")).remove("releaseSequence"),
                root -> ((ObjectNode) root.get("signed")).putNull("version"),
                root -> ((ObjectNode) root.get("signed")).remove("publishedAt"),
                root -> ((ObjectNode) root.get("signed")).putNull("minimumAppVersion"),
                root -> ((ObjectNode) root.get("signed")).remove("zipUrl"),
                root -> ((ObjectNode) root.get("signed")).putNull("zipSha256"),
                root -> ((ObjectNode) root.get("signed")).remove("sourceProvenance"),
                root -> ((ObjectNode) root.path("signed").path("sourceProvenance").get(0))
                        .putNull("name"),
                root -> ((ObjectNode) root.path("signed").path("sourceProvenance").get(0))
                        .remove("url"),
                root -> ((ObjectNode) root.path("signed").path("sourceProvenance").get(0))
                        .putNull("revision"));

        mutations.stream().map(this::mutate)
                .forEach(document -> assertInvalidContent(() -> parser.parse(document)));
    }

    @Test
    void rejectsDuplicateJsonProperties() {
        String document = new String(signedFixture("valid-manifest.json"), StandardCharsets.UTF_8)
                .replace("\"keyId\" : \"" + KEY_ID + "\",",
                        "\"keyId\" : \"" + KEY_ID + "\",\n"
                                + "  \"keyId\" : \"" + KEY_ID + "\",");

        assertInvalidContent(() -> parser.parse(document.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsScalarTypeCoercion() {
        assertInvalidContent("format textual",
                () -> parser.parse(mutate(root -> root.put("format", "1"))));
        assertInvalidContent("sequência fracionária",
                () -> parser.parse(mutate(root ->
                        ((ObjectNode) root.get("signed")).put("releaseSequence", 7.5))));
        assertInvalidContent("versão numérica",
                () -> parser.parse(mutate(root ->
                        ((ObjectNode) root.get("signed")).put("version", 20260730))));
        assertInvalidContent("artefato numérico",
                () -> parser.parse(mutate(root ->
                        ((ObjectNode) root.get("signed")).put("artifact", 0))));
        assertInvalidContent("data numérica",
                () -> parser.parse(mutate(root ->
                        ((ObjectNode) root.get("signed")).put("publishedAt", 0))));
        assertInvalidContent("URL numérica",
                () -> parser.parse(mutate(root ->
                        ((ObjectNode) root.get("signed")).put("zipUrl", 1))));
        assertInvalidContent("assinatura booleana",
                () -> parser.parse(mutate(root -> root.put("signature", true))));
    }

    @Test
    void rejectsUnknownKeyIds() {
        var manifest = parser.parse(signedFixture("valid-manifest.json"));

        assertInvalidContent(() -> verifier.verify("unknown-key",
                parser.canonicalSignedBytes(manifest), manifest.signature()));
    }

    @Test
    void rejectsMalformedBase64AndCryptographicallyInvalidSignatures() {
        var manifest = parser.parse(signedFixture("valid-manifest.json"));

        assertInvalidContent(() -> verifier.verify(manifest.keyId(),
                parser.canonicalSignedBytes(manifest), "not base64!"));
        assertInvalidContent(() -> verifier.verify(manifest.keyId(),
                parser.canonicalSignedBytes(manifest),
                Base64.getEncoder().encodeToString(new byte[64])));
    }

    @Test
    void rejectsUnsupportedFormatAndNonpositiveReleaseSequence() {
        for (byte[] document : List.of(
                mutate(root -> root.put("format", 2)),
                mutate(root -> ((ObjectNode) root.get("signed")).put("releaseSequence", 0)),
                mutate(root -> ((ObjectNode) root.get("signed")).put("releaseSequence", -1)))) {
            assertInvalidContent(() -> parser.parse(document));
        }
    }

    @Test
    void rejectsUnsafeIdentifiersVersionsHashesAndEmptyProvenance() {
        for (byte[] document : List.of(
                mutate(root -> root.put("keyId", "../key")),
                mutate(root -> ((ObjectNode) root.get("signed")).put("version", "../schemas")),
                mutate(root -> ((ObjectNode) root.get("signed")).put("minimumAppVersion", "")),
                mutate(root -> ((ObjectNode) root.get("signed")).put("zipSha256",
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")),
                mutate(root -> ((ObjectNode) root.get("signed")).putArray("sourceProvenance")))) {
            assertInvalidContent(() -> parser.parse(document));
        }
    }

    @Test
    void rejectsReleaseVersionsThatAreNotSafeDirectoryNames() {
        for (String version : List.of(
                ".", "..", "-", "_",
                ".rtc", "rtc.", "-rtc", "rtc-", "_rtc", "rtc_",
                "a".repeat(101))) {
            byte[] document = mutate(root ->
                    ((ObjectNode) root.get("signed")).put("version", version));

            assertInvalidContent("version=" + version, () -> parser.parse(document));
        }
    }

    @Test
    void acceptsReleaseVersionsAtTheSafeBoundaries() {
        for (String version : List.of(
                "a",
                "rtc-2026.07.30_7",
                "a" + "_".repeat(98) + "z")) {
            var manifest = parser.parse(mutate(root ->
                    ((ObjectNode) root.get("signed")).put("version", version)));

            assertThat(manifest.signed().version()).isEqualTo(version);
        }
    }

    @Test
    void rejectsMinimumAppVersionsThatCannotBeOrderedByTheFutureComparator() {
        for (String version : List.of(
                ".", "..", "-", "_",
                ".1.0.0", "1.0.0.", "1.0", "1.0.0.0",
                "v1.0.0", "1.0.0-rc1", "01.0.0", "2147483648.0.0")) {
            byte[] document = mutate(root ->
                    ((ObjectNode) root.get("signed")).put("minimumAppVersion", version));

            assertInvalidContent("minimumAppVersion=" + version,
                    () -> parser.parse(document));
        }
    }

    @Test
    void acceptsOrderedNumericAppVersionBoundaries() {
        for (String version : List.of("0.0.0", "0.1.0", "2147483647.2147483647.2147483647")) {
            var manifest = parser.parse(mutate(root ->
                    ((ObjectNode) root.get("signed")).put("minimumAppVersion", version)));

            assertThat(manifest.signed().minimumAppVersion()).isEqualTo(version);
        }
    }

    @Test
    void rejectsNonHttpsZipUrls() {
        byte[] document = mutate(root -> ((ObjectNode) root.get("signed"))
                .put("zipUrl", "http://fixtures.invalid/schemas.zip"));

        assertInvalidContent(() -> parser.parse(document));
    }

    @Test
    void canonicalSignedBytesIgnoreJsonObjectFieldOrdering() {
        byte[] original = signedFixture("valid-manifest.json");
        ObjectNode reorderedRoot = readObject(original);
        ObjectNode signed = (ObjectNode) reorderedRoot.get("signed");
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        signed.fields().forEachRemaining(fields::add);
        ObjectNode reversed = json.createObjectNode();
        for (int index = fields.size() - 1; index >= 0; index--) {
            Map.Entry<String, JsonNode> field = fields.get(index);
            reversed.set(field.getKey(), field.getValue());
        }
        reorderedRoot.set("signed", reversed);

        var first = parser.parse(original);
        var second = parser.parse(write(reorderedRoot));

        assertThat(parser.canonicalSignedBytes(second))
                .isEqualTo(parser.canonicalSignedBytes(first));
    }

    private byte[] signedFixture(String name) {
        String validTemplate = fixtureText("valid-manifest.json");
        byte[] canonicalSigned = write(readObject(validTemplate.getBytes(StandardCharsets.UTF_8))
                .get("signed"));
        String signature = sign(canonicalSigned);
        return fixtureText(name).replace(SIGNATURE_MARKER, signature)
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] mutate(Consumer<ObjectNode> mutation) {
        ObjectNode root = readObject(signedFixture("valid-manifest.json"));
        mutation.accept(root);
        return write(root);
    }

    private ObjectNode readObject(byte[] document) {
        try {
            return (ObjectNode) json.readTree(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] write(JsonNode node) {
        try {
            return json.writeValueAsBytes(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String fixtureText(String name) {
        try (var input = getClass().getResourceAsStream(
                "/fixtures/update/curated-schemas/" + name)) {
            if (input == null) throw new AssertionError("Fixture ausente: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String sign(byte[] bytes) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(keyPair.getPrivate());
            signature.update(bytes);
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (java.security.GeneralSecurityException e) {
            throw new AssertionError(e);
        }
    }

    private KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private void assertInvalidContent(ThrowingOperation operation) {
        assertInvalidContent("conteúdo inválido", operation);
    }

    private void assertInvalidContent(String description, ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .as(description)
                .isInstanceOf(ArtifactUpdateException.class)
                .extracting(error -> ((ArtifactUpdateException) error).kind())
                .isEqualTo(ArtifactFailureKind.INVALID_CONTENT);
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }
}
