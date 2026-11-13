package br.com.qsoft.governance.iam.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleAssignmentNotFoundExceptionTest {

    @Test
    void usesGenericMessageWithoutRevealingAssignment() {
        assertThat(new RoleAssignmentNotFoundException()).hasMessage("Concessão não encontrada.");
    }
}
