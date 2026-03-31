package com.example.notification.model;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class BatchNotificationRequest {
	private List<NotificationItem> notifications;

	@Data
	public static class NotificationItem {
		private String type;
		private String channel;
		private String recipient;
		private String subject;
		private TemplateType templateType;
		private String templateId;
		private String template;
		private Map<String, Object> data;
		private Map<String, String> metadata;
	}
}
