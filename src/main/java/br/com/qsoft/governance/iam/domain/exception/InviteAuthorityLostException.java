package br.com.qsoft.governance.iam.domain.exception;

/**
 * Lançada quando quem convidou perdeu a autoridade de conceder o papel/escopo do
 * convite antes do aceite (ADR-37 item 2). O aceite deve responder 409 e não
 * criar usuário algum.
 */
public class InviteAuthorityLostException extends RuntimeException {

    public InviteAuthorityLostException() {
        super("O convidante não pode mais conceder este papel.");
    }
}
