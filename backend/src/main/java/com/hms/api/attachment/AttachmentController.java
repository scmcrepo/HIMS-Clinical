package com.hms.api.attachment;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.application.attachment.AttachmentService;
import com.hms.domain.attachment.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
@RestController @RequestMapping("/attachment") @RequiredArgsConstructor
@PreAuthorize("hasPermission('ATTACHMENT','')")
public class AttachmentController {
    private final AttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Attachment>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "attachmentType") AttachmentType attachmentType,
            @RequestParam(name = "encounterId", required = false) UUID encounterId,
            @RequestParam(name = "patientId", required = false) UUID patientId,
            @RequestParam(name = "providerId", required = false) UUID providerId,
            @RequestParam(name = "category", required = false) String category) throws IOException {
        Attachment saved = attachmentService.saveAttachment(file, attachmentType, encounterId, patientId, providerId, category);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("File uploaded successfully", saved));
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<ApiResponse<Attachment>> getById(@PathVariable("attachmentId") UUID attachmentId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", attachmentService.getById(attachmentId)));
    }

    @GetMapping("/encounter/{encounterId}")
    public ResponseEntity<ApiResponse<List<Attachment>>> getByEncounter(@PathVariable("encounterId") UUID encounterId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", attachmentService.getByEncounter(encounterId)));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<Attachment>>> getByPatient(@PathVariable("patientId") UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", attachmentService.getByPatient(patientId)));
    }

    @GetMapping("/download/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable("attachmentId") UUID attachmentId) {
        Attachment att = attachmentService.getById(attachmentId);
        Resource   res = attachmentService.downloadFile(attachmentId);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
            .filename(att.getFileName())
            .build();
        return ResponseEntity.ok()
            .contentType(att.getContentType() != null ? MediaType.parseMediaType(att.getContentType()) : MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .body(res);
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("attachmentId") UUID attachmentId) {
        attachmentService.deleteAttachment(attachmentId);
        return ResponseEntity.ok(ApiResponse.ok("Attachment deleted successfully"));
    }
}
