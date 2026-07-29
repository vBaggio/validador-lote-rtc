package br.com.validadorlote.infrastructure.tables;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** HTTPS restrito para artefatos normativos: sem hosts implícitos ou redirects abertos. */
public final class SafeHttpsClient {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    public static final int SVRS_MAX_BYTES = 6 * 1024 * 1024;
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
                SVRS_MAX_BYTES, new JdkTransport());
    }

    /** Retorna sempre UTF-8; a página da fonte é pública e não dita o charset ao aplicativo. */
    public String getUtf8(URI initial) {
        URI current = validate(initial);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpsTransport.Response response;
            try {
                response = transport.get(current, timeout);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Consulta HTTPS interrompida", e);
            } catch (IOException e) {
                throw new IllegalStateException("Não foi possível consultar a fonte HTTPS", e);
            }
            validate(response.uri());
            if (response.body().length > maxBytes) {
                throw new IllegalStateException("Resposta da fonte excede o limite de tamanho");
            }
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                String location = response.firstHeader("Location");
                if (location == null || location.isBlank()) {
                    throw new IllegalStateException("Redirecionamento HTTPS sem destino");
                }
                current = validate(current.resolve(location));
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Fonte HTTPS respondeu HTTP " + response.statusCode());
            }
            return new String(response.body(), StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("A fonte excedeu o limite de redirecionamentos");
    }

    private URI validate(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || !allowedHosts.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Origem HTTPS não permitida: " + uri);
        }
        return uri;
    }

    /** Implementação de produção; redirects ficam deliberadamente desligados no JDK. */
    private static final class JdkTransport implements HttpsTransport {
        private final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();

        @Override
        public Response get(URI uri, Duration timeout) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                return new Response(response.statusCode(), response.uri(), response.headers().map(),
                        readLimited(body, SVRS_MAX_BYTES));
            }
        }

        private byte[] readLimited(InputStream input, int limit) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > limit) {
                    throw new IOException("Resposta excede o limite de tamanho");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
