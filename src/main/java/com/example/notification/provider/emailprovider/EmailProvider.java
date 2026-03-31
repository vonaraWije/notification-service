package com.example.notification.provider.emailprovider;

import com.example.notification.model.EmailRequest;

public interface EmailProvider {
    void send(EmailRequest request);
}

