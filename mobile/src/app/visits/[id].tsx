import React, { useState, useEffect, useRef } from "react";
import {
  ActivityIndicator,
  Alert,
  Image,
  Linking,
  Modal,
  PanResponder,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { WebView } from "react-native-webview";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useQuery } from "@tanstack/react-query";
import Constants from "expo-constants";
import * as Print from "expo-print";
import * as Sharing from "expo-sharing";
import * as FileSystem from "expo-file-system";
import { Ionicons } from "@expo/vector-icons";
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

/** In-App Full-Screen Document & Image Viewer Modal with Pure Finger Pinch-to-Zoom, Download, and Close. */
function DocumentViewerModal({
  visible,
  title,
  html,
  imageUri,
  onClose,
  onPrint,
  onDownload,
}: {
  visible: boolean;
  title: string;
  html?: string;
  imageUri?: string;
  onClose: () => void;
  onPrint?: () => void;
  onDownload?: () => void;
}) {
  if (!visible) return null;

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.viewerOverlay}>
        {/* Top Floating Control Bar (Title + Download + Close) */}
        <View style={styles.viewerHeader}>
          <View style={styles.viewerHeaderTitleGroup}>
            <Ionicons name="document-text" size={20} color={colors.primary} />
            <Text style={styles.viewerHeaderTitle} numberOfLines={1}>
              {title}
            </Text>
          </View>

          {/* Header Action Buttons: Download & Close */}
          <View style={styles.viewerActions}>
            {onDownload && (
              <Pressable onPress={onDownload} style={styles.viewerDownloadBtn} hitSlop={6} accessibilityLabel="Download">
                <Ionicons name="download-outline" size={16} color={colors.surface} />
                <Text style={styles.viewerDownloadBtnText}>Download</Text>
              </Pressable>
            )}
            <Pressable onPress={onClose} style={styles.viewerCloseBtn} hitSlop={6} accessibilityLabel="Close Viewer">
              <Ionicons name="close" size={20} color={colors.textMuted} />
            </Pressable>
          </View>
        </View>

        {/* Real PDF & Document Print View Container with Native Finger Pinch-To-Zoom */}
        <View style={styles.viewerCanvasContainer}>
          {imageUri ? (
            <ImageViewerCard imageUri={imageUri} />
          ) : html ? (
            <DocHtmlCard html={html} />
          ) : (
            <Text style={styles.viewerEmptyText}>No document preview available.</Text>
          )}
        </View>
      </View>
    </Modal>
  );
}

function ImageViewerCard({ imageUri }: { imageUri: string }) {
  const [scale, setScale] = useState(1);
  const [translateX, setTranslateX] = useState(0);
  const [translateY, setTranslateY] = useState(0);

  const scaleRef = useRef(1);
  const txRef = useRef(0);
  const tyRef = useRef(0);

  const startDistRef = useRef<number | null>(null);
  const startScaleRef = useRef(1);
  const startTouchRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
  const startTxRef = useRef(0);
  const startTyRef = useRef(0);
  const lastTapRef = useRef(0);

  const apiBase = (Constants.expoConfig?.extra?.apiBaseUrl as string | undefined) || "";
  let fullUri = imageUri;
  if (imageUri && imageUri.startsWith("/") && apiBase) {
    fullUri = `${apiBase}${imageUri}`;
  }

  const getDistance = (touches: Array<{ pageX: number; pageY: number }>) => {
    const [t1, t2] = touches;
    if (!t1 || !t2) return 0;
    const dx = t1.pageX - t2.pageX;
    const dy = t1.pageY - t2.pageY;
    return Math.sqrt(dx * dx + dy * dy);
  };

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: (evt) => {
        const touches = evt.nativeEvent.touches;
        const now = Date.now();

        if (now - lastTapRef.current < 300) {
          // Double-tap to quick zoom in (2.5x) or reset (1.0x)
          const nextScale = scaleRef.current > 1.2 ? 1.0 : 2.5;
          scaleRef.current = nextScale;
          txRef.current = 0;
          tyRef.current = 0;
          setScale(nextScale);
          setTranslateX(0);
          setTranslateY(0);
        }
        lastTapRef.current = now;

        if (touches.length === 1 && touches[0]) {
          startTouchRef.current = { x: touches[0].pageX, y: touches[0].pageY };
          startTxRef.current = txRef.current;
          startTyRef.current = tyRef.current;
        } else if (touches.length === 2) {
          startDistRef.current = getDistance(touches);
          startScaleRef.current = scaleRef.current;
        }
      },
      onPanResponderMove: (evt) => {
        const touches = evt.nativeEvent.touches;

        if (touches.length === 1 && touches[0] && scaleRef.current > 1.0) {
          // One-finger pan around enlarged image
          const dx = touches[0].pageX - startTouchRef.current.x;
          const dy = touches[0].pageY - startTouchRef.current.y;
          const nextTx = startTxRef.current + dx;
          const nextTy = startTyRef.current + dy;
          txRef.current = nextTx;
          tyRef.current = nextTy;
          setTranslateX(nextTx);
          setTranslateY(nextTy);
        } else if (touches.length === 2) {
          // Two-finger pinch zoom (0.6x to 5.0x)
          const currentDist = getDistance(touches);
          if (startDistRef.current && startDistRef.current > 0 && currentDist > 0) {
            const factor = currentDist / startDistRef.current;
            const nextScale = Math.max(0.6, Math.min(5.0, startScaleRef.current * factor));
            scaleRef.current = nextScale;
            setScale(nextScale);
          }
        }
      },
      onPanResponderRelease: () => {
        if (scaleRef.current < 1.0) {
          scaleRef.current = 1.0;
          txRef.current = 0;
          tyRef.current = 0;
          setScale(1.0);
          setTranslateX(0);
          setTranslateY(0);
        }
        startDistRef.current = null;
      },
    })
  ).current;

  return (
    <View style={styles.imageViewerSheet} {...panResponder.panHandlers}>
      <View style={styles.imageViewerScrollContent}>
        <Image
          source={{ uri: fullUri }}
          style={[
            styles.fullViewerImage,
            { transform: [{ translateX }, { translateY }, { scale }] },
          ]}
          resizeMode="contain"
        />
      </View>
    </View>
  );
}

