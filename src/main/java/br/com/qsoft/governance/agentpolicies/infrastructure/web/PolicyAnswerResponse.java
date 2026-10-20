package br.com.qsoft.governance.agentpolicies.infrastructure.web;

import br.com.qsoft.governance.agentpolicies.application.PolicyAnswer;
import java.util.List;

/** Resposta do agente (SPEC-05 §4). O recibo é nulo quando não houve LLM. */
public record PolicyAnswerResponse(
    String resposta,
    List<PolicySourceResponse> fontes,
    List<String> ferramentasUsadas,
    AgentReceiptResponse recibo
) {

    public static PolicyAnswerResponse from(PolicyAnswer answer) {
        return new PolicyAnswerResponse(
            answer.answer(),
            answer.sources().stream()
                .map(hit -> new PolicySourceResponse(hit.documentId(), hit.title(),
                    hit.versionNumber(), hit.text()))
                .toList(),
            answer.toolsUsed(),
            answer.completion() == null ? null : AgentReceiptResponse.from(answer.completion())
        );
    }
}
