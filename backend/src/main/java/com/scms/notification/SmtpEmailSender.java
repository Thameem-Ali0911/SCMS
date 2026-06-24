package com.scms.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

/**
 * SmtpEmailSender — real email delivery via Spring's JavaMailSender.
 *
 * Activated by setting `notifications.email.enabled=true` and configuring
 * standard `spring.mail.*` properties (host, port, username, password) —
 * see application.properties / README.md "Configuring email notifications".
 *
 * Delivery failures are logged and swallowed rather than propagated: a
 * down SMTP server must never fail the underlying complaint/status-change
 * operation that triggered the notification (the operation already
 * committed before this listener runs — see ComplaintService and
 * NotificationListener's @TransactionalEventListener(phase = AFTER_COMMIT)).
 */
@Component
@ConditionalOnProperty(name = "notifications.email.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${notifications.email.from:no-reply@scms.local}")
    private String fromAddress;

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }
}