function DocHtmlCard({ html }: { html: string }) {
  let formattedHtml = html;

  // Replace or inject fixed A4 paper canvas viewport (width=794px) so print layout does not collapse
  const a4Viewport = '<meta name="viewport" content="width=794, initial-scale=0.45, minimum-scale=0.2, maximum-scale=3.0, user-scalable=yes" />';

  if (formattedHtml.includes('<meta name="viewport"')) {
    formattedHtml = formattedHtml.replace(/<meta\s+name="viewport"[^>]*>/i, a4Viewport);
  } else if (formattedHtml.includes("<head>")) {
    formattedHtml = formattedHtml.replace("<head>", `<head>${a4Viewport}`);
  } else {
    formattedHtml = `${a4Viewport}${formattedHtml}`;
  }

  // Ensure BASE_PDF_CSS is present
  if (!formattedHtml.includes("font-family: 'Inter'") && !formattedHtml.includes("BASE_PDF_CSS")) {
    if (formattedHtml.includes("</head>")) {
      formattedHtml = formattedHtml.replace("</head>", `<style>${BASE_PDF_CSS}</style></head>`);
    } else if (formattedHtml.includes("<head>")) {
      formattedHtml = formattedHtml.replace("<head>", `<head><style>${BASE_PDF_CSS}</style>`);
    }
  }

  if (Platform.OS === "web") {
    return (
      <iframe
        srcDoc={formattedHtml}
        style={{
          width: "100%",
          minWidth: 340,
          maxWidth: 640,
          height: "100%",
          minHeight: 720,
          border: "none",
          backgroundColor: "#ffffff",
          borderRadius: 8,
          boxShadow: "0 4px 16px rgba(0,0,0,0.18)",
        }}
      />
    );
  }

  return (
    <View style={styles.a4PageSheet}>
      <WebView
        originWhitelist={["*"]}
        allowFileAccess={true}
        allowUniversalAccessFromFileURLs={true}
        allowFileAccessFromFileURLs={true}
        source={{ html: formattedHtml }}
        scalesPageToFit={true}
        showsHorizontalScrollIndicator={false}
        showsVerticalScrollIndicator={false}
        style={styles.docWebView}
      />
    </View>
  );
}

