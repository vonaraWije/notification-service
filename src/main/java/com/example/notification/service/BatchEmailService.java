package com.example.notification.service;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;

import com.example.notification.model.BatchEmailRequest;
import com.example.notification.model.BatchEmailResponse;
import com.example.notification.model.EmailRequest;
import com.example.notification.model.TemplateType;
import com.example.notification.provider.resend.ResendBatchEmailProvider;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BatchEmailService {

	private final ResendBatchEmailProvider resendBatchProvider;
	private final TemplateService templateService;
	private static final int MAX_BATCH_SIZE = 100;

	public BatchEmailService(
			ResendBatchEmailProvider resendBatchProvider,
			TemplateService templateService) {
		this.resendBatchProvider = resendBatchProvider;
		this.templateService = templateService;
	}

	public BatchEmailResponse processBatch(BatchEmailRequest request) {
		List<BatchEmailRequest.EmailItem> emails = request.getEmails();

		if (emails == null || emails.isEmpty()) {
			throw new IllegalArgumentException("Emails list cannot be empty");
		}

		List<BatchEmailResponse.BatchResult> allResults = new ArrayList<>();
		StringJoiner errorJoiner = new StringJoiner("; ");

		for (int start = 0; start < emails.size(); start += MAX_BATCH_SIZE) {
			int end = Math.min(start + MAX_BATCH_SIZE, emails.size());
			List<BatchEmailRequest.EmailItem> emailChunk = emails.subList(start, end);
			List<EmailRequest> emailRequests = buildEmailRequests(emailChunk);

			BatchEmailResponse chunkResponse = resendBatchProvider.sendBatch(emailRequests, emailChunk);
			if (chunkResponse != null && chunkResponse.getData() != null) {
				allResults.addAll(chunkResponse.getData());
			}

			if (chunkResponse != null && chunkResponse.getError() != null
					&& !chunkResponse.getError().isBlank()) {
				errorJoiner.add(chunkResponse.getError());
			}
		}

		BatchEmailResponse response = new BatchEmailResponse();
		response.setData(allResults);
		if (errorJoiner.length() > 0) {
			response.setError(errorJoiner.toString());
		}
		return response;
	}

	private List<EmailRequest> buildEmailRequests(List<BatchEmailRequest.EmailItem> emails) {
		List<EmailRequest> emailRequests = new ArrayList<>();

		for (BatchEmailRequest.EmailItem item : emails) {
			EmailRequest emailRequest = new EmailRequest();
			emailRequest.setTo(item.getTo());
			emailRequest.setSubject(item.getSubject());

			TemplateType templateType = item.getTemplateType() == null
					? TemplateType.INTERNAL
					: item.getTemplateType();
			emailRequest.setTemplateType(templateType);

			if (templateType == TemplateType.INTERNAL) {
				String html = templateService.render(item.getTemplate(), item.getData());
				emailRequest.setHtml(html);
			} else {
				String resendTemplateId = item.getTemplateId();
				if (resendTemplateId == null || resendTemplateId.isBlank()) {
					resendTemplateId = item.getTemplate();
				}
				if (resendTemplateId == null || resendTemplateId.isBlank()) {
					throw new IllegalArgumentException(
							"templateId (or template) is required when templateType is RESEND");
				}
				emailRequest.setTemplateId(resendTemplateId);
				emailRequest.setVariables(item.getData());
				String html;
				try {
					html = templateService.render(item.getTemplate(), item.getData());
				} catch (Exception ignored) {
					html = "<p>" + item.getSubject() + "</p>";
				}
				emailRequest.setHtml(html);
			}

			emailRequests.add(emailRequest);
		}

		return emailRequests;
	}
}
