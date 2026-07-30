package br.com.validadorlote.application;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationRuntimeTest {

    @Test
    void factoryIssuesStrictlyIncreasingRuntimeGenerations() {
        ValidationRuntimeFactory factory = new ValidationRuntimeFactory();
        RuntimeBases r1 = factory.nextBases("schemas-r1", "canal-r1", "tabelas-r1", "svrs-r1");
        RuntimeBases r2 = factory.nextBases("schemas-r2", "canal-r2", "tabelas-r2", "svrs-r2");

        assertThat(r1).isNotEqualTo(r2);
        assertThat(r2.generation()).isGreaterThan(r1.generation());
        assertThat(r1.schemaVersion()).isEqualTo("schemas-r1");
        assertThat(r1.tableVersion()).isEqualTo("tabelas-r1");
    }

    @Test
    void factoryKeepsGenerationsUniqueWhenCalledConcurrently() throws InterruptedException {
        ValidationRuntimeFactory factory = new ValidationRuntimeFactory();
        ExecutorService workers = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(24);
        var generations = new ConcurrentSkipListSet<Long>();
        try {
            for (int index = 0; index < 24; index++) {
                workers.submit(() -> {
                    try {
                        start.await();
                        generations.add(factory.nextBases("schemas", "canal", "tabelas", "svrs")
                                .generation());
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await();
        } finally {
            workers.shutdownNow();
        }

        assertThat(generations).containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(1, 24).boxed().toList());
    }
}
