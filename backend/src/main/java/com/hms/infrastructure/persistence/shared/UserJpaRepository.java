package com.hms.infrastructure.persistence.shared;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.features WHERE u.username = :username AND u.status = 1")
    Optional<UserEntity> findByUsernameWithRolesAndFeatures(@Param("username") String username);
    Optional<UserEntity> findByUsernameAndStatus(String username, short status);
    boolean existsByUsername(String username);
    boolean existsByPhoneNo(String phoneNo);
    boolean existsByPhoneNoAndIdNot(String phoneNo, UUID id);
}
