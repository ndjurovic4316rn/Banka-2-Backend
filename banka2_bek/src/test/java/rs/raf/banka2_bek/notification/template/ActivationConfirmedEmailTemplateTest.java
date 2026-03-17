package rs.raf.banka2_bek.notification.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivationConfirmedEmailTemplateTest {

    private ActivationConfirmedEmailTemplate template;

    @BeforeEach
    void setUp() {
        template = new ActivationConfirmedEmailTemplate();
    }

    @Test
    void buildSubject_returnsExpectedString() {
        assertThat(template.buildSubject()).isEqualTo("Banka 2 account activated");
    }

    @Test
    void buildBody_containsFirstName() {
        String body = template.buildBody("Jovana");
        assertThat(body).contains("Jovana");
    }

    @Test
    void buildBody_containsActivationSuccessMessage() {
        String body = template.buildBody("Jovana");
        assertThat(body).contains("activated successfully");
    }

    @Test
    void buildBody_nullFirstName_usesDefaultGreeting() {
        String body = template.buildBody(null);
        assertThat(body).contains("Hi there,");
    }

    @Test
    void buildBody_blankFirstName_usesDefaultGreeting() {
        String body = template.buildBody("   ");
        assertThat(body).contains("Hi there,");
    }
}
