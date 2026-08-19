import React, { useState } from "react";
import { Image, Pressable, StyleSheet, Text, View, Linking } from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { formatIsoDate, formatFileSize } from "../../core/format";
import { t } from "../../i18n";
import {
  Badge,
  Body,
  Caption,
  Card,
  EmptyState,
  ErrorBanner,
  Heading,
  Loading,
  Row,
  Screen,
  Title,
  BackButton,
} from "../../ui/components";
import { colors, radius, spacing, typography } from "../../ui/tokens";
import { PortalError } from "../../core/errors";
import type {
  CaseSheetSection,
  DiagnosticOrderGroup,
  AttachmentMeta,
} from "../../core/contracts";

/**
 * Screen 12 — Visit detail with four tabs (PRD §7, WO-019 §4.7).
 *
 * Clinical data is memory-only (WO-019 §4.5): QueryKeys.visitDetail,
 * casesheet, labReports, diagnosticReports, and attachments are all in
 * the CLINICAL_ROOTS set and are never persisted to disk.
 */

/** Check if a string looks like a base64 image data URI. */
function isBase64Image(v: unknown): v is string {
  return typeof v === "string" && v.startsWith("data:image/");
}

/** Extract image URIs from a field value (may be nested in objects/arrays). */
function extractImageUris(value: unknown): string[] {
  if (isBase64Image(value)) return [value];
  if (Array.isArray(value)) {
    return value.flatMap((item) => {
      if (isBase64Image(item)) return [item];
      if (typeof item === "object" && item !== null) {
        // Take the last base64 image per object — the edited/updated image
        // comes after the template image in the object's properties
        const images = Object.values(item).filter(isBase64Image);
        return images.length > 0 ? [images[images.length - 1]!] : [];
      }
      return [];
    });
  }
  if (typeof value === "object" && value !== null) {
    const images = Object.values(value).filter(isBase64Image);
    return images.length > 0 ? [images[images.length - 1]!] : [];
  }
  return [];
}

/** Format a case-sheet field value for display, handling objects/arrays. */
function formatFieldValue(value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
    // Don't render base64 strings as text
    if (isBase64Image(value)) return "";
    return String(value);
  }
  if (Array.isArray(value)) {
    return value
      .map((item) => {
        if (isBase64Image(item)) return null;
        if (typeof item === "object" && item !== null) {
          return Object.values(item)
            .filter((v) => v != null && v !== "" && !isBase64Image(v))
            .join(", ");
        }
        return String(item);
      })
      .filter(Boolean)
      .join(" | ") || "—";
  }
  if (typeof value === "object") {
    return Object.values(value)
      .filter((v) => v != null && v !== "" && !isBase64Image(v))
      .join(", ") || "—";
  }
  return String(value);
}

const TABS = ["casesheet", "lab", "diagnostic", "attachments"] as const;
type TabKey = (typeof TABS)[number];

const TAB_LABELS: Record<TabKey, string> = {
  casesheet: "visit.tab.casesheet",
  lab: "visit.tab.lab",
  diagnostic: "visit.tab.diagnostic",
  attachments: "visit.tab.attachments",
};

export default function VisitDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { api } = useContainer();
  const [activeTab, setActiveTab] = useState<TabKey>("casesheet");

  const visit = useQuery({
    queryKey: QueryKeys.visitDetail(id),
    queryFn: () => api.getVisit(id),
    enabled: !!id,
  });

  if (visit.isLoading) return <Loading />;

  if (visit.isError) {
    const err = visit.error as PortalError;
    return (
      <Screen>
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

      {/* Visit header */}
      {detail ? (
        <Card>
          <Title>{detail.consultantName ?? "—"}</Title>
          <Body>
            {formatIsoDate(detail.visitDate)} · {detail.encounterType}
          </Body>
          {detail.departmentName ? (
            <Caption>{detail.departmentName}</Caption>
          ) : null}
          {detail.branchName ? (
            <Caption>{detail.branchName}</Caption>
          ) : null}
          {detail.diagnosis ? (
            <View style={styles.diagnosisBox}>
              <Caption>Diagnosis</Caption>
              <Body>{detail.diagnosis}</Body>
            </View>
          ) : null}
          <Badge label={detail.status} />
        </Card>
      ) : null}

      {/* Tab bar */}
      <View style={styles.tabBar}>
        {TABS.map((tab) => {
          const count = detail?.counts?.[tab === "attachments" ? "attachments" : tab === "lab" ? "labReports" : tab === "diagnostic" ? "diagnosticReports" : "casesheet"] ?? 0;
          const isActive = activeTab === tab;
          return (
            <Pressable
              key={tab}
              onPress={() => setActiveTab(tab)}
              style={[styles.tab, isActive && styles.tabActive]}
            >
              <Text style={[styles.tabLabel, isActive && styles.tabLabelActive]}>
                {t(TAB_LABELS[tab])}
                {count > 0 ? ` (${count})` : ""}
              </Text>
            </Pressable>
          );
        })}
      </View>

      {/* Tab content */}
      {activeTab === "casesheet" && <CasesheetTab encounterId={id} />}
      {activeTab === "lab" && <LabTab encounterId={id} />}
      {activeTab === "diagnostic" && <DiagnosticTab encounterId={id} />}
      {activeTab === "attachments" && <AttachmentsTab encounterId={id} />}
    </Screen>
  );
}

