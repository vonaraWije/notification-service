package com.example.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.notification.model.BatchEmailRequest;
import com.example.notification.model.BatchEmailResponse;
import com.example.notification.model.EmailRequest;
import com.example.notification.model.TemplateType;
import com.example.notification.provider.resend.ResendBatchEmailProvider;

@ExtendWith(MockitoExtension.class)
class BatchEmailServiceTest {

	@Mock
	private ResendBatchEmailProvider resendBatchProvider;

	@Mock
	private TemplateService templateService;

	@InjectMocks
	private BatchEmailService batchEmailService;

	@Test
	void shouldSplitLargeRequestIntoChunksOfHundred() {
		BatchEmailRequest request = new BatchEmailRequest();
		request.setEmails(buildEmailItems(205));

		when(templateService.render(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap()))
				.thenReturn("<p>Rendered</p>");
		when(resendBatchProvider.sendBatch(anyList(), anyList())).thenAnswer(invocation -> {
			List<EmailRequest> chunk = invocation.getArgument(0);
			List<BatchEmailResponse.BatchResult> results = new ArrayList<>();
			for (EmailRequest emailRequest : chunk) {
				results.add(BatchEmailResponse.BatchResult.builder()
						.recipient(emailRequest.getTo())
						.status("SUCCESS")
						.statusCode(200)
						.build());
			}
			BatchEmailResponse response = new BatchEmailResponse();
			response.setData(results);
			return response;
		});

		BatchEmailResponse response = batchEmailService.processBatch(request);

		assertEquals(205, response.getData().size());
		verify(resendBatchProvider, times(3)).sendBatch(anyList(), anyList());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<EmailRequest>> requestCaptor = ArgumentCaptor.forClass(List.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<BatchEmailRequest.EmailItem>> originalItemsCaptor = ArgumentCaptor
				.forClass(List.class);
		verify(resendBatchProvider, times(3)).sendBatch(requestCaptor.capture(),
				originalItemsCaptor.capture());

		List<List<EmailRequest>> capturedRequestChunks = requestCaptor.getAllValues();
		List<List<BatchEmailRequest.EmailItem>> capturedOriginalChunks = originalItemsCaptor
				.getAllValues();

		assertEquals(100, capturedRequestChunks.get(0).size());
		assertEquals(100, capturedRequestChunks.get(1).size());
		assertEquals(5, capturedRequestChunks.get(2).size());

		assertEquals(100, capturedOriginalChunks.get(0).size());
		assertEquals(100, capturedOriginalChunks.get(1).size());
		assertEquals(5, capturedOriginalChunks.get(2).size());
	}

	@Test
	void shouldThrowWhenEmailsListIsEmpty() {
		BatchEmailRequest request = new BatchEmailRequest();
		request.setEmails(List.of());

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> batchEmailService.processBatch(request));

		assertEquals("Emails list cannot be empty", ex.getMessage());
	}

	private List<BatchEmailRequest.EmailItem> buildEmailItems(int count) {
		List<BatchEmailRequest.EmailItem> emails = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			BatchEmailRequest.EmailItem item = new BatchEmailRequest.EmailItem();
			item.setTo("user" + i + "@example.com");
			item.setSubject("Subject " + i);
			item.setTemplateType(TemplateType.INTERNAL);
			item.setTemplate("<p>Hello {{name}}</p>");
			item.setData(Map.of("name", "User " + i));
			emails.add(item);
		}
		return emails;
	}
}
