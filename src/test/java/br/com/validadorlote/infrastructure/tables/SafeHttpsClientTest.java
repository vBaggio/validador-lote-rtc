package br.com.validadorlote.infrastructure.tables;

import br.com.validadorlote.infrastructure.update.ArtifactFailureKind;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeHttpsClientTest {

    private static final URI SVRS = URI.create("https://dfe-portal.svrs.rs.gov.br/DFE/TabelaClassificacaoTributaria");

    @Test
    void usesTheConfiguredTimeoutAndUtf8WithoutOpeningRealNetwork() {
        AtomicReference<Duration> receivedTimeout = new AtomicReference<>();
        SafeHttpsClient client = client((uri, timeout) -> {
            receivedTimeout.set(timeout);
            return response(200, uri, Map.of(), "tabela ç");
        });

        assertThat(client.getUtf8(SVRS)).isEqualTo("tabela ç");
        assertThat(receivedTimeout).hasValue(Duration.ofSeconds(3));
    }

    @Test
    void followsOnlyAnAllowedHttpsRedirect() {
        SafeHttpsClient client = client((uri, timeout) -> uri.getPath().equals("/primeiro")
                ? response(302, uri, Map.of("Location", List.of("/segundo")), "")
                : response(200, uri, Map.of(), "ok"));

        assertThat(client.getUtf8(URI.create("https://dfe-portal.svrs.rs.gov.br/primeiro"))).isEqualTo("ok");
    }

    @Test
    void rejectsNonAllowedOriginsRedirectsAndLargeResponses() {
        SafeHttpsClient client = client((uri, timeout) -> response(302, uri,
                Map.of("Location", List.of("https://example.invalid/payload")), ""));
        assertThatThrownBy(() -> client.getUtf8(SVRS))
                .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.INVALID_CONTENT);
                    assertThat(failure.retryable()).isFalse();
                });
        assertThatThrownBy(() -> client.getUtf8(URI.create("http://dfe-portal.svrs.rs.gov.br/x")))
                .isInstanceOfSatisfying(ArtifactUpdateException.class, failure ->
                        assertThat(failure.retryable()).isFalse());

        SafeHttpsClient tooLarge = new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"),
                Duration.ofSeconds(3), 4,
                (uri, timeout) -> response(200, uri, Map.of(), "12345"));
        assertThatThrownBy(() -> tooLarge.getUtf8(SVRS))
                .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.INVALID_CONTENT);
                    assertThat(failure.retryable()).isFalse();
                    assertThat(failure).hasMessageContaining("limite");
                });
    }

    @Test
    void identifiesTheSourceHostWhenTransportFails() {
        SafeHttpsClient client = client((uri, timeout) -> { throw new IOException("certificado"); });

        assertThatThrownBy(() -> client.getUtf8(SVRS))
                .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.CONNECTION);
                    assertThat(failure.retryable()).isTrue();
                    assertThat(failure).hasMessageContaining("dfe-portal.svrs.rs.gov.br");
                });
    }

    @Test
    void classifiesOnlyTransientGatewayAndServerFailuresAsRetryable() {
        for (int status : List.of(502, 503, 504)) {
            SafeHttpsClient client = client((uri, timeout) ->
                    response(status, uri, Map.of(), ""));

            assertThatThrownBy(() -> client.getBytes(SVRS))
                    .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                        assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.TEMPORARY_HTTP);
                        assertThat(failure.retryable()).isTrue();
                    });
        }
    }

    @Test
    void classifiesTlsAndClientErrorsAsNonRetryable() {
        SafeHttpsClient tls = client((uri, timeout) -> {
            throw new SSLHandshakeException("certificate");
        });
        assertThatThrownBy(() -> tls.getBytes(SVRS))
                .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.SECURE_CONNECTION);
                    assertThat(failure.retryable()).isFalse();
                });

        SafeHttpsClient missing = client((uri, timeout) ->
                response(404, uri, Map.of(), ""));
        assertThatThrownBy(() -> missing.getBytes(SVRS))
                .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.REJECTED_HTTP);
                    assertThat(failure.retryable()).isFalse();
                });
    }

    @Test
    void classifiesTimeoutAsConnectionAndPreservesInterruption() {
        SafeHttpsClient timeout = client((uri, requestTimeout) -> {
            throw new HttpTimeoutException("timeout");
        });
        assertThatThrownBy(() -> timeout.getBytes(SVRS))
                .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.CONNECTION);
                    assertThat(failure.retryable()).isTrue();
                });

        SafeHttpsClient interrupted = client((uri, requestTimeout) -> {
            throw new InterruptedException("interrompida");
        });
        try {
            assertThatThrownBy(() -> interrupted.getBytes(SVRS))
                    .isInstanceOfSatisfying(ArtifactUpdateException.class, failure -> {
                        assertThat(failure.kind()).isEqualTo(ArtifactFailureKind.INTERRUPTED);
                        assertThat(Thread.currentThread().isInterrupted()).isTrue();
                    });
        } finally {
            Thread.interrupted();
        }
    }

    private SafeHttpsClient client(HttpsTransport transport) {
        return new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"), Duration.ofSeconds(3), 32, transport);
    }

    private HttpsTransport.Response response(int status, URI uri, Map<String, List<String>> headers,
            String body) {
        return new HttpsTransport.Response(status, uri, headers, body.getBytes(StandardCharsets.UTF_8));
    }
}
