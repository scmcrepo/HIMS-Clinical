package com.hms.domain.patient.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "referrals") @Getter @Setter @NoArgsConstructor
public class Referral extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 100) private String name;
    @Column(name = "type", length = 50) private String type;
    @Column(name = "contact", length = 20) private String contact;
    @Column(name = "salutation", length = 10) private String salutation;
    @Column(name = "first_name", length = 60) private String firstName;
    @Column(name = "last_name", length = 60) private String lastName;
    @Column(name = "address", length = 500) private String address;
}
