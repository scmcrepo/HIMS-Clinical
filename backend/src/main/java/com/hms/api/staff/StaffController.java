package com.hms.api.staff;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.shared.model.Staff;
import com.hms.infrastructure.persistence.staff.StaffJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/staff") @RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_STAFF','')")
@Transactional(readOnly = true)
public class StaffController {
    private final StaffJpaRepository repo;
    private final PiiSearchTokenService tokenService;
    @GetMapping
    public ResponseEntity<ApiResponse<List<Staff>>> getAll(@RequestParam(name = "type", required=false) String type) {
        return ResponseEntity.ok(ApiResponse.ok("OK", type != null ? repo.findByType(type) : repo.findAllActive()));
    }
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<String[]>> getTypes() {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            new String[]{"NURSE","TECHNICIAN","PHARMACIST","RECEPTIONIST","ADMIN","HOUSEKEEPING","SECURITY"}));
    }
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<Staff>> create(@RequestBody Staff req) {
        if (req.getContact() == null || req.getContact().isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Contact number is required");
        }
        String contact = req.getContact().trim();
        String contactToken = tokenService.phoneToken(contact);
        UUID branchId = req.getBranchId() != null ? req.getBranchId() : com.hms.infrastructure.tenant.BranchContext.get();
        if (branchId == null) {
            throw new com.hms.exception.BusinessRuleViolationException("Branch is required for staff");
        }
        if (contactToken != null && repo.existsByContactTokenAndBranchIdAndStatusNot(contactToken, branchId, com.hms.domain.shared.model.EntityStatus.DELETED)) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Contact number '" + req.getContact() + "' already exists in this branch");
        }
        req.setContact(contact);
        req.setContactToken(contactToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Staff saved successfully", repo.save(req)));
    }
    @PutMapping
    @Transactional
    public ResponseEntity<ApiResponse<Staff>> update(@RequestBody Staff req) {
        if (req.getContact() == null || req.getContact().isBlank()) {
            throw new com.hms.exception.BusinessRuleViolationException("Contact number is required");
        }
        String contact = req.getContact().trim();
        String contactToken = tokenService.phoneToken(contact);
        UUID branchId = req.getBranchId() != null ? req.getBranchId() : (req.getId() != null ? repo.findById(req.getId()).map(Staff::getBranchId).orElse(null) : null);
        if (branchId == null) {
            branchId = com.hms.infrastructure.tenant.BranchContext.get();
        }
        if (branchId == null) {
            throw new com.hms.exception.BusinessRuleViolationException("Branch is required for staff");
        }
        if (contactToken != null && repo.existsByContactTokenAndBranchIdAndStatusNotAndIdNot(contactToken, branchId, com.hms.domain.shared.model.EntityStatus.DELETED, req.getId())) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Contact number '" + req.getContact() + "' already exists in this branch");
        }
        req.setContact(contact);
        req.setContactToken(contactToken);
        return ResponseEntity.ok(ApiResponse.ok("Staff updated successfully", repo.save(req)));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<Staff>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<Staff> all = repo.findAllOrdered();
        
        if (value != null && !value.isBlank()) {
            String lowerValue = value.toLowerCase();
            all = all.stream().filter(e -> 
                (e.getName() != null && e.getName().toLowerCase().contains(lowerValue)) ||
                (e.getStaffType() != null && e.getStaffType().toLowerCase().contains(lowerValue)) ||
                (e.getContact() != null && e.getContact().toLowerCase().contains(lowerValue)) ||
                (e.getEmail() != null && e.getEmail().toLowerCase().contains(lowerValue)) ||
                (e.getDesignation() != null && e.getDesignation().toLowerCase().contains(lowerValue))
            ).toList();
        }
        
        int total = all.size();
        int startIndex = Math.min(start, total);
        int endIndex = Math.min(start + limit, total);
        List<Staff> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<Staff> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

    
}
