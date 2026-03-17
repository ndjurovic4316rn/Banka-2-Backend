package rs.raf.banka2_bek.notification.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.auth.model.PasswordResetRequestedEvent;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetRequestedListenerTest {

    @Mock
    private MailNotificationService mailNotificationService;

    @InjectMocks
    private PasswordResetRequestedListener listener;

    @Test
    void onPasswordResetRequested_callsSendPasswordResetMailWithCorrectArgs() {
        PasswordResetRequestedEvent event = new PasswordResetRequestedEvent("korisnik@test.com", "resetToken999");

        listener.onPasswordResetRequested(event);

        verify(mailNotificationService).sendPasswordResetMail("korisnik@test.com", "resetToken999");
    }
}
