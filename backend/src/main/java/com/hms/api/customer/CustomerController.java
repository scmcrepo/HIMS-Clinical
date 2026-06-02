package com.hms.api.customer;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.sales.model.Customer;
import com.hms.infrastructure.persistence.customer.CustomerJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/customer") @RequiredArgsConstructor
public class CustomerController {
    private final CustomerJpaRepository repo;
    @PostMapping
    public ResponseEntity<ApiResponse<Customer>> create(@RequestBody Customer req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Customer saved successfully", repo.save(req)));
    }
}
