package com.example.notification.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.notification.entity.NotificationLog;
import com.example.notification.model.BatchEmailRequest;
import com.example.notification.model.BatchEmailResponse;
import com.example.notification.model.BatchNotificationRequest;
import com.example.notification.repository.NotificationLogRepository;
import com.example.notification.service.BatchEmailService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/notifications/batch")
public class BatchNotificationController {

	private final BatchEmailService batchEmailService;
	private final NotificationLogRepository notificationLogRepository;

	public BatchNotificationController(
			BatchEmailService batchEmailService,
			NotificationLogRepository notificationLogRepository) {
		this.batchEmailService = batchEmailService;
		this.notificationLogRepository = notificationLogRepository;
	}

	/**
	 * Send batch notifications via async processing
	 * 
	 * POST /api/notifications/batch/send
	 * 
	 * Example payload:
	 * {
	 *   "notifications": [
	 *     {
	 *       "type": "WELCOME",
	 *       "channel": "EMAIL",
	 *       "recipient": "user1@example.com",
	 *       "subject": "Welcome!",
	 *       "templateType": "RESEND",
	 *       "templateId": "order-confirmation",
	 *       "data": {"PRODUCT": "Item1", "PRICE": 100}
	 *     },
	 *     {
	 *       "type": "WELCOME",
	 *       "channel": "EMAIL",
	 *       "recipient": "user2@example.com",
	 *       "subject": "Welcome!",
	 *       "templateType": "INTERNAL",
	 *       "template": "<p>Hello {{name}}</p>",
	 *       "data": {"name": "Jane"}
	 *     }
	 *   ]
	 * }
	 */
	@PostMapping("/send")
	public ResponseEntity<Map<String, Object>> sendBatchNotifications(
			@RequestBody BatchNotificationRequest request) {
		try {
			List<BatchEmailRequest.EmailItem> emailItems = new ArrayList<>();

			for (BatchNotificationRequest.NotificationItem notification : request.getNotifications()) {
				BatchEmailRequest.EmailItem item = new BatchEmailRequest.EmailItem();
				item.setTo(notification.getRecipient());
				item.setSubject(notification.getSubject());
				item.setTemplateType(notification.getTemplateType());
				item.setTemplateId(notification.getTemplateId());
				item.setTemplate(notification.getTemplate());
				item.setData(notification.getData());
				emailItems.add(item);
			}

			BatchEmailRequest batchRequest = new BatchEmailRequest();
			batchRequest.setEmails(emailItems);

			BatchEmailResponse response = batchEmailService.processBatch(batchRequest);

			// Save logs
			for (int i = 0; i < request.getNotifications().size(); i++) {
				BatchNotificationRequest.NotificationItem notification = request
						.getNotifications()
						.get(i);
				saveLog(notification, i < response.getData().size() ? response.getData()
						.get(i) : null);
			}

			return ResponseEntity.ok(Map.of(
					"status", "SUCCESS",
					"message", "Batch notifications sent",
					"totalEmails", emailItems.size(),
					"results", response.getData()));

		} catch (IllegalArgumentException ex) {
			return ResponseEntity
					.status(400)
					.body(Map.of(
							"status", "FAILED",
							"message", "Invalid request: " + ex.getMessage()));
		} catch (Exception ex) {
			log.error("Batch notification failed", ex);
			return ResponseEntity
					.status(500)
					.body(Map.of(
							"status", "FAILED",
							"message", "Batch processing failed: " + ex.getMessage()));
		}
	}

	/**
	 * Enhanced batch send with direct email payloads
	 * 
	 * POST /api/notifications/batch/emails
	 * 
	 * Example payload:
	 * {
	 *   "emails": [
	 *     {
	 *       "to": "user1@example.com",
	 *       "subject": "Order Confirmation",
	 *       "templateType": "RESEND",
	 *       "templateId": "order-confirmation",
	 *       "data": {"PRODUCT": "Laptop", "PRICE": 1500}
	 *     },
	 *     {
	 *       "to": "user2@example.com",
	 *       "subject": "Hello!",
	 *       "templateType": "INTERNAL",
	 *       "template": "<h1>Hi {{name}}</h1>",
	 *       "data": {"name": "Bob"}
	 *     }
	 *   ]
	 * }
	 */
	@PostMapping("/emails")
	public ResponseEntity<Map<String, Object>> sendBatchEmails(
			@RequestBody BatchEmailRequest request) {
		try {
			if (request.getEmails() == null || request.getEmails().isEmpty()) {
				return ResponseEntity
						.status(400)
						.body(Map.of(
								"status", "FAILED",
								"message", "Emails list cannot be empty"));
			}

			BatchEmailResponse response = batchEmailService.processBatch(request);
			int totalEmails = request.getEmails().size();
			int batchSize = 100;
			int totalBatches = (int) Math.ceil((double) totalEmails / batchSize);

			int successCount = (int) response.getData()
					.stream()
					.filter(r -> "SUCCESS".equals(r.getStatus()))
					.count();

			String overallStatus = successCount == response.getData().size()
					? "SUCCESS"
					: (successCount == 0 ? "FAILED" : "PARTIAL");

			return ResponseEntity.ok(Map.of(
					"status", overallStatus,
					"message", successCount + " of " + response.getData().size()
							+ " emails sent successfully in " + totalBatches + " batch(es)",
					"totalEmails", response.getData().size(),
					"batchSize", batchSize,
					"totalBatches", totalBatches,
					"successCount", successCount,
					"failureCount", response.getData().size() - successCount,
					"results", response.getData()));

		} catch (Exception ex) {
			log.error("Batch emails send failed", ex);
			return ResponseEntity
					.status(500)
					.body(Map.of(
							"status", "FAILED",
							"message", "Batch send failed: " + ex.getMessage()));
		}
	}

	private void saveLog(BatchNotificationRequest.NotificationItem notification,
			BatchEmailResponse.BatchResult result) {
		NotificationLog log = new NotificationLog();
		if (notification.getMetadata() != null) {
			log.setEventId(notification.getMetadata().get("eventId"));
		}
		log.setRecipient(notification.getRecipient());
		log.setType(notification.getType());
		log.setProvider("RESEND_BATCH");
		log.setTemplateType(notification.getTemplateType() == null ? null : notification
				.getTemplateType().name());
		log.setTemplateId(notification.getTemplateId());

		if (result != null) {
			log.setStatus(result.getStatus());
			log.setError(result.getError());
		} else {
			log.setStatus("UNKNOWN");
			log.setError("No result available");
		}

		notificationLogRepository.save(log);
	}
}
