import React, { useState } from "react";
import { StyleSheet, View } from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import * as Print from "expo-print";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { formatIsoDate } from "../../core/format";
import { t } from "../../i18n";
import {
  BackButton,
  Body,
  Caption,
  Card,
  ErrorBanner,
  Loading,
  Screen,
  SkeletonCard,
  Title,
} from "../../ui/components";
import { colors, spacing } from "../../ui/tokens";
import { PortalError } from "../../core/errors";
import { DocumentViewerModal } from "../../ui/visits/DocumentViewerModal";
import { CasesheetSectionCard } from "../../ui/visits/CasesheetSectionCard";
import { PrescriptionsSectionCard } from "../../ui/visits/PrescriptionsSectionCard";
import { DiagnosticOrdersSectionCard } from "../../ui/visits/DiagnosticOrdersSectionCard";
import { BillingSectionCard } from "../../ui/visits/BillingSectionCard";
import { DischargeSummarySectionCard } from "../../ui/visits/DischargeSummarySectionCard";
import { downloadHtml } from "../../ui/visits/helpers";

export default function VisitDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { api } = useContainer();

  const visit = useQuery({
    queryKey: QueryKeys.visitDetail(id),
    queryFn: () => api.getVisit(id),
    enabled: !!id,
  });

  const [docViewer, setDocViewer] = useState<{
    visible: boolean;
    title: string;
    html?: string;
    imageUri?: string;
    onDownload?: () => void;
  }>({
    visible: false,
    title: "",
  });

  const handleViewHtml = (title: string, html: string, onDownloadAction?: () => void) => {
    setDocViewer({
      visible: true,
      title,
      html,
      onDownload: onDownloadAction ?? (() => void downloadHtml(html)),
    });
  };

  const handleViewImage = (title: string, imageUri: string) => {
    setDocViewer({
      visible: true,
      title,
      imageUri,
      onDownload: () => void downloadHtml(imageUri),
    });
  };

  const isIp = visit.data?.encounterType === "IP";

  const casesheets = useQuery({
    queryKey: QueryKeys.casesheet(id),
    queryFn: () => api.getCasesheet(id),
    enabled: !!id && !isIp,
  });

  const dischargeSummaries = useQuery({
    queryKey: QueryKeys.dischargeSummary(id),
    queryFn: () => api.getDischargeSummary(id),
    enabled: !!id && isIp,
  });

  const prescriptions = useQuery({
    queryKey: QueryKeys.prescriptions(id),
    queryFn: () => api.getPrescriptions(id),
    enabled: !!id,
  });

  const diagnosticReports = useQuery({
    queryKey: QueryKeys.diagnosticReports(id),
    queryFn: () => api.getDiagnosticReports(id),
    enabled: !!id,
  });

  const bills = useQuery({
    queryKey: QueryKeys.bills(id),
    queryFn: () => api.getBills(id),
    enabled: !!id,
  });

  const attachments = useQuery({
    queryKey: QueryKeys.attachments(id),
    queryFn: () => api.listAttachments(id),
    enabled: !!id,
  });

  if (visit.isLoading) {
    return (
      <Screen>
        <BackButton onPress={() => router.back()} label={t("common.back")} />
        <SkeletonCard lines={4} />
        <SkeletonCard lines={3} />
        <SkeletonCard lines={3} />
      </Screen>
    );
  }

  if (visit.isError) {
    const err = visit.error as PortalError;
    return (
      <Screen>
        <BackButton onPress={() => router.back()} label={t("common.back")} />
        <ErrorBanner
          messageKey={err.message}
          correlationId={err.correlationId}
          onRetry={() => void visit.refetch()}
        />
      </Screen>
    );
  }

  const detail = visit.data;

  return (
    <Screen>
      {/* Back button */}
      <BackButton onPress={() => router.back()} label={t("common.back")} />

      {/* Visit Header */}
      {detail ? (
        <Card style={styles.headerCard}>
          <Title>{detail.consultantName ?? "—"}</Title>
          <Body>
            {formatIsoDate(detail.visitDate)} · {detail.encounterType}
          </Body>
          {detail.departmentName ? <Caption>{detail.departmentName}</Caption> : null}
          {detail.branchName ? <Caption>{detail.branchName}</Caption> : null}
          {detail.diagnosis ? (
            <View style={styles.diagnosisBox}>
              <Caption>Diagnosis</Caption>
              <Body>{detail.diagnosis}</Body>
            </View>
          ) : null}
        </Card>
      ) : null}

      <View style={styles.sectionsContainer}>
        {/* OP Patient: Show Casesheet first */}
        {!isIp && (
          <CasesheetSectionCard
            visit={detail}
            sections={casesheets.data ?? []}
            isLoading={casesheets.isLoading}
            onViewHtml={handleViewHtml}
          />
        )}

        {/* PRESCRIPTIONS */}
        <PrescriptionsSectionCard
          visit={detail}
          prescriptions={prescriptions.data ?? []}
          isLoading={prescriptions.isLoading}
          onViewHtml={handleViewHtml}
        />

        {/* DIAGNOSTIC & LAB ORDERS */}
        <DiagnosticOrdersSectionCard
          visit={detail}
          groups={diagnosticReports.data ?? []}
          attachments={attachments.data ?? []}
          isLoading={diagnosticReports.isLoading || attachments.isLoading}
          onViewHtml={handleViewHtml}
          onViewImage={handleViewImage}
        />

        {/* BILLING & INVOICES */}
        <BillingSectionCard
          visit={detail}
          bills={bills.data ?? []}
          isLoading={bills.isLoading}
          onViewHtml={handleViewHtml}
        />

        {/* IP Patient: Show Discharge Summary LAST */}
        {isIp && (
          <DischargeSummarySectionCard
            visit={detail}
            sections={dischargeSummaries.data ?? []}
            isLoading={dischargeSummaries.isLoading}
            onViewHtml={handleViewHtml}
          />
        )}
      </View>

      {/* Full-Screen Document & Image Viewer Modal */}
      <DocumentViewerModal
        visible={docViewer.visible}
        title={docViewer.title}
        html={docViewer.html}
        imageUri={docViewer.imageUri}
        onClose={() => setDocViewer((prev) => ({ ...prev, visible: false }))}
        onPrint={docViewer.html ? () => void Print.printAsync({ html: docViewer.html! }) : undefined}
        onDownload={docViewer.onDownload}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerCard: {
    borderLeftWidth: 4,
    borderLeftColor: colors.primary,
  },
  diagnosisBox: {
    marginTop: spacing.sm,
    padding: spacing.md,
    backgroundColor: colors.primarySoft,
    borderRadius: 8,
  },
  sectionsContainer: {
    gap: spacing.md,
  },
});
