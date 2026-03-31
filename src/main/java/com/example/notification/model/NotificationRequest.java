package com.example.notification.model;

import java.util.Map;

import lombok.Data;

@Data
public class NotificationRequest {
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

