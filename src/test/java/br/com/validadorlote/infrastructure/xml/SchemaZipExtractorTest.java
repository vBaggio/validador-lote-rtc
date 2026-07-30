package br.com.validadorlote.infrastructure.xml;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaZipExtractorTest {
    private static final byte[] EMPTY_SCHEMA =
            "<?xml version=\"1.0\"?><xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>"
                    .getBytes(StandardCharsets.UTF_8);

    @Test
    void preservesTheCompleteXsdTreeFromASingleRootDirectoryAndCompilesItsNotaEntrypoint()
            throws Exception {
        Path candidate = new SchemaZipExtractor().extract(zip(Map.of(
                "NFe/adicionais/extensao.xsd", EMPTY_SCHEMA)));
        try {
            assertThat(candidate.resolve("nota.xsd")).exists();
            assertThat(candidate.resolve("originais/leiauteNFe_v4.00.xsd")).exists();
            assertThat(candidate.resolve("adicionais/extensao.xsd")).exists();
            assertThat(new SchemaValidatorEngine(new XsdErrorTranslator(), candidate)).isNotNull();
        } finally {
            delete(candidate);
        }
    }

    @Test
    void acceptsACompleteTreeAlreadyRootedAtNotaXsd() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        releaseEntries().forEach((name, content) ->
                entries.put(name.substring("NFe/".length()), content));
        entries.put("adicionais/extensao.xsd", EMPTY_SCHEMA);

        Path candidate = new SchemaZipExtractor().extract(zipEntries(entries));
        try {
            assertThat(candidate.resolve("nota.xsd")).exists();
            assertThat(candidate.resolve("adicionais/extensao.xsd")).exists();
            assertThat(new SchemaValidatorEngine(new XsdErrorTranslator(), candidate)).isNotNull();
        } finally {
            delete(candidate);
        }
    }

    @Test
    void rejectsAReleaseThatDoesNotBringNotaXsd() throws Exception {
        Map<String, byte[]> entries = releaseEntries();
        entries.remove("NFe/nota.xsd");

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(zipEntries(entries)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nota.xsd");
    }

    @Test
    void rejectsRegularNonXsdEntries() throws Exception {
        assertThatThrownBy(() -> new SchemaZipExtractor().extract(
                zip(Map.of("NFe/readme.txt", "conteúdo".getBytes(StandardCharsets.UTF_8)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("não-XSD");
    }

    @Test
    void rejectsMultipleNotaEntrypoints() throws Exception {
        assertThatThrownBy(() -> new SchemaZipExtractor().extract(
                zip(Map.of("NFe/outro/nota.xsd", EMPTY_SCHEMA))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nota.xsd", "ambígu");
    }

    @Test
    void rejectsPathsOutsideTheSingleRootDirectory() throws Exception {
        assertThatThrownBy(() -> new SchemaZipExtractor().extract(
                zip(Map.of("Outro/extra.xsd", EMPTY_SCHEMA))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diretório raiz");
    }

    @Test
    void rejectsZipSlipBeforeItCanReachTheCandidateDirectory() throws Exception {
        byte[] hostile = zip(Map.of("../nota.xsd", "hostile".getBytes()));

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fora da base");
    }

    @Test
    void rejectsDuplicateEntries() throws Exception {
        Map<String, byte[]> extras = new LinkedHashMap<>();
        extras.put("NFe/duplicata-a.xsd", EMPTY_SCHEMA);
        extras.put("NFe/duplicata-b.xsd", EMPTY_SCHEMA);
        byte[] hostile = zip(extras);
        replaceAscii(hostile, "duplicata-b", "duplicata-a");

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicada");
    }

    @Test
    void rejectsCaseInsensitiveCollisionsInTheFinalPortablePath() throws Exception {
        assertThatThrownBy(() -> new SchemaZipExtractor().extract(zip(Map.of(
                "NFe/originais/dfetiposbasicos_v1.00.xsd", EMPTY_SCHEMA))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("colisão", "maiúsculas");
    }

    @Test
    void rejectsMoreThanTheMaximumNumberOfEntries() throws Exception {
        Map<String, byte[]> entries = releaseEntries();
        for (int index = 0; index < 2_995; index++) {
            entries.put("NFe/extras/schema-%04d.xsd".formatted(index), EMPTY_SCHEMA);
        }

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(zipEntries(entries)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("entradas demais");
    }

    @Test
    void rejectsMoreThanTheMaximumExtractedSize() throws Exception {
        assertThatThrownBy(() -> new SchemaZipExtractor().extract(oversizedZip()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("limite extraído");
    }

    @Test
    void rejectsUnixSymbolicLinkEntriesBeforeWritingTheClosure() throws Exception {
        byte[] hostile = zip(Map.of("NFe/link.xsd", EMPTY_SCHEMA));
        markLastCentralDirectoryEntryAsSymlink(hostile);

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("link simbólico");
    }

    @Test
    void rejectsSymlinkEvenWhenEocdEntryCountersAreForgedToZero() throws Exception {
        byte[] hostile = zip(Map.of("NFe/link.xsd", EMPTY_SCHEMA));
        markLastCentralDirectoryEntryAsSymlink(hostile);
        int end = endOfCentralDirectory(hostile);
        writeLittleEndianShort(hostile, end + 8, 0);
        writeLittleEndianShort(hostile, end + 10, 0);

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diretório central");
    }

    @Test
    void rejectsSanitizedShadowCentralDirectoryAppendedAfterASymlinkArchive()
            throws Exception {
        byte[] original = zip(Map.of("NFe/link.xsd", EMPTY_SCHEMA));
        markLastCentralDirectoryEntryAsSymlink(original);
        byte[] hostile = appendSanitizedShadowCentralDirectory(original);

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diretório central", "região local");
    }

    @Test
    void rejectsEarlyRealDescriptorHiddenByAForgedLateDescriptorAndSanitizedCentral()
            throws Exception {
        byte[] original = zip(Map.of("NFe/link.xsd", EMPTY_SCHEMA));
        markLastCentralDirectoryEntryAsSymlink(original);
        byte[] hostile = appendForgedDescriptorAndSanitizedCentralDirectory(original);

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("entrada local", "diretório central");
    }

    @Test
    void rejectsTrailingBytesThatAreNotDeclaredAsTheEocdComment() throws Exception {
        byte[] valid = zip(Map.of());
        byte[] hostile = java.util.Arrays.copyOf(valid, valid.length + 1);

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diretório central");
    }

    @Test
    void rejectsAForgedEmptyEocdAppendedAfterTheRealArchive() throws Exception {
        byte[] valid = zip(Map.of());
        byte[] hostile = java.util.Arrays.copyOf(valid, valid.length + 22);
        writeLittleEndianInt(hostile, valid.length, 0x06054b50);

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diretório central");
    }

    @Test
    void rejectsCentralDirectoryWhoseDeclaredSizeExceedsTheArchive() throws Exception {
        byte[] hostile = zip(Map.of());
        int end = endOfCentralDirectory(hostile);
        writeLittleEndianInt(hostile, end + 12, Integer.MAX_VALUE);

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diretório central");
    }

    @Test
    void rejectsDifferentNamesInLocalAndCentralDirectoryEntries() throws Exception {
        byte[] hostile = zip(Map.of("NFe/portable-a.xsd", EMPTY_SCHEMA));
        replaceAsciiOccurrence(hostile, "portable-a", "portable-b", 2);

        assertThatThrownBy(() -> new SchemaZipExtractor().extract(hostile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("diretório central");
    }

    static byte[] zip(Map<String, byte[]> extras) throws IOException {
        Map<String, byte[]> entries = releaseEntries();
        entries.putAll(extras);
        return zipEntries(entries);
    }

    private static Map<String, byte[]> releaseEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try {
            entries.put("NFe/nota.xsd",
                    Files.readAllBytes(Path.of("src/main/resources/schemas/nfe/nota.xsd")));
            for (String file : new String[] {"DFeTiposBasicos_v1.00.xsd",
                    "leiauteNFe_v4.00.xsd", "nfe_v4.00.xsd", "tiposBasico_v4.00.xsd",
                    "xmldsig-core-schema_v1.01.xsd"}) {
                entries.put("NFe/originais/" + file, Files.readAllBytes(
                        Path.of("src/main/resources/schemas/nfe/originais", file)));
            }
            return entries;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static byte[] zipEntries(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] oversizedZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (var entry : releaseEntries().entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("NFe/extras/grande.xsd"));
            byte[] block = new byte[8_192];
            for (int written = 0; written <= 64 * 1024 * 1024; written += block.length) {
                zip.write(block);
            }
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static void replaceAscii(byte[] bytes, String original, String replacement) {
        int replacements = replaceAsciiOccurrence(bytes, original, replacement, 0);
        assertThat(replacements).isEqualTo(2);
    }

    private static int replaceAsciiOccurrence(byte[] bytes, String original,
            String replacement, int targetOccurrence) {
        byte[] from = original.getBytes(StandardCharsets.US_ASCII);
        byte[] to = replacement.getBytes(StandardCharsets.US_ASCII);
        if (from.length != to.length) throw new IllegalArgumentException("Tamanhos distintos");
        int occurrences = 0;
        for (int offset = 0; offset <= bytes.length - from.length; offset++) {
            boolean match = true;
            for (int index = 0; index < from.length; index++) {
                if (bytes[offset + index] != from[index]) {
                    match = false;
                    break;
                }
            }
            if (!match) continue;
            occurrences++;
            if (targetOccurrence == 0 || occurrences == targetOccurrence) {
                System.arraycopy(to, 0, bytes, offset, to.length);
            }
        }
        if (targetOccurrence > 0) assertThat(occurrences).isEqualTo(2);
        return occurrences;
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

    private byte[] appendSanitizedShadowCentralDirectory(byte[] zip) {
        int originalEnd = endOfCentralDirectory(zip);
        int entries = littleEndianShort(zip, originalEnd + 10);
        int centralSize = littleEndianInt(zip, originalEnd + 12);
        int originalCentralOffset = littleEndianInt(zip, originalEnd + 16);
        int shadowCentralOffset = zip.length;
        int shadowEnd = shadowCentralOffset + centralSize;
        byte[] shadowed = java.util.Arrays.copyOf(zip, shadowEnd + 22);
        System.arraycopy(zip, originalCentralOffset, shadowed, shadowCentralOffset, centralSize);

        int offset = shadowCentralOffset;
        for (int index = 0; index < entries; index++) {
            int next = offset + 46 + littleEndianShort(shadowed, offset + 28)
                    + littleEndianShort(shadowed, offset + 30)
                    + littleEndianShort(shadowed, offset + 32);
            if (index == entries - 1) writeLittleEndianInt(shadowed, offset + 38, 0);
            offset = next;
        }

        writeLittleEndianInt(shadowed, shadowEnd, 0x06054b50);
        writeLittleEndianShort(shadowed, shadowEnd + 8, entries);
        writeLittleEndianShort(shadowed, shadowEnd + 10, entries);
        writeLittleEndianInt(shadowed, shadowEnd + 12, centralSize);
        writeLittleEndianInt(shadowed, shadowEnd + 16, shadowCentralOffset);
        return shadowed;
    }

    private byte[] appendForgedDescriptorAndSanitizedCentralDirectory(byte[] zip) {
        int originalEnd = endOfCentralDirectory(zip);
        int entries = littleEndianShort(zip, originalEnd + 10);
        int centralSize = littleEndianInt(zip, originalEnd + 12);
        int originalCentralOffset = littleEndianInt(zip, originalEnd + 16);
        int originalLastEntry = lastCentralDirectoryEntryOffset(
                zip, originalCentralOffset, entries);
        int localOffset = littleEndianInt(zip, originalLastEntry + 42);
        int localNameLength = littleEndianShort(zip, localOffset + 26);
        int localExtraLength = littleEndianShort(zip, localOffset + 28);
        int dataStart = localOffset + 30 + localNameLength + localExtraLength;
        int forgedDescriptorOffset = zip.length;
        int expandedCompressedSize = forgedDescriptorOffset - dataStart;
        int shadowCentralOffset = forgedDescriptorOffset + 16;
        int shadowEnd = shadowCentralOffset + centralSize;
        byte[] shadowed = java.util.Arrays.copyOf(zip, shadowEnd + 22);

        writeLittleEndianInt(shadowed, forgedDescriptorOffset, 0x08074b50);
        writeLittleEndianInt(shadowed, forgedDescriptorOffset + 4,
                littleEndianInt(zip, originalLastEntry + 16));
        writeLittleEndianInt(shadowed, forgedDescriptorOffset + 8, expandedCompressedSize);
        writeLittleEndianInt(shadowed, forgedDescriptorOffset + 12,
                littleEndianInt(zip, originalLastEntry + 24));

        System.arraycopy(zip, originalCentralOffset, shadowed, shadowCentralOffset, centralSize);
        int shadowLastEntry = lastCentralDirectoryEntryOffset(
                shadowed, shadowCentralOffset, entries);
        writeLittleEndianInt(shadowed, shadowLastEntry + 20, expandedCompressedSize);
        writeLittleEndianInt(shadowed, shadowLastEntry + 38, 0);

        writeLittleEndianInt(shadowed, shadowEnd, 0x06054b50);
        writeLittleEndianShort(shadowed, shadowEnd + 8, entries);
        writeLittleEndianShort(shadowed, shadowEnd + 10, entries);
        writeLittleEndianInt(shadowed, shadowEnd + 12, centralSize);
        writeLittleEndianInt(shadowed, shadowEnd + 16, shadowCentralOffset);
        return shadowed;
    }

    private int lastCentralDirectoryEntryOffset(byte[] zip, int first, int entries) {
        int offset = first;
        for (int index = 1; index < entries; index++) {
            offset += 46 + littleEndianShort(zip, offset + 28)
                    + littleEndianShort(zip, offset + 30)
                    + littleEndianShort(zip, offset + 32);
        }
        return offset;
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

    private void writeLittleEndianShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
    }

    private void writeLittleEndianInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
    }
}
