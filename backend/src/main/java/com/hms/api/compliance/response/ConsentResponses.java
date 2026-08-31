package com.hms.api.compliance.response;

import com.hms.application.compliance.ConsentPurpose;
import com.hms.infrastructure.persistence.compliance.ConsentNoticeEntity;
import com.hms.infrastructure.persistence.compliance.ConsentRecordEntity;

import java.time.Instant;
import java.util.UUID;

/** Response shapes for the consent management surface — WO-023. */
public final class ConsentResponses {

    private ConsentResponses() {}

    /**
     * One consent record.
     *
     * <p>{@code provenance} is exposed deliberately. A SYSTEM_INFERRED row is
     * visible in the history as what it is — a grant the pre-V205 system
     * manufactured — rather than being hidden or quietly presented as real
     * consent. An operator looking at this screen should be able to see why a
     * patient is being asked again.
     *
     * <p>{@code noticeTextHash} is included so the record can be tied back to the
     * exact text; the text itself comes from the notice endpoint.
     */
    public record ConsentRecord(
        UUID id,
        UUID patientId,
        ConsentPurpose purpose,
        String state,
        String provenance,
        boolean reliable,
        String noticeVersion,
        String noticeLanguage,
        String noticeTextHash,
        String captureChannel,
        UUID capturedBy,
        Instant grantedAt,
        Instant expiresAt,
        Instant withdrawnAt,
        String withdrawalChannel,
        boolean minor,
        boolean guardianVerified
    ) {
        public static ConsentRecord from(ConsentRecordEntity e) {
            boolean reliable = !"SYSTEM_INFERRED".equals(e.getProvenance());
            return new ConsentRecord(
                e.getId(), e.getPatientId(),
                ConsentPurpose.valueOf(e.getPurpose()), e.getState(),
                e.getProvenance(), reliable,
                e.getNoticeVersion(), e.getNoticeLanguage(), e.getNoticeTextHash(),
                e.getCaptureChannel(), e.getCapturedBy(),
                e.getGrantedAt(), e.getExpiresAt(),
                e.getWithdrawnAt(), e.getWithdrawalChannel(),
                e.isMinor(), e.isGuardianVerified());
        }
    }

    /**
     * Live state for one purpose, with the notice that would be shown if the
     * patient were asked now.
     *
     * <p>{@code noticeIsDraft} surfaces the V205/V207 placeholder problem in the
     * interface instead of only in a metric: an operator about to read a notice
     * aloud can see that it is not the real thing.
     */
    public record PurposeStatus(
        ConsentPurpose purpose,
        String summary,
        boolean requiredForCare,
        boolean granted,
        String noticeVersion,
        String noticeLanguage,
        String noticeText,
        boolean noticeIsDraft,
        boolean noticeMissing
    ) {
        public static PurposeStatus of(ConsentPurpose purpose, boolean granted,
                                       ConsentNoticeEntity notice) {
            return new PurposeStatus(
                purpose, purpose.getNoticeSummary(), purpose.isRequiredForCare(), granted,
                notice == null ? null : notice.getVersion(),
                notice == null ? null : notice.getLanguage(),
                notice == null ? null : notice.getBodyText(),
                notice != null && "DRAFT".equals(notice.getNoticeState()),
                notice == null);
        }
    }

    public record Notice(
        ConsentPurpose purpose,
        String version,
        String language,
        String bodyText,
        boolean draft
    ) {
        public static Notice from(ConsentNoticeEntity n) {
            return new Notice(
                ConsentPurpose.valueOf(n.getPurpose()), n.getVersion(), n.getLanguage(),
                n.getBodyText(), "DRAFT".equals(n.getNoticeState()));
        }
    }
}
