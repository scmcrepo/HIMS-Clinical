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
    @Query("SELECT DISTINCT u FROM UserEntity u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.features WHERE u.username = :username AND u.status = 1")
    Optional<UserEntity> findByUsernameWithRolesAndFeatures(@Param("username") String username);

    Optional<UserEntity> findByUsernameAndStatus(String username, short status);
    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByEmailToken(String emailToken);

    boolean existsByUsername(String username);
    boolean existsByPhoneNoTokenAndTenantId(String phoneNoToken, UUID tenantId);
    boolean existsByPhoneNoTokenAndTenantIdAndIdNot(String phoneNoToken, UUID tenantId, UUID id);

    @Query("SELECT COUNT(u) > 0 FROM UserEntity u LEFT JOIN u.branches b WHERE u.phoneNoToken = :phoneNoToken AND u.tenantId = :tenantId AND (:branchId IS NULL OR u.branchId = :branchId OR b.id = :branchId)")
    boolean existsByPhoneNoTokenAndTenantIdAndBranchId(
        @Param("phoneNoToken") String phoneNoToken,
        @Param("tenantId") UUID tenantId,
        @Param("branchId") UUID branchId);

    @Query("SELECT COUNT(u) > 0 FROM UserEntity u LEFT JOIN u.branches b WHERE u.phoneNoToken = :phoneNoToken AND u.tenantId = :tenantId AND (:branchId IS NULL OR u.branchId = :branchId OR b.id = :branchId) AND u.id != :id")
    boolean existsByPhoneNoTokenAndTenantIdAndBranchIdAndIdNot(
        @Param("phoneNoToken") String phoneNoToken,
        @Param("tenantId") UUID tenantId,
        @Param("branchId") UUID branchId,
        @Param("id") UUID id);

    /**
     * Tenant-scoped user listing. UserEntity is NOT an AuditableEntity, so the Hibernate
     * tenant/branch @Filters do NOT apply to it — listing MUST be scoped explicitly here, or a
     * hospital admin would see users of other hospitals (audit finding 17.2).
     */
    List<UserEntity> findAllByTenantId(UUID tenantId);

    /** Branch-scoped user listing (branch admin / branch staff see only their branch's users). */
    @Query("SELECT DISTINCT u FROM UserEntity u LEFT JOIN u.branches b WHERE u.tenantId = :tenantId AND (u.branchId = :branchId OR b.id = :branchId)")
    List<UserEntity> findAllByTenantIdAndBranchId(@Param("tenantId") UUID tenantId, @Param("branchId") UUID branchId);
}
