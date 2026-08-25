import React, { useState } from "react";
import { ActivityIndicator, Alert, Image, Linking, Modal, Pressable, StyleSheet, Text, View } from "react-native";
import Constants from "expo-constants";
import * as FileSystem from "expo-file-system";
import * as Sharing from "expo-sharing";
import { Ionicons } from "@expo/vector-icons";
import { Card, Caption, Heading } from "../components";
import { colors, radius, spacing, typography } from "../tokens";
import { useContainer } from "../../app/_layout";
import { formatFileSize, formatIsoDate } from "../../core/format";
import type { AttachmentMeta, DiagnosticOrderGroup, VisitDetail } from "../../core/contracts";
import { BASE_PDF_CSS, downloadHtml, isImageFile, renderPdfHeaderAndPatientCard } from "./helpers";

export function DiagnosticOrdersSectionCard({
  visit,
  groups,
  attachments,
  isLoading,
  onViewHtml,
  onViewImage,
}: {
  visit?: VisitDetail;
  groups: DiagnosticOrderGroup[];
  attachments: AttachmentMeta[];
  isLoading: boolean;
  onViewHtml?: (title: string, html: string, onDownload?: () => void) => void;
  onViewImage?: (title: string, uri: string) => void;
}) {
  const container = useContainer();
  const [viewing, setViewing] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [busyAttId, setBusyAttId] = useState<string | null>(null);
  const [previewImage, setPreviewImage] = useState<{ uri: string; title: string } | null>(null);

  const totalTests = groups.reduce((acc, g) => acc + (g.lines?.length ?? 0), 0);

  const handleAction = async (mode: "view" | "download") => {
    if (!groups.length || totalTests === 0) {
      Alert.alert("Reports", "No diagnostic or lab orders found for this visit.");
      return;
    }
    mode === "view" ? setViewing(true) : setDownloading(true);
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

      if (mode === "view") {
        if (onViewHtml) {
          onViewHtml("Diagnostic Orders Document", html, () => void downloadHtml(html));
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

  const handleAttachmentAction = async (att: AttachmentMeta, mode: "view" | "download") => {
    setBusyAttId(att.attachmentId);
    try {
      const token = container.session.getAccessToken();
      const extra = (Constants.expoConfig?.extra ?? {}) as { apiBaseUrl?: string };
      const baseUrl = extra.apiBaseUrl ?? "";
      const downloadEndpoint = `${baseUrl}/portal/attachments/${att.attachmentId}/content`;

      const filename = att.fileName || `attachment_${att.attachmentId}`;
      const localUri = `${FileSystem.cacheDirectory}${filename}`;

      let targetUri = localUri;
      let downloadedOk = false;

      try {
        const res = await FileSystem.downloadAsync(downloadEndpoint, localUri, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (res.status === 200) {
          targetUri = res.uri;
          downloadedOk = true;
        }
      } catch (e) {
        console.warn("Direct attachment download failed, trying signed URL", e);
      }

      if (!downloadedOk) {
        const { url } = await container.api.getAttachmentDownload(att.attachmentId);
        targetUri = url.startsWith("http") ? url : `${baseUrl}${url}`;
      }

      if (mode === "download") {
        if (targetUri.startsWith("file://")) {
          if (await Sharing.isAvailableAsync()) {
            await Sharing.shareAsync(targetUri, {
              UTI: att.contentType,
              mimeType: att.contentType || "application/octet-stream",
              dialogTitle: `Download ${filename}`,
            });
          } else {
            Alert.alert("Download Complete", `File saved at ${filename}`);
          }
        } else {
          await Linking.openURL(targetUri);
        }
      } else {
        if (isImageFile(att.fileName, att.contentType)) {
          if (onViewImage) {
            onViewImage(filename, targetUri);
          } else {
            setPreviewImage({ uri: targetUri, title: filename });
          }
        } else if (onViewHtml) {
          const attHtml = `
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="utf-8" />
                <title>${filename}</title>
                <style>${BASE_PDF_CSS}</style>
              </head>
              <body>
                ${renderPdfHeaderAndPatientCard(visit)}
                <div class="section-title">ATTACHMENT: ${filename}</div>
                <p style="font-size: 13px; margin-top: 12px; margin-bottom: 24px;">Attached document file: <strong>${filename}</strong></p>
                <div class="footer">
                  <span>HIMS Patient Health Record</span>
                </div>
              </body>
            </html>
          `;
          onViewHtml(filename, attHtml, () => void Sharing.shareAsync(targetUri));
        } else if (targetUri.startsWith("file://")) {
          if (await Sharing.isAvailableAsync()) {
            await Sharing.shareAsync(targetUri, {
              UTI: att.contentType || "com.adobe.pdf",
              mimeType: att.contentType || "application/pdf",
              dialogTitle: `View ${filename}`,
            });
          } else {
            Alert.alert("Attachment", `File saved at ${filename}`);
          }
        } else {
          await Linking.openURL(targetUri);
        }
      }
    } catch (err) {
      console.error("Attachment action error", err);
      Alert.alert("Attachment Error", "Could not open or view attachment. Please try again.");
    } finally {
      setBusyAttId(null);
    }
  };

  return (
    <Card>
      <View style={s.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Diagnostic Orders</Heading>
          <Caption>
            {totalTests > 0 ? `${totalTests} test order(s) placed` : "No diagnostic tests recorded"}
          </Caption>
        </View>
      </View>

      {totalTests > 0 && (
        <View style={s.actionRow}>
          <Pressable
            onPress={() => handleAction("view")}
            disabled={viewing || downloading || isLoading}
            style={[
              s.secondaryActionBtn,
              (viewing || downloading || isLoading) && { opacity: 0.6 },
            ]}
            hitSlop={8}
          >
            {viewing ? (
              <ActivityIndicator size="small" color={colors.primary} />
            ) : (
              <View style={s.btnContent}>
                <Ionicons name="eye-outline" color={colors.primary} size={16} />
                <Text style={s.secondaryActionBtnText}>View Reports</Text>
              </View>
            )}
          </Pressable>

          <Pressable
            onPress={() => handleAction("download")}
            disabled={viewing || downloading || isLoading}
            style={[
              s.primaryActionBtn,
              (viewing || downloading || isLoading) && { opacity: 0.6 },
            ]}
            hitSlop={8}
          >
            {downloading ? (
              <ActivityIndicator size="small" color={colors.surface} />
            ) : (
              <View style={s.btnContent}>
                <Ionicons name="download-outline" color={colors.surface} size={16} />
                <Text style={s.primaryActionBtnText}>Download Reports</Text>
              </View>
            )}
          </Pressable>
        </View>
      )}

      {/* X-Rays, Scans & Attachments Subsection */}
      {attachments.length > 0 && (
        <View style={s.attachmentSubsection}>
          <Text style={s.subsectionTitle}>X-Rays & Scan Attachments ({attachments.length})</Text>
          <View style={s.attachmentList}>
            {attachments.map((att) => {
              const isBusy = busyAttId === att.attachmentId;
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
                <View key={att.attachmentId} style={s.attachmentItemRow}>
                  <View style={{ flex: 1 }}>
                    <Text style={s.attachmentName} numberOfLines={1}>
                      {att.fileName}
                    </Text>
                    {subtitleParts.length > 0 ? (
                      <Caption>{subtitleParts.join(" · ")}</Caption>
                    ) : null}
                  </View>

                  <View style={{ flexDirection: "row", gap: spacing.xs }}>
                    <Pressable
                      onPress={() => handleAttachmentAction(att, "view")}
                      disabled={isBusy}
                      style={s.attachmentDownloadBtn}
                      hitSlop={8}
                    >
                      <View style={s.btnSmallContent}>
                        <Ionicons name="eye-outline" color={colors.primary} size={13} />
                        <Text style={s.attachmentDownloadText}>View</Text>
                      </View>
                    </Pressable>

                    <Pressable
                      onPress={() => handleAttachmentAction(att, "download")}
                      disabled={isBusy}
                      style={[s.attachmentDownloadBtn, { backgroundColor: colors.primary }]}
                      hitSlop={8}
                    >
                      {isBusy ? (
                        <ActivityIndicator size="small" color={colors.surface} />
                      ) : (
                        <View style={s.btnSmallContent}>
                          <Ionicons name="download-outline" color={colors.surface} size={13} />
                          <Text style={[s.attachmentDownloadText, { color: colors.surface }]}>Download</Text>
                        </View>
                      )}
                    </Pressable>
                  </View>
                </View>
              );
            })}
          </View>
        </View>
      )}

      {/* Fullscreen Image Preview Modal */}
      <Modal
        visible={!!previewImage}
        transparent
        animationType="fade"
        onRequestClose={() => setPreviewImage(null)}
      >
        <View style={s.imageModalOverlay}>
          <View style={s.imageModalHeader}>
            <Text style={s.imageModalTitle} numberOfLines={1}>
              {previewImage?.title}
            </Text>
            <Pressable
              onPress={() => setPreviewImage(null)}
              hitSlop={8}
              style={s.modalCloseBtn}
            >
              <Ionicons name="close" size={20} color={colors.text} />
            </Pressable>
          </View>
          {previewImage ? (
            <Image
              source={{ uri: previewImage.uri }}
              style={s.fullImagePreview}
              resizeMode="contain"
            />
          ) : null}
        </View>
      </Modal>
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
  btnSmallContent: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
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
  attachmentSubsection: {
    marginTop: spacing.md,
    paddingTop: spacing.md,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  subsectionTitle: {
    ...typography.label,
    fontSize: 13,
    fontWeight: "700",
    color: colors.text,
    marginBottom: spacing.xs,
  },
  attachmentList: {
    gap: spacing.xs,
  },
  attachmentItemRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: spacing.xs,
    gap: spacing.sm,
  },
  attachmentName: {
    ...typography.body,
    fontSize: 13,
    fontWeight: "600",
    color: colors.text,
  },
  attachmentDownloadBtn: {
    paddingHorizontal: spacing.sm,
    paddingVertical: 4,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceAlt,
  },
  attachmentDownloadText: {
    ...typography.caption,
    fontSize: 11,
    fontWeight: "700",
    color: colors.primary,
  },
  imageModalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.9)",
    justifyContent: "center",
  },
  imageModalHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    padding: spacing.md,
    backgroundColor: colors.surface,
  },
  imageModalTitle: {
    ...typography.heading,
    fontSize: 15,
    color: colors.text,
    flex: 1,
  },
  modalCloseBtn: {
    padding: spacing.xs,
  },
  fullImagePreview: {
    flex: 1,
    width: "100%",
  },
});
