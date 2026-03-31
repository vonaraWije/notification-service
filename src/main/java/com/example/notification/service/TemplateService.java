package com.example.notification.service;

import java.util.Collections;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class TemplateService {

	private final TemplateEngine templateEngine;

	public TemplateService(TemplateEngine templateEngine) {
		this.templateEngine = templateEngine;
	}

	public String render(String template, Map<String, Object> data) {
		Context context = new Context();
		if (data != null) {
			context.setVariables(data);
		} else {
			context.setVariables(Collections.emptyMap());
		}
		return templateEngine.process(template, context);
	}
}

