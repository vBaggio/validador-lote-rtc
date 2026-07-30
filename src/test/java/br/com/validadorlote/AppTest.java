package br.com.validadorlote;

import br.com.validadorlote.infrastructure.tables.SvrsTableUpdater;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateAction;
import br.com.validadorlote.infrastructure.update.ArtifactCheckResult;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

    @Test
    void keepsCuratedSchemasExplicitlyDisabledUntilRealBootstrapExists() {
        SvrsTableUpdater tables = new SvrsTableUpdater(null, null, null, null);

        List<ArtifactUpdateAction> actions = App.updateActions(Optional.empty(),
                "curated-schemas-disabled-v1", tables);
        ArtifactCheckResult schemas = actions.getFirst().check();

        assertThat(actions).extracting(ArtifactUpdateAction::artifact)
                .containsExactly(ArtifactId.NFE_SCHEMAS, ArtifactId.FISCAL_TABLES);
        assertThat(actions).extracting(ArtifactUpdateAction::channelId)
                .containsExactly("curated-schemas-disabled-v1", "svrs-fiscal-table-v1");
        assertThat(schemas.status()).isEqualTo(ArtifactCheckResult.Status.UP_TO_DATE);
        assertThat(schemas.detail()).containsIgnoringCase("desabilitada")
                .containsIgnoringCase("base embarcada");
    }
}
