package br.com.validadorlote.domain;

import java.net.URI;
import java.util.Objects;

/** Release estável do aplicativo que pode ser apresentada ao usuário. */
public record ApplicationRelease(String version, URI page) {

    public ApplicationRelease {
        version = Objects.requireNonNull(version);
        page = Objects.requireNonNull(page);
    }
}
