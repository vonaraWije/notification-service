package com.example.notification.provider.resend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.example.notification.exception.ProviderUnavailableException;
import com.example.notification.model.BatchEmailRequest;
import com.example.notification.model.BatchEmailResponse;
import com.example.notification.model.EmailRequest;
import com.example.notification.model.TemplateType;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ResendBatchEmailProvider {

	private final RestClient resendRestClient;
	private final String fromEmail;

	public ResendBatchEmailProvider(
			@Qualifier("resendRestClient") RestClient resendRestClient,
			@Value("${resend.from.email}") String fromEmail) {
		this.resendRestClient = resendRestClient;
		this.fromEmail = fromEmail;
	}

	@CircuitBreaker(name = "resendBatchEmail", fallbackMethod = "fallback")
	public BatchEmailResponse sendBatch(List<EmailRequest> emailRequests,
			List<BatchEmailRequest.EmailItem> originalItems) {
		List<Map<String, Object>> payloads = new ArrayList<>();

		for (int i = 0; i < emailRequests.size(); i++) {
			EmailRequest emailRequest = emailRequests.get(i);
			Map<String, Object> payload;

			if (emailRequest.getTemplateType() == TemplateType.RESEND) {
				if (emailRequest.getTemplateId() == null || emailRequest.getTemplateId().isBlank()) {
					throw new IllegalArgumentException(
							"templateId is required for email: " + emailRequest.getTo());
				}
				payload = Map.of(
						"from", fromEmail,
						"to", List.of(emailRequest.getTo()),
						"subject", emailRequest.getSubject(),
						"template", Map.of(
								"id", emailRequest.getTemplateId(),
								"variables",
								emailRequest.getVariables() == null ? Map.of()
										: emailRequest.getVariables()));
			} else {
				payload = Map.of(
						"from", fromEmail,
						"to", List.of(emailRequest.getTo()),
						"subject", emailRequest.getSubject(),
						"html", emailRequest.getHtml());
			}

			payloads.add(payload);
		}

		try {
			Map<String, Object> response = resendRestClient.post()
					.uri("/emails/batch")
					.body(payloads)
					.retrieve()
					.toEntity(Map.class)
					.getBody();

			return parseResponse(response, emailRequests);
		} catch (HttpClientErrorException ex) {
			return handleError(ex, emailRequests);
		} catch (Exception ex) {
			log.error("Batch email send failed", ex);
			throw new ProviderUnavailableException("Resend batch unavailable", ex);
		}
	}

	private BatchEmailResponse parseResponse(Map<String, Object> response,
			List<EmailRequest> emailRequests) {
		BatchEmailResponse batchResponse = new BatchEmailResponse();
		List<BatchEmailResponse.BatchResult> results = new ArrayList<>();

		if (response != null && response.containsKey("data")) {
			List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
			for (int i = 0; i < data.size(); i++) {
				Map<String, Object> item = data.get(i);
				BatchEmailResponse.BatchResult result = BatchEmailResponse.BatchResult.builder()
						.recipient(emailRequests.get(i).getTo())
						.id((String) item.get("id"))
						.status("SUCCESS")
						.statusCode(200)
						.build();
				results.add(result);
			}
		}

		batchResponse.setData(results);
		return batchResponse;
	}

	private BatchEmailResponse handleError(HttpClientErrorException ex,
			List<EmailRequest> emailRequests) {
		BatchEmailResponse batchResponse = new BatchEmailResponse();
		List<BatchEmailResponse.BatchResult> results = new ArrayList<>();

		int statusCode = ex.getStatusCode().value();
		String errorMessage = getErrorMessage(statusCode, ex.getResponseBodyAsString());

		// All emails in batch fail with the same error
		for (EmailRequest emailRequest : emailRequests) {
			BatchEmailResponse.BatchResult result = BatchEmailResponse.BatchResult.builder()
					.recipient(emailRequest.getTo())
					.status("FAILED")
					.statusCode(statusCode)
					.error(errorMessage)
					.build();
			results.add(result);
		}

		batchResponse.setData(results);
		batchResponse.setError(errorMessage);
		return batchResponse;
	}

	private String getErrorMessage(int statusCode, String responseBody) {
		return switch (statusCode) {
		case 400 -> "Bad Request: Check that the parameters were correct. " + responseBody;
		case 401 -> "Unauthorized: The API key used was missing.";
		case 403 -> "Forbidden: The API key used was invalid.";
		case 404 -> "Not Found: The resource was not found.";
		case 429 -> "Too Many Requests: The rate limit was exceeded.";
		case 500, 502, 503, 504 -> "Server Error: Indicates an error with Resend servers. Try again later.";
		default -> "Unknown error: Status code " + statusCode;
		};
	}

	public BatchEmailResponse fallback(List<EmailRequest> emailRequests,
			List<BatchEmailRequest.EmailItem> originalItems, Throwable ex) {
		log.error("Batch email fallback invoked", ex);
		BatchEmailResponse batchResponse = new BatchEmailResponse();
		List<BatchEmailResponse.BatchResult> results = new ArrayList<>();

		for (EmailRequest emailRequest : emailRequests) {
			BatchEmailResponse.BatchResult result = BatchEmailResponse.BatchResult.builder()
					.recipient(emailRequest.getTo())
					.status("FAILED")
					.statusCode(503)
					.error("Resend batch service unavailable: " + ex.getMessage())
					.build();
			results.add(result);
		}

		batchResponse.setData(results);
		batchResponse.setError("Resend batch service unavailable");
		return batchResponse;
	}
}
