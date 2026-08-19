package com.hms.api.portal.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Portal response payloads.
 *
 * <p>{@link OtpRequested} is the one worth reading twice: it says nothing about
 * whether the number is known to any hospital. That fact is itself disclosable —
 * someone probing numbers against a fertility or de-addiction clinic learns
 * something real from a "not found" — so the response is identical either way
 * and the difference surfaces only after the code is verified.
 */
public final class PortalResponses {

    private PortalResponses() {}

    public record OtpRequested(
        UUID challengeId,
        long expiresInSeconds,
        long resendAvailableInSeconds
    ) {}

    public record PatientCandidate(
        UUID patientId,
        String fullName,
        Integer age,
        String gender,
        String numberSequenceSuffix,
        String photoUrl
    ) {}

    public record BranchSummary(
        UUID branchId,
        String name,
        String code,
        String address,
        String contactNumber,
        boolean isDefault,
        boolean isActive
    ) {}

    public record HospitalCandidate(
        UUID tenantId,
        String tenantName,
        String address,
        String contactNumber,
        String logoUrl,
        List<PatientCandidate> patients,
        List<BranchSummary> branches
    ) {}

    public record OtpVerified(
        String identityToken,
        Instant identityTokenExpiresAt,
        List<HospitalCandidate> candidates
    ) {}

    public record SessionTokens(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt
    ) {}

    public record PatientProfile(
        UUID patientId,
        String fullName,
        Integer age,
        String gender,
        String bloodGroup,
        String numberSequenceSuffix,
        String photoUrl,
        boolean selfRegistered,
        String tenantName,
        String branchName,
        LocalDate dateOfBirth,
        String mobile,
        String email,
        String address
    ) {}

    public record ConsultantSummary(
        UUID consultantId,
        String fullName,
        String specialisation,
        String qualification,
        String departmentName,
        String consultantType,
        String photoUrl
    ) {}

    public record SlotAvailability(
        UUID slotId,
        String fromTime,
        String toTime,
        int maxPatients,
        int bookedCount,
        int availableCount,
        boolean isAvailable
    ) {}

    public record AppointmentSummary(
        UUID appointmentId,
        String appointmentDate,
        String fromTime,
        String toTime,
        String status,
        UUID consultantId,
        String consultantName,
        String departmentName,
        String branchName,
        String notes
    ) {}

    public record VisitSummary(
        UUID encounterId,
        Instant visitDate,
        String consultantName,
        String encounterType,
        String status
    ) {}

    public record VisitCounts(
        int casesheet,
        int labReports,
        int diagnosticReports,
        int attachments
    ) {}

    public record VisitDetail(
        UUID encounterId,
        Instant visitDate,
        String consultantName,
        String encounterType,
        String status,
        String branchName,
        String departmentName,
        String diagnosis,
        VisitCounts counts
    ) {}

    public record CaseSheetField(
        String key,
        String label,
        String type,
        Object value
    ) {}

    public record CaseSheetSection(
        String templateName,
        String visitType,
        String recordedBy,
        String recordedAt,
        List<CaseSheetField> fields
    ) {}

    public record DiagnosticReportLine(
        UUID reportId,
        String testName,
        String value,
        String unit,
        String referenceRange,
        String result,
        boolean isApproved
    ) {}

    public record DiagnosticOrderGroup(
        UUID orderId,
        String sequenceNumber,
        Instant orderDate,
        String status,
        List<DiagnosticReportLine> lines
    ) {}

    public record AttachmentMeta(
        UUID attachmentId,
        String fileName,
        String contentType,
        String category,
        Long sizeBytes,
        Instant uploadedAt
    ) {}

    public record SignedDownload(
        String url,
        Instant expiresAt
    ) {}

    public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {}
}
