package com.hms.infrastructure.notification;
import com.hms.domain.shared.port.out.NotificationPort;
import com.hms.security.encryption.PiiMasking;
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
            log.debug("event=sms.skipped reason=provider_disabled to={}",
                      PiiMasking.phone(message.toNumber()));
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
                log.info("event=sms.sent to={} template={}",
                         PiiMasking.phone(message.toNumber()), message.templateKey());
            }
        } catch (Exception ex) {
            // WO-029 / U-005. Three separate leaks were on this line: the number
            // in the clear, and ex.getMessage(), which for a messaging provider
            // routinely quotes the destination number back in the error text.
            // Same defect as S1-02 in SmtpConfigService, which was fixed; nobody
            // came back for this one. The exception type is enough to
            // investigate, and these lines are shipped to Loki and kept a year.
            log.error("event=sms.failed to={} error_type={}",
                      PiiMasking.phone(message.toNumber()), ex.getClass().getSimpleName());
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
