package br.com.validadorlote.infrastructure.tables;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Fronteira pequena para que a política HTTP seja testada sem abrir rede. */
@FunctionalInterface
public interface HttpsTransport {

    Response get(URI uri, Duration timeout) throws IOException, InterruptedException;

    record Response(int statusCode, URI uri, Map<String, List<String>> headers, byte[] body) {
        public Response {
            headers = Map.copyOf(headers);
            body = body.clone();
        }

        public String firstHeader(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst().orElse(null);
        }
    }
}
