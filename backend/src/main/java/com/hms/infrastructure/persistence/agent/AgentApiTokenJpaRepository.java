package com.hms.infrastructure.persistence.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentApiTokenJpaRepository extends JpaRepository<AgentApiTokenEntity, UUID> {

    /**
     * Hash lookup on the authentication hot path.
     *
     * <p>Intentionally NOT tenant-filtered: at this point in the request there is
     * no authenticated principal and therefore no tenant context — the token is
     * what establishes it. The tenant is then read from the token row and used to
     * scope everything downstream, so a token can only ever reach its own
     * tenant's data.
     */
    Optional<AgentApiTokenEntity> findByTokenHash(String tokenHash);

    List<AgentApiTokenEntity> findAllByOrderByCreatedAtDesc();
}
