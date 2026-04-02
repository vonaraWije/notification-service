package com.example.notification.messaging.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.notification.entity.NotificationLog;
import com.example.notification.messaging.config.RabbitMQConfig;
import com.example.notification.model.EmailMessage;
import com.example.notification.model.EmailRequest;
import com.example.notification.repository.NotificationLogRepository;
import com.example.notification.service.FailoverEmailService;

import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "notification.messaging.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ExternalEmailListener {

    private final FailoverEmailService failoverEmailService;
    private final NotificationLogRepository notificationLogRepository;

    public ExternalEmailListener(FailoverEmailService failoverEmailService,
            NotificationLogRepository notificationLogRepository) {
        this.failoverEmailService = failoverEmailService;
        this.notificationLogRepository = notificationLogRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.EXTERNAL_EMAIL_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void receive(EmailMessage message) {
        try {
            // Notify that a message was received (console + log)
            log.info("Received external email message for recipient={}", message.getTo());
            System.out.println("[notification-service] Email message received for: " + message.getTo());

            EmailRequest req = new EmailRequest();
            req.setTo(message.getTo());
            req.setSubject(message.getSubject());
            req.setHtml(message.getBody());

            String provider = "UNKNOWN";
            String status = "FAILED";
            String error = null;
            try {
                var result = failoverEmailService.send(req);
                provider = result.getProvider();
                status = "SUCCESS";
                // Notify successful send (console + log)
                log.info("Email sent successfully to {} via {}", message.getTo(), provider);
                System.out.println("[notification-service] Email sent successfully to: " + message.getTo() + " via " + provider);
            } catch (Exception ex) {
                error = ex.getMessage();
                log.error("Failed to send external email to {}: {}", message.getTo(), ex.getMessage(), ex);
            }

            NotificationLog logEntry = new NotificationLog();
            logEntry.setRecipient(message.getTo());
            logEntry.setType("EXTERNAL_EMAIL");
            logEntry.setProvider(provider);
            logEntry.setTemplateType(null);
            logEntry.setTemplateId(null);
            logEntry.setStatus(status);
            logEntry.setError(error);
            notificationLogRepository.save(logEntry);

        } catch (Exception e) {
            log.error("Unhandled error processing external email message: {}", e.getMessage(), e);
        }
    }
}
