package com.hms.application.diagnostic;

import com.hms.domain.diagnostic.model.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.diagnostic.DiagnosticReportJpaRepository;
import com.hms.infrastructure.persistence.diagnostic.DiagnosticOrderJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiagnosticReportService {

    private final DiagnosticReportJpaRepository reportRepo;
    private final DiagnosticOrderJpaRepository orderRepo;

    /**
     * Save/update lab reports — batch save per lab template detail parameter.
     * Map key = labTemplateDetailId, value = entered result value.
     */
    @Transactional
    public List<DiagnosticReport> saveLabReports(UUID orderLineId, UUID templateId,
                                                  Map<String, String> reportValues) {
        for (Map.Entry<String, String> entry : reportValues.entrySet()) {
            UUID ltdId = UUID.fromString(entry.getKey());
            String value = entry.getValue();

            Optional<DiagnosticReport> existing = reportRepo.findByDiagnosticOrderLineId(orderLineId)
                .stream()
                .filter(r -> ltdId.equals(r.getLabTemplateDetailId()))
                .findFirst();

            if (existing.isPresent()) {
                DiagnosticReport r = existing.get();
                if (value != null && !value.isBlank()) {
                    r.setValue(value);
                    reportRepo.save(r);
                } else {
                    reportRepo.delete(r);
                }
            } else if (value != null && !value.isBlank()) {
                DiagnosticReport r = new DiagnosticReport();
                r.setDiagnosticOrderLineId(orderLineId);
                r.setDiagnosticTemplateId(templateId);
                r.setLabTemplateDetailId(ltdId);
                r.setValue(value);
                r.setIsApproved(false);
                reportRepo.save(r);
            }
        }

        // Auto-advance test status to RESULTED when reports are saved
        List<DiagnosticReport> savedReports = reportRepo.findByDiagnosticOrderLineId(orderLineId);
        if (!savedReports.isEmpty()) {
            orderRepo.findByLineId(orderLineId).ifPresent(order -> {
                order.getLines().stream()
                     .filter(l -> l.getId().equals(orderLineId))
                     .findFirst()
                     .ifPresent(line -> {
                         line.setTestStatus(DiagnosticTestStatus.RESULTED);
                         line.setResultValue("Report saved");
                         line.setResultRecordedAt(java.time.Instant.now());
                     });

                // Auto-advance order status when all non-cancelled lines are resulted
                boolean allResulted = order.getLines().stream()
                        .filter(l -> l.getTestStatus() != DiagnosticTestStatus.CANCELLED)
                        .allMatch(l -> l.getTestStatus() == DiagnosticTestStatus.RESULTED);
                if (allResulted) {
                    order.setTestStatus(DiagnosticTestStatus.RESULTED);
                }
                orderRepo.save(order);
            });
        }

        return savedReports;
    }

    @Transactional(readOnly = true)
    public List<DiagnosticReport> getReportsByOrderLine(UUID orderLineId) {
        return reportRepo.findByDiagnosticOrderLineId(orderLineId);
    }

    @Transactional(readOnly = true)
    public List<DiagnosticReport> getReportsByEncounter(UUID encounterId) {
        List<DiagnosticOrder> orders = orderRepo.findByEncounterId(encounterId);
        List<UUID> lineIds = orders.stream()
            .flatMap(o -> o.getLines().stream())
            .map(DiagnosticOrderLine::getId)
            .collect(Collectors.toList());
        if (lineIds.isEmpty()) return List.of();
        return reportRepo.findByOrderLineIds(lineIds);
    }

    @Transactional
    public DiagnosticReport saveCustomReport(UUID orderLineId, UUID templateId, String templateData) {
        Optional<DiagnosticReport> existing = reportRepo.findCustomReport(orderLineId, templateId);
        DiagnosticReport report;
        if (existing.isPresent()) {
            report = existing.get();
            report.setTemplateData(templateData);
        } else {
            report = new DiagnosticReport();
            report.setDiagnosticOrderLineId(orderLineId);
            report.setDiagnosticTemplateId(templateId);
            report.setTemplateData(templateData);
            report.setIsApproved(false);
        }
        DiagnosticReport saved = reportRepo.save(report);

        // Auto-advance test status to RESULTED when custom report is saved
        orderRepo.findByLineId(orderLineId).ifPresent(order -> {
            order.getLines().stream()
                 .filter(l -> l.getId().equals(orderLineId))
                 .findFirst()
                 .ifPresent(line -> {
                     line.setTestStatus(DiagnosticTestStatus.RESULTED);
                     line.setResultValue("Report saved");
                     line.setResultRecordedAt(java.time.Instant.now());
                 });

            boolean allResulted = order.getLines().stream()
                    .filter(l -> l.getTestStatus() != DiagnosticTestStatus.CANCELLED)
                    .allMatch(l -> l.getTestStatus() == DiagnosticTestStatus.RESULTED);
            if (allResulted) {
                order.setTestStatus(DiagnosticTestStatus.RESULTED);
            }
            orderRepo.save(order);
        });

        return saved;
    }

    @Transactional(readOnly = true)
    public DiagnosticReport getCustomReport(UUID orderLineId, UUID templateId) {
        return reportRepo.findCustomReport(orderLineId, templateId).orElse(null);
    }
}
