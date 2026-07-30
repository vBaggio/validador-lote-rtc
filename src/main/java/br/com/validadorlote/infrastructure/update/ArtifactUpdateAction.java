package br.com.validadorlote.infrastructure.update;

import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.ArtifactManifest;

/** Ação de aquisição que nunca recebe documentos do usuário. */
public interface ArtifactUpdateAction {
    ArtifactId artifact();
    String channelId();
    ArtifactCheckResult check();
    ArtifactManifest apply(ArtifactUpdateCandidate candidate);
}
