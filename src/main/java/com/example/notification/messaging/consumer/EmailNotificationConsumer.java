package com.example.notification.messaging.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.notification.messaging.config.RabbitMQConfig;
import com.example.notification.model.NotificationRequest;
import com.example.notification.service.NotificationService;

@Component
@ConditionalOnProperty(name = "notification.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class EmailNotificationConsumer {

	private final NotificationService notificationService;

	public EmailNotificationConsumer(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@RabbitListener(
			queues = RabbitMQConfig.EMAIL_QUEUE,
			containerFactory = "rabbitListenerContainerFactory")
	public void consume(NotificationRequest request) {
		notificationService.processEmail(request);
	}
}

