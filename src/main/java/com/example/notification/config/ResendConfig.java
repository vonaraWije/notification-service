package com.example.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ResendConfig {

	@Bean("resendRestClient")
	public RestClient resendRestClient(
			@Value("${resend.api.base-url:https://api.resend.com}") String baseUrl,
			@Value("${resend.api.key}") String apiKey) {
		return RestClient.builder()
				.baseUrl(baseUrl)
				.defaultHeader("Authorization", "Bearer " + apiKey)
				.build();
	}
}

