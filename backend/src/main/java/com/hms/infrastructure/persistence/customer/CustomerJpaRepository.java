package com.hms.infrastructure.persistence.customer;
import com.hms.domain.sales.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CustomerJpaRepository extends JpaRepository<Customer, UUID> {}
