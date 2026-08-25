import React, { useState } from "react";
import { ActivityIndicator, Alert, Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Card, Caption, Heading } from "../components";
import { colors, radius, spacing, typography } from "../tokens";
import { useContainer } from "../../app/_layout";
import { formatIsoDate } from "../../core/format";
import type { BillSummary, ReceiptSummary, VisitDetail } from "../../core/contracts";
import { BASE_PDF_CSS, downloadHtml, renderPdfHeaderAndPatientCard } from "./helpers";

export function BillingSectionCard({
  visit,
  bills,
  isLoading,
  onViewHtml,
}: {
  visit?: VisitDetail;
  bills: BillSummary[];
  isLoading: boolean;
  onViewHtml?: (title: string, html: string, onDownload?: () => void) => void;
}) {
  const { api } = useContainer();
  const [viewingBill, setViewingBill] = useState(false);
  const [downloadingBill, setDownloadingBill] = useState(false);
  const [busyReceiptId, setBusyReceiptId] = useState<string | null>(null);
  const [showReceiptsModal, setShowReceiptsModal] = useState(false);

  const bill = bills.length > 0 ? bills[0] : null;
  const receipts = bill?.receipts ?? [];
  const isDraft = !bill || bill.status === "DRAFT" || !bill.billNumber || bill.billNumber.toLowerCase() === "draft";
  const billNumberDisplay = isDraft ? "Draft" : bill.billNumber;

  const handleBillAction = async (mode: "view" | "download") => {
    if (!bill) {
      Alert.alert("Billing", "No bill or invoice generated for this visit yet.");
      return;
    }
    mode === "view" ? setViewingBill(true) : setDownloadingBill(true);
    try {
      let htmlData = "";
      if (visit?.encounterId) {
        try {
          const res = await api.getVisitPrint(visit.encounterId, "BILL", bill.billId);
          if (res?.printData) {
            htmlData = res.printData;
          }
        } catch {
          // Fallback
        }
      }

      if (!htmlData) {
        htmlData = `
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
      }

      if (mode === "view") {
        if (onViewHtml) {
          onViewHtml("Bill & Invoice", htmlData, () => void downloadHtml(htmlData));
        } else {
          await downloadHtml(htmlData);
        }
      } else {
        await downloadHtml(htmlData);
      }
    } finally {
      setViewingBill(false);
      setDownloadingBill(false);
    }
  };

  const handleSingleReceiptAction = async (receipt: ReceiptSummary | undefined, mode: "view" | "download") => {
    if (!bill) {
      Alert.alert("Receipt", "No receipt generated for this visit yet.");
      return;
    }
    const receiptId = receipt?.receiptId ?? "default";
    setBusyReceiptId(receiptId);
    try {
      const receiptNo = receipt?.receiptNumber ?? bill.billNumber;
      let htmlData = "";

      if (visit?.encounterId) {
        try {
          const res = await api.getVisitPrint(
            visit.encounterId,
            "OP_RECEIPT",
            receipt?.receiptId ?? bill.billId
          );
          if (res?.printData) {
            htmlData = res.printData;
          }
        } catch {
          // Fallback
        }
      }

      if (!htmlData) {
        const receiptAmount = receipt ? receipt.amount : bill.paidAmount;
        const receiptDate = receipt ? formatIsoDate(receipt.receiptDate) : formatIsoDate(new Date().toISOString());
        const pMode = receipt?.paymentMode ?? "CASH";

        htmlData = `
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
                      <small style="color: #6b7280;">Mode: ${pMode} &nbsp;|&nbsp; Date: ${receiptDate}</small>
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
      }

      if (mode === "view") {
        if (onViewHtml) {
          onViewHtml(`Receipt - ${receiptNo}`, htmlData, () => void downloadHtml(htmlData));
        } else {
          await downloadHtml(htmlData);
        }
      } else {
        await downloadHtml(htmlData);
      }
    } finally {
      setBusyReceiptId(null);
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
      <View style={s.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Billing & Payment</Heading>

          {bill ? (
            <Caption>
              Total: ₹{bill.totalAmount.toFixed(2)} &nbsp;|&nbsp; Paid: ₹{bill.paidAmount.toFixed(2)}
              {bill.balanceAmount > 0 ? (
                <Text style={{ color: colors.danger, fontWeight: "700" }}>
                  {" "}
                  · Due: ₹{bill.balanceAmount.toFixed(2)}
                </Text>
              ) : null}
            </Caption>
          ) : (
            <Caption>No billing statement generated yet</Caption>
          )}
        </View>
      </View>

      <View style={s.actionRowGroup}>
        <View style={s.billingRowItem}>
          <View style={{ flex: 1 }}>
            <Text style={s.billingItemTitle}>Bill / Invoice ({billNumberDisplay})</Text>
            {bill ? (
              <Caption>Total Statement Amount: ₹{bill.totalAmount.toFixed(2)}</Caption>
            ) : null}
          </View>

          <View style={{ flexDirection: "row", gap: spacing.xs }}>
            <Pressable
              onPress={() => handleBillAction("view")}
              disabled={viewingBill || downloadingBill || isLoading || !bill}
              style={[s.billingSmallBtn, (!bill || viewingBill || downloadingBill) && { opacity: 0.6 }]}
              hitSlop={8}
            >
              {viewingBill ? (
                <ActivityIndicator size="small" color={colors.primary} />
              ) : (
                <View style={s.btnSmallContent}>
                  <Ionicons name="eye-outline" color={colors.primary} size={13} />
                  <Text style={s.billingSmallBtnText}>View</Text>
                </View>
              )}
            </Pressable>

            <Pressable
              onPress={() => handleBillAction("download")}
              disabled={viewingBill || downloadingBill || isLoading || !bill}
              style={[
                s.billingSmallBtn,
                { backgroundColor: colors.primary },
                (!bill || viewingBill || downloadingBill) && { opacity: 0.6 },
              ]}
              hitSlop={8}
            >
              {downloadingBill ? (
                <ActivityIndicator size="small" color={colors.surface} />
              ) : (
                <View style={s.btnSmallContent}>
                  <Ionicons name="download-outline" color={colors.surface} size={13} />
                  <Text style={[s.billingSmallBtnText, { color: colors.surface }]}>Download</Text>
                </View>
              )}
            </Pressable>
          </View>
        </View>

        <View style={s.billingRowItem}>
          <View style={{ flex: 1 }}>
            <Text style={s.billingItemTitle}>
              {receipts.length > 1 ? `Payment Receipts (${receipts.length})` : "Payment Receipt"}
            </Text>
            {bill ? (
              <Caption>
                {bill.paidAmount > 0
                  ? `Total Paid: ₹${bill.paidAmount.toFixed(2)}`
                  : "No payments recorded"}
              </Caption>
            ) : null}
          </View>

          {receipts.length > 1 ? (
            <Pressable
              onPress={handleOpenReceiptsModal}
              disabled={!bill || bill.paidAmount <= 0}
              style={[
                s.billingSmallBtn,
                { backgroundColor: colors.primary },
                (!bill || bill.paidAmount <= 0) && { opacity: 0.6 },
              ]}
              hitSlop={8}
            >
              <View style={s.btnSmallContent}>
                <Ionicons name="documents-outline" color={colors.surface} size={13} />
                <Text style={[s.billingSmallBtnText, { color: colors.surface }]}>
                  Select Receipt ({receipts.length})
                </Text>
              </View>
            </Pressable>
          ) : (
            <View style={{ flexDirection: "row", gap: spacing.xs }}>
              <Pressable
                onPress={() => handleSingleReceiptAction(receipts[0], "view")}
                disabled={!bill || bill.paidAmount <= 0 || !!busyReceiptId}
                style={[
                  s.billingSmallBtn,
                  (!bill || bill.paidAmount <= 0 || !!busyReceiptId) && { opacity: 0.6 },
                ]}
                hitSlop={8}
              >
                {busyReceiptId === (receipts[0]?.receiptId ?? "default") ? (
                  <ActivityIndicator size="small" color={colors.primary} />
                ) : (
                  <View style={s.btnSmallContent}>
                    <Ionicons name="eye-outline" color={colors.primary} size={13} />
                    <Text style={s.billingSmallBtnText}>View</Text>
                  </View>
                )}
              </Pressable>

              <Pressable
                onPress={() => handleSingleReceiptAction(receipts[0], "download")}
                disabled={!bill || bill.paidAmount <= 0 || !!busyReceiptId}
                style={[
                  s.billingSmallBtn,
                  { backgroundColor: colors.primary },
                  (!bill || bill.paidAmount <= 0 || !!busyReceiptId) && { opacity: 0.6 },
                ]}
                hitSlop={8}
              >
                {busyReceiptId === (receipts[0]?.receiptId ?? "default") ? (
                  <ActivityIndicator size="small" color={colors.surface} />
                ) : (
                  <View style={s.btnSmallContent}>
                    <Ionicons name="download-outline" color={colors.surface} size={13} />
                    <Text style={[s.billingSmallBtnText, { color: colors.surface }]}>Download</Text>
                  </View>
                )}
              </Pressable>
            </View>
          )}
        </View>
      </View>

      <Modal
        visible={showReceiptsModal}
        transparent
        animationType="fade"
        onRequestClose={() => setShowReceiptsModal(false)}
      >
        <View style={s.modalOverlay}>
          <View style={s.modalCard}>
            <View style={s.modalHeader}>
              <Text style={s.modalTitle}>Select Payment Receipt</Text>
              <Pressable onPress={() => setShowReceiptsModal(false)} hitSlop={8}>
                <Ionicons name="close" size={20} color={colors.text} />
              </Pressable>
            </View>

            <View style={s.receiptList}>
              {receipts.map((rcp) => {
                const isBusy = busyReceiptId === rcp.receiptId;
                return (
                  <View key={rcp.receiptId} style={s.receiptModalItem}>
                    <View style={{ flex: 1 }}>
                      <Text style={s.receiptNoText}>Receipt #{rcp.receiptNumber}</Text>
                      <Caption>
                        ₹{rcp.amount.toFixed(2)} · {rcp.paymentMode} · {formatIsoDate(rcp.receiptDate)}
                      </Caption>
                    </View>

                    <View style={{ flexDirection: "row", gap: spacing.xs }}>
                      <Pressable
                        onPress={() => {
                          setShowReceiptsModal(false);
                          void handleSingleReceiptAction(rcp, "view");
                        }}
                        disabled={isBusy}
                        style={s.billingSmallBtn}
                      >
                        <Text style={s.billingSmallBtnText}>View</Text>
                      </Pressable>
                      <Pressable
                        onPress={() => {
                          setShowReceiptsModal(false);
                          void handleSingleReceiptAction(rcp, "download");
                        }}
                        disabled={isBusy}
                        style={[s.billingSmallBtn, { backgroundColor: colors.primary }]}
                      >
                        <Text style={[s.billingSmallBtnText, { color: colors.surface }]}>Download</Text>
                      </Pressable>
                    </View>
                  </View>
                );
              })}
            </View>
          </View>
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
  actionRowGroup: {
    marginTop: spacing.md,
    gap: spacing.sm,
  },
  billingRowItem: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: spacing.xs,
    gap: spacing.sm,
  },
  billingItemTitle: {
    ...typography.body,
    fontSize: 14,
    fontWeight: "700",
    color: colors.text,
  },
  btnSmallContent: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  billingSmallBtn: {
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surfaceAlt,
  },
  billingSmallBtnText: {
    ...typography.caption,
    fontSize: 12,
    fontWeight: "700",
    color: colors.primary,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.6)",
    alignItems: "center",
    justifyContent: "center",
    padding: spacing.lg,
  },
  modalCard: {
    width: "100%",
    maxWidth: 380,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.lg,
  },
  modalHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: spacing.md,
  },
  modalTitle: {
    ...typography.heading,
    fontSize: 16,
    color: colors.text,
  },
  receiptList: {
    gap: spacing.sm,
  },
  receiptModalItem: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: spacing.xs,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  receiptNoText: {
    ...typography.body,
    fontSize: 14,
    fontWeight: "600",
    color: colors.text,
  },
});
