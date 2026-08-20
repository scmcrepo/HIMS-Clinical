import React, { useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Linking,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import Constants from "expo-constants";
import * as Print from "expo-print";
import * as Sharing from "expo-sharing";
import * as FileSystem from "expo-file-system";
import { useContainer } from "../_layout";
import { QueryKeys } from "../../core/cachePolicy";
import { formatIsoDate, formatFileSize } from "../../core/format";
import { t } from "../../i18n";
import {
  Body,
  Caption,
  Card,
  ErrorBanner,
  Heading,
  Loading,
  Screen,
  Title,
  BackButton,
} from "../../ui/components";
import { colors, radius, spacing, typography } from "../../ui/tokens";
import { PortalError } from "../../core/errors";
import type {
  CaseSheetField,
  CaseSheetSection,
  DiagnosticOrderGroup,
  AttachmentMeta,
  PrescriptionSummary,
  BillSummary,
  ReceiptSummary,
  VisitDetail,
} from "../../core/contracts";

/** Check if a string looks like a base64 image data URI. */
function isBase64Image(v: unknown): v is string {
  return typeof v === "string" && v.startsWith("data:image/");
}

/** Extract image URIs from a field value. */
function extractImageUris(value: unknown): string[] {
  if (isBase64Image(value)) return [value];
  if (Array.isArray(value)) {
    return value.flatMap((item) => {
      if (isBase64Image(item)) return [item];
      if (typeof item === "object" && item !== null) {
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

/** Format a case-sheet field value for display. Returns null/empty if empty so empty fields are omitted. */
function formatFieldValue(value: unknown): string | null {
  if (value == null || value === "") return null;
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "string" || typeof value === "number") {
    if (isBase64Image(value)) return null;
    const str = String(value).trim();
    return str.length > 0 ? str : null;
  }
  if (Array.isArray(value)) {
    const joined = value
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
      .join(" | ");
    return joined.length > 0 ? joined : null;
  }
  if (typeof value === "object") {
    const joined = Object.values(value)
      .filter((v) => v != null && v !== "" && !isBase64Image(v))
      .join(", ");
    return joined.length > 0 ? joined : null;
  }
  return String(value);
}

/** Base styles identical to the website's print stylesheets. */
const BASE_PDF_CSS = `
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap');
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    color: #1f2937;
    margin: 0;
    padding: 20px;
    font-size: 11px;
    line-height: 1.5;
    background: #ffffff;
  }
  .header-container {
    display: flex;
    align-items: center;
    border-bottom: 2px solid #e5e7eb;
    padding-bottom: 12px;
    margin-bottom: 20px;
  }
  .logo-container {
    width: 60px;
    height: 60px;
    margin-right: 16px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .logo-container img {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
  }
  .hospital-details {
    flex-grow: 1;
  }
  .hospital-name {
    font-size: 16px;
    font-weight: 800;
    color: #111827;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin: 0 0 2px 0;
  }
  .hospital-info {
    font-size: 10px;
    color: #4b5563;
    margin: 0;
  }
  .patient-card {
    background-color: #f9fafb;
    border: 1px solid #e5e7eb;
    border-radius: 6px;
    padding: 10px 14px;
    margin-bottom: 20px;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 6px;
  }
  .info-item {
    font-size: 11px;
  }
  .info-label {
    font-weight: 600;
    color: #4b5563;
  }
  .info-val {
    color: #111827;
    font-weight: 500;
  }
  .section-title {
    font-size: 12px;
    font-weight: 700;
    color: #111827;
    border-bottom: 1.5px solid #d1d5db;
    padding-bottom: 3px;
    margin-top: 20px;
    margin-bottom: 10px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
  .group-title {
    font-size: 11px;
    font-weight: 700;
    color: #111827;
    border-bottom: 1px solid #e5e7eb;
    padding-bottom: 3px;
    margin-top: 14px;
    margin-bottom: 8px;
    text-transform: uppercase;
  }
  .field-row {
    margin-bottom: 8px;
    display: flex;
    flex-wrap: wrap;
    border-bottom: 1px solid #f3f4f6;
    padding-bottom: 4px;
  }
  .field-label {
    font-weight: 600;
    color: #374151;
    width: 180px;
    flex-shrink: 0;
  }
  .field-val {
    flex-grow: 1;
    color: #111827;
  }
  .print-table {
    width: 100%;
    border-collapse: collapse;
    margin-top: 6px;
    margin-bottom: 16px;
  }
  .print-table th, .print-table td {
    border: 1px solid #e5e7eb;
    padding: 6px 8px;
    text-align: left;
    font-size: 10px;
  }
  .print-table th {
    background-color: #f3f4f6;
    font-weight: 600;
    color: #374151;
  }
  .end-report {
    text-align: center;
    margin: 28px 0 15px;
    font-size: 10px;
    color: #4b5563;
    font-weight: 700;
    letter-spacing: 1px;
  }
  .signature-box {
    margin-top: 30px;
    text-align: right;
  }
  .signature-line {
    display: inline-block;
    border-top: 1px solid #111827;
    width: 200px;
    padding-top: 4px;
    text-align: center;
    font-size: 11px;
    font-weight: 600;
  }
  .footer {
    margin-top: 30px;
    padding-top: 12px;
    border-top: 1px solid #e5e7eb;
    display: flex;
    justify-content: space-between;
    font-size: 10px;
    color: #9ca3af;
  }
`;

/** Generates the website-style hospital header and patient info card. */
function renderPdfHeaderAndPatientCard(visit?: VisitDetail): string {
  const hospitalName = visit?.branchName ?? "Hospital";
  const hospitalAddress = visit?.hospitalAddress ?? "";
  const hospitalContact = visit?.hospitalContact ?? "";
  const logoUrl = visit?.hospitalLogoUrl;

  const patientName = visit?.patientName ?? "—";
  const patientNumber = visit?.patientNumber ?? "—";
  const consultantName = visit?.consultantName ?? "—";
  const qualification = visit?.consultantQualification ? ` (${visit.consultantQualification})` : "";
  const visitDate = formatIsoDate(visit?.visitDate ?? new Date().toISOString());

  return `
    <div class="header-container">
      ${
        logoUrl
          ? `<div class="logo-container"><img src="${logoUrl}" alt="Hospital Logo" /></div>`
          : `<div class="logo-container"><div style="width: 48px; height: 48px; border-radius: 8px; background: #0f766e; display: flex; align-items: center; justify-content: center;"><span style="color: #fff; font-size: 20px; font-weight: bold;">+</span></div></div>`
      }
      <div class="hospital-details">
        <h1 class="hospital-name">${hospitalName}</h1>
        ${hospitalAddress ? `<p class="hospital-info">${hospitalAddress}</p>` : ""}
        ${hospitalContact ? `<p class="hospital-info">${hospitalContact}</p>` : ""}
      </div>
    </div>

    <div class="patient-card">
      <div class="info-item">
        <span class="info-label">PATIENT NAME:</span>
        <span class="info-val">${patientName}</span>
      </div>
      <div class="info-item">
        <span class="info-label">PATIENT ID:</span>
        <span class="info-val">${patientNumber}</span>
      </div>
      <div class="info-item">
        <span class="info-label">CONSULTANT:</span>
        <span class="info-val">${consultantName}${qualification}</span>
      </div>
      <div class="info-item">
        <span class="info-label">VISIT DATE:</span>
        <span class="info-val">${visitDate}</span>
      </div>
    </div>
  `;
}

/** Helper to generate and open print/share dialog. */
async function printOrShareHtml(html: string) {
  try {
    const { uri } = await Print.printToFileAsync({ html });
    if (await Sharing.isAvailableAsync()) {
      await Sharing.shareAsync(uri, {
        UTI: ".pdf",
        mimeType: "application/pdf",
        dialogTitle: "Download Document",
      });
    } else {
      await Print.printAsync({ html });
    }
  } catch (err) {
    console.error("Print/PDF error", err);
    Alert.alert("Download Error", "Could not generate PDF document. Please try again.");
  }
}

export default function VisitDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { api } = useContainer();

  const visit = useQuery({
    queryKey: QueryKeys.visitDetail(id),
    queryFn: () => api.getVisit(id),
    enabled: !!id,
  });

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

      {/* Visit Header */}
      {detail ? (
        <Card>
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
          />
        )}

        {/* PRESCRIPTIONS */}
        <PrescriptionsSectionCard
          visit={detail}
          prescriptions={prescriptions.data ?? []}
          isLoading={prescriptions.isLoading}
        />

        {/* DIAGNOSTIC & LAB ORDERS */}
        <DiagnosticOrdersSectionCard
          visit={detail}
          groups={diagnosticReports.data ?? []}
          attachments={attachments.data ?? []}
          isLoading={diagnosticReports.isLoading || attachments.isLoading}
        />

        {/* BILLING & INVOICES */}
        <BillingSectionCard
          visit={detail}
          bills={bills.data ?? []}
          isLoading={bills.isLoading}
        />

        {/* IP Patient: Show Discharge Summary LAST */}
        {isIp && (
          <DischargeSummarySectionCard
            visit={detail}
            sections={dischargeSummaries.data ?? []}
            isLoading={dischargeSummaries.isLoading}
          />
        )}
      </View>
    </Screen>
  );
}

/* ========================================================================== */
/* SECTION 1: CASESHEET CARD (FOR OP)                                         */
/* ========================================================================== */

function CasesheetSectionCard({
  visit,
  sections,
  isLoading,
}: {
  visit?: VisitDetail;
  sections: CaseSheetSection[];
  isLoading: boolean;
}) {
  const [downloading, setDownloading] = useState(false);

  const handleDownloadPrint = async () => {
    if (!sections.length) {
      Alert.alert("Casesheet", "No recorded casesheet available for this visit.");
      return;
    }
    setDownloading(true);
    try {
      const doctorName = visit?.consultantName ?? "Doctor";

      let templateFieldsHtml = "";
      sections.forEach((sec) => {
        interface SectionGroup {
          title: string | null;
          fields: CaseSheetField[];
        }
        const groups: SectionGroup[] = [];
        const hasHeadings = sec.fields.some((f) => f.type === "HEADING");

        if (hasHeadings) {
          let currentGroup: SectionGroup = { title: null, fields: [] };
          for (const f of sec.fields) {
            if (f.type === "HEADING") {
              if (currentGroup.fields.length > 0 || currentGroup.title !== null) {
                groups.push(currentGroup);
              }
              currentGroup = { title: f.label, fields: [] };
            } else {
              currentGroup.fields.push(f);
            }
          }
          if (currentGroup.fields.length > 0 || currentGroup.title !== null) {
            groups.push(currentGroup);
          }
        } else {
          const sectionMap = new Map<string, CaseSheetField[]>();
          for (const f of sec.fields) {
            const key = f.section ?? "__root__";
            if (!sectionMap.has(key)) sectionMap.set(key, []);
            sectionMap.get(key)!.push(f);
          }
          Array.from(sectionMap.entries()).forEach(([title, fields]) => {
            groups.push({
              title: title === "__root__" ? null : title,
              fields,
            });
          });
        }

        templateFieldsHtml += `
          <div class="section-title">${sec.templateName}</div>
          <div class="template-content">
            ${groups
              .map((grp) => {
                let secHtml = "";
                if (grp.title) {
                  secHtml += `<h4 class="group-title">${grp.title}</h4>`;
                }

                const fieldsHtml = grp.fields
                  .map((f) => {
                    if (f.type === "HEADING") return "";
                    const val = formatFieldValue(f.value);
                    const imageUris = extractImageUris(f.value);

                    if (!val && imageUris.length === 0) return "";

                    if (imageUris.length > 0) {
                      return `
                        <div class="field-row">
                          <div class="field-label">${f.label}</div>
                          <div class="field-val">
                            ${imageUris
                              .map(
                                (img) =>
                                  `<img src="${img}" style="max-width: 100%; height: auto; max-height: 250px; border-radius: 4px; border: 1px solid #e5e7eb; margin-top: 4px;" />`
                              )
                              .join("")}
                          </div>
                        </div>
                      `;
                    }

                    return `
                      <div class="field-row">
                        <div class="field-label">${f.label}</div>
                        <div class="field-val">${val}</div>
                      </div>
                    `;
                  })
                  .join("");

                return secHtml + fieldsHtml;
              })
              .join("")}
          </div>
        `;
      });

      const html = `
        <!DOCTYPE html>
        <html>
          <head>
            <meta charset="utf-8" />
            <title>${visit?.encounterType ?? "OP"} Case Sheet - ${visit?.patientName ?? "Patient"}</title>
            <style>${BASE_PDF_CSS}</style>
          </head>
          <body>
            ${renderPdfHeaderAndPatientCard(visit)}

            ${templateFieldsHtml}

            <div class="end-report">--End of report--</div>

            <div class="signature-box">
              <div class="signature-line">${doctorName}</div>
              <div style="font-size: 10px; color: #6b7280; margin-top: 2px;">Consultant Signature</div>
            </div>

            <div class="footer">
              <span>HIMS Patient Health Record</span>
              <span>Generated on ${new Date().toLocaleDateString()}</span>
            </div>
          </body>
        </html>
      `;

      await printOrShareHtml(html);
    } finally {
      setDownloading(false);
    }
  };

  const templateName = sections.length > 0 ? sections[0]?.templateName : null;

  return (
    <Card>
      <View style={styles.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Casesheet</Heading>
          <Caption>
            {templateName ? `${templateName} · Recorded` : "Clinical examination and notes"}
          </Caption>
        </View>
      </View>

      <View style={styles.actionRow}>
        <Pressable
          onPress={handleDownloadPrint}
          disabled={downloading || isLoading}
          style={[styles.primaryActionBtn, (downloading || isLoading) && { opacity: 0.6 }]}
          hitSlop={8}
        >
          {downloading ? (
            <ActivityIndicator size="small" color={colors.surface} />
          ) : (
            <View style={styles.btnContent}>
              <DownloadTrayIcon color={colors.surface} size={16} />
              <Text style={styles.primaryActionBtnText}>Download Casesheet</Text>
            </View>
          )}
        </Pressable>
      </View>
    </Card>
  );
}

/* ========================================================================== */
/* SECTION 2: PRESCRIPTIONS CARD                                              */
/* ========================================================================== */

function PrescriptionsSectionCard({
  visit,
  prescriptions,
  isLoading,
}: {
  visit?: VisitDetail;
  prescriptions: PrescriptionSummary[];
  isLoading: boolean;
}) {
  const [downloading, setDownloading] = useState(false);

  const totalMedicines = prescriptions.reduce((acc, p) => acc + (p.items?.length ?? 0), 0);

  const handleDownloadPrint = async () => {
    if (!prescriptions.length || totalMedicines === 0) {
      Alert.alert("Prescriptions", "No prescription recorded for this visit.");
      return;
    }
    setDownloading(true);
    try {
      const doctorName = visit?.consultantName ?? "Doctor";

      let tablesHtml = "";
      prescriptions.forEach((prx, idx) => {
        tablesHtml += `
          <div class="section-title">Prescriptions ${prescriptions.length > 1 ? `#${idx + 1}` : ""}</div>
          <table class="print-table">
            <thead>
              <tr>
                <th style="width: 5%;">#</th>
                <th style="width: 35%;">Drug</th>
                <th style="width: 20%;">Frequency</th>
                <th style="width: 15%;">Duration</th>
                <th style="width: 10%;">Qty</th>
                <th style="width: 15%;">Instructions</th>
              </tr>
            </thead>
            <tbody>
              ${prx.items
                .map(
                  (item, i) => `
                <tr>
                  <td>${i + 1}</td>
                  <td><strong>${item.drugName}</strong>${item.route ? `<br/><small style="color: #64748b;">${item.route}</small>` : ""}</td>
                  <td>${item.frequency ?? "—"}</td>
                  <td>${item.duration ?? "—"}</td>
                  <td>${item.quantity ?? 1}</td>
                  <td>${item.instructions ?? item.remarks ?? "—"}</td>
                </tr>
              `
                )
                .join("")}
            </tbody>
          </table>
        `;
      });

      const html = `
        <!DOCTYPE html>
        <html>
          <head>
            <meta charset="utf-8" />
            <title>Doctor's Prescription (Rx)</title>
            <style>${BASE_PDF_CSS}</style>
          </head>
          <body>
            ${renderPdfHeaderAndPatientCard(visit)}

            <div style="font-size: 24px; font-weight: bold; color: #111827; margin: 12px 0 6px 0;">Rx</div>

            ${tablesHtml}

            <div class="end-report">--End of report--</div>

            <div class="signature-box">
              <div class="signature-line">${doctorName}</div>
              <div style="font-size: 10px; color: #6b7280; margin-top: 2px;">Authorized Medical Practitioner</div>
            </div>

            <div class="footer">
              <span>HIMS Patient Health Record</span>
              <span>Keep out of reach of children</span>
            </div>
          </body>
        </html>
      `;

      await printOrShareHtml(html);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <Card>
      <View style={styles.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Prescriptions</Heading>
          <Caption>
            {totalMedicines > 0
              ? `${totalMedicines} medication(s) prescribed`
              : "No active medications recorded"}
          </Caption>
        </View>
      </View>

      <View style={styles.actionRow}>
        <Pressable
          onPress={handleDownloadPrint}
          disabled={downloading || isLoading || totalMedicines === 0}
          style={[
            styles.primaryActionBtn,
            (downloading || isLoading || totalMedicines === 0) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          {downloading ? (
            <ActivityIndicator size="small" color={colors.surface} />
          ) : (
            <View style={styles.btnContent}>
              <DownloadTrayIcon color={colors.surface} size={16} />
              <Text style={styles.primaryActionBtnText}>Download Prescription</Text>
            </View>
          )}
        </Pressable>
      </View>
    </Card>
  );
}

/* ========================================================================== */
/* SECTION 3: DIAGNOSTIC & LAB ORDERS CARD                                    */
/* ========================================================================== */

function DiagnosticOrdersSectionCard({
  visit,
  groups,
  attachments,
  isLoading,
}: {
  visit?: VisitDetail;
  groups: DiagnosticOrderGroup[];
  attachments: AttachmentMeta[];
  isLoading: boolean;
}) {
  const container = useContainer();
  const [downloading, setDownloading] = useState(false);
  const [downloadingAttId, setDownloadingAttId] = useState<string | null>(null);

  const totalTests = groups.reduce((acc, g) => acc + (g.lines?.length ?? 0), 0);

  const handleDownloadReports = async () => {
    if (!groups.length || totalTests === 0) {
      Alert.alert("Reports", "No diagnostic or lab orders found for this visit.");
      return;
    }
    setDownloading(true);
    try {
      const allLines = groups.flatMap((g) => g.lines);
      const resultedLines = allLines.filter(
        (l) =>
          l.status === "RESULTED" ||
          (l.parameters && l.parameters.length > 0) ||
          l.templateData ||
          (l.value && l.value.trim().length > 0)
      );

      let ordersTableHtml = `
        <div class="section-title">DIAGNOSTIC ORDERS</div>
        <table class="print-table">
          <thead>
            <tr>
              <th>Test Name</th>
              <th>Category</th>
              <th>Status</th>
              <th>Date</th>
            </tr>
          </thead>
          <tbody>
            ${allLines
              .map(
                (item) => `
              <tr>
                <td><strong>${item.testName}</strong></td>
                <td>${item.category || "LAB"}</td>
                <td>${item.status === "RESULTED" ? "Result Entered" : item.status || "Result Entered"}</td>
                <td>${formatIsoDate(item.orderedAt || visit?.visitDate || new Date().toISOString())}</td>
              </tr>
            `
              )
              .join("")}
          </tbody>
        </table>
      `;

      let resultsHtml = "";
      if (resultedLines.length > 0) {
        resultsHtml = `
          <div class="section-title" style="margin-top: 24px; border-bottom: 2px solid #111827;">DIAGNOSTIC RESULTS</div>
          ${resultedLines
            .map((line) => {
              let itemHtml = `<h4 style="font-size: 13px; font-weight: 800; color: #111827; margin-top: 16px; margin-bottom: 8px; text-transform: uppercase;">${line.testName}</h4>`;

              if (line.category === "RADIOLOGY") {
                let findings = "Nil";
                let impression = "Nil";
                let conclusion = "Nil";
                if (line.templateData) {
                  try {
                    const parsed = JSON.parse(line.templateData);
                    if (parsed.findings) findings = parsed.findings;
                    if (parsed.impression) impression = parsed.impression;
                    if (parsed.conclusion) conclusion = parsed.conclusion;
                  } catch (e) {}
                }

                itemHtml += `
                  <div style="margin-bottom: 8px;">
                    <strong style="font-size: 10px; color: #4b5563; text-transform: uppercase;">FINDINGS:</strong>
                    <div style="margin-top: 2px; padding-left: 8px; font-size: 11px;">${findings}</div>
                  </div>
                  <div style="margin-bottom: 8px;">
                    <strong style="font-size: 10px; color: #4b5563; text-transform: uppercase;">IMPRESSION:</strong>
                    <div style="margin-top: 2px; padding-left: 8px; font-size: 11px;">${impression}</div>
                  </div>
                  <div style="margin-bottom: 8px;">
                    <strong style="font-size: 10px; color: #4b5563; text-transform: uppercase;">CONCLUSION:</strong>
                    <div style="margin-top: 2px; padding-left: 8px; font-size: 11px;">${conclusion}</div>
                  </div>
                `;
              } else {
                if (line.parameters && line.parameters.length > 0) {
                  itemHtml += `
                    <table class="print-table">
                      <thead>
                        <tr>
                          <th>Test Parameter</th>
                          <th style="text-align: center;">Result</th>
                          <th style="text-align: center;">Unit</th>
                          <th>Normal Range</th>
                        </tr>
                      </thead>
                      <tbody>
                        ${line.parameters
                          .map(
                            (p) => `
                          <tr>
                            <td>${p.name}</td>
                            <td style="text-align: center; font-weight: bold;">${p.result || "—"}</td>
                            <td style="text-align: center;">${p.unit || "—"}</td>
                            <td style="white-space: pre-wrap;">${p.normalRange || "—"}</td>
                          </tr>
                        `
                          )
                          .join("")}
                      </tbody>
                    </table>
                  `;
                } else {
                  itemHtml += `
                    <table class="print-table">
                      <thead>
                        <tr>
                          <th>Test Parameter</th>
                          <th style="text-align: center;">Result</th>
                          <th style="text-align: center;">Unit</th>
                          <th>Normal Range</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr>
                          <td>${line.testName}</td>
                          <td style="text-align: center; font-weight: bold;">${line.value || "—"}</td>
                          <td style="text-align: center;">${line.unit || "—"}</td>
                          <td style="white-space: pre-wrap;">${line.referenceRange || "—"}</td>
                        </tr>
                      </tbody>
                    </table>
                  `;
                }
              }

              return itemHtml;
            })
            .join("")}
        `;
      }

      const html = `
        <!DOCTYPE html>
        <html>
          <head>
            <meta charset="utf-8" />
            <title>Diagnostic Orders & Results - ${visit?.patientName ?? "Patient"}</title>
            <style>${BASE_PDF_CSS}</style>
          </head>
          <body>
            ${renderPdfHeaderAndPatientCard(visit)}

            ${ordersTableHtml}

            ${resultsHtml}

            <div class="end-report">--End of report--</div>

            <div class="footer">
              <span>HIMS Patient Health Record</span>
              <span>Generated on ${new Date().toLocaleDateString()}</span>
            </div>
          </body>
        </html>
      `;

      await printOrShareHtml(html);
    } finally {
      setDownloading(false);
    }
  };

  const handleDownloadAttachment = async (att: AttachmentMeta) => {
    setDownloadingAttId(att.attachmentId);
    try {
      const token = container.session.getAccessToken();
      const extra = (Constants.expoConfig?.extra ?? {}) as { apiBaseUrl?: string };
      const baseUrl = extra.apiBaseUrl ?? "";
      const downloadEndpoint = `${baseUrl}/portal/attachments/${att.attachmentId}/content`;

      const filename = att.fileName || `attachment_${att.attachmentId}`;
      const localUri = `${FileSystem.cacheDirectory}${filename}`;

      const res = await FileSystem.downloadAsync(downloadEndpoint, localUri, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });

      if (res.status === 200) {
        if (await Sharing.isAvailableAsync()) {
          await Sharing.shareAsync(res.uri, {
            UTI: att.contentType,
            mimeType: att.contentType || "application/octet-stream",
            dialogTitle: `Download ${filename}`,
          });
        } else {
          await Linking.openURL(res.uri);
        }
      } else {
        const { url } = await container.api.getAttachmentDownload(att.attachmentId);
        const fullUrl = url.startsWith("http") ? url : `${baseUrl}${url}`;
        await Linking.openURL(fullUrl);
      }
    } catch (err) {
      console.error("Attachment download error", err);
      Alert.alert("Download Error", "Could not download the attachment. Please try again.");
    } finally {
      setDownloadingAttId(null);
    }
  };

  return (
    <Card>
      <View style={styles.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Diagnostic & Lab Orders</Heading>
          <Caption>
            {totalTests > 0 ? `${totalTests} test order(s) placed` : "No diagnostic tests recorded"}
          </Caption>
        </View>
      </View>

      {totalTests > 0 && (
        <View style={styles.actionRow}>
          <Pressable
            onPress={handleDownloadReports}
            disabled={downloading || isLoading}
            style={[styles.primaryActionBtn, (downloading || isLoading) && { opacity: 0.6 }]}
            hitSlop={8}
          >
            {downloading ? (
              <ActivityIndicator size="small" color={colors.surface} />
            ) : (
              <View style={styles.btnContent}>
                <DownloadTrayIcon color={colors.surface} size={16} />
                <Text style={styles.primaryActionBtnText}>Download Reports</Text>
              </View>
            )}
          </Pressable>
        </View>
      )}

      {/* X-Rays, Scans & Attachments Subsection */}
      {attachments.length > 0 && (
        <View style={styles.attachmentSubsection}>
          <Text style={styles.subsectionTitle}>X-Rays & Scan Attachments ({attachments.length})</Text>
          <View style={styles.attachmentList}>
            {attachments.map((att) => {
              const isBusy = downloadingAttId === att.attachmentId;
              const isUuid =
                !!att.category &&
                /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(att.category.trim());
              const displayCategory = att.category && !isUuid ? att.category : null;
              const subtitleParts = [
                displayCategory,
                att.sizeBytes ? formatFileSize(att.sizeBytes) : null,
                att.uploadedAt ? formatIsoDate(att.uploadedAt) : null,
              ].filter(Boolean);

              return (
                <View key={att.attachmentId} style={styles.attachmentItemRow}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.attachmentName} numberOfLines={1}>
                      {att.fileName}
                    </Text>
                    {subtitleParts.length > 0 ? (
                      <Caption>{subtitleParts.join(" · ")}</Caption>
                    ) : null}
                  </View>

                  <Pressable
                    onPress={() => handleDownloadAttachment(att)}
                    disabled={isBusy}
                    style={styles.attachmentDownloadBtn}
                    hitSlop={8}
                  >
                    {isBusy ? (
                      <ActivityIndicator size="small" color={colors.primary} />
                    ) : (
                      <View style={styles.btnSmallContent}>
                        <DownloadTrayIcon color={colors.primary} size={13} />
                        <Text style={styles.attachmentDownloadText}>Download</Text>
                      </View>
                    )}
                  </Pressable>
                </View>
              );
            })}
          </View>
        </View>
      )}
    </Card>
  );
}

/* ========================================================================== */
/* SECTION 4: BILLING & INVOICES CARD (WITH RECEIPT SELECTION MODAL)           */
/* ========================================================================== */

function BillingSectionCard({
  visit,
  bills,
  isLoading,
}: {
  visit?: VisitDetail;
  bills: BillSummary[];
  isLoading: boolean;
}) {
  const { api } = useContainer();
  const [downloadingBill, setDownloadingBill] = useState(false);
  const [downloadingReceiptId, setDownloadingReceiptId] = useState<string | null>(null);
  const [showReceiptsModal, setShowReceiptsModal] = useState(false);

  const bill = bills.length > 0 ? bills[0] : null;
  const receipts = bill?.receipts ?? [];
  const isDraft = !bill || bill.status === "DRAFT" || !bill.billNumber || bill.billNumber.toLowerCase() === "draft";
  const billNumberDisplay = isDraft ? "Draft" : bill.billNumber;

  const handleDownloadBillPdf = async () => {
    if (!bill) {
      Alert.alert("Billing", "No bill or invoice generated for this visit yet.");
      return;
    }
    setDownloadingBill(true);
    try {
      if (visit?.encounterId) {
        try {
          const res = await api.getVisitPrint(visit.encounterId, "BILL", bill.billId);
          if (res?.printData) {
            await printOrShareHtml(res.printData);
            return;
          }
        } catch {
          // Fallback to client layout
        }
      }

      const html = `
        <!DOCTYPE html>
        <html>
          <head>
            <meta charset="utf-8" />
            <title>Hospital Bill & Invoice</title>
            <style>${BASE_PDF_CSS}</style>
          </head>
          <body>
            ${renderPdfHeaderAndPatientCard(visit)}

            <div class="section-title">PROVISIONAL BILL / INVOICE: ${bill.billNumber} (${bill.status})</div>
            <table class="print-table">
              <thead>
                <tr>
                  <th>Description</th>
                  <th style="text-align: right;">Amount (₹)</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Total Billable Services & Consultation</td>
                  <td style="text-align: right;">₹ ${bill.totalAmount.toFixed(2)}</td>
                </tr>
                <tr>
                  <td>Total Paid Amount</td>
                  <td style="text-align: right; color: #15803d;">₹ ${bill.paidAmount.toFixed(2)}</td>
                </tr>
                <tr style="font-weight: bold; background: #f9fafb;">
                  <td>Balance Due</td>
                  <td style="text-align: right; color: ${bill.balanceAmount > 0 ? "#dc2626" : "#15803d"};">
                    ₹ ${bill.balanceAmount.toFixed(2)}
                  </td>
                </tr>
              </tbody>
            </table>

            <div class="end-report">--End of report--</div>

            <div class="signature-box">
              <div class="signature-line">Billing Desk / Cashier</div>
              <div style="font-size: 10px; color: #6b7280; margin-top: 2px;">Official Payment Acknowledgement</div>
            </div>

            <div class="footer">
              <span>HIMS Patient Health Record</span>
              <span>Thank you for choosing our healthcare services</span>
            </div>
          </body>
        </html>
      `;

      await printOrShareHtml(html);
    } finally {
      setDownloadingBill(false);
    }
  };

  const handleDownloadSingleReceipt = async (receipt?: ReceiptSummary) => {
    if (!bill) {
      Alert.alert("Receipt", "No receipt generated for this visit yet.");
      return;
    }
    const receiptId = receipt?.receiptId ?? "default";
    setDownloadingReceiptId(receiptId);
    try {
      if (visit?.encounterId) {
        try {
          const res = await api.getVisitPrint(
            visit.encounterId,
            "OP_RECEIPT",
            receipt?.receiptId ?? bill.billId
          );
          if (res?.printData) {
            await printOrShareHtml(res.printData);
            return;
          }
        } catch {
          // Fallback to client layout
        }
      }

      const receiptNo = receipt?.receiptNumber ?? bill.billNumber;
      const receiptAmount = receipt ? receipt.amount : bill.paidAmount;
      const receiptDate = receipt ? formatIsoDate(receipt.receiptDate) : formatIsoDate(new Date().toISOString());
      const mode = receipt?.paymentMode ?? "CASH";

      const html = `
        <!DOCTYPE html>
        <html>
          <head>
            <meta charset="utf-8" />
            <title>Payment Receipt - ${receiptNo}</title>
            <style>${BASE_PDF_CSS}</style>
          </head>
          <body>
            ${renderPdfHeaderAndPatientCard(visit)}

            <div class="section-title">OFFICIAL PAYMENT RECEIPT: ${receiptNo}</div>
            <table class="print-table">
              <thead>
                <tr>
                  <th>Receipt Details</th>
                  <th style="text-align: right;">Amount Paid (₹)</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>
                    <strong>Payment Received</strong><br/>
                    <small style="color: #6b7280;">Mode: ${mode} &nbsp;|&nbsp; Date: ${receiptDate}</small>
                  </td>
                  <td style="text-align: right; font-weight: bold; color: #15803d;">₹ ${receiptAmount.toFixed(2)}</td>
                </tr>
                <tr style="background: #f9fafb;">
                  <td>Bill Balance Due</td>
                  <td style="text-align: right;">₹ ${bill.balanceAmount.toFixed(2)}</td>
                </tr>
              </tbody>
            </table>

            <div class="end-report">--End of report--</div>

            <div class="signature-box">
              <div class="signature-line">Authorized Cashier / Signatory</div>
              <div style="font-size: 10px; color: #6b7280; margin-top: 2px;">Official Receipt Acknowledgement</div>
            </div>

            <div class="footer">
              <span>HIMS Patient Health Record</span>
              <span>Computer generated payment acknowledgment</span>
            </div>
          </body>
        </html>
      `;

      await printOrShareHtml(html);
    } finally {
      setDownloadingReceiptId(null);
    }
  };

  const handleOpenReceiptsModal = () => {
    if (!bill || bill.paidAmount <= 0) {
      Alert.alert("Receipts", "No payment receipts recorded for this bill.");
      return;
    }
    setShowReceiptsModal(true);
  };

  return (
    <Card>
      <View style={styles.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Billing & Receipts</Heading>
          <Caption>
            {bill
              ? `${billNumberDisplay} · Total: ₹ ${bill.totalAmount.toFixed(2)}${bill.paidAmount > 0 ? ` · Paid: ₹ ${bill.paidAmount.toFixed(2)}` : ""}`
              : "No invoice or receipt recorded"}
          </Caption>
        </View>
      </View>

      <View style={[styles.actionRow, { flexDirection: "row", gap: spacing.sm }]}>
        <Pressable
          onPress={handleDownloadBillPdf}
          disabled={downloadingBill || isLoading || !bill}
          style={[
            styles.primaryActionBtn,
            { flex: 1 },
            (downloadingBill || isLoading || !bill) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          {downloadingBill ? (
            <ActivityIndicator size="small" color={colors.surface} />
          ) : (
            <View style={styles.btnContent}>
              <DownloadTrayIcon color={colors.surface} size={16} />
              <Text style={styles.primaryActionBtnText}>Download Bill</Text>
            </View>
          )}
        </Pressable>

        <Pressable
          onPress={handleOpenReceiptsModal}
          disabled={isLoading || !bill || bill.paidAmount <= 0}
          style={[
            styles.secondaryActionBtn,
            { flex: 1 },
            (isLoading || !bill || bill.paidAmount <= 0) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          <View style={styles.btnContent}>
            <DownloadTrayIcon color={colors.primary} size={16} />
            <Text style={styles.secondaryActionBtnText}>
              Download Receipts {receipts.length > 0 ? `(${receipts.length})` : ""}
            </Text>
          </View>
        </Pressable>
      </View>

      {/* Receipts Selection Modal */}
      <Modal
        visible={showReceiptsModal}
        transparent
        animationType="fade"
        onRequestClose={() => setShowReceiptsModal(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.modalContainer}>
            <View style={styles.modalHeader}>
              <View style={{ flex: 1 }}>
                <Text style={styles.modalTitle}>Payment Receipts</Text>
                <Text style={styles.modalSubtitle}>
                  {receipts.length} receipt(s) available for bill {bill?.billNumber}
                </Text>
              </View>
              <Pressable
                onPress={() => setShowReceiptsModal(false)}
                hitSlop={8}
                style={styles.modalCloseBtn}
              >
                <Text style={styles.modalCloseText}>✕</Text>
              </Pressable>
            </View>

            <ScrollView style={styles.modalList} contentContainerStyle={{ gap: spacing.sm }}>
              {receipts.length > 0 ? (
                receipts.map((r) => {
                  const isBusy = downloadingReceiptId === r.receiptId;
                  return (
                    <View key={r.receiptId} style={styles.receiptCard}>
                      <View style={{ flex: 1 }}>
                        <View style={{ flexDirection: "row", alignItems: "center", gap: spacing.xs }}>
                          <Text style={styles.receiptNumber}>{r.receiptNumber}</Text>
                          <View style={styles.paymentModeBadge}>
                            <Text style={styles.paymentModeText}>{r.paymentMode}</Text>
                          </View>
                        </View>
                        <Text style={styles.receiptDate}>Date: {formatIsoDate(r.receiptDate)}</Text>
                        <Text style={styles.receiptAmount}>Amount: ₹ {r.amount.toFixed(2)}</Text>
                      </View>

                      <Pressable
                        onPress={() => handleDownloadSingleReceipt(r)}
                        disabled={isBusy}
                        style={styles.receiptDownloadBtn}
                        hitSlop={8}
                      >
                        {isBusy ? (
                          <ActivityIndicator size="small" color={colors.primary} />
                        ) : (
                          <View style={styles.btnSmallContent}>
                            <DownloadTrayIcon color={colors.primary} size={14} />
                            <Text style={styles.receiptDownloadBtnText}>Download</Text>
                          </View>
                        )}
                      </Pressable>
                    </View>
                  );
                })
              ) : (
                <View style={styles.receiptCard}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.receiptNumber}>{bill?.billNumber ?? "Receipt"}</Text>
                    <Text style={styles.receiptAmount}>Paid: ₹ {bill?.paidAmount.toFixed(2)}</Text>
                  </View>
                  <Pressable
                    onPress={() => handleDownloadSingleReceipt()}
                    disabled={downloadingReceiptId === "default"}
                    style={styles.receiptDownloadBtn}
                    hitSlop={8}
                  >
                    {downloadingReceiptId === "default" ? (
                      <ActivityIndicator size="small" color={colors.primary} />
                    ) : (
                      <View style={styles.btnSmallContent}>
                        <DownloadTrayIcon color={colors.primary} size={14} />
                        <Text style={styles.receiptDownloadBtnText}>Download</Text>
                      </View>
                    )}
                  </Pressable>
                </View>
              )}
            </ScrollView>

            <View style={styles.modalFooter}>
              <Pressable
                onPress={() => setShowReceiptsModal(false)}
                style={styles.modalDismissBtn}
              >
                <Text style={styles.modalDismissBtnText}>Close</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </Card>
  );
}

/* ========================================================================== */
/* SECTION 5: DISCHARGE SUMMARY CARD (FOR IP PATIENTS, DISPLAYED LAST)         */
/* ========================================================================== */

function DischargeSummarySectionCard({
  visit,
  sections,
  isLoading,
}: {
  visit?: VisitDetail;
  sections: CaseSheetSection[];
  isLoading: boolean;
}) {
  const { api } = useContainer();
  const [downloading, setDownloading] = useState(false);

  const handleDownloadDischargeSummary = async () => {
    setDownloading(true);
    try {
      if (visit?.encounterId) {
        try {
          const res = await api.getVisitPrint(visit.encounterId, "DISCHARGE_SUMMARY");
          if (res?.printData) {
            await printOrShareHtml(res.printData);
            return;
          }
        } catch {
          // Fallback to client layout
        }
      }

      if (!sections.length) {
        Alert.alert("Discharge Summary", "No recorded discharge summary available for this IP visit.");
        return;
      }

      const doctorName = visit?.consultantName ?? "Doctor";

      let templateFieldsHtml = "";
      sections.forEach((sec) => {
        interface SectionGroup {
          title: string | null;
          fields: CaseSheetField[];
        }
        const groups: SectionGroup[] = [];
        const hasHeadings = sec.fields.some((f) => f.type === "HEADING");

        if (hasHeadings) {
          let currentGroup: SectionGroup = { title: null, fields: [] };
          for (const f of sec.fields) {
            if (f.type === "HEADING") {
              if (currentGroup.fields.length > 0 || currentGroup.title !== null) {
                groups.push(currentGroup);
              }
              currentGroup = { title: f.label, fields: [] };
            } else {
              currentGroup.fields.push(f);
            }
          }
          if (currentGroup.fields.length > 0 || currentGroup.title !== null) {
            groups.push(currentGroup);
          }
        } else {
          const sectionMap = new Map<string, CaseSheetField[]>();
          for (const f of sec.fields) {
            const key = f.section ?? "__root__";
            if (!sectionMap.has(key)) sectionMap.set(key, []);
            sectionMap.get(key)!.push(f);
          }
          Array.from(sectionMap.entries()).forEach(([title, fields]) => {
            groups.push({
              title: title === "__root__" ? null : title,
              fields,
            });
          });
        }

        templateFieldsHtml += `
          <div class="section-title">${sec.templateName}</div>
          <div class="template-content">
            ${groups
              .map((grp) => {
                let secHtml = "";
                if (grp.title) {
                  secHtml += `<h4 class="group-title">${grp.title}</h4>`;
                }

                const fieldsHtml = grp.fields
                  .map((f) => {
                    if (f.type === "HEADING") return "";
                    const val = formatFieldValue(f.value);
                    if (!val) return "";
                    return `
                      <div class="field-row">
                        <div class="field-label">${f.label}</div>
                        <div class="field-val">${val}</div>
                      </div>
                    `;
                  })
                  .join("");

                return secHtml + fieldsHtml;
              })
              .join("")}
          </div>
        `;
      });

      const html = `
        <!DOCTYPE html>
        <html>
          <head>
            <meta charset="utf-8" />
            <title>IP Discharge Summary - ${visit?.patientName ?? "Patient"}</title>
            <style>${BASE_PDF_CSS}</style>
          </head>
          <body>
            ${renderPdfHeaderAndPatientCard(visit)}

            <div style="font-size: 16px; font-weight: 800; color: #111827; text-align: center; margin: 12px 0 16px 0; text-transform: uppercase;">
              Inpatient Discharge Summary
            </div>

            ${templateFieldsHtml}

            <div class="end-report">--End of report--</div>

            <div class="signature-box">
              <div class="signature-line">${doctorName}</div>
              <div style="font-size: 10px; color: #6b7280; margin-top: 2px;">Attending Medical Officer</div>
            </div>

            <div class="footer">
              <span>HIMS Patient Health Record</span>
              <span>Generated on ${new Date().toLocaleDateString()}</span>
            </div>
          </body>
        </html>
      `;

      await printOrShareHtml(html);
    } finally {
      setDownloading(false);
    }
  };

  const templateName = sections.length > 0 ? sections[0]?.templateName : null;

  return (
    <Card>
      <View style={styles.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Discharge Summary</Heading>
          <Caption>
            {templateName ? `${templateName} · Recorded` : "Inpatient discharge notes & summary"}
          </Caption>
        </View>
      </View>

      <View style={styles.actionRow}>
        <Pressable
          onPress={handleDownloadDischargeSummary}
          disabled={downloading || isLoading}
          style={[styles.primaryActionBtn, (downloading || isLoading) && { opacity: 0.6 }]}
          hitSlop={8}
        >
          {downloading ? (
            <ActivityIndicator size="small" color={colors.surface} />
          ) : (
            <View style={styles.btnContent}>
              <DownloadTrayIcon color={colors.surface} size={16} />
              <Text style={styles.primaryActionBtnText}>Download Discharge Summary</Text>
            </View>
          )}
        </Pressable>
      </View>
    </Card>
  );
}

/** Crisp Vector Download Icon (Tray + Down Arrow) matching web download design. */
function DownloadTrayIcon({ color = colors.surface, size = 16 }: { color?: string; size?: number }) {
  return (
    <View style={{ width: size, height: size, alignItems: "center", justifyContent: "center" }}>
      {/* Down arrow stem & tip */}
      <View style={{ width: 2, height: size * 0.42, backgroundColor: color, borderRadius: 1 }} />
      <View
        style={{
          width: 0,
          height: 0,
          borderLeftWidth: size * 0.22,
          borderRightWidth: size * 0.22,
          borderTopWidth: size * 0.22,
          borderLeftColor: "transparent",
          borderRightColor: "transparent",
          borderTopColor: color,
          marginTop: -1,
        }}
      />
      {/* Tray base */}
      <View
        style={{
          width: size * 0.85,
          height: size * 0.22,
          borderBottomWidth: 1.8,
          borderLeftWidth: 1.8,
          borderRightWidth: 1.8,
          borderColor: color,
          borderRadius: 2,
          marginTop: 1,
        }}
      />
    </View>
  );
}

/* ========================================================================== */
/* STYLES                                                                     */
/* ========================================================================== */

const styles = StyleSheet.create({
  diagnosisBox: {
    marginTop: spacing.sm,
    paddingTop: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    gap: spacing.xs,
  },
  sectionsContainer: {
    gap: spacing.md,
  },
  sectionHeaderRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  actionRow: {
    marginTop: spacing.md,
  },
  primaryActionBtn: {
    backgroundColor: colors.primary,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
    alignItems: "center",
    justifyContent: "center",
  },
  primaryActionBtnText: {
    ...typography.label,
    color: colors.surface,
    fontWeight: "700",
  },
  secondaryActionBtn: {
    backgroundColor: colors.surfaceAlt,
    borderWidth: 1,
    borderColor: colors.primary,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
    alignItems: "center",
    justifyContent: "center",
  },
  secondaryActionBtnText: {
    ...typography.label,
    color: colors.primary,
    fontWeight: "700",
  },
  btnContent: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
  },
  btnSmallContent: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.xs,
  },
  attachmentSubsection: {
    marginTop: spacing.md,
    paddingTop: spacing.md,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    gap: spacing.sm,
  },
  subsectionTitle: {
    ...typography.label,
    color: colors.text,
  },
  attachmentList: {
    gap: spacing.sm,
  },
  attachmentItemRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: colors.surfaceAlt,
    padding: spacing.sm,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.border,
    gap: spacing.md,
  },
  attachmentName: {
    ...typography.body,
    fontWeight: "600",
    color: colors.text,
  },
  attachmentDownloadBtn: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.primary,
    backgroundColor: colors.surface,
  },
  attachmentDownloadText: {
    ...typography.caption,
    fontWeight: "600",
    color: colors.primary,
  },

  /* Receipts Modal Styles */
  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(17, 24, 39, 0.6)",
    justifyContent: "center",
    alignItems: "center",
    padding: spacing.lg,
  },
  modalContainer: {
    width: "100%",
    maxHeight: "80%",
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.lg,
    gap: spacing.md,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 8,
    elevation: 5,
  },
  modalHeader: {
    flexDirection: "row",
    alignItems: "flex-start",
    justifyContent: "space-between",
    paddingBottom: spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  modalTitle: {
    ...typography.heading,
    color: colors.text,
  },
  modalSubtitle: {
    ...typography.caption,
    color: colors.textMuted,
    marginTop: 2,
  },
  modalCloseBtn: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: colors.surfaceAlt,
    alignItems: "center",
    justifyContent: "center",
  },
  modalCloseText: {
    fontSize: 14,
    color: colors.textMuted,
    fontWeight: "700",
  },
  modalList: {
    maxHeight: 320,
  },
  receiptCard: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: colors.surfaceAlt,
    padding: spacing.md,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    gap: spacing.md,
  },
  receiptNumber: {
    ...typography.label,
    color: colors.text,
    fontFamily: "monospace",
  },
  paymentModeBadge: {
    backgroundColor: colors.primarySoft,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: radius.pill,
  },
  paymentModeText: {
    fontSize: 10,
    fontWeight: "700",
    color: colors.primaryDark,
  },
  receiptDate: {
    ...typography.caption,
    color: colors.textMuted,
    marginTop: 2,
  },
  receiptAmount: {
    ...typography.body,
    fontWeight: "700",
    color: colors.success,
    marginTop: 2,
  },
  receiptDownloadBtn: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.primary,
    backgroundColor: colors.surface,
  },
  receiptDownloadBtnText: {
    ...typography.caption,
    fontWeight: "700",
    color: colors.primary,
  },
  modalFooter: {
    paddingTop: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  modalDismissBtn: {
    backgroundColor: colors.surfaceAlt,
    paddingVertical: spacing.md,
    borderRadius: radius.md,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.border,
  },
  modalDismissBtnText: {
    ...typography.label,
    color: colors.text,
  },
});