function NativeHtmlRenderer({ htmlContent }: { htmlContent: string }) {
  const hospNameMatch = /<h1 class="hospital-name">([\s\S]*?)<\/h1>/i.exec(htmlContent);
  const hospitalName = hospNameMatch && hospNameMatch[1] ? hospNameMatch[1].replace(/<[^>]+>/g, "").trim() : "HIMS Hospital";

  const hospInfoMatches = Array.from(htmlContent.matchAll(/<p class="hospital-info">([\s\S]*?)<\/p>/gi))
    .map((m) => (m[1] ? m[1].replace(/<[^>]+>/g, "").trim() : ""))
    .filter(Boolean);

  const logoMatch = /<div class="logo-container">\s*<img src="([^"]+)"/i.exec(htmlContent);
  const logoUrl = logoMatch && logoMatch[1] ? logoMatch[1] : null;

  const infoItemMatches = Array.from(
    htmlContent.matchAll(
      /<div class="info-item">[\s\S]*?<span class="info-label">([\s\S]*?)<\/span>[\s\S]*?<span class="info-val">([\s\S]*?)<\/span>[\s\S]*?<\/div>/gi
    )
  );
  const patientInfoItems = infoItemMatches.map((m) => ({
    label: m[1] ? m[1].replace(/:$/, "").trim() : "",
    val: m[2] ? m[2].replace(/<[^>]+>/g, "").trim() : "",
  }));

  const imgSrcMatches = Array.from(htmlContent.matchAll(/<img[^>]+src=["']([^"']+)["']/gi))
    .map((m) => m[1] || "")
    .filter((src) => src.length > 0 && src !== logoUrl);

  const sigMatch = /<div class="signature-line">([\s\S]*?)<\/div>/i.exec(htmlContent);
  const signatureName = sigMatch && sigMatch[1] ? sigMatch[1].replace(/<[^>]+>/g, "").trim() : null;

  const cleanedText = htmlContent
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<div class="header-container">[\s\S]*?<\/div>\s*<\/div>/gi, "")
    .replace(/<div class="patient-card">[\s\S]*?<\/div>/gi, "")
    .replace(/<div class="signature-box">[\s\S]*?<\/div>/gi, "")
    .replace(/<div class="footer">[\s\S]*?<\/div>/gi, "")
    .replace(/<div class="section-title">([^<]+)<\/div>/gi, "\n\n=== $1 ===\n")
    .replace(/<h[1-6][^>]*>([\s\S]*?)<\/h[1-6]>/gi, "\n\n$1\n")
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/p>/gi, "\n\n")
    .replace(/<\/tr>/gi, "\n")
    .replace(/<\/td>/gi, "   |   ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/\n\s*\n\s*\n/g, "\n\n")
    .trim();

  return (
    <View style={styles.nativeDocumentCanvas}>
      {/* Hospital Header Banner */}
      <View style={styles.docHeaderBanner}>
        {logoUrl ? (
          <Image source={{ uri: logoUrl }} style={styles.docLogoImage} resizeMode="contain" />
        ) : (
          <View style={styles.docLogoFallback}>
            <Text style={styles.docLogoText}>+</Text>
          </View>
        )}
        <View style={{ flex: 1 }}>
          <Text style={styles.docHospitalTitle}>{hospitalName}</Text>
          {hospInfoMatches.map((info, idx) => (
            <Text key={idx} style={styles.docHospitalSub}>
              {info}
            </Text>
          ))}
        </View>
      </View>

      {/* Patient Detail Card Grid */}
      {patientInfoItems.length > 0 && (
        <View style={styles.docPatientGrid}>
          {patientInfoItems.map((item, idx) => (
            <View key={idx} style={styles.docPatientCell}>
              <Text style={styles.docPatientLabel}>{item.label}</Text>
              <Text style={styles.docPatientVal}>{item.val}</Text>
            </View>
          ))}
        </View>
      )}

      {/* Embedded Diagrams & Images */}
      {imgSrcMatches.length > 0 && (
        <View style={styles.nativeImgGrid}>
          {imgSrcMatches.map((src, i) => (
            <View key={i} style={styles.nativeImgCard}>
              <Image source={{ uri: src }} style={styles.nativeImgItem} resizeMode="contain" />
              <Text style={styles.nativeImgLabel}>Clinical Diagram #{i + 1}</Text>
            </View>
          ))}
        </View>
      )}

      {/* Document Body Text & Tables */}
      <Text style={styles.nativePaperText}>{cleanedText}</Text>

      {/* Consultant Signature Box */}
      {signatureName && (
        <View style={styles.docSignatureBox}>
          <View style={styles.docSignatureLine} />
          <Text style={styles.docSignatureName}>{signatureName}</Text>
          <Text style={styles.docSignatureTitle}>Authorized Signatory</Text>
        </View>
      )}
    </View>
  );
}

/** Helper to generate and open PDF share/save dialog for downloading without printing. */
async function downloadHtml(html: string) {
  try {
    const { uri } = await Print.printToFileAsync({ html });
    if (await Sharing.isAvailableAsync()) {
      await Sharing.shareAsync(uri, {
        UTI: "com.adobe.pdf",
        mimeType: "application/pdf",
        dialogTitle: "Save PDF Document",
      });
    } else {
      Alert.alert("PDF Generated", `Document PDF generated successfully: ${uri}`);
    }
  } catch (err) {
    console.error("PDF download error", err);
    Alert.alert("Download Error", "Could not generate PDF document. Please try again.");
  }
}

/** Check if an attachment is an image based on filename or mime type. */
function isImageFile(fileName?: string, contentType?: string): boolean {
  if (contentType && contentType.toLowerCase().startsWith("image/")) return true;
  if (fileName) {
    const ext = fileName.toLowerCase().split(".").pop();
    return ["png", "jpg", "jpeg", "webp", "gif", "bmp"].includes(ext || "");
  }
  return false;
}

/** Check if an attachment is a PDF document based on filename or mime type. */
function isPdfFile(fileName?: string, contentType?: string): boolean {
  if (contentType && contentType.toLowerCase().includes("pdf")) return true;
  if (fileName) {
    return fileName.toLowerCase().endsWith(".pdf");
  }
  return false;
}

/** Check if a string looks like a base64 image data URI. */
function isBase64Image(v: unknown): v is string {
  return typeof v === "string" && v.startsWith("data:image/");
}

