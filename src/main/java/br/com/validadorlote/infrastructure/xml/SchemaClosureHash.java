package br.com.validadorlote.infrastructure.xml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Hash canônico da closure suportada: paths UTF-8 ordenados + NUL + bytes + NUL. */
public final class SchemaClosureHash {
    private static final List<String> PATHS = List.of("nota.xsd", "originais/DFeTiposBasicos_v1.00.xsd",
            "originais/leiauteNFe_v4.00.xsd", "originais/nfe_v4.00.xsd",
            "originais/tiposBasico_v4.00.xsd", "originais/xmldsig-core-schema_v1.01.xsd");
    private SchemaClosureHash() {}
    public static String calculate(Path root) throws IOException {
        try { MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String path : PATHS) { digest.update(path.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
                digest.update(Files.readAllBytes(root.resolve(path))); digest.update((byte) 0); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
