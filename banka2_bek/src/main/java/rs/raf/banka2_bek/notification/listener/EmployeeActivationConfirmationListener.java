package rs.raf.banka2_bek.notification.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.employee.event.EmployeeActivationConfirmationEvent;
import rs.raf.banka2_bek.notification.service.MailNotificationService;

@Component
public class EmployeeActivationConfirmationListener {

    private final MailNotificationService mailNotificationService;

    public EmployeeActivationConfirmationListener(MailNotificationService mailNotificationService) {
        this.mailNotificationService = mailNotificationService;
    }

    @Async
    @EventListener
    public void onEmployeeActivationConfirmationEvent(EmployeeActivationConfirmationEvent event) {
        mailNotificationService.sendActivationConfirmationMail(
                event.getEmail(),
                event.getFirstName()
        );
    }
}
