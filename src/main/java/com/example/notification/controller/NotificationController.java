package com.example.notification.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.notification.entity.NotificationLog;
import com.example.notification.messaging.config.RabbitMQConfig;
import com.example.notification.messaging.util.RabbitQueueInspector;
import com.example.notification.model.NotificationRequest;
import com.example.notification.repository.NotificationLogRepository;
import com.example.notification.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService notificationService;
	private final RabbitTemplate rabbitTemplate;
	private final NotificationLogRepository notificationLogRepository;
    private final RabbitQueueInspector rabbitQueueInspector;

	public NotificationController(
			NotificationService notificationService,
			RabbitTemplate rabbitTemplate,
			NotificationLogRepository notificationLogRepository,
			RabbitQueueInspector rabbitQueueInspector) {
		this.notificationService = notificationService;
		this.rabbitTemplate = rabbitTemplate;
		this.notificationLogRepository = notificationLogRepository;
		this.rabbitQueueInspector = rabbitQueueInspector;
	}

	/**
	 * Send email synchronously (immediate processing)
	 * 
	 * POST /api/notifications/send
	 * 
	 * Example payload:
	 * {
	 *   "type": "WELCOME",
	 *   "channel": "EMAIL",
	 *   "recipient": "user@example.com",
	 *   "subject": "Welcome!",
	 *   "templateType": "INTERNAL",
	 *   "template": "Hello <span th:text=\"${name}\"></span>!",
	 *   "data": {"name": "John"}
	 * }
	 */
	@PostMapping("/send")
	public ResponseEntity<Map<String, String>> sendNotification(@RequestBody NotificationRequest request) {
		try {
			var sendResult = notificationService.processEmail(request);
			Map<String, String> body = new HashMap<>();
			body.put("status", "SUCCESS");
			body.put("message", "Email sent successfully");
			body.put("provider", sendResult.getProvider());
			body.put("recipient", request.getRecipient());
			if ("GMAIL".equals(sendResult.getProvider()) && sendResult.getResendFailureReason() != null
					&& !sendResult.getResendFailureReason().isBlank()) {
				body.put("resendFailureReason", sendResult.getResendFailureReason());
			}
			return ResponseEntity.ok(body);
		} catch (Exception ex) {
			return ResponseEntity
					.status(500)
					.body(Map.of(
							"status", "FAILED",
							"message", "Email delivery failed: " + ex.getMessage(),
							"recipient", request.getRecipient()));
		}
	}

	/**
	 * Queue notification for asynchronous processing via RabbitMQ
	 * 
	 * POST /api/notifications/queue
	 * 
	 * Same payload as /send but processed asynchronously through message queue
	 */
	@PostMapping("/queue")
	public ResponseEntity<Map<String, String>> queueNotification(@RequestBody NotificationRequest request) {
		try {
			rabbitTemplate.convertAndSend(
					RabbitMQConfig.NOTIFICATION_EXCHANGE,
					"notification.email.send",
					request);
			return ResponseEntity.accepted().body(Map.of(
					"status", "QUEUED",
						"message", "Email received successfully",
					"recipient", request.getRecipient()));
		} catch (Exception ex) {
			return ResponseEntity
					.status(500)
					.body(Map.of(
							"status", "FAILED",
							"message", "Failed to queue notification: " + ex.getMessage(),
							"recipient", request.getRecipient()));
		}
	}

	/**
	 * Get all notification logs
	 * 
	 * GET /api/notifications/logs
	 */
	@GetMapping("/logs")
	public ResponseEntity<List<NotificationLog>> getNotificationLogs() {
		List<NotificationLog> logs = notificationLogRepository.findAll();
		return ResponseEntity.ok(logs);
	}

	/**
	 * Health check endpoint
	 * 
	 * GET /api/notifications/health
	 */
	@GetMapping("/health")
	public ResponseEntity<Map<String, String>> health() {
		return ResponseEntity.ok(Map.of(
				"status", "UP",
				"service", "notification-service"));
	}

	/**
	 * Get ready message count for the email queue (non-destructive)
	 *
	 * GET /api/notifications/queue/count
	 */
	@GetMapping("/queue/count")
	public ResponseEntity<Map<String, Object>> getQueueCount(
			@RequestParam(value = "name", required = false) String queueName) {
		String queue = (queueName == null || queueName.isBlank()) ? RabbitMQConfig.EMAIL_QUEUE : queueName;
		int ready = rabbitQueueInspector.getReadyMessageCount(queue);
		return ResponseEntity.ok(Map.of(
				"queue", queue,
				"ready", ready
		));
	}
}
