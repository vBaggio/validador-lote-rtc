package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;

/** Ação de aquisição que nunca recebe documentos do usuário. */
public interface ArtifactUpdateAction {
    ArtifactId artifact();
    boolean updateIfNew();
}
