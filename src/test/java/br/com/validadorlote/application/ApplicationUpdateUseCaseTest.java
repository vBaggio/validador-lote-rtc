package br.com.validadorlote.application;

import br.com.validadorlote.domain.ApplicationRelease;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationUpdateUseCaseTest {

    private static final ApplicationRelease RELEASE = new ApplicationRelease("0.2.0",
            URI.create("https://github.com/vBaggio/validador-lote-rtc/releases/tag/v0.2.0"));

    @Test
    void notifiesOnlyOnceForTheSameNewerVersion() {
        var notifications = new ArrayList<ApplicationRelease>();
        ApplicationUpdateUseCase useCase = new ApplicationUpdateUseCase("0.1.0",
                () -> Optional.of(RELEASE), Runnable::run);

        useCase.checkAfterVisible(notifications::add);
        useCase.checkAfterVisible(notifications::add);

        assertThat(notifications).containsExactly(RELEASE);
    }

    @Test
    void ignoresAnEqualOrOlderRelease() {
        var notifications = new ArrayList<ApplicationRelease>();
        ApplicationUpdateUseCase useCase = new ApplicationUpdateUseCase("1.2.3",
                () -> Optional.of(RELEASE), Runnable::run);

        useCase.checkAfterVisible(notifications::add);

        assertThat(notifications).isEmpty();
        assertThat(ApplicationUpdateUseCase.isNewer("1.2.4", "1.2.3")).isTrue();
        assertThat(ApplicationUpdateUseCase.isNewer("1.2.3", "1.2.3")).isFalse();
        assertThat(ApplicationUpdateUseCase.isNewer("1.2.3-beta", "1.2.2")).isFalse();
    }

    @Test
    void containsCheckerAndExecutorFailures() {
        AtomicBoolean reached = new AtomicBoolean();
        ApplicationUpdateUseCase failedChecker = new ApplicationUpdateUseCase("0.1.0",
                () -> { throw new IllegalStateException("offline"); }, Runnable::run);
        Executor rejected = action -> { throw new IllegalStateException("encerrado"); };
        ApplicationUpdateUseCase failedExecutor = new ApplicationUpdateUseCase("0.1.0",
                () -> Optional.of(RELEASE), rejected);

        failedChecker.checkAfterVisible(release -> reached.set(true));
        failedExecutor.checkAfterVisible(release -> reached.set(true));

        assertThat(reached).isFalse();
    }
}
