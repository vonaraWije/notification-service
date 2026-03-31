package com.example.notification.service;

import org.springframework.stereotype.Service;

import com.example.notification.model.EmailRequest;
import com.example.notification.provider.gmail.GmailEmailProvider;
import com.example.notification.provider.resend.ResendEmailProvider;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FailoverEmailService {
    public static class SendResult {
        private final String provider;
        private final String resendFailureReason;

        public SendResult(String provider, String resendFailureReason) {
            this.provider = provider;
            this.resendFailureReason = resendFailureReason;
        }

        public String getProvider() {
            return provider;
        }

        public String getResendFailureReason() {
            return resendFailureReason;
        }
    }

    private final ResendEmailProvider resend;
    private final GmailEmailProvider gmail;

    public FailoverEmailService(ResendEmailProvider resend, GmailEmailProvider gmail) {
        this.resend = resend;
        this.gmail = gmail;
    }

    public SendResult send(EmailRequest request) {
        try {
            resend.send(request);
            return new SendResult("RESEND", null);
        } catch (Exception ex) {
            log.warn("Resend send failed for recipient {}. Falling back to Gmail. Reason: {}",
                    request.getTo(), ex.getMessage());
            gmail.send(request);
            return new SendResult("GMAIL", ex.getMessage());
        }
    }
}

