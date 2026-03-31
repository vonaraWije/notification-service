package com.example.notification.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchEmailResponse {
	@JsonProperty("data")
	private List<BatchResult> data;
	private String error;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BatchResult {
		private String id;
		private String error;
		private int statusCode;
		private String recipient;
		private String status;
	}
}
