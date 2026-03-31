package com.example.notification.model;

import java.util.List;

import lombok.Data;

@Data
public class BatchEmailRequest {
	private List<EmailItem> emails;

	@Data
	public static class EmailItem {
		private String to;
		private String subject;
		private TemplateType templateType;
		private String templateId;
		private String template;
		private java.util.Map<String, Object> data;
		private String html;
	}
}
