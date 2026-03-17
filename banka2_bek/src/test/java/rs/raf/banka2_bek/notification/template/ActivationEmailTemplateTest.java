package rs.raf.banka2_bek.notification.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivationEmailTemplateTest {

    private ActivationEmailTemplate template;

    @BeforeEach
    void setUp() {
        template = new ActivationEmailTemplate();
    }

    @Test
    void buildSubject_returnsExpectedString() {
        assertThat(template.buildSubject()).isEqualTo("Activate your Banka 2 account");
    }

    @Test
    void buildBody_containsActivationLink() {
        String link = "http://localhost:3000/activate-account?token=abc123";
        String body = template.buildBody(link, "Marko");
        assertThat(body).contains(link);
    }

    @Test
    void buildBody_containsFirstName() {
        String body = template.buildBody("http://example.com/activate", "Marko");
        assertThat(body).contains("Marko");
    }

    @Test
    void buildBody_containsExpiryInfo() {
        String body = template.buildBody("http://example.com/activate", "Ana");
        assertThat(body).contains("24 hours");
    }

    @Test
    void buildBody_nullFirstName_usesDefaultGreeting() {
        String body = template.buildBody("http://example.com/activate", null);
        assertThat(body).contains("Hi there,");
    }

    @Test
    void buildBody_blankFirstName_usesDefaultGreeting() {
        String body = template.buildBody("http://example.com/activate", "   ");
        assertThat(body).contains("Hi there,");
    }
}
