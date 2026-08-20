package com.hms.api.portal;

import com.hms.api.portal.request.PortalRequests;
import com.hms.api.portal.response.PortalResponses;
import com.hms.api.shared.ApiResponse;
import com.hms.application.portal.PortalPatientService;
import com.hms.security.HmsUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Authenticated patient self-service portal API surface.
 */
@RestController
@RequestMapping("/portal")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_PATIENT')")
public class PortalPatientController {

    private final PortalPatientService patientService;

    private HmsUserDetails currentPrincipal() {
        return (HmsUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PortalResponses.PatientProfile>> getProfile() {
        HmsUserDetails principal = currentPrincipal();
        PortalResponses.PatientProfile profile = patientService.getProfile(
            principal.getId(), principal.getTenantId(), principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.ok("Profile", profile));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<PortalResponses.PatientProfile>> updateProfile(
            @Valid @RequestBody PortalRequests.UpdateProfile body) {
        HmsUserDetails principal = currentPrincipal();
        PortalResponses.PatientProfile profile = patientService.updateProfile(
            principal.getId(), principal.getTenantId(), principal.getBranchId(), body);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", profile));
    }

    @GetMapping("/consultants")
    public ResponseEntity<ApiResponse<List<PortalResponses.ConsultantSummary>>> listConsultants(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "departmentId", required = false) UUID departmentId) {
        List<PortalResponses.ConsultantSummary> list = patientService.listConsultants(query, departmentId);
        return ResponseEntity.ok(ApiResponse.ok("Consultants", list));
    }

    @GetMapping("/consultants/{consultantId}/availability")
    public ResponseEntity<ApiResponse<List<PortalResponses.SlotAvailability>>> getAvailability(
            @PathVariable UUID consultantId,
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<PortalResponses.SlotAvailability> availability = patientService.getAvailability(consultantId, date);
        return ResponseEntity.ok(ApiResponse.ok("Availability", availability));
    }

    @GetMapping("/appointments")
    public ResponseEntity<ApiResponse<PortalResponses.PageResponse<PortalResponses.AppointmentSummary>>> listAppointments(
            @RequestParam(name = "scope", required = false, defaultValue = "upcoming") String scope,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size) {
        HmsUserDetails principal = currentPrincipal();
        PortalResponses.PageResponse<PortalResponses.AppointmentSummary> result =
            patientService.listAppointments(principal.getId(), scope, page, size);
        return ResponseEntity.ok(ApiResponse.ok("Appointments", result));
    }

    @PostMapping("/appointments")
    public ResponseEntity<ApiResponse<PortalResponses.AppointmentSummary>> bookAppointment(
            @Valid @RequestBody PortalRequests.BookAppointment body) {
        HmsUserDetails principal = currentPrincipal();
        PortalResponses.AppointmentSummary booked = patientService.bookAppointment(
            principal.getId(), principal.getTenantId(), principal.getBranchId(), body);
        return ResponseEntity.ok(ApiResponse.ok("Appointment booked", booked));
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    public ResponseEntity<ApiResponse<PortalResponses.AppointmentSummary>> cancelAppointment(
            @PathVariable UUID appointmentId,
            @RequestBody(required = false) PortalRequests.CancelAppointment body) {
        HmsUserDetails principal = currentPrincipal();
        String reason = body != null ? body.reason() : null;
        PortalResponses.AppointmentSummary cancelled = patientService.cancelAppointment(
            principal.getId(), appointmentId, reason);
        return ResponseEntity.ok(ApiResponse.ok("Appointment cancelled", cancelled));
    }

    @GetMapping("/visits")
    public ResponseEntity<ApiResponse<PortalResponses.PageResponse<PortalResponses.VisitSummary>>> listVisits(
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size) {
        HmsUserDetails principal = currentPrincipal();
        PortalResponses.PageResponse<PortalResponses.VisitSummary> result =
            patientService.listVisits(principal.getId(), page, size);
        return ResponseEntity.ok(ApiResponse.ok("Visits", result));
    }

    @GetMapping("/visits/{encounterId}")
    public ResponseEntity<ApiResponse<PortalResponses.VisitDetail>> getVisitDetail(
            @PathVariable UUID encounterId) {
        HmsUserDetails principal = currentPrincipal();
        PortalResponses.VisitDetail detail = patientService.getVisitDetail(principal.getId(), encounterId);
        return ResponseEntity.ok(ApiResponse.ok("Visit detail", detail));
    }

    @GetMapping("/visits/{encounterId}/casesheet")
    public ResponseEntity<ApiResponse<List<PortalResponses.CaseSheetSection>>> getCaseSheet(
            @PathVariable UUID encounterId) {
        HmsUserDetails principal = currentPrincipal();
        List<PortalResponses.CaseSheetSection> sections = patientService.getCaseSheetSections(principal.getId(), encounterId);
        return ResponseEntity.ok(ApiResponse.ok("Case sheets", sections));
    }

    @GetMapping("/visits/{encounterId}/discharge-summary")
    public ResponseEntity<ApiResponse<List<PortalResponses.CaseSheetSection>>> getDischargeSummary(
            @PathVariable UUID encounterId) {
        HmsUserDetails principal = currentPrincipal();
        List<PortalResponses.CaseSheetSection> sections = patientService.getDischargeSummary(principal.getId(), encounterId);
        return ResponseEntity.ok(ApiResponse.ok("Discharge summary", sections));
    }

    @GetMapping("/visits/{encounterId}/lab-reports")
    public ResponseEntity<ApiResponse<List<PortalResponses.DiagnosticOrderGroup>>> getLabReports(
            @PathVariable UUID encounterId) {
        HmsUserDetails principal = currentPrincipal();
        List<PortalResponses.DiagnosticOrderGroup> reports = patientService.getDiagnosticReports(principal.getId(), encounterId);
        return ResponseEntity.ok(ApiResponse.ok("Lab reports", reports));
    }

    @GetMapping("/visits/{encounterId}/diagnostic-reports")
    public ResponseEntity<ApiResponse<List<PortalResponses.DiagnosticOrderGroup>>> getDiagnosticReports(
            @PathVariable UUID encounterId) {
        HmsUserDetails principal = currentPrincipal();
        List<PortalResponses.DiagnosticOrderGroup> reports = patientService.getDiagnosticReports(principal.getId(), encounterId);
        return ResponseEntity.ok(ApiResponse.ok("Diagnostic reports", reports));
    }

    @GetMapping("/visits/{encounterId}/prescriptions")
    public ResponseEntity<ApiResponse<List<PortalResponses.PrescriptionSummary>>> getPrescriptions(
            @PathVariable UUID encounterId) {
        HmsUserDetails principal = currentPrincipal();
        List<PortalResponses.PrescriptionSummary> prescriptions = patientService.getPrescriptions(principal.getId(), encounterId);
        return ResponseEntity.ok(ApiResponse.ok("Prescriptions", prescriptions));
    }

    @GetMapping("/visits/{encounterId}/bills")
    public ResponseEntity<ApiResponse<List<PortalResponses.BillSummary>>> getBills(
            @PathVariable UUID encounterId) {
        HmsUserDetails principal = currentPrincipal();
        List<PortalResponses.BillSummary> bills = patientService.getBills(principal.getId(), encounterId);
        return ResponseEntity.ok(ApiResponse.ok("Bills", bills));
    }

    @GetMapping("/visits/{encounterId}/print")
    public ResponseEntity<ApiResponse<com.hms.api.printtemplate.response.PrintOutputResponse>> getVisitPrint(
            @PathVariable UUID encounterId,
            @RequestParam String templateType,
            @RequestParam(required = false) UUID id) {
        HmsUserDetails principal = currentPrincipal();
        com.hms.api.printtemplate.response.PrintOutputResponse output =
            patientService.getVisitPrint(principal.getId(), encounterId, templateType, id);
        return ResponseEntity.ok(ApiResponse.ok("Print output", output));
    }

    @GetMapping("/visits/{encounterId}/attachments")
    public ResponseEntity<ApiResponse<List<PortalResponses.AttachmentMeta>>> getAttachments(
            @PathVariable UUID encounterId) {
        HmsUserDetails principal = currentPrincipal();
        List<PortalResponses.AttachmentMeta> list = patientService.getAttachments(principal.getId(), encounterId);
        return ResponseEntity.ok(ApiResponse.ok("Attachments", list));
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<ApiResponse<PortalResponses.SignedDownload>> getDownload(
            @PathVariable UUID attachmentId) {
        HmsUserDetails principal = currentPrincipal();
        PortalResponses.SignedDownload download = patientService.getDownloadUrl(principal.getId(), attachmentId);
        return ResponseEntity.ok(ApiResponse.ok("Download URL", download));
    }

    @GetMapping("/attachments/{attachmentId}/content")
    public ResponseEntity<Resource> downloadContent(@PathVariable UUID attachmentId) {
        HmsUserDetails principal = currentPrincipal();
        Resource res = patientService.downloadAttachmentContent(principal.getId(), attachmentId);
        com.hms.domain.attachment.model.Attachment att = patientService.getAttachmentEntity(principal.getId(), attachmentId);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
            .filename(att.getFileName())
            .build();
        return ResponseEntity.ok()
            .contentType(att.getContentType() != null ? MediaType.parseMediaType(att.getContentType()) : MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .body(res);
    }
}
