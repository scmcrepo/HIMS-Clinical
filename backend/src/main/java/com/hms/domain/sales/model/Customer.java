package com.hms.domain.sales.model;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "customers") @Getter @Setter @NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "name", nullable = false, length = 150) private String name;
    @Column(name = "address", columnDefinition = "TEXT") private String address;
    @Column(name = "contact_no", length = 20) private String contactNo;
    @Column(name = "email", length = 120) private String email;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
}
