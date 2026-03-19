package rs.raf.banka2_bek.notification.template;

import org.springframework.stereotype.Component;

@Component
public class AccountCreatedConfirmationEmailTemplate {

    public String buildSubject() {
        return "Your new Banka 2 account is ready";
    }

    public String buildBody(String firstName, String accountNumber, String accountType) {
        String greeting = (firstName != null && !firstName.isBlank()) ? firstName : "there";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Account successfully created</title>
                </head>
                <body style="margin:0;padding:0;background-color:transparent;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="background-color:transparent;padding:32px 0;">
                    <tr>
                        <td align="center">
                            <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="max-width:520px;background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 18px 45px rgba(15,23,42,0.32);border:1px solid #e5e7eb;">
                                <tr>
                                    <td style="background:linear-gradient(135deg,#0369a1,#0c4a6e);padding:18px 24px;color:#e5e7eb;text-align:center;">
                                        <h1 style="margin:0;font-size:20px;font-weight:600;letter-spacing:0.02em;">Your account with Banka 2 awaits</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:24px;text-align:center;">
                                        <p style="margin:0 0 16px 0;font-size:14px;color:#4b5563;">Hi %s,</p>
                                        <p style="margin:0 0 20px 0;font-size:14px;color:#4b5563;">
                                            Great news — your new bank account has been successfully opened and is ready to use.
                                        </p>
                                        <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto 24px auto;background-color:#f0f9ff;border-radius:12px;border:1px solid #bae6fd;padding:0;overflow:hidden;width:100%%;">
                                            <tr>
                                                <td style="padding:14px 20px;border-bottom:1px solid #bae6fd;">
                                                    <table role="presentation" cellpadding="0" cellspacing="0" width="100%%">
                                                        <tr>
                                                            <td style="font-size:12px;color:#6b7280;text-align:left;">Account type</td>
                                                            <td style="font-size:13px;font-weight:600;color:#0c4a6e;text-align:right;">%s</td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td style="padding:14px 20px;">
                                                    <table role="presentation" cellpadding="0" cellspacing="0" width="100%%">
                                                        <tr>
                                                            <td style="font-size:12px;color:#6b7280;text-align:left;">Account number</td>
                                                            <td style="font-size:13px;font-weight:600;color:#0c4a6e;text-align:right;letter-spacing:0.05em;">%s</td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin:0 0 0 0;font-size:12px;color:#9ca3af;">
                                            Please keep your account number safe. If you did not request this account, contact our support team immediately.
                                        </p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:16px 24px;border-top:1px solid #e5e7eb;background-color:#f9fafb;">
                                        <p style="margin:0;font-size:11px;color:#9ca3af;text-align:center;">
                                            This is an automated message from Banka 2. Please do not reply to this email.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
                </body>
                </html>
                """.formatted(greeting, accountType, accountNumber);
    }
}