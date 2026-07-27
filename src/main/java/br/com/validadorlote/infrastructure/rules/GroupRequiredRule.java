package br.com.validadorlote.infrastructure.rules;

import br.com.validadorlote.domain.ReferencedNote;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Rejeição 1115 (UB12-10): o grupo IBS/CBS é obrigatório em cada item.
 *
 * <p>É a regra-mãe da virada: o XSD declara o grupo como opcional, então um documento sem ele
 * passa na validação estrutural e é recusado pela SEFAZ. As datas são escalonadas por regime.
 *
 * <p>Observa o <b>invólucro</b> {@code det/imposto/IBSCBS}, exatamente a tag que a UB12-10 cita —
 * não o {@code gIBSCBS} de dentro dele, que é território da 1021/1022 (D-027).
 *
 * <p>As duas exceções da NT são consultadas só quando a regra está prestes a acusar: a Exceção 1
 * (devolução/complementar de nota anterior a 2026) é decidida offline pelo {@code AAMM} da chave
 * referenciada; a Exceção 2 (combustível monofásico) depende de tabela que não temos e por isso
 * vira não avaliado (D-028, D-029).
 */
public final class GroupRequiredRule implements RejectionRule {

    /** Regime Normal: exigência em produção. */
    private static final LocalDate VIGENCIA_CRT3 = LocalDate.of(2026, 8, 3);
    /** Simples Nacional, excesso de sublimite e MEI. */
    private static final LocalDate VIGENCIA_SIMPLES = LocalDate.of(2027, 1, 4);
    /** CRTs previstos na NT: 1 e 2 (Simples), 3 (Regime Normal), 4 (MEI). */
    private static final Set<String> CRT_CONHECIDOS = Set.of("1", "2", "3", "4");
    /** Finalidades alcançadas pela Exceção 1: complementar e devolução/retorno. */
    private static final Set<String> FINALIDADES_DA_EXCECAO = Set.of("2", "4");
    /** A partir desta competência a nota referenciada deixa de disparar a Exceção 1. */
    private static final YearMonth CORTE_EXCECAO_1 = YearMonth.of(2026, 1);

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter COMPETENCIA_BR = DateTimeFormatter.ofPattern("MM/yyyy");

    private static final String MENSAGEM_OFICIAL = "Rejeição: IBS/CBS não informado";

    @Override public String rejectionCode() { return "1115"; }

    @Override public String ruleId() { return "UB12-10"; }

    @Override
    public RuleOutcome evaluate(RuleContext ctx) {
        String crt = normalizado(ctx.document().crt());
        if (crt == null) {
            return new RuleOutcome.NaoAvaliado(
                    "CRT do emitente não encontrado no documento: não dá para saber se a "
                    + "exigência de IBS/CBS já vale para ele.");
        }
        if (!CRT_CONHECIDOS.contains(crt)) {
            return new RuleOutcome.NaoAvaliado("CRT " + crt + " não é um dos previstos na NT "
                    + "(1, 2, 3 ou 4): sem saber o regime do emitente não dá para saber a partir "
                    + "de quando a exigência vale para ele.");
        }
        if (ctx.operationDate() == null) {
            return new RuleOutcome.NaoAvaliado("Data de emissão não encontrada no documento.");
        }
        LocalDate vigencia = "3".equals(crt) ? VIGENCIA_CRT3 : VIGENCIA_SIMPLES;
        if (ctx.operationDate().isBefore(vigencia)) {
            return new RuleOutcome.NaoAplicavel(String.format(
                    "Para CRT=%s a exigência do grupo IBS/CBS vigora a partir de %s.",
                    crt, DATA_BR.format(vigencia)));
        }
        if (ctx.item().hasIbsCbsGroup()) {
            return new RuleOutcome.Conforme();
        }
        // Só aqui as exceções importam: elas afastam a acusação, não a conformidade.
        RuleOutcome excecao = excecaoDeDevolucaoOuComplementar(ctx);
        if (excecao != null) {
            return excecao;
        }
        if (ctx.item().cProdANP() != null) {
            return new RuleOutcome.NaoAvaliado("Exceção 2 da UB12-10: o item informa cProdANP "
                    + ctx.item().cProdANP() + " e a exigência não se aplica a produto presente na "
                    + "Tabela de Combustíveis Sujeitos à Tributação Monofásica, que não está "
                    + "embarcada nesta versão.");
        }
        return new RuleOutcome.Rejeitado(rejectionCode(), ruleId(), MENSAGEM_OFICIAL);
    }

    /**
     * Exceção 1 da UB12-10: devolução ({@code finNFe=4}) ou complementar ({@code finNFe=2}) que
     * referencia NF-e emitida antes de 2026.
     *
     * <p>Duas leituras deliberadamente amplas, escolhidas e não herdadas por descuido — quem
     * revisar precisa saber que foram decisão (D-028):
     * <ol>
     *   <li>a oração "que referencia NFe com data de emissão anterior a 2026" pode
     *   gramaticalmente qualificar só a complementar ou as duas finalidades; aqui vale para as
     *   duas, porque a leitura oposta transformaria toda devolução rotineira de nota de 2025 em
     *   acusação;</li>
     *   <li>ao rigor da letra, só {@code refNFe} e {@code refNFeSig} são NF-e — {@code refNF} e
     *   {@code refNFP} são documentos em papel. Qualquer referência <b>datável</b> e anterior a
     *   2026 aciona a exceção assim mesmo. É a direção que não acusa, coerente com a regra do
     *   projeto de que falso negativo é declarado e falso positivo não se admite.</li>
     * </ol>
     *
     * @return o desfecho quando a exceção resolve o caso, ou {@code null} para a regra seguir o
     *         curso normal — inclusive quando não há {@code NFref} alguma, porque aí falta à
     *         exceção a referência que ela própria exige.
     */
    private RuleOutcome excecaoDeDevolucaoOuComplementar(RuleContext ctx) {
        String finNFe = normalizado(ctx.document().finNFe());
        if (finNFe == null || !FINALIDADES_DA_EXCECAO.contains(finNFe)) {
            return null;
        }
        Set<String> semData = new LinkedHashSet<>();
        for (ReferencedNote referencia : ctx.document().references()) {
            if (referencia.issuedAt() == null) {
                semData.add(referencia.form());
            } else if (referencia.issuedAt().isBefore(CORTE_EXCECAO_1)) {
                return new RuleOutcome.NaoAplicavel(String.format(
                        "Exceção 1 da UB12-10: %s referencia nota emitida em %s, anterior a 2026.",
                        finalidade(finNFe), COMPETENCIA_BR.format(referencia.issuedAt())));
            }
        }
        if (!semData.isEmpty()) {
            return new RuleOutcome.NaoAvaliado(String.format(
                    "Exceção 1 da UB12-10: %s referencia documento por %s, forma que não traz a "
                    + "data de emissão — não dá para saber se a nota referenciada é anterior "
                    + "a 2026.", finalidade(finNFe), String.join(", ", semData)));
        }
        return null;
    }

    private String finalidade(String finNFe) {
        return "2".equals(finNFe) ? "NF-e complementar" : "NF-e de devolução/retorno";
    }

    /** Normaliza um código do XML: espaço em volta não pode mudar o veredito. */
    private String normalizado(String valor) {
        if (valor == null) return null;
        String limpo = valor.trim();
        return limpo.isBlank() ? null : limpo;
    }
}
