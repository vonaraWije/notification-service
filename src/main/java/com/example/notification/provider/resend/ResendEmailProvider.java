package com.example.notification.provider.resend;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.example.notification.exception.ProviderUnavailableException;
import com.example.notification.model.EmailRequest;
import com.example.notification.model.TemplateType;
import com.example.notification.provider.emailprovider.EmailProvider;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Component
public class ResendEmailProvider implements EmailProvider {

	private final RestClient resendRestClient;
	private final String fromEmail;

	public ResendEmailProvider(
			@Qualifier("resendRestClient") RestClient resendRestClient,
			@Value("${resend.from.email}") String fromEmail) {
		this.resendRestClient = resendRestClient;
		this.fromEmail = fromEmail;
	}

	@Override
	@CircuitBreaker(name = "resendEmail", fallbackMethod = "fallback")
	public void send(EmailRequest request) {
		Map<String, Object> payload;
		if (request.getTemplateType() == TemplateType.RESEND) {
			if (request.getTemplateId() == null || request.getTemplateId().isBlank()) {
				throw new IllegalArgumentException("templateId is required when templateType is RESEND");
			}
			payload = Map.of(
					"from", fromEmail,
					"to", List.of(request.getTo()),
					"subject", request.getSubject(),
					"template", Map.of(
							"id", request.getTemplateId(),
							"variables", request.getVariables() == null ? Map.of() : request.getVariables()));
		} else {
			payload = Map.of(
					"from", fromEmail,
					"to", List.of(request.getTo()),
					"subject", request.getSubject(),
					"html", request.getHtml());
		}

		try {
			resendRestClient.post()
					.uri("/emails")
					.body(payload)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientResponseException ex) {
			throw new RuntimeException(
					"Resend API failed with status " + ex.getStatusCode().value()
							+ ": " + ex.getResponseBodyAsString(),
					ex);
		}
	}

	public void fallback(EmailRequest request, Throwable ex) {
		throw new ProviderUnavailableException("Resend unavailable", ex);
	}
}

