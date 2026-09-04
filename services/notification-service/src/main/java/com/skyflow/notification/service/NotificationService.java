package com.skyflow.notification.service;

import com.skyflow.common.event.SkyflowEvent;
import com.skyflow.notification.domain.NotificationLog;
import com.skyflow.notification.repository.NotificationLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

/**
 * Renders and sends one email per event.
 *
 * <p>Transport failures are rethrown so the listener can let RabbitMQ retry and eventually
 * dead-letter the message; content problems (a missing recipient) are not retryable and are
 * dropped with a log line instead.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailContentFactory contentFactory;
    private final NotificationLogRepository logs;
    private final String fromAddress;
    private final boolean deliveryEnabled;

    public NotificationService(JavaMailSender mailSender, TemplateEngine templateEngine,
                               EmailContentFactory contentFactory, NotificationLogRepository logs,
                               @Value("${skyflow.email.from:noreply@skyflow.com}") String fromAddress,
                               @Value("${skyflow.email.enabled:true}") boolean deliveryEnabled) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.contentFactory = contentFactory;
        this.logs = logs;
        this.fromAddress = fromAddress;
        this.deliveryEnabled = deliveryEnabled;
    }

    @Transactional
    public void send(SkyflowEvent event) {
        String recipient = event.recipientEmail();
        if (recipient == null || recipient.isBlank()) {
            log.warn("Dropping {} event with no recipient", event.type());
            return;
        }

        EmailContent content = contentFactory.create(event);

        if (!deliveryEnabled) {
            // Local development and CI: render and record, but do not hand anything to an SMTP
            // server that is not there.
            log.info("Email delivery disabled - would have sent \"{}\" to {}",
                    content.subject(), recipient);
            logs.save(NotificationLog.sent(event.type().name(), recipient, content.subject(),
                    event.string("bookingReference")));
            return;
        }

        try {
            mailSender.send(buildMessage(recipient, content));
            logs.save(NotificationLog.sent(event.type().name(), recipient, content.subject(),
                    event.string("bookingReference")));
            log.info("Sent {} email to {}", event.type(), recipient);
        } catch (MailException | MessagingException ex) {
            logs.save(NotificationLog.failed(event.type().name(), recipient, content.subject(),
                    event.string("bookingReference"), ex.getMessage()));
            throw new EmailDeliveryException("Could not send " + event.type() + " email", ex);
        }
    }

    private MimeMessage buildMessage(String recipient, EmailContent content)
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(fromAddress);
        helper.setTo(recipient);
        helper.setSubject(content.subject());

        Context context = new Context();
        context.setVariable("content", content);
        // Both alternatives, so text-only clients still get something readable.
        helper.setText(content.toPlainText(), templateEngine.process("email/notification", context));
        return message;
    }

    /** Retryable: the listener lets it propagate so RabbitMQ redelivers. */
    public static class EmailDeliveryException extends RuntimeException {
        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
