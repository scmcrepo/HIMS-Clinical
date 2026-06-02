package com.hms.domain.shared.model;
import java.time.Instant;
public interface DomainEvent {
    Instant occurredAt();
}
