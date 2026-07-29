package br.com.validadorlote.infrastructure.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Materializa somente a closure NF-e suportada, sem expor entradas arbitrárias do ZIP ao disco. */
public final class SchemaZipExtractor {

    private static final int MAX_ENTRIES = 3_000;
    private static final long MAX_EXTRACTED_BYTES = 64L * 1024 * 1024;
    private static final Set<String> REQUIRED = Set.of("DFeTiposBasicos_v1.00.xsd",
            "leiauteNFe_v4.00.xsd", "nfe_v4.00.xsd", "tiposBasico_v4.00.xsd",
            "xmldsig-core-schema_v1.01.xsd");

    /** A pasta devolvida pertence ao chamador e deve ser apagada depois da instalação no store. */
    public Path extract(byte[] zip) {
        try {
            Path candidate = Files.createTempDirectory("validador-schemas-");
            try {
                Map<String, byte[]> closure = readClosure(zip);
                writeWrapper(candidate);
                Path originals = Files.createDirectories(candidate.resolve("originais"));
                for (String name : REQUIRED) Files.write(originals.resolve(name), closure.get(name));
                return candidate;
            } catch (RuntimeException | IOException e) {
                delete(candidate);
                throw e;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível preparar os schemas baixados", e);
        }
    }

    private Map<String, byte[]> readClosure(byte[] zip) throws IOException {
        rejectSymbolicLinks(zip);
        Map<String, byte[]> closure = new HashMap<>();
        Set<String> entries = new HashSet<>();
        long extracted = 0;
        int count = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                if (++count > MAX_ENTRIES) throw new IllegalStateException("ZIP contém entradas demais");
                validateEntryName(entry.getName(), entry.isDirectory());
                if (!entries.add(entry.getName())) throw new IllegalStateException("ZIP contém entrada duplicada");
                byte[] content = readEntry(input, MAX_EXTRACTED_BYTES - extracted);
                extracted += content.length;
                if (extracted > MAX_EXTRACTED_BYTES) {
                    throw new IllegalStateException("ZIP excede o limite extraído");
                }
                String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
                if (!entry.isDirectory() && REQUIRED.contains(name)
                        && closure.putIfAbsent(name, content) != null) {
                    throw new IllegalStateException("ZIP contém closure ambígua: " + name);
                }
            }
        }
        if (!closure.keySet().containsAll(REQUIRED)) {
            throw new IllegalStateException("ZIP não contém a closure NF-e suportada");
        }
        return closure;
    }

    /**
     * {@link ZipEntry} não expõe os atributos externos Unix. Lemos somente o diretório central
     * para recusar explicitamente S_IFLNK antes que qualquer entrada seja escrita. Mesmo uma
     * entrada não usada pela closure não pode esconder esse tipo de objeto no pacote aceito.
     */
    private void rejectSymbolicLinks(byte[] zip) {
        int end = findEndOfCentralDirectory(zip);
        int entries = littleEndianShort(zip, end + 10);
        int offset = littleEndianInt(zip, end + 16);
        for (int index = 0; index < entries; index++) {
            if (offset < 0 || offset + 46 > zip.length || littleEndianInt(zip, offset) != 0x02014b50) {
                throw new IllegalStateException("ZIP possui diretório central inválido");
            }
            int externalAttributes = littleEndianInt(zip, offset + 38);
            int unixType = (externalAttributes >>> 16) & 0xF000;
            if (unixType == 0xA000) throw new IllegalStateException("ZIP contém link simbólico");
            int nameLength = littleEndianShort(zip, offset + 28);
            int extraLength = littleEndianShort(zip, offset + 30);
            int commentLength = littleEndianShort(zip, offset + 32);
            offset += 46 + nameLength + extraLength + commentLength;
        }
    }

    private int findEndOfCentralDirectory(byte[] zip) {
        for (int offset = zip.length - 22; offset >= Math.max(0, zip.length - 65_557); offset--) {
            if (littleEndianInt(zip, offset) == 0x06054b50) return offset;
        }
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

    private void validateEntryName(String name, boolean directory) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\")) {
            throw new IllegalStateException("ZIP contém caminho inválido");
        }
        String normalized = directory && name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        for (String part : normalized.split("/")) {
            if (part.equals(".") || part.equals("..") || part.isEmpty()) {
                throw new IllegalStateException("ZIP contém caminho fora da base");
            }
        }
    }

    private void writeWrapper(Path candidate) throws IOException {
        try (InputStream wrapper = SchemaZipExtractor.class.getResourceAsStream("/schemas/nfe/nota.xsd")) {
            if (wrapper == null) throw new IllegalStateException("Wrapper nota.xsd ausente no classpath");
            Files.write(candidate.resolve("nota.xsd"), wrapper.readAllBytes());
        }
    }

    private void delete(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
