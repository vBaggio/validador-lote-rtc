package br.com.validadorlote.infrastructure.tables;

import br.com.validadorlote.infrastructure.update.ArtifactFailureKind;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeHttpsClientTest {

    private static final URI SVRS = URI.create("https://dfe-portal.svrs.rs.gov.br/DFE/TabelaClassificacaoTributaria");

    @Test
    void curatedSchemaFactoriesKeepManifestAndZipPoliciesIndependent() {
        URI artifact = URI.create("https://channel.test/artifact");
        byte[] largerThanManifest = new byte[
                SafeHttpsClient.CURATED_SCHEMA_MANIFEST_MAX_BYTES + 1];
        HttpsTransport transport = (uri, timeout) ->
                new HttpsTransport.Response(200, uri, Map.of(), largerThanManifest);
        SafeHttpsClient manifest = SafeHttpsClient.forCuratedSchemaManifest(
                Set.of("channel.test"), transport);
        SafeHttpsClient zip = SafeHttpsClient.forCuratedSchemaZip(
                Set.of("channel.test"), transport);

        assertThatThrownBy(() -> manifest.getBytes(artifact))
                .isInstanceOf(ArtifactUpdateException.class)
                .hasMessageContaining("limite");
        assertThat(zip.getBytes(artifact)).hasSize(largerThanManifest.length);

        assertThatThrownBy(() -> SafeHttpsClient.forCuratedSchemaManifest(
                Set.of("manifest.test"), transport).getBytes(artifact))
                .isInstanceOf(ArtifactUpdateException.class)
                .hasMessageContaining("não permitida");
        assertThatThrownBy(() -> SafeHttpsClient.forCuratedSchemaZip(
                Set.of("downloads.test"), transport).getBytes(artifact))
                .isInstanceOf(ArtifactUpdateException.class)
                .hasMessageContaining("não permitida");
    }

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

    @Test
    void jdkTransportTimesOutWhenTheServerSendsHeadersAndStopsTheBody() throws Exception {
        CountDownLatch headersSent = new CountDownLatch(1);
        CountDownLatch releaseBody = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try (ServerSocket server = new ServerSocket(0, 1,
                InetAddress.getByName("127.0.0.1"))) {
            workers.submit(() -> {
                try (var socket = server.accept()) {
                    consumeRequestHeaders(socket.getInputStream());
                    socket.getOutputStream().write(("""
                            HTTP/1.1 200 OK\r
                            Content-Length: 4\r
                            Connection: close\r
                            \r
                            """).getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    headersSent.countDown();
                    releaseBody.await(5, TimeUnit.SECONDS);
                }
                return null;
            });
            URI uri = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/artifact");
            HttpsTransport transport = new SafeHttpsClient.JdkTransport(32);
            Future<HttpsTransport.Response> response = workers.submit(
                    () -> transport.get(uri, Duration.ofMillis(500)));

            assertThat(headersSent.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> response.get(3, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(HttpTimeoutException.class);
        } finally {
            releaseBody.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void jdkTransportRejectsTheBodyAsSoonAsTheStreamingLimitIsExceeded() throws Exception {
        ExecutorService serverWorker = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1,
                InetAddress.getByName("127.0.0.1"))) {
            Future<?> responseWriter = serverWorker.submit(() -> {
                try (var socket = server.accept()) {
                    consumeRequestHeaders(socket.getInputStream());
                    socket.getOutputStream().write(("""
                            HTTP/1.1 200 OK\r
                            Content-Length: 5\r
                            Connection: close\r
                            \r
                            12345""").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                }
                return null;
            });
            URI uri = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/artifact");
            HttpsTransport transport = new SafeHttpsClient.JdkTransport(4);

            assertThatThrownBy(() -> transport.get(uri, Duration.ofSeconds(2)))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Resposta da fonte excede o limite de tamanho");
            responseWriter.get(2, TimeUnit.SECONDS);
        } finally {
            serverWorker.shutdownNow();
            assertThat(serverWorker.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void consumeRequestHeaders(InputStream input) throws IOException {
        byte[] marker = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        int matched = 0;
        while (matched < marker.length) {
            int value = input.read();
            if (value == -1) {
                throw new IOException("Requisição HTTP terminou antes dos cabeçalhos");
            }
            matched = value == marker[matched] ? matched + 1 : value == marker[0] ? 1 : 0;
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
