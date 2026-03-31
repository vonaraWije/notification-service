package com.example.notification.provider.gmail;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.example.notification.model.EmailRequest;
import com.example.notification.provider.emailprovider.EmailProvider;

import jakarta.mail.internet.MimeMessage;

@Component
public class GmailEmailProvider implements EmailProvider {

	private final JavaMailSender javaMailSender;

	public GmailEmailProvider(JavaMailSender javaMailSender) {
		this.javaMailSender = javaMailSender;
	}

	@Override
	public void send(EmailRequest request) {
		try {
			MimeMessage message = javaMailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setTo(request.getTo());
			helper.setSubject(request.getSubject());
			helper.setText(request.getHtml(), true);
			javaMailSender.send(message);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to send email through Gmail", ex);
		}
	}
}

