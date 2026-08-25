import React, { useState } from "react";
import { ActivityIndicator, Alert, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Card, Caption, Heading } from "../components";
import { colors, radius, spacing, typography } from "../tokens";
import type { CaseSheetField, CaseSheetSection, VisitDetail } from "../../core/contracts";
import { BASE_PDF_CSS, downloadHtml, extractImageUris, formatFieldValue, renderPdfHeaderAndPatientCard } from "./helpers";

export function CasesheetSectionCard({
  visit,
  sections,
  isLoading,
  onViewHtml,
}: {
  visit?: VisitDetail;
  sections: CaseSheetSection[];
  isLoading: boolean;
  onViewHtml?: (title: string, html: string, onDownload?: () => void) => void;
}) {
  const [viewing, setViewing] = useState(false);
  const [downloading, setDownloading] = useState(false);

  const handleAction = async (mode: "view" | "download") => {
    if (!sections.length) {
      Alert.alert("Casesheet", "No recorded casesheet available for this visit.");
      return;
    }
    mode === "view" ? setViewing(true) : setDownloading(true);
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

      if (mode === "view") {
        if (onViewHtml) {
          onViewHtml("Casesheet Document", html, () => void downloadHtml(html));
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

  const templateName = sections.length > 0 ? sections[0]?.templateName : null;

  return (
    <Card>
      <View style={s.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Casesheet</Heading>
          <Caption>
            {templateName ? `${templateName} · Recorded` : "Clinical examination and notes"}
          </Caption>
        </View>
      </View>

      <View style={s.actionRow}>
        <Pressable
          onPress={() => handleAction("view")}
          disabled={viewing || downloading || isLoading || !sections.length}
          style={[
            s.secondaryActionBtn,
            (viewing || downloading || isLoading || !sections.length) && { opacity: 0.6 },
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
          disabled={viewing || downloading || isLoading || !sections.length}
          style={[
            s.primaryActionBtn,
            (viewing || downloading || isLoading || !sections.length) && { opacity: 0.6 },
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
