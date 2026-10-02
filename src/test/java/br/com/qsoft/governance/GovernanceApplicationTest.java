package br.com.qsoft.governance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class GovernanceApplicationTest {

    @Test
    void shouldInstantiateAndRunMain() {
        GovernanceApplication application = new GovernanceApplication();
        assertThat(application).isNotNull();

        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            GovernanceApplication.main(new String[]{"--option"});
            mocked.verify(() -> SpringApplication.run(GovernanceApplication.class, new String[]{"--option"}));
        }
    }
}
