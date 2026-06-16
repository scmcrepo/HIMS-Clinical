package com.hms.infrastructure.persistence.shared;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Login lookup. Username is globally unique across the platform, so this single query
     * unambiguously resolves the user; the tenant and branch are then read from the row.
     * This is what makes "no tenant selection at login" both possible and safe.
     */
    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.features WHERE u.username = :username AND u.status = 1")
    Optional<UserEntity> findByUsernameWithRolesAndFeatures(@Param("username") String username);

    Optional<UserEntity> findByUsernameAndStatus(String username, short status);

    boolean existsByUsername(String username);
    boolean existsByPhoneNo(String phoneNo);
    boolean existsByPhoneNoAndIdNot(String phoneNo, UUID id);

    /**
     * Tenant-scoped user listing. UserEntity is NOT an AuditableEntity, so the Hibernate
     * tenant/branch @Filters do NOT apply to it — listing MUST be scoped explicitly here, or a
     * hospital admin would see users of other hospitals (audit finding 17.2).
     */
    List<UserEntity> findAllByTenantId(UUID tenantId);

    /** Branch-scoped user listing (branch admin / branch staff see only their branch's users). */
    List<UserEntity> findAllByTenantIdAndBranchId(UUID tenantId, UUID branchId);
}
