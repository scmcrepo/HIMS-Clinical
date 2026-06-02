package com.hms.api.bedtype;

import com.hms.api.shared.ApiResponse;
import com.hms.domain.bed.model.*;
import com.hms.infrastructure.persistence.bed.RoomCategoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * BedTypeController — maps SRS /bedType URL to our RoomCategory entity.
 *
 * SRS §4 Critical bug: BedTypeDto calls setBaseModel() not setBaseDataStatusModel()
 * — status field never written in legacy. We FIX this: status is always persisted.
 */
@RestController
@RequestMapping("/bedType")
@RequiredArgsConstructor
public class BedTypeController {

    private final RoomCategoryJpaRepository bedTypeRepo;

    /** POST /bedType — creates bed type. Status IS written (bug fixed vs legacy). */
    @PostMapping
    @PreAuthorize("hasPermission('SETTINGS_BEDTYPE','')")
    public ResponseEntity<ApiResponse<RoomCategory>> create(@RequestBody RoomCategory req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Bed type saved successfully", bedTypeRepo.save(req)));
    }

    /** PUT /bedType — updates bed type. */
    @PutMapping
    @PreAuthorize("hasPermission('SETTINGS_BEDTYPE','')")
    public ResponseEntity<ApiResponse<RoomCategory>> update(@RequestBody RoomCategory req) {
        if (req.getId() == null) return (ResponseEntity) ResponseEntity.badRequest().body(ApiResponse.error("id required"));
        return ResponseEntity.ok(ApiResponse.ok("Bed type updated successfully", bedTypeRepo.save(req)));
    }

    /** GET /bedType?status= — all bed types. */
    @GetMapping
    @PreAuthorize("hasPermission('SETTINGS_BED','')")
    public ResponseEntity<ApiResponse<List<RoomCategory>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", bedTypeRepo.findAllActive()));
    }



    

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<RoomCategory>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<RoomCategory> all = bedTypeRepo.findAllOrdered();
        
        if (value != null && !value.isBlank()) {
            String lowerValue = value.toLowerCase();
            all = all.stream().filter(e -> {
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getName");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getFirstName");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getUsername");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getPrefix");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                return false;
            }).toList();
        }
        
        int total = all.size();
        int startIndex = Math.min(start, total);
        int endIndex = Math.min(start + limit, total);
        List<RoomCategory> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<RoomCategory> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
