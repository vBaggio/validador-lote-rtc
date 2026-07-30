package br.com.validadorlote.infrastructure.xml;

import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Verifica manifestos somente com chaves Ed25519 previamente confiadas pelo aplicativo. */
public final class Ed25519ManifestVerifier {

    private final Map<String, PublicKey> trustedKeys;

    public Ed25519ManifestVerifier(Map<String, String> trustedPublicKeysBase64) {
        Objects.requireNonNull(trustedPublicKeysBase64);
        Map<String, PublicKey> decoded = new HashMap<>();
        trustedPublicKeysBase64.forEach((keyId, encodedKey) ->
                decoded.put(Objects.requireNonNull(keyId), decodeTrustedKey(encodedKey)));
        trustedKeys = Map.copyOf(decoded);
    }

    public void verify(String keyId, byte[] signedBytes, String signatureBase64) {
        PublicKey trustedKey = keyId == null ? null : trustedKeys.get(keyId);
        if (trustedKey == null) {
            throw ArtifactUpdateException.invalidContent(
                    "Manifesto assinado por chave desconhecida: " + keyId);
        }
        if (signedBytes == null || signatureBase64 == null) {
            throw ArtifactUpdateException.invalidContent(
                    "Assinatura do manifesto de schemas inválida");
        }

        final byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signatureBase64);
        } catch (IllegalArgumentException e) {
            throw ArtifactUpdateException.invalidContent(
                    "Assinatura do manifesto de schemas não está em Base64 válido", e);
        }

        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(trustedKey);
            verifier.update(signedBytes);
            if (!verifier.verify(signatureBytes)) {
                throw ArtifactUpdateException.invalidContent(
                        "Assinatura do manifesto de schemas não confere");
            }
        } catch (ArtifactUpdateException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw ArtifactUpdateException.invalidContent(
                    "Assinatura do manifesto de schemas inválida", e);
        }
    }

    private static PublicKey decodeTrustedKey(String encodedKey) {
        try {
            byte[] x509 = Base64.getDecoder().decode(Objects.requireNonNull(encodedKey));
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(x509));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Chave pública Ed25519 confiável inválida", e);
        }
    }
}
