package br.com.validadorlote.infrastructure.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Materializa uma árvore XSD curada sem expor entradas arbitrárias do ZIP ao disco. */
public final class SchemaZipExtractor {

    private static final int MAX_ENTRIES = 3_000;
    private static final long MAX_EXTRACTED_BYTES = 64L * 1024 * 1024;

    /** A pasta devolvida pertence ao chamador e deve ser apagada depois da instalação no store. */
    public Path extract(byte[] zip) {
        try {
            Path candidate = Files.createTempDirectory("validador-schemas-");
            try {
                ExtractedTree tree = readTree(zip);
                writeTree(candidate, tree);
                return candidate;
            } catch (RuntimeException | IOException e) {
                delete(candidate);
                throw e;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível preparar os schemas baixados", e);
        }
    }

    private ExtractedTree readTree(byte[] zip) {
        CentralDirectory centralDirectory = rejectSymbolicLinks(zip);
        Map<String, byte[]> files = new HashMap<>();
        Set<String> entries = new HashSet<>();
        long extracted = 0;
        int count = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                if (++count > MAX_ENTRIES) throw new IllegalStateException("ZIP contém entradas demais");
                if (count > centralDirectory.entryNames().size()
                        || !entry.getName().equals(centralDirectory.entryNames().get(count - 1))) {
                    throw new IllegalStateException(
                            "ZIP possui diretório central incompatível com as entradas locais");
                }
                String name = validateEntryName(entry.getName(), entry.isDirectory());
                if (!entries.add(name)) throw new IllegalStateException("ZIP contém entrada duplicada");
                if (!entry.isDirectory() && !name.endsWith(".xsd")) {
                    throw new IllegalStateException("ZIP contém arquivo regular não-XSD: " + name);
                }
                byte[] content = readEntry(input, MAX_EXTRACTED_BYTES - extracted);
                extracted += content.length;
                if (extracted > MAX_EXTRACTED_BYTES) {
                    throw new IllegalStateException("ZIP excede o limite extraído");
                }
                if (!entry.isDirectory()) files.put(name, content);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("ZIP contém dados comprimidos inválidos");
        }
        if (count != centralDirectory.entryNames().size()) {
            throw new IllegalStateException(
                    "ZIP possui diretório central incompatível com as entradas locais");
        }
        List<String> entrypoints = files.keySet().stream()
                .filter(name -> name.equals("nota.xsd") || name.endsWith("/nota.xsd"))
                .sorted()
                .toList();
        if (entrypoints.isEmpty()) {
            throw new IllegalStateException("ZIP não contém nota.xsd");
        }
        if (entrypoints.size() != 1) {
            throw new IllegalStateException("ZIP contém nota.xsd ambíguo");
        }
        String entrypoint = entrypoints.getFirst();
        String rootPrefix = rootPrefix(entrypoint);
        validateSingleRoot(entries, rootPrefix);
        validatePortablePaths(files.keySet(), rootPrefix);
        return new ExtractedTree(files, rootPrefix);
    }

    /**
     * {@link ZipEntry} não expõe os atributos externos Unix. Lemos somente o diretório central
     * para recusar explicitamente S_IFLNK antes que qualquer entrada seja escrita. Mesmo uma
     * entrada não usada pela closure não pode esconder esse tipo de objeto no pacote aceito.
     */
    private CentralDirectory rejectSymbolicLinks(byte[] zip) {
        int end = findEndOfCentralDirectory(zip);
        int disk = littleEndianShort(zip, end + 4);
        int centralDirectoryDisk = littleEndianShort(zip, end + 6);
        int entriesOnDisk = littleEndianShort(zip, end + 8);
        int entries = littleEndianShort(zip, end + 10);
        long centralDirectorySize = littleEndianUnsignedInt(zip, end + 12);
        long centralDirectoryOffset = littleEndianUnsignedInt(zip, end + 16);
        if (entries > MAX_ENTRIES) {
            throw new IllegalStateException("ZIP contém entradas demais");
        }
        if (disk != 0 || centralDirectoryDisk != 0 || entriesOnDisk != entries
                || centralDirectoryOffset + centralDirectorySize != end) {
            throw new IllegalStateException("ZIP possui diretório central inválido");
        }
        int offset = Math.toIntExact(centralDirectoryOffset);
        List<String> entryNames = new ArrayList<>(entries);
        for (int index = 0; index < entries; index++) {
            if ((long) offset + 46 > end || littleEndianInt(zip, offset) != 0x02014b50) {
                throw new IllegalStateException("ZIP possui diretório central inválido");
            }
            int externalAttributes = littleEndianInt(zip, offset + 38);
            int unixType = (externalAttributes >>> 16) & 0xF000;
            if (unixType == 0xA000) throw new IllegalStateException("ZIP contém link simbólico");
            int nameLength = littleEndianShort(zip, offset + 28);
            int extraLength = littleEndianShort(zip, offset + 30);
            int commentLength = littleEndianShort(zip, offset + 32);
            long next = (long) offset + 46 + nameLength + extraLength + commentLength;
            if (next > end) {
                throw new IllegalStateException("ZIP possui diretório central inválido");
            }
            entryNames.add(decodeEntryName(zip, offset + 46, nameLength));
            offset = Math.toIntExact(next);
        }
        if (offset != end) {
            throw new IllegalStateException("ZIP possui diretório central inválido");
        }
        return new CentralDirectory(List.copyOf(entryNames));
    }

