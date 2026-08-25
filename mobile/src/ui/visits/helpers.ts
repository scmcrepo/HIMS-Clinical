import { Alert, Platform } from "react-native";
import * as Print from "expo-print";
import * as Sharing from "expo-sharing";
import { formatIsoDate } from "../../core/format";
import type { VisitDetail } from "../../core/contracts";

/** Check if an attachment is an image based on filename or mime type. */
export function isImageFile(fileName?: string, contentType?: string): boolean {
  if (contentType && contentType.toLowerCase().startsWith("image/")) return true;
  if (fileName) {
    const ext = fileName.toLowerCase().split(".").pop();
    return ["png", "jpg", "jpeg", "webp", "gif", "bmp"].includes(ext || "");
  }
  return false;
}

/** Check if an attachment is a PDF document based on filename or mime type. */
export function isPdfFile(fileName?: string, contentType?: string): boolean {
  if (contentType && contentType.toLowerCase().includes("pdf")) return true;
  if (fileName) {
    return fileName.toLowerCase().endsWith(".pdf");
  }
  return false;
}

/** Check if a string looks like a base64 image data URI. */
export function isBase64Image(v: unknown): v is string {
  return typeof v === "string" && v.startsWith("data:image/");
}

/** Extract image URIs from a field value. */
export function extractImageUris(value: unknown): string[] {
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
export function formatFieldValue(value: unknown): string | null {
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
export const BASE_PDF_CSS = `
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
export function renderPdfHeaderAndPatientCard(visit?: VisitDetail): string {
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
export async function downloadHtml(html: string) {
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

/** Helper to generate and open PDF share/save dialog. */
export async function printOrShareHtml(html: string) {
  return downloadHtml(html);
}
