package com.scms.notification;

/**
 * EmailSender — abstraction over "actually deliver this email".
 *
 * Two implementations are wired by Spring profile/property:
 *   - SmtpEmailSender   — real delivery via JavaMailSender. Active when
 *                         `notifications.email.enabled=true` and SMTP_*
 *                         environment variables are configured.
 *   - LoggingEmailSender — default. Logs what WOULD have been sent. This
 *                         lets the notification feature ship correctly and
 *                         safely with zero SMTP credentials configured
 *                         (the common case for a student/demo deployment),
 *                         while remaining a one-property change away from
 *                         real delivery once a mail provider is configured.
 */
public interface EmailSender {
    void send(String toEmail, String subject, String htmlBody);
}
