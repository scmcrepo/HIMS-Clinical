import React, { useState } from "react";
import { ActivityIndicator, Alert, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Card, Caption, Heading } from "../components";
import { colors, radius, spacing, typography } from "../tokens";
import type { PrescriptionSummary, VisitDetail } from "../../core/contracts";
import { BASE_PDF_CSS, downloadHtml, renderPdfHeaderAndPatientCard } from "./helpers";

export function PrescriptionsSectionCard({
  visit,
  prescriptions,
  isLoading,
  onViewHtml,
}: {
  visit?: VisitDetail;
  prescriptions: PrescriptionSummary[];
  isLoading: boolean;
  onViewHtml?: (title: string, html: string, onDownload?: () => void) => void;
}) {
  const [viewing, setViewing] = useState(false);
  const [downloading, setDownloading] = useState(false);

  const totalMedicines = prescriptions.reduce((acc, p) => acc + (p.items?.length ?? 0), 0);

  const handleAction = async (mode: "view" | "download") => {
    if (!prescriptions.length || totalMedicines === 0) {
      Alert.alert("Prescriptions", "No prescription recorded for this visit.");
      return;
    }
    mode === "view" ? setViewing(true) : setDownloading(true);
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

            ${tablesHtml}

            <div class="end-report">--End of report--</div>

            <div class="signature-box">
              <div class="signature-line">${doctorName}</div>
              <div style="font-size: 10px; color: #6b7280; margin-top: 2px;">Authorized Medical Practitioner</div>
            </div>

            <div class="footer">
              <span>HIMS Prescription Record</span>
              <span>Generated on ${new Date().toLocaleDateString()}</span>
            </div>
          </body>
        </html>
      `;

      if (mode === "view") {
        if (onViewHtml) {
          onViewHtml("Prescription (Rx)", html, () => void downloadHtml(html));
        } else {
          await downloadHtml(html);
        }
      } else {
        await downloadHtml(html);
      }
    } finally {
      setViewing(false);
      setDownloading(false);
    }
  };

  return (
    <Card>
      <View style={s.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Prescriptions</Heading>
          <Caption>
            {totalMedicines > 0 ? `${totalMedicines} medicine(s) prescribed` : "No medicines prescribed"}
          </Caption>
        </View>
      </View>

      <View style={s.actionRow}>
        <Pressable
          onPress={() => handleAction("view")}
          disabled={viewing || downloading || isLoading || totalMedicines === 0}
          style={[
            s.secondaryActionBtn,
            (viewing || downloading || isLoading || totalMedicines === 0) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          {viewing ? (
            <ActivityIndicator size="small" color={colors.primary} />
          ) : (
            <View style={s.btnContent}>
              <Ionicons name="eye-outline" color={colors.primary} size={16} />
              <Text style={s.secondaryActionBtnText}>View</Text>
            </View>
          )}
        </Pressable>

        <Pressable
          onPress={() => handleAction("download")}
          disabled={viewing || downloading || isLoading || totalMedicines === 0}
          style={[
            s.primaryActionBtn,
            (viewing || downloading || isLoading || totalMedicines === 0) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          {downloading ? (
            <ActivityIndicator size="small" color={colors.surface} />
          ) : (
            <View style={s.btnContent}>
              <Ionicons name="download-outline" color={colors.surface} size={16} />
              <Text style={s.primaryActionBtnText}>Download</Text>
            </View>
          )}
        </Pressable>
      </View>
    </Card>
  );
}

const s = StyleSheet.create({
  sectionHeaderRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
  },
  actionRow: {
    flexDirection: "row",
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  btnContent: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  secondaryActionBtn: {
    flex: 1,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    alignItems: "center",
    justifyContent: "center",
    minHeight: 40,
  },
  secondaryActionBtnText: {
    ...typography.label,
    fontSize: 13,
    fontWeight: "700",
    color: colors.primary,
  },
  primaryActionBtn: {
    flex: 1,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    minHeight: 40,
  },
  primaryActionBtnText: {
    ...typography.label,
    fontSize: 13,
    fontWeight: "700",
    color: colors.surface,
  },
});