/* ---------- Casesheet tab ---------- */

function CasesheetTab({ encounterId }: { encounterId: string }) {
  const { api } = useContainer();
  const query = useQuery({
    queryKey: QueryKeys.casesheet(encounterId),
    queryFn: () => api.getCasesheet(encounterId),
  });

  if (query.isLoading) return <Loading />;
  if (query.isError) return <ErrorBanner messageKey="error.UNKNOWN" />;
  if (!query.data?.length)
    return <EmptyState messageKey="visit.noApprovedReports" />;

  return (
    <View style={styles.tabContent}>
      {query.data.map((section: CaseSheetSection, i: number) => (
        <Card key={`cs-${i}`}>
          <Heading>{section.templateName}</Heading>
          {section.recordedBy ? (
            <Caption>
              {section.recordedBy}
              {section.recordedAt ? ` · ${formatIsoDate(section.recordedAt)}` : ""}
            </Caption>
          ) : null}
          {section.fields.map((f, j) => {
            const imageUris = extractImageUris(f.value);
            const hasImages = imageUris.length > 0;
            const textValue = hasImages ? null : formatFieldValue(f.value);
            return (
              <View key={`f-${j}`}>
                {hasImages ? null : (
                  <Row label={f.label} value={textValue ?? "—"} />
                )}
                {hasImages ? (
                  <View style={styles.imageGrid}>
                    {imageUris.map((uri, k) => (
                      <Image
                        key={`img-${j}-${k}`}
                        source={{ uri }}
                        style={styles.caseSheetImage}
                        resizeMode="contain"
                      />
                    ))}
                  </View>
                ) : null}
              </View>
            );
          })}
        </Card>
      ))}
    </View>
  );
}

/* ---------- Lab reports tab ---------- */

function LabTab({ encounterId }: { encounterId: string }) {
  const { api } = useContainer();
  const query = useQuery({
    queryKey: QueryKeys.labReports(encounterId),
    queryFn: () => api.getLabReports(encounterId),
  });

  if (query.isLoading) return <Loading />;
  if (query.isError) return <ErrorBanner messageKey="error.UNKNOWN" />;
  if (!query.data?.length)
    return <EmptyState messageKey="visit.noApprovedReports" />;

  return <DiagnosticGroupList groups={query.data} />;
}

/* ---------- Diagnostic reports tab ---------- */

function DiagnosticTab({ encounterId }: { encounterId: string }) {
  const { api } = useContainer();
  const query = useQuery({
    queryKey: QueryKeys.diagnosticReports(encounterId),
    queryFn: () => api.getDiagnosticReports(encounterId),
  });

  if (query.isLoading) return <Loading />;
  if (query.isError) return <ErrorBanner messageKey="error.UNKNOWN" />;
  if (!query.data?.length)
    return <EmptyState messageKey="visit.noApprovedReports" />;

  return <DiagnosticGroupList groups={query.data} />;
}

