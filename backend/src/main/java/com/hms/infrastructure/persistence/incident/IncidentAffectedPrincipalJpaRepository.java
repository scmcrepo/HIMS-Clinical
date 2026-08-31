package com.hms.infrastructure.persistence.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentAffectedPrincipalJpaRepository
        extends JpaRepository<IncidentAffectedPrincipalEntity, UUID> {

    List<IncidentAffectedPrincipalEntity> findByIncidentId(UUID incidentId);

    List<IncidentAffectedPrincipalEntity> findByIncidentIdAndNotificationState(
        UUID incidentId, String notificationState);

    long countByIncidentIdAndNotificationState(UUID incidentId, String notificationState);
}
