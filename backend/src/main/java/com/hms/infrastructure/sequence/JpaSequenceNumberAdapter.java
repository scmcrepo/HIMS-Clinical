package com.hms.infrastructure.sequence;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.BusinessRuleViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
@Component @RequiredArgsConstructor
public class JpaSequenceNumberAdapter implements SequenceNumberPort {
    private final SequenceGeneratorJpaRepository repo;
    /**
     * Called within an existing transaction.
     * The @Lock(PESSIMISTIC_WRITE) on the repository query ensures
     * SELECT FOR UPDATE — no two threads can generate the same number.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String generateNext(DocumentType documentType) {
        Optional<SequenceGeneratorEntity> active = repo.findActiveByDocumentTypeForUpdate(documentType);
        if (active.isPresent()) {
            return active.get().formatAndIncrement();
        }

        // If not active, check if any exists at all to provide specific feedback
        List<SequenceGeneratorEntity> all = repo.findAllByDocumentType(documentType);
        if (all.isEmpty()) {
            throw new BusinessRuleViolationException(
                "No sequence generator configured for " + documentType + ". Please create one in Admin > Sequences.");
        } else {
            throw new BusinessRuleViolationException(
                "The sequence generator for " + documentType + " is currently deactivated. Please activate it in Admin > Sequences.");
        }
    }
}