    private int findEndOfCentralDirectory(byte[] zip) {
        boolean signatureFound = false;
        for (int offset = zip.length - 22; offset >= Math.max(0, zip.length - 65_557); offset--) {
            if (littleEndianInt(zip, offset) != 0x06054b50) continue;
            signatureFound = true;
            int commentLength = littleEndianShort(zip, offset + 20);
            if ((long) offset + 22 + commentLength == zip.length) return offset;
        }
        if (signatureFound) throw new IllegalStateException("ZIP possui diretório central inválido");
        throw new IllegalStateException("ZIP sem diretório central");
    }

    private int littleEndianShort(byte[] bytes, int offset) {
        if (offset < 0 || offset + 2 > bytes.length) throw new IllegalStateException("ZIP truncado");
        return Byte.toUnsignedInt(bytes[offset]) | (Byte.toUnsignedInt(bytes[offset + 1]) << 8);
    }

    private int littleEndianInt(byte[] bytes, int offset) {
        if (offset < 0 || offset + 4 > bytes.length) throw new IllegalStateException("ZIP truncado");
        return Byte.toUnsignedInt(bytes[offset]) | (Byte.toUnsignedInt(bytes[offset + 1]) << 8)
                | (Byte.toUnsignedInt(bytes[offset + 2]) << 16)
                | (Byte.toUnsignedInt(bytes[offset + 3]) << 24);
    }

    private long littleEndianUnsignedInt(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(littleEndianInt(bytes, offset));
    }

    private String decodeEntryName(byte[] zip, int offset, int length) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(zip, offset, length))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalStateException("ZIP possui nome inválido no diretório central");
        }
    }

    private byte[] readEntry(InputStream input, long remaining) throws IOException {
        if (remaining < 0) throw new IllegalStateException("ZIP excede o limite extraído");
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = input.read(buffer)) != -1;) {
            if (output.size() + read > remaining) throw new IllegalStateException("ZIP excede o limite extraído");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String validateEntryName(String name, boolean directory) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\")) {
            throw new IllegalStateException("ZIP contém caminho inválido");
        }
        String normalized = directory && name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        for (String part : normalized.split("/")) {
            if (part.equals(".") || part.equals("..") || part.isEmpty()) {
                throw new IllegalStateException("ZIP contém caminho fora da base");
            }
        }
        return normalized;
    }

    private String rootPrefix(String entrypoint) {
        int separator = entrypoint.indexOf('/');
        if (separator < 0) return "";
        if (separator != entrypoint.lastIndexOf('/')) {
            throw new IllegalStateException(
                    "nota.xsd deve estar na raiz do ZIP ou em um único diretório raiz");
        }
        return entrypoint.substring(0, separator + 1);
    }

    private void validateSingleRoot(Set<String> entries, String rootPrefix) {
        if (rootPrefix.isEmpty()) return;
        String rootDirectory = rootPrefix.substring(0, rootPrefix.length() - 1);
        if (entries.stream().anyMatch(name ->
                !name.equals(rootDirectory) && !name.startsWith(rootPrefix))) {
            throw new IllegalStateException(
                    "ZIP contém caminho fora do diretório raiz dos schemas");
        }
    }

    private void validatePortablePaths(Set<String> files, String rootPrefix) {
        Set<String> portablePaths = new HashSet<>();
        for (String file : files) {
            String relativeName = file.substring(rootPrefix.length());
            if (!portablePaths.add(relativeName.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException(
                        "ZIP contém colisão de caminho entre maiúsculas e minúsculas");
            }
        }
    }

    private void writeTree(Path candidate, ExtractedTree tree) throws IOException {
        for (var file : tree.files().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            String relativeName = file.getKey().substring(tree.rootPrefix().length());
            Path destination = candidate.resolve(relativeName).normalize();
            if (!destination.startsWith(candidate)) {
                throw new IllegalStateException("ZIP contém caminho fora da base");
            }
            Files.createDirectories(destination.getParent());
            Files.write(destination, file.getValue());
        }
    }

    private void delete(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record ExtractedTree(Map<String, byte[]> files, String rootPrefix) {}

    private record CentralDirectory(List<String> entryNames) {}
}
