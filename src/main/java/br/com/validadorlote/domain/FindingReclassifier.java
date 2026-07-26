package br.com.validadorlote.domain;

import java.util.List;

/** Aplica o modo pré-emissão: assinatura ausente vira INFO (ligado) ou REJECTION (desligado). */
public final class FindingReclassifier {

    private FindingReclassifier() {}

    public static List<Finding> reclassify(List<Finding> findings, boolean preEmissionMode) {
        Severity signatureSeverity = preEmissionMode ? Severity.INFO : Severity.REJECTION;
        return findings.stream()
                .map(f -> f.kind() == FindingKind.SIGNATURE_MISSING ? f.withSeverity(signatureSeverity) : f)
                .toList();
    }
}
