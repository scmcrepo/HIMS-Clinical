package com.hms.infrastructure.notification;
import com.hms.domain.shared.port.out.NotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
@Component @Slf4j
public class TwilioSmsAdapter implements NotificationPort {
    @Value("${hms.sms.provider:disabled}") private String provider;
    @Value("${hms.sms.twilio.account-sid:}") private String accountSid;
    @Value("${hms.sms.twilio.auth-token:}") private String authToken;
    @Value("${hms.sms.twilio.from-number:}") private String fromNumber;
    @Override @Async
    public void sendSms(SmsMessage message) {
        if ("disabled".equalsIgnoreCase(provider)) {
            log.debug("SMS provider disabled — skipping SMS to {}", message.toNumber());
            return;
        }
        try {
            String body = resolvePlaceholders(message);
            if ("twilio".equalsIgnoreCase(provider)) {
                com.twilio.Twilio.init(accountSid, authToken);
                com.twilio.rest.api.v2010.account.Message.creator(
                    new com.twilio.type.PhoneNumber(message.toNumber()),
                    new com.twilio.type.PhoneNumber(fromNumber),
                    body).create();
                log.info("SMS sent to {}", message.toNumber());
            }
        } catch (Exception ex) {
            log.error("SMS send failed to {}: {}", message.toNumber(), ex.getMessage());
        }
    }
    private String resolvePlaceholders(SmsMessage msg) {
        String body = msg.templateKey();
        if (msg.variables() != null) {
            for (var entry : msg.variables().entrySet()) {
                body = body.replace("$" + entry.getKey() + "$", entry.getValue());
            }
        }
        return body;
    }
}