/** Shared renderer for lab and diagnostic order groups. */
function DiagnosticGroupList({ groups }: { groups: DiagnosticOrderGroup[] }) {
  return (
    <View style={styles.tabContent}>
      {groups.map((group) => (
        <Card key={group.orderId}>
          <View style={styles.groupHeader}>
            <Heading>{group.sequenceNumber ?? group.orderId.slice(0, 8)}</Heading>
            <Badge
              label={group.status}
              tone={
                group.status === "RESULTED"
                  ? "success"
                  : group.status === "CANCELLED"
                    ? "danger"
                    : "warning"
              }
            />
          </View>
          <Caption>{formatIsoDate(group.orderDate)}</Caption>
          {group.lines.map((line) => (
            <View key={line.reportId} style={styles.reportLine}>
              <Body>{line.testName}</Body>
              <View style={styles.reportValues}>
                <Text style={styles.reportValue}>
                  {line.value ?? "—"}
                  {line.unit ? ` ${line.unit}` : ""}
                </Text>
                {line.referenceRange ? (
                  <Caption>Ref: {line.referenceRange}</Caption>
                ) : null}
                {line.result ? (
                  <Badge
                    label={line.result}
                    tone={
                      line.result === "NORMAL"
                        ? "success"
                        : line.result === "ABNORMAL" ||
                            line.result === "HIGH" ||
                            line.result === "LOW"
                          ? "danger"
                          : "neutral"
                    }
                  />
                ) : null}
              </View>
            </View>
          ))}
        </Card>
      ))}
    </View>
  );
}

/* ---------- Attachments tab ---------- */

function AttachmentsTab({ encounterId }: { encounterId: string }) {
  const { api } = useContainer();
  const query = useQuery({
    queryKey: QueryKeys.attachments(encounterId),
    queryFn: () => api.listAttachments(encounterId),
  });

  if (query.isLoading) return <Loading />;
  if (query.isError) return <ErrorBanner messageKey="error.UNKNOWN" />;
  if (!query.data?.length)
    return <EmptyState messageKey="visit.noApprovedReports" />;

  return (
    <View style={styles.tabContent}>
      {query.data.map((att: AttachmentMeta) => (
        <AttachmentRow key={att.attachmentId} attachment={att} />
      ))}
    </View>
  );
}

function AttachmentRow({ attachment }: { attachment: AttachmentMeta }) {
  const { api } = useContainer();
  const [busy, setBusy] = useState(false);

  const handleDownload = async () => {
    setBusy(true);
    try {
      const { url } = await api.getAttachmentDownload(attachment.attachmentId);
      await Linking.openURL(url);
    } catch {
      // Error is transient; the patient can tap again.
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <View style={styles.attachRow}>
        <View style={{ flex: 1 }}>
          <Body>{attachment.fileName}</Body>
          <Caption>
            {attachment.category ?? attachment.contentType}
            {attachment.sizeBytes ? ` · ${formatFileSize(attachment.sizeBytes)}` : ""}
          </Caption>
          <Caption>{formatIsoDate(attachment.uploadedAt)}</Caption>
        </View>
        <Pressable
          onPress={handleDownload}
          disabled={busy}
          style={styles.downloadBtn}
          hitSlop={8}
        >
          <Text style={[styles.downloadText, busy && { opacity: 0.5 }]}>
            {t("common.download")}
          </Text>
        </Pressable>
      </View>
    </Card>
  );
}

/* ---------- Styles ---------- */

const styles = StyleSheet.create({
  back: {
    ...typography.label,
    color: colors.primary,
    paddingVertical: spacing.xs,
  },
  diagnosisBox: {
    marginTop: spacing.sm,
    paddingTop: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    gap: spacing.xs,
  },
  tabBar: {
    flexDirection: "row",
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: "hidden",
  },
  tab: {
    flex: 1,
    paddingVertical: spacing.sm,
    alignItems: "center",
  },
  tabActive: {
    backgroundColor: colors.primary,
  },
  tabLabel: {
    ...typography.caption,
    fontWeight: "600",
    color: colors.textMuted,
  },
  tabLabelActive: {
    color: colors.surface,
  },
  tabContent: {
    gap: spacing.md,
  },
  groupHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  reportLine: {
    paddingVertical: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    gap: spacing.xs,
  },
  reportValues: {
    flexDirection: "row",
    gap: spacing.md,
    alignItems: "center",
    flexWrap: "wrap",
  },
  reportValue: {
    ...typography.body,
    fontWeight: "600",
    color: colors.text,
  },
  attachRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
  },
  downloadBtn: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.primary,
  },
  downloadText: {
    ...typography.label,
    color: colors.primary,
  },
  imageFieldRow: {
    paddingVertical: spacing.sm,
  },
  imageFieldLabel: {
    ...typography.body,
    color: colors.textMuted,
  },
  imageGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: spacing.sm,
    paddingVertical: spacing.sm,
  },
  caseSheetImage: {
    width: "100%",
    height: 200,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceAlt,
  },
});
