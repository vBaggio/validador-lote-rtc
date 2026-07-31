package br.com.validadorlote.infrastructure.tables;

import br.com.validadorlote.infrastructure.update.ArtifactUpdateException;

import javax.net.ssl.SSLException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** HTTPS restrito para artefatos normativos: sem hosts implícitos ou redirects abertos. */
public final class SafeHttpsClient {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final int SVRS_MAX_BYTES = 6 * 1024 * 1024;
    public static final int CURATED_SCHEMA_MANIFEST_MAX_BYTES = 256 * 1024;
    public static final int GITHUB_RELEASE_MAX_BYTES = 64 * 1024;
    public static final int SCHEMA_MAX_BYTES = 32 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;

    private final Set<String> allowedHosts;
    private final Duration timeout;
    private final int maxBytes;
    private final HttpsTransport transport;

    public SafeHttpsClient(Set<String> allowedHosts, Duration timeout, int maxBytes,
            HttpsTransport transport) {
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.timeout = Objects.requireNonNull(timeout);
        this.maxBytes = maxBytes;
        this.transport = Objects.requireNonNull(transport);
        if (this.allowedHosts.isEmpty() || timeout.isNegative() || timeout.isZero() || maxBytes < 1) {
            throw new IllegalArgumentException("Política HTTPS inválida");
        }
    }

