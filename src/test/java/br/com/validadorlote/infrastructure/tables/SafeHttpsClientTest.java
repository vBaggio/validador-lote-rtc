package br.com.validadorlote.infrastructure.tables;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.io.IOException;
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
        assertThatThrownBy(() -> client.getUtf8(SVRS)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getUtf8(URI.create("http://dfe-portal.svrs.rs.gov.br/x")))
                .isInstanceOf(IllegalArgumentException.class);

        SafeHttpsClient tooLarge = new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"),
                Duration.ofSeconds(3), 4,
                (uri, timeout) -> response(200, uri, Map.of(), "12345"));
        assertThatThrownBy(() -> tooLarge.getUtf8(SVRS)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("limite");
    }

    @Test
    void identifiesTheSourceHostWhenTransportFails() {
        SafeHttpsClient client = client((uri, timeout) -> { throw new IOException("certificado"); });

        assertThatThrownBy(() -> client.getUtf8(SVRS)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dfe-portal.svrs.rs.gov.br");
    }

    private SafeHttpsClient client(HttpsTransport transport) {
        return new SafeHttpsClient(Set.of("dfe-portal.svrs.rs.gov.br"), Duration.ofSeconds(3), 32, transport);
    }

    private HttpsTransport.Response response(int status, URI uri, Map<String, List<String>> headers,
            String body) {
        return new HttpsTransport.Response(status, uri, headers, body.getBytes(StandardCharsets.UTF_8));
    }
}
