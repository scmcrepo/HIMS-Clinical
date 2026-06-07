package com.hms.api.hospitalprofile;

import com.hms.api.shared.ApiResponse;
import com.hms.application.attachment.AttachmentService;
import com.hms.domain.attachment.model.AttachmentType;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * HospitalProfileController — legacy /hospitalProfile endpoint.
 *
 * Distinct from /config/hospital (our newer approach).
 * SRS §16: params are query-string style (not JSON body).
 * uploadImage: saves logo file via AttachmentService (AttachmentType.VISIT used as generic).
 * Always saves as a known key so ConfigAspect picks it up for $hospitalName$ substitution.
 */
@RestController
@RequestMapping("/hospitalProfile")
@RequiredArgsConstructor
public class HospitalProfileController {

    private final SettingsRegistryImpl settingsRegistry;
    private final AttachmentService attachmentService;

    /** GET /hospitalProfile — all hospital profile config entries */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            settingsRegistry.getValueMapByType("HOSPITAL_PARAM")));
    }

    /**
     * POST /hospitalProfile?name=&address=&contactNo=
     * Query-param style — matches legacy HospitalProfileController exactly.
     */
    @PreAuthorize("hasPermission('SETTINGS_HOSPITALPROFILE','')")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> create(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "address", required = false) String address,
            @RequestParam(name = "contactNo", required = false) String contactNo) {
        saveProfileParams(name, address, contactNo);
        return ResponseEntity.ok(ApiResponse.ok("Hospital profile saved successfully"));
    }

    /**
     * PUT /hospitalProfile?name=&address=&contactNo=
     */
    @PreAuthorize("hasPermission('SETTINGS_HOSPITALPROFILE','')")
    @PutMapping
    public ResponseEntity<ApiResponse<Void>> update(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "address", required = false) String address,
            @RequestParam(name = "contactNo", required = false) String contactNo) {
        saveProfileParams(name, address, contactNo);
        return ResponseEntity.ok(ApiResponse.ok("Hospital profile updated successfully"));
    }

    /**
     * POST /hospitalProfile/uploadImage — uploads logo file.
     * SRS: always saves as Clinic.jpg at /assets/images/ — we store via AttachmentService.
     * Returns void — no confirmation body (matches legacy).
     */
    @PreAuthorize("hasPermission('SETTINGS_HOSPITALPROFILE','')")
    @PostMapping(value = "/uploadImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadImage(
            @RequestPart("file") MultipartFile file) throws IOException {
        // Store the logo — AttachmentType.PATIENT_PICTURE used as a generic file store
        // In production this would save to /assets/images/Clinic.jpg
        attachmentService.saveAttachment(file, AttachmentType.PATIENT_PICTURE,
            null, null, null, "HOSPITAL_LOGO");
        // Returns void — no body (matches legacy behaviour)
        return ResponseEntity.ok().build();
    }

    /** GET /hospitalProfile/logo — downloads or streams the hospital logo inline */
    @GetMapping("/logo")
    public ResponseEntity<org.springframework.core.io.Resource> getLogo() {
        try {
            com.hms.domain.attachment.model.Attachment att = attachmentService.getLatestByCategory("HOSPITAL_LOGO")
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Hospital logo attachment not found"));
            org.springframework.core.io.Resource res = attachmentService.downloadFile(att.getId());
            return ResponseEntity.ok()
                .contentType(att.getContentType() != null ? MediaType.parseMediaType(att.getContentType()) : MediaType.IMAGE_JPEG)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + att.getFileName() + "\"")
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(org.springframework.http.HttpHeaders.PRAGMA, "no-cache")
                .header(org.springframework.http.HttpHeaders.EXPIRES, "0")
                .body(res);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void saveProfileParams(String name, String address, String contactNo) {
        if (name      != null) settingsRegistry.save("HOSPITAL_PARAM", "hospital.name.param",      name);
        if (address   != null) settingsRegistry.save("HOSPITAL_PARAM", "hospital.address.param",   address);
        if (contactNo != null) settingsRegistry.save("HOSPITAL_PARAM", "hospital.contactNo.param", contactNo);
    }
}
