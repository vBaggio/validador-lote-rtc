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

/** Varre recursivamente uma pasta em busca de arquivos .xml (case-insensitive). */
public final class FolderScanner {

    public List<Path> scan(Path folder) {
        if (!Files.exists(folder)) {
            throw new ScanException("Pasta não encontrada: " + folder);
        }
        if (!Files.isDirectory(folder)) {
            throw new ScanException("O caminho informado não é uma pasta, é um arquivo: " + folder);
        }
        // FOLLOW_LINKS: subpastas que são atalhos/links simbólicos (ex.: para um share de
        // rede, comum em escritório contábil) também são varridas. Sem isso, Files.walk não
        // entra nelas e o lote é validado incompleto sem nenhum aviso ao usuário.
        try (Stream<Path> walk = Files.walk(folder, FileVisitOption.FOLLOW_LINKS)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new ScanException("Falha ao ler a pasta: " + folder, e);
        } catch (UncheckedIOException e) {
            // Files.walk é lazy: erros de I/O durante a travessia (ex.: subpasta sem
            // permissão de leitura, ou ciclo de links) chegam encapsulados aqui, não no
            // catch acima.
            if (e.getCause() instanceof FileSystemLoopException) {
                throw new ScanException(
                        "A pasta \"" + folder + "\" contém um atalho (link) que aponta de volta para "
                                + "ela mesma ou para uma pasta acima, formando um ciclo sem fim. "
                                + "Verifique os atalhos dentro dela e remova o que causa a repetição.",
                        e.getCause());
            }
            throw new ScanException("Falha ao ler a pasta: " + folder, e.getCause());
        }
    }
}
