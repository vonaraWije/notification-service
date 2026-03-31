package com.example.notification.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.notification.entity.NotificationLog;
import com.example.notification.model.EmailRequest;
import com.example.notification.model.NotificationRequest;
import com.example.notification.model.TemplateType;
import com.example.notification.repository.NotificationLogRepository;

@Service
public class NotificationService {

	private final TemplateService templateService;
	private final FailoverEmailService failoverEmailService;
	private final NotificationLogRepository notificationLogRepository;

	public NotificationService(
			TemplateService templateService,
			FailoverEmailService failoverEmailService,
			NotificationLogRepository notificationLogRepository) {
		this.templateService = templateService;
		this.failoverEmailService = failoverEmailService;
		this.notificationLogRepository = notificationLogRepository;
	}

	public FailoverEmailService.SendResult processEmail(NotificationRequest request) {
		EmailRequest emailRequest = new EmailRequest();
		emailRequest.setTo(request.getRecipient());
		emailRequest.setSubject(resolveSubject(request));

		TemplateType templateType = request.getTemplateType() == null
				? TemplateType.INTERNAL
				: request.getTemplateType();
		emailRequest.setTemplateType(templateType);

		if (templateType == TemplateType.INTERNAL) {
			String html = templateService.render(request.getTemplate(), request.getData());
			emailRequest.setHtml(html);
		} else {
			String resendTemplateId = request.getTemplateId();
			if (resendTemplateId == null || resendTemplateId.isBlank()) {
				resendTemplateId = request.getTemplate();
			}
			if (resendTemplateId == null || resendTemplateId.isBlank()) {
				throw new IllegalArgumentException("templateId (or template) is required when templateType is RESEND");
			}

			emailRequest.setTemplateId(resendTemplateId);
			emailRequest.setVariables(request.getData());
			// Fallback provider (Gmail) needs an HTML body if resend is unavailable.
			String html;
			try {
				html = templateService.render(request.getTemplate(), request.getData());
			} catch (Exception ignored) {
				html = "<p>" + resolveSubject(request) + "</p>";
			}
			emailRequest.setHtml(html);
		}

		String provider = "UNKNOWN";
		try {
			FailoverEmailService.SendResult sendResult = failoverEmailService.send(emailRequest);
			provider = sendResult.getProvider();
			saveLog(request, provider, "SUCCESS", null);
			return sendResult;
		} catch (Exception ex) {
			saveLog(request, provider, "FAILED", ex.getMessage());
			throw ex;
		}
	}

	private void saveLog(NotificationRequest request, String provider, String status, String error) {
		NotificationLog log = new NotificationLog();
		Map<String, String> metadata = request.getMetadata();
		if (metadata != null) {
			log.setEventId(metadata.get("eventId"));
		}
		log.setRecipient(request.getRecipient());
		log.setType(request.getType());
		log.setProvider(provider);
		log.setTemplateType(request.getTemplateType() == null ? null : request.getTemplateType().name());
		log.setTemplateId(request.getTemplateId());
		log.setStatus(status);
		log.setError(error);
		notificationLogRepository.save(log);
	}

	private String resolveSubject(NotificationRequest request) {
		if (request.getSubject() != null && !request.getSubject().isBlank()) {
			return request.getSubject();
		}
		return request.getType() == null ? "Notification" : request.getType().replace('_', ' ');
	}
}

