package br.com.validadorlote.infrastructure.fs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Recebe uma pasta ou um XML individual para formar o lote de validação. */
public final class FolderScanner {

    public List<Path> scan(Path input) {
        if (!Files.exists(input)) {
            throw new ScanException("Entrada não encontrada: " + input);
        }
        if (Files.isRegularFile(input)) {
            if (!isXml(input)) {
                throw new ScanException("O arquivo selecionado não é um arquivo XML: " + input);
            }
            return List.of(input);
        }
        if (!Files.isDirectory(input)) {
            throw new ScanException("A entrada informada não é uma pasta nem um arquivo XML: " + input);
        }
        // FOLLOW_LINKS: subpastas que são atalhos/links simbólicos (ex.: para um share de
        // rede, comum em escritório contábil) também são varridas. Sem isso, Files.walk não
        // entra nelas e o lote é validado incompleto sem nenhum aviso ao usuário.
        try (Stream<Path> walk = Files.walk(input, FileVisitOption.FOLLOW_LINKS)) {
            return walk.filter(Files::isRegularFile)
                    .filter(FolderScanner::isXml)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new ScanException("Falha ao ler a pasta: " + input, e);
        } catch (UncheckedIOException e) {
            // Files.walk é lazy: erros de I/O durante a travessia (ex.: subpasta sem
            // permissão de leitura, ou ciclo de links) chegam encapsulados aqui, não no
            // catch acima.
            if (e.getCause() instanceof FileSystemLoopException) {
                throw new ScanException(
                        "A pasta \"" + input + "\" contém um atalho (link) que aponta de volta para "
                                + "ela mesma ou para uma pasta acima, formando um ciclo sem fim. "
                                + "Verifique os atalhos dentro dela e remova o que causa a repetição.",
                        e.getCause());
            }
            throw new ScanException("Falha ao ler a pasta: " + input, e.getCause());
        }
    }

    private static boolean isXml(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".xml");
    }
}
