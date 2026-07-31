package br.com.validadorlote.infrastructure.fs;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FolderScannerTest {

    private final FolderScanner scanner = new FolderScanner();

    @Test
    void findsXmlFilesRecursivelyCaseInsensitiveAndSorted(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("b.xml"), "<x/>");
        Files.writeString(dir.resolve("sub/a.XML"), "<x/>");
        Files.writeString(dir.resolve("ignore.txt"), "nada");
        Files.writeString(dir.resolve("sub/ignore.pdf"), "nada");

        var result = scanner.scan(dir);

        assertThat(result).containsExactly(dir.resolve("b.xml"), dir.resolve("sub/a.XML"));
    }

    @Test
    void ignoresSubfoldersWhenRecursiveScanIsDisabled(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("direto.xml"), "<x/>");
        Files.writeString(dir.resolve("sub/interno.xml"), "<x/>");

        var result = scanner.scan(dir, false);

        assertThat(result).containsExactly(dir.resolve("direto.xml"));
    }

    /**
     * Cria os arquivos fora da ordem alfabética (z, m, a, y, b...) espalhados em
     * subpastas variadas, para que o teste só passe se a implementação de fato
     * ordenar o resultado — a travessia natural do sistema de arquivos não coincide
     * com essa ordem de criação. Verificação prática (feita e desfeita manualmente):
     * removendo o .sorted() de FolderScanner.scan, este teste falha.
     */
    @Test
    void resultIsSortedRegardlessOfCreationOrder(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("sub1"));
        Files.createDirectories(dir.resolve("sub2"));
        String[] creationOrder = {"z", "m", "a", "y", "b", "x", "c", "w", "d", "v", "e", "u"};
        Path[] targets = {
                dir.resolve("z.xml"), dir.resolve("sub1/m.xml"), dir.resolve("a.xml"),
                dir.resolve("sub2/y.xml"), dir.resolve("b.xml"), dir.resolve("sub1/x.xml"),
                dir.resolve("c.xml"), dir.resolve("sub2/w.xml"), dir.resolve("d.xml"),
                dir.resolve("sub1/v.xml"), dir.resolve("e.xml"), dir.resolve("sub2/u.xml"),
        };
        for (int i = 0; i < creationOrder.length; i++) {
            Files.writeString(targets[i], "<x/>");
        }

        List<Path> result = scanner.scan(dir);

        assertThat(result)
                .hasSize(targets.length)
                .containsExactlyInAnyOrder(targets)
                .isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    void emptyFolderYieldsEmptyList(@TempDir Path dir) {
        assertThat(scanner.scan(dir)).isEmpty();
    }

    @Test
    void missingFolderThrowsScanException(@TempDir Path dir) {
        assertThatThrownBy(() -> scanner.scan(dir.resolve("nao-existe")))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("não encontrada");
    }

    @Test
    void singleXmlFileYieldsAOneFileBatch(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nota.xml");
        Files.writeString(file, "<x/>");

        assertThat(scanner.scan(file)).containsExactly(file);
    }

    @Test
    void nonXmlFileIsRejectedWithAHelpfulMessage(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nota.pdf");
        Files.writeString(file, "não é XML");

        assertThatThrownBy(() -> scanner.scan(file))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("não é um arquivo XML")
                .hasMessageNotContaining("não encontrada");
    }

    /**
     * Cenário real de escritório contábil: uma subpasta é um atalho (link simbólico) para
     * um share de rede. Sem FileVisitOption.FOLLOW_LINKS, Files.walk não entra nela e o
     * arquivo nunca é encontrado — o lote seria validado incompleto sem nenhum aviso.
     * Aqui o XML só existe do lado de fora da pasta varrida, alcançável apenas pelo link:
     * se a implementação não seguir o link, o resultado vem vazio e o teste falha.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void findsXmlInsideSymlinkedSubfolder(@TempDir Path dir, @TempDir Path networkShare) throws IOException {
        Files.writeString(networkShare.resolve("nota.xml"), "<x/>");
        Path link = dir.resolve("atalho-para-rede");
        try {
            Files.createSymbolicLink(link, networkShare);
        } catch (UnsupportedOperationException e) {
            Assumptions.abort("Sistema de arquivos não suporta links simbólicos.");
        }

        var result = scanner.scan(dir);

        assertThat(result).containsExactly(link.resolve("nota.xml"));
    }

    /**
     * Um link que aponta de volta para um ancestral cria um ciclo sem fim. Files.walk com
     * FOLLOW_LINKS detecta isso e lança FileSystemLoopException (encapsulada em
     * UncheckedIOException) durante a travessia lazy. Precisa virar ScanException com
     * mensagem específica — não travar, e não confundir o usuário com o erro genérico de
     * falha de leitura.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void symlinkCycleThrowsScanExceptionWithSpecificMessage(@TempDir Path dir) throws IOException {
        Path sub = dir.resolve("sub");
        Files.createDirectory(sub);
        Path cycleLink = sub.resolve("volta-para-a-raiz");
        try {
            Files.createSymbolicLink(cycleLink, dir);
        } catch (UnsupportedOperationException e) {
            Assumptions.abort("Sistema de arquivos não suporta links simbólicos.");
        }

        assertThatThrownBy(() -> scanner.scan(dir))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("ciclo");
    }

    /**
     * Específico de POSIX: usa permissões POSIX para tornar uma subpasta ilegível
     * durante a travessia. Files.walk é lazy, então o IOException surge como
     * UncheckedIOException — precisa ser convertido em ScanException, não vazar cru.
     * Pulado em Windows (sem permissões POSIX) e quando o processo roda como root
     * (root ignora a permissão removida, então a leitura continuaria funcionando).
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void illegibleSubfolderThrowsScanExceptionInsteadOfLeakingRawError(@TempDir Path dir) throws IOException {
        Path unreadable = dir.resolve("sem-permissao");
        Files.createDirectory(unreadable);
        Files.writeString(dir.resolve("b.xml"), "<x/>");
        Files.writeString(unreadable.resolve("a.xml"), "<x/>");

        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));
        try {
            boolean stillReadable = Files.isReadable(unreadable) || tryListing(unreadable);
            Assumptions.assumeTrue(!stillReadable, "Executando como root: permissão removida é ignorada.");

            assertThatThrownBy(() -> scanner.scan(dir))
                    .isInstanceOf(ScanException.class)
                    .hasCauseInstanceOf(IOException.class);
        } finally {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rwx------"));
        }
    }

    private boolean tryListing(Path dir) {
        try (var stream = Files.list(dir)) {
            stream.findAny();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
