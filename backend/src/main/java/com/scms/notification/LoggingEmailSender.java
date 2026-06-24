package com.scms.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LoggingEmailSender — the default EmailSender. Active unless
 * `notifications.email.enabled=true` is explicitly set.
 *
 * Logs the email that would have been sent at INFO level instead of failing
 * the request or silently swallowing the notification. This is the correct
 * default for any environment without configured SMTP credentials — see
 * EmailSender for the full rationale.
 */
@Component
@ConditionalOnProperty(name = "notifications.email.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        log.info("[EMAIL-DISABLED] Would send to {} | Subject: {}", toEmail, subject);
    }
}