/** Extract image URIs from a field value. */
function extractImageUris(value: unknown): string[] {
  if (!value) return [];
  if (isBase64Image(value)) return [value];
  if (typeof value === "string") {
    if (value.startsWith("http://") || value.startsWith("https://")) return [value];
    try {
      const parsed = JSON.parse(value);
      return extractImageUris(parsed);
    } catch {
      return [];
    }
  }
  if (Array.isArray(value)) {
    return value.flatMap((item) => extractImageUris(item));
  }
  if (typeof value === "object" && value !== null) {
    const obj = value as Record<string, unknown>;
    if (isBase64Image(obj.annotated)) return [obj.annotated as string];
    if (isBase64Image(obj.original)) return [obj.original as string];
    if (typeof obj.annotated === "string" && (obj.annotated.startsWith("http://") || obj.annotated.startsWith("https://"))) return [obj.annotated];
    if (typeof obj.original === "string" && (obj.original.startsWith("http://") || obj.original.startsWith("https://"))) return [obj.original];
    const images = Object.values(value).filter(isBase64Image);
    if (images.length > 0) return images as string[];
  }
  return [];
}

/** Format a case-sheet field value for display. Returns null/empty if empty so empty fields are omitted. */
function formatFieldValue(value: unknown): string | null {
  if (value == null || value === "") return null;
  if (extractImageUris(value).length > 0) return null;
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "string" || typeof value === "number") {
    if (isBase64Image(value)) return null;
    const str = String(value).trim();
    return str.length > 0 ? str : null;
  }
  if (Array.isArray(value)) {
    const joined = value
      .map((item) => {
        if (isBase64Image(item) || extractImageUris(item).length > 0) return null;
        if (typeof item === "object" && item !== null) {
          return Object.values(item)
            .filter((v) => v != null && v !== "" && !isBase64Image(v))
            .join(", ");
        }
        return String(item);
      })
      .filter(Boolean)
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
    padding: 16px;
    font-size: 10.5px;
    line-height: 1.5;
    background: #ffffff;
  }
  .header-container {
    display: flex;
    align-items: center;
    border-bottom: 2px solid #e5e7eb;
    padding-bottom: 10px;
    margin-bottom: 14px;
  }
  .logo-container {
    width: 48px;
    height: 48px;
    margin-right: 12px;
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
    font-size: 14px;
    font-weight: 800;
    color: #111827;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin: 0 0 2px 0;
  }
  .hospital-info {
    font-size: 9.5px;
    color: #4b5563;
    margin: 0;
  }
  .patient-card {
    background-color: #f9fafb;
    border: 1px solid #e5e7eb;
    border-radius: 6px;
    padding: 8px 12px;
    margin-bottom: 14px;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 6px;
  }
  .info-item {
    font-size: 10px;
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
    font-size: 11px;
    font-weight: 700;
    color: #111827;
    border-bottom: 1.5px solid #d1d5db;
    padding-bottom: 3px;
    margin-top: 14px;
    margin-bottom: 8px;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
  .group-title {
    font-size: 11px;
    font-weight: 700;
    color: #111827;
    border-bottom: 1px solid #e5e7eb;
    padding-bottom: 3px;
    margin-top: 12px;
    margin-bottom: 6px;
    text-transform: uppercase;
  }
  .field-row {
    margin-bottom: 6px;
    display: flex;
    flex-wrap: wrap;
    border-bottom: 1px solid #f3f4f6;
    padding-bottom: 4px;
  }
  .field-label {
    font-weight: 600;
    color: #374151;
    width: 150px;
    flex-shrink: 0;
    font-size: 10px;
  }
  .field-val {
    flex-grow: 1;
    color: #111827;
    font-size: 10px;
  }
  .print-table {
    width: 100%;
    border-collapse: collapse;
    margin-top: 6px;
    margin-bottom: 14px;
  }
  .print-table th, .print-table td {
    border: 1px solid #e5e7eb;
    padding: 5px 7px;
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
    margin: 20px 0 12px;
    font-size: 10px;
    color: #4b5563;
    font-weight: 700;
    letter-spacing: 1px;
  }
  .signature-box {
    margin-top: 20px;
    text-align: right;
  }
  .signature-line {
    display: inline-block;
    border-top: 1px solid #111827;
    width: 160px;
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

/** Helper to generate and open PDF share/save dialog. */
async function printOrShareHtml(html: string) {
  try {
    const { uri } = await Print.printToFileAsync({ html });
    if (await Sharing.isAvailableAsync()) {
      await Sharing.shareAsync(uri, {
        UTI: "com.adobe.pdf",
        mimeType: "application/pdf",
        dialogTitle: "Save Document",
      });
    } else {
      Alert.alert("PDF Generated", `Document PDF generated successfully: ${uri}`);
    }
  } catch (err) {
    console.error("PDF generation error", err);
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
      onDownload: () => void Sharing.shareAsync(imageUri),
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

/* ========================================================================== */
/* SECTION 1: CASESHEET CARD (FOR OP)                                         */
/* ========================================================================== */

function CasesheetSectionCard({
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
      <View style={styles.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Casesheet</Heading>
          <Caption>
            {templateName ? `${templateName} · Recorded` : "Clinical examination and notes"}
          </Caption>
        </View>
      </View>

      <View style={[styles.actionRow, { flexDirection: "row", gap: spacing.sm }]}>
        <Pressable
          onPress={() => handleAction("view")}
          disabled={viewing || downloading || isLoading || !sections.length}
          style={[
            styles.secondaryActionBtn,
            { flex: 1 },
            (viewing || downloading || isLoading || !sections.length) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          {viewing ? (
            <ActivityIndicator size="small" color={colors.primary} />
          ) : (
            <View style={styles.btnContent}>
              <Ionicons name="eye-outline" color={colors.primary} size={16} />
              <Text style={styles.secondaryActionBtnText}>View</Text>
            </View>
          )}
        </Pressable>

        <Pressable
          onPress={() => handleAction("download")}
          disabled={viewing || downloading || isLoading || !sections.length}
          style={[
            styles.primaryActionBtn,
            { flex: 1 },
            (viewing || downloading || isLoading || !sections.length) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          {downloading ? (
            <ActivityIndicator size="small" color={colors.surface} />
          ) : (
            <View style={styles.btnContent}>
              <DownloadTrayIcon color={colors.surface} size={16} />
              <Text style={styles.primaryActionBtnText}>Download</Text>
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
              <span>HIMS Patient Health Record</span>
              <span>Keep out of reach of children</span>
            </div>
          </body>
        </html>
      `;

      if (mode === "view") {
        if (onViewHtml) {
          onViewHtml("Prescription Document (Rx)", html, () => void downloadHtml(html));
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

      <View style={[styles.actionRow, { flexDirection: "row", gap: spacing.sm }]}>
        <Pressable
          onPress={() => handleAction("view")}
          disabled={viewing || downloading || isLoading || totalMedicines === 0}
          style={[
            styles.secondaryActionBtn,
            { flex: 1 },
            (viewing || downloading || isLoading || totalMedicines === 0) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          {viewing ? (
            <ActivityIndicator size="small" color={colors.primary} />
          ) : (
            <View style={styles.btnContent}>
              <Ionicons name="eye-outline" color={colors.primary} size={16} />
              <Text style={styles.secondaryActionBtnText}>View</Text>
            </View>
          )}
        </Pressable>

        <Pressable
          onPress={() => handleAction("download")}
          disabled={viewing || downloading || isLoading || totalMedicines === 0}
          style={[
            styles.primaryActionBtn,
            { flex: 1 },
            (viewing || downloading || isLoading || totalMedicines === 0) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          {downloading ? (
            <ActivityIndicator size="small" color={colors.surface} />
          ) : (
            <View style={styles.btnContent}>
              <DownloadTrayIcon color={colors.surface} size={16} />
              <Text style={styles.primaryActionBtnText}>Download</Text>
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
        // mode === "view"
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
      <View style={styles.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Diagnostic Orders</Heading>
          <Caption>
            {totalTests > 0 ? `${totalTests} test order(s) placed` : "No diagnostic tests recorded"}
          </Caption>
        </View>
      </View>

      {totalTests > 0 && (
        <View style={[styles.actionRow, { flexDirection: "row", gap: spacing.sm }]}>
          <Pressable
            onPress={() => handleAction("view")}
            disabled={viewing || downloading || isLoading}
            style={[
              styles.secondaryActionBtn,
              { flex: 1 },
              (viewing || downloading || isLoading) && { opacity: 0.6 },
            ]}
            hitSlop={8}
          >
            {viewing ? (
              <ActivityIndicator size="small" color={colors.primary} />
            ) : (
              <View style={styles.btnContent}>
                <Ionicons name="eye-outline" color={colors.primary} size={16} />
                <Text style={styles.secondaryActionBtnText}>View Reports</Text>
              </View>
            )}
          </Pressable>

          <Pressable
            onPress={() => handleAction("download")}
            disabled={viewing || downloading || isLoading}
            style={[
              styles.primaryActionBtn,
              { flex: 1 },
              (viewing || downloading || isLoading) && { opacity: 0.6 },
            ]}
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
                <View key={att.attachmentId} style={styles.attachmentItemRow}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.attachmentName} numberOfLines={1}>
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
                      style={styles.attachmentDownloadBtn}
                      hitSlop={8}
                    >
                      <View style={styles.btnSmallContent}>
                        <Ionicons name="eye-outline" color={colors.primary} size={13} />
                        <Text style={styles.attachmentDownloadText}>View</Text>
                      </View>
                    </Pressable>

                    <Pressable
                      onPress={() => handleAttachmentAction(att, "download")}
                      disabled={isBusy}
                      style={[styles.attachmentDownloadBtn, { backgroundColor: colors.primary }]}
                      hitSlop={8}
                    >
                      {isBusy ? (
                        <ActivityIndicator size="small" color={colors.surface} />
                      ) : (
                        <View style={styles.btnSmallContent}>
                          <DownloadTrayIcon color={colors.surface} size={13} />
                          <Text style={[styles.attachmentDownloadText, { color: colors.surface }]}>Download</Text>
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
        <View style={styles.imageModalOverlay}>
          <View style={styles.imageModalHeader}>
            <Text style={styles.imageModalTitle} numberOfLines={1}>
              {previewImage?.title}
            </Text>
            <Pressable
              onPress={() => setPreviewImage(null)}
              hitSlop={8}
              style={styles.modalCloseBtn}
            >
              <Text style={styles.modalCloseText}>✕</Text>
            </Pressable>
          </View>
          {previewImage ? (
            <Image
              source={{ uri: previewImage.uri }}
              style={styles.fullImagePreview}
              resizeMode="contain"
            />
          ) : null}
        </View>
      </Modal>
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
      <View style={styles.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Bills</Heading>
          <Caption>
            {bill
              ? `${billNumberDisplay} · Total: ₹ ${bill.totalAmount.toFixed(2)}${bill.paidAmount > 0 ? ` · Paid: ₹ ${bill.paidAmount.toFixed(2)}` : ""}`
              : "No invoice or receipt recorded"}
          </Caption>
        </View>
      </View>

      <View style={[styles.actionRow, { flexDirection: "row", gap: spacing.sm }]}>
        <Pressable
          onPress={() => handleBillAction("view")}
          disabled={viewingBill || downloadingBill || isLoading || !bill}
          style={[
            styles.secondaryActionBtn,
            { flex: 1 },
            (viewingBill || downloadingBill || isLoading || !bill) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          {viewingBill ? (
            <ActivityIndicator size="small" color={colors.primary} />
          ) : (
            <View style={styles.btnContent}>
              <Ionicons name="eye-outline" color={colors.primary} size={16} />
              <Text style={styles.secondaryActionBtnText}>View Bill</Text>
            </View>
          )}
        </Pressable>

        <Pressable
          onPress={() => handleBillAction("download")}
          disabled={viewingBill || downloadingBill || isLoading || !bill}
          style={[
            styles.primaryActionBtn,
            { flex: 1 },
            (viewingBill || downloadingBill || isLoading || !bill) && { opacity: 0.6 },
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
      </View>

      {bill && bill.paidAmount > 0 && (
        <View style={{ marginTop: spacing.sm }}>
          <Pressable
            onPress={handleOpenReceiptsModal}
            disabled={isLoading}
            style={[styles.secondaryActionBtn, { width: "100%" }]}
            hitSlop={8}
          >
            <View style={styles.btnContent}>
              <Ionicons name="receipt-outline" color={colors.primary} size={16} />
              <Text style={styles.secondaryActionBtnText}>
                Receipts {receipts.length > 0 ? `(${receipts.length})` : ""}
              </Text>
            </View>
          </Pressable>
        </View>
      )}

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
                  const isBusy = busyReceiptId === r.receiptId;
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

                      <View style={{ flexDirection: "row", gap: spacing.xs }}>
                        <Pressable
                          onPress={() => handleSingleReceiptAction(r, "view")}
                          disabled={isBusy}
                          style={styles.receiptDownloadBtn}
                          hitSlop={8}
                        >
                          <View style={styles.btnSmallContent}>
                            <Ionicons name="eye-outline" color={colors.primary} size={13} />
                            <Text style={styles.receiptDownloadBtnText}>View</Text>
                          </View>
                        </Pressable>

                        <Pressable
                          onPress={() => handleSingleReceiptAction(r, "download")}
                          disabled={isBusy}
                          style={[styles.receiptDownloadBtn, { backgroundColor: colors.primary }]}
                          hitSlop={8}
                        >
                          {isBusy ? (
                            <ActivityIndicator size="small" color={colors.surface} />
                          ) : (
                            <View style={styles.btnSmallContent}>
                              <DownloadTrayIcon color={colors.surface} size={13} />
                              <Text style={[styles.receiptDownloadBtnText, { color: colors.surface }]}>Download</Text>
                            </View>
                          )}
                        </Pressable>
                      </View>
                    </View>
                  );
                })
              ) : (
                <View style={styles.receiptCard}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.receiptNumber}>{bill?.billNumber ?? "Receipt"}</Text>
                    <Text style={styles.receiptAmount}>Paid: ₹ {bill?.paidAmount.toFixed(2)}</Text>
                  </View>

                  <View style={{ flexDirection: "row", gap: spacing.xs }}>
                    <Pressable
                      onPress={() => handleSingleReceiptAction(undefined, "view")}
                      disabled={busyReceiptId === "default"}
                      style={styles.receiptDownloadBtn}
                      hitSlop={8}
                    >
                      <View style={styles.btnSmallContent}>
                        <Ionicons name="eye-outline" color={colors.primary} size={13} />
                        <Text style={styles.receiptDownloadBtnText}>View</Text>
                      </View>
                    </Pressable>

                    <Pressable
                      onPress={() => handleSingleReceiptAction(undefined, "download")}
                      disabled={busyReceiptId === "default"}
                      style={[styles.receiptDownloadBtn, { backgroundColor: colors.primary }]}
                      hitSlop={8}
                    >
                      {busyReceiptId === "default" ? (
                        <ActivityIndicator size="small" color={colors.surface} />
                      ) : (
                        <View style={styles.btnSmallContent}>
                          <DownloadTrayIcon color={colors.surface} size={13} />
                          <Text style={[styles.receiptDownloadBtnText, { color: colors.surface }]}>Download</Text>
                        </View>
                      )}
                    </Pressable>
                  </View>
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
  onViewHtml,
}: {
  visit?: VisitDetail;
  sections: CaseSheetSection[];
  isLoading: boolean;
  onViewHtml?: (title: string, html: string, onDownload?: () => void) => void;
}) {
  const { api } = useContainer();
  const [viewing, setViewing] = useState(false);
  const [downloading, setDownloading] = useState(false);

  const handleAction = async (mode: "view" | "download") => {
    mode === "view" ? setViewing(true) : setDownloading(true);
    try {
      if (visit?.encounterId) {
        try {
          const res = await api.getVisitPrint(visit.encounterId, "DISCHARGE_SUMMARY");
          if (res?.printData) {
            if (mode === "view") {
              if (onViewHtml) {
                onViewHtml("Discharge Summary", res.printData, () => void downloadHtml(res.printData));
              } else {
                await downloadHtml(res.printData);
              }
            } else {
              await downloadHtml(res.printData);
            }
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

      if (mode === "view") {
        if (onViewHtml) {
          onViewHtml("Discharge Summary", html, () => void downloadHtml(html));
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
      <View style={styles.sectionHeaderRow}>
        <View style={{ flex: 1 }}>
          <Heading>Discharge Summary</Heading>
          <Caption>
            {templateName ? `${templateName} · Recorded` : "Inpatient discharge notes & summary"}
          </Caption>
        </View>
      </View>

      <View style={[styles.actionRow, { flexDirection: "row", gap: spacing.sm }]}>
        <Pressable
          onPress={() => handleAction("view")}
          disabled={viewing || downloading || isLoading}
          style={[
            styles.secondaryActionBtn,
            { flex: 1 },
            (viewing || downloading || isLoading) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          {viewing ? (
            <ActivityIndicator size="small" color={colors.primary} />
          ) : (
            <View style={styles.btnContent}>
              <Ionicons name="eye-outline" color={colors.primary} size={16} />
              <Text style={styles.secondaryActionBtnText}>View</Text>
            </View>
          )}
        </Pressable>

        <Pressable
          onPress={() => handleAction("download")}
          disabled={viewing || downloading || isLoading}
          style={[
            styles.primaryActionBtn,
            { flex: 1 },
            (viewing || downloading || isLoading) && { opacity: 0.6 },
          ]}
          hitSlop={8}
        >
          {downloading ? (
            <ActivityIndicator size="small" color={colors.surface} />
          ) : (
            <View style={styles.btnContent}>
              <DownloadTrayIcon color={colors.surface} size={16} />
              <Text style={styles.primaryActionBtnText}>Download</Text>
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

  /* In-App Full-Screen Document & Image Viewer Modal Styles */
  viewerOverlay: {
    flex: 1,
    backgroundColor: "#ffffff",
    justifyContent: "flex-start",
    alignItems: "center",
  },
  viewerHeader: {
    width: "100%",
    height: 54,
    backgroundColor: colors.surface,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 4,
    zIndex: 20,
  },
  viewerHeaderTitleGroup: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    maxWidth: "50%",
  },
  viewerHeaderTitle: {
    ...typography.label,
    fontSize: 14,
    fontWeight: "700",
    color: colors.text,
  },
  viewerActions: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  viewerDownloadBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: colors.primary,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    borderRadius: radius.pill,
  },
  viewerDownloadBtnText: {
    ...typography.caption,
    fontSize: 12,
    fontWeight: "700",
    color: colors.surface,
  },
  viewerCloseBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: colors.surfaceAlt,
    alignItems: "center",
    justifyContent: "center",
  },

  /* Canvas */
  viewerCanvasContainer: {
    flex: 1,
    width: "100%",
    padding: spacing.xs,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#ffffff",
  },
  viewerScrollContent: {
    alignItems: "center",
    paddingVertical: spacing.xs,
  },
  imageViewerSheet: {
    width: "100%",
    height: "100%",
    maxWidth: 640,
    backgroundColor: "#ffffff",
    borderRadius: radius.md,
    overflow: "hidden",
    alignItems: "center",
    justifyContent: "center",
  },
  imgZoomBar: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "rgba(15, 23, 42, 0.85)",
    borderRadius: radius.pill,
    paddingHorizontal: 6,
    paddingVertical: 3,
    gap: 6,
    position: "absolute",
    top: 12,
    right: 12,
    zIndex: 30,
  },
  imgZoomBtn: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: "rgba(255, 255, 255, 0.2)",
    alignItems: "center",
    justifyContent: "center",
  },
  imgZoomResetBtn: {
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  imgZoomResetText: {
    fontSize: 12,
    fontWeight: "700",
    color: "#ffffff",
  },
  imageViewerScrollContent: {
    flexGrow: 1,
    alignItems: "center",
    justifyContent: "center",
    width: "100%",
    height: "100%",
  },
  fullViewerImage: {
    width: "100%",
    height: "100%",
    minWidth: 300,
    minHeight: 450,
  },
  viewerEmptyText: {
    color: colors.textMuted,
    ...typography.body,
  },

  /* Real A4 PDF Page Document Styling */
  a4PageSheet: {
    width: "100%",
    height: "100%",
    maxWidth: 680,
    backgroundColor: "#ffffff",
    borderRadius: radius.md,
    overflow: "hidden",
    shadowColor: "#000000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 12,
    elevation: 8,
    borderWidth: 1,
    borderColor: "#e2e8f0",
  },
  docWebView: {
    flex: 1,
    width: "100%",
    height: "100%",
    backgroundColor: "#ffffff",
  },
  paperCardContainer: {
    width: "100%",
    maxWidth: 580,
    minHeight: 680,
    backgroundColor: "#ffffff",
    borderRadius: radius.md,
    padding: spacing.lg,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 10,
    elevation: 6,
  },
  nativeDocumentCanvas: {
    gap: spacing.md,
  },
  docHeaderBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    paddingBottom: spacing.md,
    borderBottomWidth: 2,
    borderBottomColor: colors.border,
  },
  docLogoImage: {
    width: 48,
    height: 48,
    borderRadius: radius.sm,
  },
  docLogoFallback: {
    width: 48,
    height: 48,
    borderRadius: radius.sm,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
  docLogoText: {
    color: "#ffffff",
    fontSize: 22,
    fontWeight: "bold",
  },
  docHospitalTitle: {
    fontSize: 16,
    fontWeight: "800",
    color: colors.text,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  docHospitalSub: {
    fontSize: 11,
    color: colors.textMuted,
    marginTop: 1,
  },
  docPatientGrid: {
    backgroundColor: colors.surfaceAlt,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.sm,
    padding: spacing.md,
    flexDirection: "row",
    flexWrap: "wrap",
    gap: spacing.sm,
  },
  docPatientCell: {
    width: "47%",
  },
  docPatientLabel: {
    fontSize: 10,
    fontWeight: "700",
    color: colors.textMuted,
    textTransform: "uppercase",
  },
  docPatientVal: {
    fontSize: 12,
    fontWeight: "600",
    color: colors.text,
    marginTop: 1,
  },
  docSignatureBox: {
    marginTop: spacing.lg,
    alignSelf: "flex-end",
    alignItems: "center",
    minWidth: 160,
  },
  docSignatureLine: {
    width: 140,
    height: 1,
    backgroundColor: colors.border,
    marginBottom: 4,
  },
  docSignatureName: {
    fontSize: 12,
    fontWeight: "700",
    color: colors.text,
  },
  docSignatureTitle: {
    fontSize: 10,
    color: colors.textMuted,
  },
  nativePaperCard: {
    gap: spacing.md,
  },
  nativeImgGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: spacing.sm,
    marginBottom: spacing.sm,
  },
  nativeImgCard: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.sm,
    padding: spacing.xs,
    backgroundColor: colors.surfaceAlt,
    alignItems: "center",
  },
  nativeImgItem: {
    width: 140,
    height: 140,
    borderRadius: radius.sm,
  },
  nativeImgLabel: {
    fontSize: 10,
    color: colors.textMuted,
    marginTop: 4,
  },
  nativePaperText: {
    ...typography.body,
    fontSize: 12,
    lineHeight: 18,
    color: colors.text,
    fontFamily: "monospace",
  },

  /* Image Preview Modal Styles */
  imageModalOverlay: {
    flex: 1,
    backgroundColor: "rgba(0, 0, 0, 0.92)",
    justifyContent: "center",
    alignItems: "center",
    padding: spacing.md,
  },
  imageModalHeader: {
    width: "100%",
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    position: "absolute",
    top: 40,
    left: 0,
    right: 0,
    zIndex: 10,
  },
  imageModalTitle: {
    ...typography.label,
    color: "#ffffff",
    fontWeight: "700",
    flex: 1,
    marginRight: spacing.md,
  },
  fullImagePreview: {
    width: "100%",
    height: "80%",
    marginTop: 60,
  },
});
