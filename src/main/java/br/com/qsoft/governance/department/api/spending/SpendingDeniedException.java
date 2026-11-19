package br.com.qsoft.governance.department.api.spending;

public class SpendingDeniedException extends RuntimeException {
    private final String code;
    public SpendingDeniedException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String code() { return code; }
}
