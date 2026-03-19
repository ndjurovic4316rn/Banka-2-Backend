package rs.raf.banka2_bek.notification.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.employee.event.EmployeeAccountCreatedEvent;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmployeeAccountCreatedListenerTest {

    @Mock
    private MailNotificationService mailNotificationService;

    @InjectMocks
    private EmployeeAccountCreatedListener listener;

    @Test
    void onEmployeeAccountCreated_callsSendActivationMailWithCorrectArgs() {
        EmployeeAccountCreatedEvent event = new EmployeeAccountCreatedEvent(
                this, "zaposleni@test.com", "Jovan", "token123"
        );

        listener.onEmployeeAccountCreated(event);

        verify(mailNotificationService).sendActivationMail("zaposleni@test.com", "Jovan", "token123");
    }
}
