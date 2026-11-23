package br.com.qsoft.governance.agents.infrastructure.web;

/** `rowVersion` é alternativa ao header `If-Match` na publicação. */
public record PublishAgentFlowRequest(Long rowVersion) {
}
