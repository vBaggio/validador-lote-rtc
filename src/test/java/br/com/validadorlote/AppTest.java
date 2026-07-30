package br.com.validadorlote;

import br.com.validadorlote.infrastructure.tables.SvrsTableUpdater;
import br.com.validadorlote.infrastructure.update.ArtifactUpdateAction;
import br.com.validadorlote.infrastructure.xml.ArtifactId;
import br.com.validadorlote.infrastructure.xml.SvrsSchemaUpdater;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

    @Test
    void wiresTheTwoActiveSourcesToStableChannelIds() {
        SvrsSchemaUpdater schemas = new SvrsSchemaUpdater(null, null, null, null, "010e_v1.02");
        SvrsTableUpdater tables = new SvrsTableUpdater(null, null, null, null);

        List<ArtifactUpdateAction> actions = App.updateActions(schemas, tables);

        assertThat(actions).extracting(ArtifactUpdateAction::artifact)
                .containsExactly(ArtifactId.NFE_SCHEMAS, ArtifactId.FISCAL_TABLES);
        assertThat(actions).extracting(ArtifactUpdateAction::channelId)
                .containsExactly("svrs-schemas-documents-v1", "svrs-fiscal-table-v1");
    }
}
