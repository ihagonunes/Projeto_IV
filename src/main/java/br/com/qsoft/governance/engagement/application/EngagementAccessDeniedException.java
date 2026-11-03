package br.com.qsoft.governance.engagement.application;

/** O recorte pedido está fora do escopo de visibilidade do ator. */
public class EngagementAccessDeniedException extends RuntimeException {

    public EngagementAccessDeniedException() {
        super("Acesso negado ao recorte de engajamento solicitado.");
    }
}
