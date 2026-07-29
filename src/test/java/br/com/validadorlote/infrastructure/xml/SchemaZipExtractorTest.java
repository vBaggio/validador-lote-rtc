package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaZipExtractorTest {

    @Test
    void keepsOnlyTheSupportedClosureAndProductWrapper() throws Exception {
        Path candidate = new SchemaZipExtractor().extract(zip(Map.of("ignored/readme.txt", "x".getBytes())));
        try {
            assertThat(candidate.resolve("nota.xsd")).exists();
            assertThat(candidate.resolve("originais/leiauteNFe_v4.00.xsd")).exists();
            assertThat(candidate.resolve("ignored/readme.txt")).doesNotExist();
            assertThat(new SchemaValidatorEngine(new XsdErrorTranslator(), candidate)).isNotNull();
        } finally {
            delete(candidate);
        }
    }

    @Test
    void rejectsZipSlipBeforeItCanReachTheCandidateDirectory() throws Exception {
        byte[] hostile = zip(Map.of("../nota.xsd", "hostile".getBytes()));

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fora da base");
    }

    @Test
    void rejectsUnixSymbolicLinkEntriesBeforeWritingTheClosure() throws Exception {
        byte[] hostile = zip(Map.of("link", "target".getBytes()));
        markLastCentralDirectoryEntryAsSymlink(hostile);

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("link simbólico");
    }

    static byte[] zip(Map<String, byte[]> extras) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String file : new String[] {"DFeTiposBasicos_v1.00.xsd", "leiauteNFe_v4.00.xsd",
                    "nfe_v4.00.xsd", "tiposBasico_v4.00.xsd", "xmldsig-core-schema_v1.01.xsd"}) {
                zip.putNextEntry(new ZipEntry("NFe/" + file));
                zip.write(Files.readAllBytes(Path.of("src/main/resources/schemas/nfe/originais", file)));
                zip.closeEntry();
            }
            for (var extra : extras.entrySet()) {
                zip.putNextEntry(new ZipEntry(extra.getKey()));
                zip.write(extra.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private void delete(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private void markLastCentralDirectoryEntryAsSymlink(byte[] zip) {
        int end = endOfCentralDirectory(zip);
        int entries = littleEndianShort(zip, end + 10);
        int offset = littleEndianInt(zip, end + 16);
        for (int index = 0; index < entries; index++) {
            int next = offset + 46 + littleEndianShort(zip, offset + 28)
                    + littleEndianShort(zip, offset + 30) + littleEndianShort(zip, offset + 32);
            if (index == entries - 1) {
                int attributes = 0120777 << 16;
                zip[offset + 38] = (byte) attributes;
                zip[offset + 39] = (byte) (attributes >>> 8);
                zip[offset + 40] = (byte) (attributes >>> 16);
                zip[offset + 41] = (byte) (attributes >>> 24);
            }
            offset = next;
        }
    }

    private int endOfCentralDirectory(byte[] zip) {
        for (int offset = zip.length - 22; offset >= 0; offset--) {
            if (littleEndianInt(zip, offset) == 0x06054b50) return offset;
        }
        throw new AssertionError("EOCD ausente");
    }

    private int littleEndianShort(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]) | (Byte.toUnsignedInt(bytes[offset + 1]) << 8);
    }

    private int littleEndianInt(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset]) | (Byte.toUnsignedInt(bytes[offset + 1]) << 8)
                | (Byte.toUnsignedInt(bytes[offset + 2]) << 16) | (Byte.toUnsignedInt(bytes[offset + 3]) << 24);
    }
}
