package com.example.notification.model;

import java.util.Map;

import lombok.Data;

@Data
public class EmailRequest {
	private String to;
	private String subject;
	private TemplateType templateType;
	private String html;
	private String templateId;
	private Map<String, Object> variables;
}