    public static SafeHttpsClient forSvrs() {
        return new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"), DEFAULT_TIMEOUT,
                SVRS_MAX_BYTES, new JdkTransport(SVRS_MAX_BYTES));
    }

    public static SafeHttpsClient forSvrsSchemas() {
        return new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"), DEFAULT_TIMEOUT,
                SCHEMA_MAX_BYTES, new JdkTransport(SCHEMA_MAX_BYTES));
    }

    /** Cliente restrito para a consulta curta e consultiva da release do aplicativo. */
    public static SafeHttpsClient forGitHubRelease() {
        return new SafeHttpsClient(Set.of("api.github.com"), Duration.ofSeconds(3),
                GITHUB_RELEASE_MAX_BYTES, new JdkTransport(GITHUB_RELEASE_MAX_BYTES));
    }

    public static SafeHttpsClient forCuratedSchemaManifest(Set<String> hosts) {
        return forCuratedSchemaManifest(hosts,
                new JdkTransport(CURATED_SCHEMA_MANIFEST_MAX_BYTES));
    }

    static SafeHttpsClient forCuratedSchemaManifest(Set<String> hosts,
            HttpsTransport transport) {
        return new SafeHttpsClient(hosts, DEFAULT_TIMEOUT,
                CURATED_SCHEMA_MANIFEST_MAX_BYTES, transport);
    }

    public static SafeHttpsClient forCuratedSchemaZip(Set<String> hosts) {
        return forCuratedSchemaZip(hosts, new JdkTransport(SCHEMA_MAX_BYTES));
    }

    static SafeHttpsClient forCuratedSchemaZip(Set<String> hosts, HttpsTransport transport) {
        return new SafeHttpsClient(hosts, DEFAULT_TIMEOUT, SCHEMA_MAX_BYTES, transport);
    }

    /** Retorna sempre UTF-8; a página da fonte é pública e não dita o charset ao aplicativo. */
    public String getUtf8(URI initial) {
        return new String(getBytes(initial), StandardCharsets.UTF_8);
    }

    /** Baixa bytes de artefato sem permitir que a origem escolha host, redirect ou tamanho. */
    public byte[] getBytes(URI initial) {
        URI current = validate(initial);
        long deadline = System.nanoTime() + timeout.toNanos();
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpsTransport.Response response;
            try {
                response = transport.get(current, remainingUntil(deadline));
                remainingUntil(deadline);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw ArtifactUpdateException.interrupted("Consulta HTTPS interrompida", e);
            } catch (HttpTimeoutException e) {
                throw ArtifactUpdateException.connection(
                        "A consulta HTTPS excedeu o tempo limite", e);
            } catch (SSLException e) {
                throw ArtifactUpdateException.secureConnection(
                        "Não foi possível estabelecer uma conexão HTTPS segura", e);
            } catch (ResponseTooLargeException e) {
                throw ArtifactUpdateException.invalidContent(
                        "Resposta da fonte excede o limite de tamanho", e);
            } catch (IOException e) {
                throw ArtifactUpdateException.connection(
                        "Não foi possível consultar " + current.getHost(), e);
            }
            validate(response.uri());
            if (response.body().length > maxBytes) {
                throw ArtifactUpdateException.invalidContent(
                        "Resposta da fonte excede o limite de tamanho");
            }
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                String location = response.firstHeader("Location");
                if (location == null || location.isBlank()) {
                    throw ArtifactUpdateException.invalidContent(
                            "Redirecionamento HTTPS sem destino");
                }
                current = validate(current.resolve(location));
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = "Fonte HTTPS respondeu HTTP " + response.statusCode();
                if (response.statusCode() == 502 || response.statusCode() == 503
                        || response.statusCode() == 504) {
                    throw ArtifactUpdateException.temporaryHttp(message);
                }
                throw ArtifactUpdateException.rejectedHttp(message);
            }
            return response.body();
        }
        throw ArtifactUpdateException.invalidContent(
                "A fonte excedeu o limite de redirecionamentos");
    }

    private static Duration remainingUntil(long deadline) throws HttpTimeoutException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new HttpTimeoutException("A consulta HTTPS excedeu o tempo limite");
        }
        return Duration.ofNanos(remaining);
    }

    private URI validate(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || !allowedHosts.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))) {
            throw ArtifactUpdateException.invalidContent("Origem HTTPS não permitida: " + uri);
        }
        return uri;
    }

    /** Implementação de produção; redirects ficam deliberadamente desligados no JDK. */
    static final class JdkTransport implements HttpsTransport {
        private final int maxBytes;
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkTransport(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public Response get(URI uri, Duration timeout) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
            LimitedBodySubscriber subscriber = new LimitedBodySubscriber(maxBytes);
            CompletableFuture<HttpResponse<byte[]>> exchange = client.sendAsync(request,
                    ignored -> subscriber);
            try {
                HttpResponse<byte[]> response = exchange.get(timeout.toNanos(),
                        TimeUnit.NANOSECONDS);
                return new Response(response.statusCode(), response.uri(), response.headers().map(),
                        response.body());
            } catch (TimeoutException e) {
                subscriber.cancel();
                exchange.cancel(true);
                HttpTimeoutException failure =
                        new HttpTimeoutException("Leitura do corpo HTTP excedeu o tempo limite");
                failure.initCause(e);
                throw failure;
            } catch (ExecutionException e) {
                throw transportFailure(e.getCause());
            } catch (InterruptedException e) {
                subscriber.cancel();
                exchange.cancel(true);
                throw e;
            }
        }

        private static IOException transportFailure(Throwable cause) {
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof IOException failure) {
                return failure;
            }
            if (cause instanceof RuntimeException failure) {
                throw failure;
            }
            if (cause instanceof Error failure) {
                throw failure;
            }
            return new IOException("Falha no transporte HTTP", cause);
        }
    }

    private static final class LimitedBodySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {

        private final int maxBytes;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private volatile Flow.Subscription subscription;

        private LimitedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = Objects.requireNonNull(subscription);
            if (body.isDone()) {
                subscription.cancel();
                return;
            }
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                if (length > maxBytes - output.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new ResponseTooLargeException());
                    return;
                }
                byte[] bytes = new byte[length];
                buffer.get(bytes);
                output.writeBytes(bytes);
            }
        }

        @Override
        public void onError(Throwable failure) {
            body.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }

        private void cancel() {
            body.cancel(false);
            Flow.Subscription current = subscription;
            if (current != null) {
                current.cancel();
            }
        }
    }

    private static final class ResponseTooLargeException extends IOException {

        private ResponseTooLargeException() {
            super("Resposta da fonte excede o limite de tamanho");
        }
    }
}
