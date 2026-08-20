/**
 * OpCaseSheetPage.tsx — full OP case sheet with:
 *  - Left column: Patient visit history timeline
 *  - Right column: Selected visit case sheet details with curved consultant header tab
 *  - 4 tabs: Clinical Notes, Prescription, Diagnostic Order, Attachments, Vitals
 */
import { useState, useEffect, useRef, lazy, Suspense } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Paperclip, Eye, Download, Activity, ClipboardList, Pill, TestTube, AlertTriangle, FileText } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { encounterApi } from '../../../services/encounter/encounterApi'
import { opQueueApi, templateApi } from '../../../services/casesheet/casesheetApi'
// Master template and department APIs removed to use consolidated listTemplates API
import { consultantApi } from '../../../services/consultant/consultantApi'
import { DynamicCaseSheetForm } from '../components/DynamicCaseSheetForm'
import { PrescriptionTab } from '../components/PrescriptionTab'
import { DiagnosticOrderTab } from '../components/DiagnosticOrderTab'
import { attachmentApi, type Attachment } from '../../../services/attachment/attachmentApi'
import { opPrescriptionApi, opDiagnosticApi } from '../../../services/opip/opipApi'
import { configApi } from '../../../services/config/configApi'

const ExternalRecordsViewer = lazy(() => import('../../abdm/components/ExternalRecordsViewer'))
import { formatDateTime } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'
import BackButton from '../../../components/shared/BackButton'
import { toast } from '../../../hooks/useToast'
import { usePatient } from '../../../hooks/patient/usePatient'
import { diagnosticReportApi } from '../../../services/diagnostic/diagnosticReportApi'
import { diagTemplateApi } from '../../../services/diagnostic/diagTemplateApi'
import type { CaseSheetData } from '../../../types/casesheet'
import { useAuthStore } from '../../../store/authStore'

/* const STATUS_STYLES: Record<string, string> = {
  CHECKED_IN: 'bg-orange-50 text-orange-700 border-orange-200',
  CONSULTATION_STARTED: 'bg-purple-50 text-purple-700 border-purple-200',
  CASESHEET_RECORDED: 'bg-amber-50 text-amber-700 border-amber-200',
  BILLING_DONE: 'bg-green-50 text-green-700 border-green-200',
}
const STATUS_LABELS: Record<string, string> = {
  CHECKED_IN: 'Checked In',
  CONSULTATION_STARTED: 'Vitals Entered',
  CASESHEET_RECORDED: 'Casesheet Done',
  BILLING_DONE: 'Consulted',
} */

type Tab = 'vitals' | 'clinical' | 'prescription' | 'diagnostic' | 'attachments' | 'externalRecords'

export default function OpCaseSheetPage() {
  const { encounterId } = useParams<{ encounterId: string }>()
  const [activeTab, setActiveTab] = useState<Tab>('vitals')
  const [sidebarCollapsed, setSidebarCollapsed] = useState(true)
  const qc = useQueryClient()
  const selectedBranchId = useAuthStore(s => s.selectedBranchId)
  const tenantId = useAuthStore(s => s.user?.tenantId)
  const loggedInConsultantId = useAuthStore(s => s.user?.consultantId)

  // 1. Fetch current encounter
  const { data: encounter, isLoading: encLoading } = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn: () => encounterApi.getById(encounterId!),
    enabled: !!encounterId,
    refetchInterval: 30_000,
  })

  // Fetch patient details for demographics
  const { data: patient } = usePatient(encounter?.patientId)

  // 2. Fetch consultants
  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'],
    queryFn: consultantApi.getAll,
  })

  // 3. Fetch patient's full encounter history (filter to OP only for OP casesheet)
  const { data: encountersPage } = useQuery({
    queryKey: ['patient-encounters', encounter?.patientId],
    queryFn: () => encounterApi.getByPatient(encounter!.patientId, 0, 100),
    enabled: !!encounter?.patientId,
  })
  const patientEncounters = (encountersPage?.content ?? []).filter(e => e.encounterType === 'OUTPATIENT')

  // 4. Load case sheet data for the current encounter
  const { data: csData, isLoading: csLoading } = useQuery({
    queryKey: ['op-casesheet', encounterId],
    queryFn: () => opQueueApi.loadCasesheet(encounterId!, undefined, 'OP'),
    enabled: !!encounterId,
  })

  const selectedConsultant = consultants.find(c => c.id === encounter?.primaryProviderId)

  // 5. Fetch templates list for the select dropdown (displays all templates, prioritized by department)
  const { data: templates = [] } = useQuery({
    queryKey: ['case-sheet-templates', 'OP', selectedConsultant?.departmentId],
    queryFn:  () => templateApi.list(undefined, 'OP', 'ACTIVE', selectedConsultant?.departmentId || undefined),
    enabled: !!encounter,
  })

  // 6. Handle selection of template when creating a new case sheet
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('')

  const { data: selectedTemplate, isLoading: templateDetailLoading } = useQuery({
    queryKey: ['case-sheet-template-detail', selectedTemplateId],
    queryFn: () => templateApi.getById(selectedTemplateId),
    enabled: !!selectedTemplateId && selectedTemplateId !== csData?.template?.id,
  })

  const lastEncounterIdRef = useRef<string | null>(null)
  const hasSyncedTemplateRef = useRef(false)

  useEffect(() => {
    if (encounterId !== lastEncounterIdRef.current) {
      setSelectedTemplateId('')
      hasSyncedTemplateRef.current = false
      lastEncounterIdRef.current = encounterId || null
    }
  }, [encounterId])

  // Sync selectedTemplateId with loaded casesheet template ONLY on initial load
  useEffect(() => {
    if (csData?.template?.id && !hasSyncedTemplateRef.current) {
      setSelectedTemplateId(csData.template.id)
      hasSyncedTemplateRef.current = true
    }
  }, [csData?.template?.id])

  // Print options and queries
  const [showPrintModal, setShowPrintModal] = useState(false)
  const [printOptions, setPrintOptions] = useState({
    caseSheet: true,
    caseSheetTemplate: true,
    prescription: true,
    diagnostic: true,
  })

  const { data: prescriptions = [] } = useQuery({
    queryKey: ['prescriptions', encounterId],
    queryFn: () => opPrescriptionApi.list(encounterId!),
    enabled: !!encounterId,
  })

  const { data: diagnosticOrders = [] } = useQuery({
    queryKey: ['diagnostic-orders', encounterId],
    queryFn: () => opDiagnosticApi.list(encounterId!),
    enabled: !!encounterId,
  })

  const { data: hospitalConfig } = useQuery({
    queryKey: ['config', 'hospital'],
    queryFn: () => configApi.getHospital(),
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['encounter', encounterId] })
    qc.invalidateQueries({ queryKey: ['op-casesheet', encounterId] })
    qc.invalidateQueries({ queryKey: ['patient-encounters', encounter?.patientId] })
    qc.invalidateQueries({ queryKey: ['op-queue'] })
  }

  const saveMut = useMutation({
    mutationFn: (data: CaseSheetData) => {
      const payload: { data: CaseSheetData; templateId?: string } = { data }
      const tid = selectedTemplateId || csData?.template?.id
      if (tid) {
        payload.templateId = tid
      }
      return opQueueApi.saveCasesheet(encounterId!, payload)
    },
    onSuccess: () => { invalidate(); toast({ title: 'Case sheet saved', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  /* const markConsultedMut = useMutation({
    mutationFn: () => opQueueApi.markConsulted(encounterId!),
    onSuccess: () => { invalidate(); toast({ title: 'Encounter marked as consulted', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  }) */

  const handlePrint = async (customOptions?: { caseSheet: boolean; caseSheetTemplate: boolean; prescription: boolean; diagnostic: boolean }) => {
    if (!encounter) return
    const options = customOptions || printOptions
    const printWindow = window.open('', '_blank')
    if (!printWindow) {
      toast({ title: 'Popup blocked', description: 'Please allow popups to print.', variant: 'destructive' })
      return
    }

    const hospitalName = hospitalConfig?.['hospital.name.param'] || 'SCMC MULTISPECIALITY HOSPITAL'
    const hospitalAddress = hospitalConfig?.['hospital.address.param'] || 'No: 01/345, Main road, Gotham Nagar, Chennai'
    const hospitalContact = hospitalConfig?.['hospital.contactNo.param'] || 'Ph: 193453343434'

    const activeTemplate = csData?.template || selectedTemplate
    const activeAnswers = csData?.records?.[0]?.data || {}

    let caseSheetHtml = ''
    if (options.caseSheet) {
      // 1. Vitals section
      const excludedKeys = new Set(['casesheet', 'dischargeNotes', 'vitals_history',
        'prescriptions', 'diagnostic_orders', 'progress_notes', 'nurse_notes', 'other_charges'])
      const vitalsEntries = Object.entries(encounter.vitalData ?? {})
        .filter(([k]) => !excludedKeys.has(k))

      let vitalsHtml = ''
      if (vitalsEntries.length > 0) {
        vitalsHtml = `
          <div class="section-title">Vital Signs</div>
          <div class="vitals-grid">
            ${vitalsEntries.map(([key, value]) => `
              <div class="vital-card">
                <div class="vital-label">${key.replace(/([A-Z])/g, ' $1').trim()}</div>
                <div class="vital-val">${String(value)}</div>
              </div>
            `).join('')}
          </div>
        `
      }

      // 2. Template form fields section
      let templateFieldsHtml = ''
      if (options.caseSheetTemplate && activeTemplate && activeTemplate.fields) {
        // Group fields into sections just like in form
        interface SectionGroup {
          title: string | null
          fields: any[]
        }
        const sections: SectionGroup[] = []
        const hasHeadings = activeTemplate.fields.some(f => f.fieldType === 'HEADING')
        
        if (hasHeadings) {
          let currentGroup: SectionGroup = { title: null, fields: [] }
          for (const f of activeTemplate.fields) {
            if (f.fieldType === 'HEADING') {
              if (currentGroup.fields.length > 0 || currentGroup.title !== null) {
                sections.push(currentGroup)
              }
              currentGroup = { title: f.label, fields: [] }
            } else {
              currentGroup.fields.push(f)
            }
          }
          if (currentGroup.fields.length > 0 || currentGroup.title !== null) {
            sections.push(currentGroup)
          }
        } else {
          const sectionMap = new Map<string, any[]>()
          for (const f of activeTemplate.fields) {
            const key = f.section ?? '__root__'
            if (!sectionMap.has(key)) sectionMap.set(key, [])
            sectionMap.get(key)!.push(f)
          }
          Array.from(sectionMap.entries()).forEach(([title, fields]) => {
            sections.push({
              title: title === '__root__' ? null : title,
              fields,
            })
          })
        }

        templateFieldsHtml = `
          <div class="section-title">${activeTemplate.name}</div>
          <div class="template-content">
            ${sections.map(sec => {
              let secHtml = ''
              if (sec.title) {
                secHtml += `<h4 style="font-size: 11px; font-weight: 700; color: #111827; border-bottom: 1px solid #e5e7eb; padding-bottom: 3px; margin-top: 14px; margin-bottom: 8px; text-transform: uppercase;">${sec.title}</h4>`
              }
              
              const fieldsHtml = sec.fields.map(f => {
                const val = activeAnswers[f.fieldKey]
                let formattedVal = ''
                
                if (f.fieldType === 'ROM_GRID') {
                  formattedVal = formatRomGrid(val)
                } else if (f.fieldType === 'IMPLANT_LOG') {
                  formattedVal = formatImplantLog(val)
                } else if (f.fieldType === 'FUNCTIONAL_SCORE') {
                  formattedVal = formatFunctionalScore(val)
                } else if (f.fieldType === 'PREOP_CHECKLIST') {
                  formattedVal = formatPreopChecklist(val, f)
                } else {
                  formattedVal = formatSimpleValue(val, f)
                }

                // Don't show empty fields or HEADING fields here
                if (f.fieldType === 'HEADING') return ''
                if (formattedVal === '—' || !formattedVal) return ''

                return `
                  <div class="field-row">
                    <div class="field-label">${f.label}</div>
                    <div class="field-val">${formattedVal}</div>
                  </div>
                `
              }).join('')

              return secHtml + fieldsHtml
            }).join('')}
          </div>
        `
      }

      caseSheetHtml = vitalsHtml + templateFieldsHtml
    }

    let prescriptionHtml = ''
    if (options.prescription && prescriptions.length > 0) {
      prescriptionHtml = `
        <div class="section-title">Prescriptions</div>
        <table class="print-table">
          <thead>
            <tr>
              <th>Drug</th>
              <th>Frequency</th>
              <th>Duration</th>
              <th>Qty</th>
              <th>Instruction</th>
              <th>Route</th>
              <th>Remarks</th>
            </tr>
          </thead>
          <tbody>
            ${prescriptions.flatMap(rx => rx.items).map(item => `
              <tr>
                <td><strong>${item.drugName}</strong></td>
                <td>${item.frequency || '—'}</td>
                <td>${item.duration || '—'}</td>
                <td>${item.qty || '—'}</td>
                <td>${item.instructionLabel || '—'}</td>
                <td>${item.routeLabel || '—'}</td>
                <td>${item.remarks || '—'}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      `
    }

    let diagnosticHtml = ''
    if (options.diagnostic && diagnosticOrders.length > 0) {
      diagnosticHtml = `
        <div class="section-title">Diagnostic Orders</div>
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
            ${diagnosticOrders.flatMap(ord => ord.items.map(item => ({ ...item, orderedAt: ord.orderedAt }))).map(item => `
              <tr>
                <td><strong>${item.testName}</strong></td>
                <td>${item.category || '—'}</td>
                <td>${item.status === 'RESULTED' ? 'Result Entered' : (item.status || '—')}</td>
                <td>${formatDateTime(item.orderedAt)}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      `

      // --- RESULTS SECTION ---
      try {
        const allLines = diagnosticOrders.flatMap(ord => ord.items.map(item => ({ ...item, orderedAt: ord.orderedAt })))
        const resultedLines = allLines.filter(item => item.status === 'RESULTED')
        
        if (resultedLines.length > 0) {
          const [encounterReports, allTemplates] = await Promise.all([
            diagnosticReportApi.getReportsByEncounter(encounterId!),
            diagTemplateApi.getAll()
          ])

          let resultsHtml = `<div class="section-title" style="margin-top: 24px; border-bottom: 2px solid #000;">Diagnostic Results</div>`
          
          for (const line of resultedLines) {
            const lineId = line.realOrderLineId || line.id
            const lineReports = encounterReports.filter(r => r.diagnosticOrderLineId === lineId)
            
            if (lineReports.length === 0) continue
            
            const template = allTemplates.find(t => 
              t.id === line.diagnosticTestId || 
              (t.name && line.testName && t.name.toLowerCase().trim() === line.testName.toLowerCase().trim())
            )
            
            resultsHtml += `<h4 style="font-size: 13px; font-weight: 800; color: #111827; margin-top: 16px; margin-bottom: 8px; text-transform: uppercase;">${line.testName}</h4>`
            
            if (line.category === 'RADIOLOGY') {
              let findings = ''
              let impression = ''
              let conclusion = ''
              try {
                if (lineReports[0]?.templateData) {
                  const parsed = JSON.parse(lineReports[0].templateData)
                  findings = parsed.findings || ''
                  impression = parsed.impression || ''
                  conclusion = parsed.conclusion || ''
                }
              } catch (e) {
                console.error('Failed to parse radiology templateData', e)
              }
              
              if (findings) {
                resultsHtml += `
                  <div style="margin-bottom: 8px;">
                    <strong style="font-size: 11px; color: #4b5563; text-transform: uppercase;">Findings:</strong>
                    <div style="margin-top: 4px; padding-left: 8px; border-left: 2px solid #e5e7eb;">${findings}</div>
                  </div>
                `
              }
              if (impression) {
                resultsHtml += `
                  <div style="margin-bottom: 8px;">
                    <strong style="font-size: 11px; color: #4b5563; text-transform: uppercase;">Impression:</strong>
                    <div style="margin-top: 4px; padding-left: 8px; border-left: 2px solid #e5e7eb;">${impression}</div>
                  </div>
                `
              }
              if (conclusion) {
                resultsHtml += `
                  <div style="margin-bottom: 8px;">
                    <strong style="font-size: 11px; color: #4b5563; text-transform: uppercase;">Conclusion:</strong>
                    <div style="margin-top: 4px; padding-left: 8px; border-left: 2px solid #e5e7eb;">${conclusion}</div>
                  </div>
                `
              }
              if (!findings && !impression && !conclusion) {
                resultsHtml += `<div style="color: #6b7280; font-style: italic;">No detailed report available.</div>`
              }
            } else {
              // LAB
              if (template && template.labTemplateDetails && template.labTemplateDetails.length > 0) {
                const sortedDetails = [...template.labTemplateDetails].sort((a: any, b: any) => a.orderNumber - b.orderNumber)
                resultsHtml += `
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
                `
                for (const ltd of sortedDetails) {
                  const r = lineReports.find(rep => rep.labTemplateDetailId === ltd.id)
                  const val = r?.value || '—'
                  if (ltd.labType === 'HEADER') {
                    resultsHtml += `
                      <tr style="background-color: #f3f4f6; font-weight: bold;">
                        <td colspan="4">${ltd.resultName}</td>
                      </tr>
                    `
                  } else {
                    resultsHtml += `
                      <tr>
                        <td>${ltd.resultName}</td>
                        <td style="text-align: center; font-weight: bold;">${val}</td>
                        <td style="text-align: center;">${ltd.unit || '—'}</td>
                        <td style="white-space: pre-wrap;">${ltd.normalRange || '—'}</td>
                      </tr>
                    `
                  }
                }
                resultsHtml += `</tbody></table>`
              } else {
                resultsHtml += `
                  <div class="field-row">
                    <div class="field-label">Result</div>
                    <div class="field-val"><strong>${lineReports[0]?.value || '—'}</strong></div>
                  </div>
                  <div class="field-row">
                    <div class="field-label">Unit</div>
                    <div class="field-val">${template?.unit || '—'}</div>
                  </div>
                  <div class="field-row">
                    <div class="field-label">Normal Range</div>
                    <div class="field-val" style="white-space: pre-wrap;">${template?.referenceRange || '—'}</div>
                  </div>
                `
              }
            }
          }
          
          diagnosticHtml += resultsHtml
        }
      } catch (err) {
        console.error('Failed to load diagnostic results for print:', err)
      }
    }

    // Helper functions for formatters:
    function formatRomGrid(value: any) {
      if (!Array.isArray(value) || value.length === 0) return '';
      return `
        <table class="print-table" style="margin-top: 4px;">
          <thead>
            <tr>
              <th>Joint</th>
              <th>Flexion</th>
              <th>Extension</th>
              <th>Abduction</th>
              <th>Adduction</th>
              <th>IR</th>
              <th>ER</th>
              <th>Notes</th>
            </tr>
          </thead>
          <tbody>
            ${value.map((row: any) => `
              <tr>
                <td><strong>${row.joint || '—'}</strong></td>
                <td>${row.active_flexion ? row.active_flexion + '°' : '—'}</td>
                <td>${row.active_extension ? row.active_extension + '°' : '—'}</td>
                <td>${row.active_abduction ? row.active_abduction + '°' : '—'}</td>
                <td>${row.active_adduction ? row.active_adduction + '°' : '—'}</td>
                <td>${row.active_ir ? row.active_ir + '°' : '—'}</td>
                <td>${row.active_er ? row.active_er + '°' : '—'}</td>
                <td>${row.notes || '—'}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      `;
    }

    function formatImplantLog(value: any) {
      if (!Array.isArray(value) || value.length === 0) return '';
      return `
        <table class="print-table" style="margin-top: 4px;">
          <thead>
            <tr>
              <th>Component</th>
              <th>Name</th>
              <th>Manufacturer</th>
              <th>Batch/Lot</th>
              <th>Size</th>
              <th>Notes</th>
            </tr>
          </thead>
          <tbody>
            ${value.map((row: any) => `
              <tr>
                <td><strong>${row.component || '—'}</strong></td>
                <td>${row.name || '—'}</td>
                <td>${row.manufacturer || '—'}</td>
                <td>${row.batchLot || '—'}</td>
                <td>${row.size || '—'}</td>
                <td>${row.notes || '—'}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      `;
    }

    function formatFunctionalScore(value: any) {
      if (!Array.isArray(value) || value.length === 0) return '';
      return `
        <table class="print-table" style="margin-top: 4px;">
          <thead>
            <tr>
              <th>Score Type</th>
              <th>Value</th>
              <th>Date</th>
              <th>Notes</th>
            </tr>
          </thead>
          <tbody>
            ${value.map((row: any) => `
              <tr>
                <td><strong>${row.scoreType || '—'}</strong></td>
                <td>${row.value || '—'}</td>
                <td>${row.date || '—'}</td>
                <td>${row.notes || '—'}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      `;
    }

    function formatPreopChecklist(value: any, field: any) {
      if (!value || typeof value !== 'object') return '';
      const checklistOptions = field.validation?.['checklistOptions'] || [];
      const checkedItems = Object.entries(value)
        .filter(([_, checked]) => checked)
        .map(([key]) => {
          const opt = checklistOptions.find((o: any) => o.value === key);
          return opt ? opt.label : key;
        });
      if (checkedItems.length === 0) return '—';
      return `<ul style="margin: 0; padding-left: 20px;">${checkedItems.map(item => `<li>${item}</li>`).join('')}</ul>`;
    }

    function formatSimpleValue(value: any, field: any) {
      if (value === undefined || value === null || value === '') return '—';
      if (field.fieldType === 'CHECKBOX') {
        return value ? 'Yes' : 'No';
      }
      if (field.fieldType === 'MULTI_SELECT') {
        if (Array.isArray(value)) return value.join(', ');
        return String(value);
      }
      return String(value);
    }

    const htmlContent = `
      <!DOCTYPE html>
      <html>
      <head>
        <title>OP Case Sheet - ${encounter.patientName}</title>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
        <style>
          body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            color: #1f2937;
            margin: 0;
            padding: 20px;
            font-size: 11px;
            line-height: 1.5;
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
          .vitals-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 10px;
            margin-bottom: 16px;
          }
          .vital-card {
            background-color: #f9fafb;
            border: 1px solid #e5e7eb;
            border-radius: 4px;
            padding: 6px 10px;
            text-align: center;
          }
          .vital-label {
            font-size: 9px;
            color: #4b5563;
            text-transform: capitalize;
            font-weight: 600;
          }
          .vital-val {
            font-size: 12px;
            font-weight: 700;
            color: #111827;
            margin-top: 1px;
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
          @media print {
            body {
              padding: 0;
            }
          }
        </style>
      </head>
      <body>
        <div class="header-container">
          <div class="logo-container">
            <img src="/api/hospitalProfile/logo?tenantId=${tenantId || ''}&branchId=${selectedBranchId || ''}" alt="Hospital Logo" onerror="this.style.display='none'">
          </div>
          <div class="hospital-details">
            <h1 class="hospital-name">${hospitalName}</h1>
            <p class="hospital-info">${hospitalAddress}</p>
            <p class="hospital-info">${hospitalContact}</p>
          </div>
        </div>

        <div class="patient-card">
          <div class="info-item">
            <span class="info-label">PATIENT NAME:</span>
            <span class="info-val">${encounter.patientName}</span>
          </div>
          <div class="info-item">
            <span class="info-label">PATIENT ID:</span>
            <span class="info-val">${encounter.patientNumber}</span>
          </div>
          <div class="info-item">
            <span class="info-label">CONSULTANT:</span>
            <span class="info-val">${consultantName} (${qualification})</span>
          </div>
          <div class="info-item">
            <span class="info-label">VISIT DATE:</span>
            <span class="info-val">${formatDateTime(encounter.startedAt)}</span>
          </div>
        </div>

        ${caseSheetHtml}
        ${prescriptionHtml}
        ${diagnosticHtml}

        <div style="text-align: center; margin: 30px 0 20px; font-size: 10px; color: #4b5563; font-weight: 700; letter-spacing: 1px;">
          --End of report--
        </div>

        <script>
          window.onload = function() {
            setTimeout(function() {
              window.print();
              window.close();
            }, 300);
          }
        </script>
      </body>
      </html>
    `

    printWindow.document.write(htmlContent)
    printWindow.document.close()
    setShowPrintModal(false)
  }

  if (encLoading) return <div className="p-6 text-sm text-gray-500">Loading…</div>
  if (!encounter) return <div className="p-6 text-sm text-red-600">Encounter not found</div>

  const todayStr = new Date().toISOString().split('T')[0]
  const encDateStr = new Date(encounter.startedAt).toISOString().split('T')[0]
  const isToday = todayStr === encDateStr
  // const canMarkConsulted = encounter.status === 'CASESHEET_RECORDED' && isToday
  const isDifferentConsultant = !!loggedInConsultantId && encounter.primaryProviderId !== loggedInConsultantId
  const isReadOnly = encounter.status === 'BILLING_DONE' || !isToday || isDifferentConsultant

  const activeTemplate = (selectedTemplateId === csData?.template?.id)
    ? csData?.template
    : selectedTemplate

  const initialData = (selectedTemplateId === csData?.template?.id)
    ? csData?.records?.[0]?.data
    : undefined

  const isLoadingTemplate = (selectedTemplateId && selectedTemplateId !== csData?.template?.id)
    ? templateDetailLoading
    : false

  const TABS = [
    { key: 'vitals', label: 'Vitals', icon: Activity },
    { key: 'clinical', label: 'Case Sheet', icon: ClipboardList },
    { key: 'prescription', label: 'Prescription', icon: Pill },
    { key: 'diagnostic', label: 'Diagnostic Order', icon: TestTube },
    { key: 'attachments', label: 'Attachments', icon: Paperclip },
    { key: 'externalRecords', label: 'External Records (ABHA)', icon: FileText },
  ] as const

  // Group encounters by date string, e.g. "02 JUN"
  const sortedEncounters = [...patientEncounters].sort(
    (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime()
  )


  const qualification = selectedConsultant?.qualification || selectedConsultant?.specialisation || 'Consultant'
  const consultantName = selectedConsultant
    ? `${selectedConsultant.salutation ? selectedConsultant.salutation + ' ' : ''}${selectedConsultant.firstName} ${selectedConsultant.lastName}`
    : 'Unknown Consultant'

  return (
    <div className="space-y-4 max-w-7xl">
      {/* Top Patient Header Banner */}
      <div className="flex items-start justify-between flex-wrap gap-3 pb-4 border-b border-gray-200">
        <div>
          {/* Line 1: SCMC-3256 : Mr Nrusinganath Panda P (Male / 76 yrs ) */}
          <h2 className="text-xl font-bold text-gray-900 tracking-tight">
            <span className="text-neutral-600 mr-3">{encounter.patientNumber}</span>{patient?.salutation ? patient.salutation + ' ' : ''}{encounter.patientName}{' '}
            <span className="text-gray-600 font-semibold">
              ({patient?.gender ? (patient.gender === 'MALE' ? 'Male' : patient.gender === 'FEMALE' ? 'Female' : 'Other') : '—'} / {patient?.age || '—'} )
            </span>
          </h2>
          
          {/* Line 2: Visit Type : Outpatient        Primary Consultant : Dr A Srinivasula Reddy MBBS        Visit Date : 04/06/2026 05:09 PM */}
          <div className="flex flex-wrap items-center gap-x-6 gap-y-2 mt-2 text-xs font-semibold text-gray-500">
            <div className="flex items-center gap-1.5">
              <span className="text-gray-400">Visit Type :</span>
              <span className="text-gray-900 font-bold">Outpatient</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-gray-400">Primary Consultant :</span>
              <span className="text-gray-900 font-bold">
                {consultantName} {qualification && <span className="text-gray-500 font-medium text-[10px] bg-gray-100 px-1.5 py-0.5 rounded ml-1">{qualification}</span>}
              </span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="text-gray-400">Visit Date :</span>
              <span className="text-gray-900 font-bold">{formatDateTime(encounter.startedAt)}</span>
            </div>
          </div>
        </div>
        
        <div className="flex items-center gap-2">
          {/* {STATUS_LABELS[encounter.status] && (
            <span className={cn(
              'inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold border',
              STATUS_STYLES[encounter.status]
            )}>
              {STATUS_LABELS[encounter.status]}
            </span>
          )} */}
          <button
            onClick={() => setShowPrintModal(true)}
            className="px-3 py-1.5 bg-white text-gray-700 hover:bg-gray-50 border border-gray-300 text-xs font-bold rounded-lg shadow-sm flex items-center gap-1.5 transition-colors"
          >
            <svg className="w-3.5 h-3.5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 00-2 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
            </svg>
            PRINT
          </button>
          <BackButton to="/op-queue" />
        </div>
      </div>

      {/* Main Two-Column Layout */}
      <div className="flex flex-col lg:flex-row gap-6 items-start">
        {/* Left Column: Case Sheet Content Area */}
        <div className="flex-1 w-full space-y-4">
          {/* Tabs Navigation */}
          <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit flex-wrap" role="tablist">
            {TABS.map(t => {
              const Icon = t.icon
              return (
                <button key={t.key} role="tab" aria-selected={activeTab === t.key}
                  onClick={() => setActiveTab(t.key)}
                  className={cn(
                    'px-3 py-1.5 text-xs font-medium rounded-md transition-colors whitespace-nowrap flex items-center gap-1.5',
                    activeTab === t.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
                  )}>
                  <Icon size={14} className="shrink-0 text-neutral-500" />
                  {t.label}
                </button>
              )
            })}
          </div>

          {/* Read-only banner */}
          {isReadOnly && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-2.5 text-xs text-amber-750 shadow-sm flex items-center gap-2">
              <AlertTriangle size={14} className="text-amber-600 shrink-0" />
              <span>
                {isDifferentConsultant
                  ? `This encounter belongs to ${consultantName}. You can only view this case sheet.`
                  : encounter.status === 'BILLING_DONE'
                    ? 'This encounter is closed (Consulted). All tabs are read-only.'
                    : 'This is a past day encounter. All tabs are read-only.'}
              </span>
            </div>
          )}

          {/* Curved Consultant Header Tab & Main Content Box */}
          <div className="flex flex-col shadow-sm rounded-xl border border-gray-200">
            {/* Gray Curved Header Tab */}
            <div className="bg-gray-50 px-5 py-3 border-b border-gray-200 rounded-t-xl flex items-center justify-between flex-wrap gap-2">
              <div>
                <p className="text-sm font-bold text-gray-800">
                  {consultantName}
                </p>
                <p className="text-xs text-gray-500">
                  {qualification} · {formatDateTime(encounter.startedAt)}
                </p>
              </div>
              <div className="flex items-center gap-2 flex-wrap">
                {activeTab === 'clinical' && (csData?.template || selectedTemplateId) && (
                  <button
                    onClick={() => handlePrint({ caseSheet: true, caseSheetTemplate: true, prescription: false, diagnostic: false })}
                    className="px-3 py-1.5 bg-white hover:bg-gray-50 border border-gray-300 text-xs font-bold text-gray-700 rounded-lg shadow-sm flex items-center gap-1.5 transition-colors"
                  >
                    <svg className="w-3.5 h-3.5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                    </svg>
                    PRINT
                  </button>
                )}
                {activeTab === 'prescription' && prescriptions.length > 0 && (
                  <button
                    onClick={() => handlePrint({ caseSheet: false, caseSheetTemplate: false, prescription: true, diagnostic: false })}
                    className="px-3 py-1.5 bg-white hover:bg-gray-50 border border-gray-300 text-xs font-bold text-gray-700 rounded-lg shadow-sm flex items-center gap-1.5 transition-colors"
                  >
                    <svg className="w-3.5 h-3.5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                    </svg>
                    PRINT
                  </button>
                )}
                {activeTab === 'diagnostic' && diagnosticOrders.length > 0 && (
                  <button
                    onClick={() => handlePrint({ caseSheet: false, caseSheetTemplate: false, prescription: false, diagnostic: true })}
                    className="px-3 py-1.5 bg-white hover:bg-gray-50 border border-gray-300 text-xs font-bold text-gray-700 rounded-lg shadow-sm flex items-center gap-1.5 transition-colors"
                  >
                    <svg className="w-3.5 h-3.5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                    </svg>
                    PRINT
                  </button>
                )}
                {isReadOnly && (
                  <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-extrabold bg-gray-200 text-gray-700 border border-gray-300">
                     {isDifferentConsultant ? 'VIEW ONLY' : 'READ-ONLY PAST ENCOUNTER'}
                  </span>
                )}
              </div>
            </div>

            {/* Tab content inside */}
            <div className="bg-white p-5">
              {activeTab === 'clinical' && (
                csLoading ? (
                  <div className="text-sm text-gray-500 py-8 text-center">Loading case sheet…</div>
                ) : (
                  <div className="space-y-6">
                    {/* Template select dropdown */}
                    <div className="flex items-center gap-4 border-b border-gray-100 pb-4 mb-4">
                      <label className="text-sm font-semibold text-gray-700 w-32 shrink-0">Case Sheet Form</label>
                      <select
                        value={selectedTemplateId}
                        onChange={e => setSelectedTemplateId(e.target.value)}
                        disabled={isReadOnly || (csData?.records && csData.records.length > 0)}
                        className="px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500 max-w-md w-full disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed"
                      >
                        <option value="">Select Template</option>
                        {templates.map(t => (
                          <option key={t.id} value={t.id}>
                            {t.name}
                          </option>
                        ))}
                        {csData?.template && !templates.some(t => t.id === csData.template?.id) && (
                          <option key={csData.template.id} value={csData.template.id}>
                            {csData.template.name}
                          </option>
                        )}
                      </select>
                    </div>

                    {selectedTemplateId ? (
                      isLoadingTemplate ? (
                        <div className="text-sm text-gray-500 py-8 text-center">Loading template details…</div>
                      ) : activeTemplate ? (
                        <DynamicCaseSheetForm
                          key={selectedTemplateId}
                          template={activeTemplate}
                          initialData={initialData}
                          onSave={data => saveMut.mutate(data)}
                          isSaving={saveMut.isPending}
                          readOnly={isReadOnly}
                          saveButtonText={initialData ? 'Update Case Sheet' : 'Save Case Sheet'}
                        />
                      ) : (
                        <div className="text-sm text-red-500 text-center py-8">Failed to load template.</div>
                      )
                    ) : (
                      <div className="border border-dashed border-red-200 bg-red-50/30 rounded-xl p-8 text-center text-sm text-red-700">
                        <span className="font-extrabold block text-base mb-1 text-red-800">No Medical History!</span>
                        There is no medical history for this visit. Please select a template above to create a case sheet.
                      </div>
                    )}
                  </div>
                )
              )}

              {activeTab === 'prescription' && (
                <PrescriptionTab
                  encounterId={encounterId!}
                  mode="OP"
                  consultantId={encounter.primaryProviderId}
                  readOnly={isReadOnly}
                />
              )}

              {activeTab === 'diagnostic' && (
                <DiagnosticOrderTab
                  encounterId={encounterId!}
                  mode="OP"
                  consultantId={encounter.primaryProviderId}
                  readOnly={isReadOnly}
                />
              )}

              {activeTab === 'attachments' && (
                <AttachmentsTab encounterId={encounterId!} readOnly={isReadOnly} />
              )}

              {activeTab === 'vitals' && (
                <VitalsDisplay vitalData={encounter.vitalData} />
              )}

              {activeTab === 'externalRecords' && encounter.patientId && (
                <Suspense fallback={<p className="text-sm text-gray-500">Loading external records…</p>}>
                  <ExternalRecordsViewer
                    patientId={encounter.patientId}
                    encounterId={encounterId}
                    caseSheetId={csData?.id}
                  />
                </Suspense>
              )}
            </div>
          </div>
        </div>

        {/* Right Column: Visit History Sidebar (Timeline) */}
        <div className={`w-full shrink-0 flex flex-col gap-3 transition-all duration-300 ${sidebarCollapsed ? "lg:w-24" : "lg:w-64"}`}>
          <div className={`bg-white border border-gray-200 rounded-2xl shadow-xs flex flex-col gap-3 max-h-[600px] overflow-y-auto custom-scrollbar ${sidebarCollapsed ? "p-2" : "p-3.5"}`}>
            
            {/* Sidebar Header Toggle */}
            {sidebarCollapsed ? (
              <div className="flex justify-center pb-2 border-b border-gray-150">
                <button
                  type="button"
                  onClick={() => setSidebarCollapsed(false)}
                  className="w-8 h-8 rounded-xl bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-600 hover:text-slate-900 transition-all shadow-xs flex items-center justify-center group"
                  title="Expand Visit History"
                >
                  <svg className="w-4 h-4 text-slate-500 transition-transform group-hover:scale-110" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24">
                    <polyline points="15 18 9 12 15 6" />
                  </svg>
                </button>
              </div>
            ) : (
              <div className="flex items-center justify-between border-b border-gray-150 pb-2 mb-0.5">
                <h3 className="text-[11px] font-bold text-gray-700 flex items-center gap-1.5 uppercase tracking-wider">
                  <span className="w-1.5 h-3 bg-neutral-600 rounded-full animate-pulse"></span>
                  Visit History
                </h3>
                <button
                  type="button"
                  onClick={() => setSidebarCollapsed(true)}
                  className="w-7 h-7 rounded-lg bg-gray-50 hover:bg-neutral-100 border border-gray-200 text-neutral-500 hover:text-neutral-900 transition-all flex items-center justify-center group"
                  title="Collapse Sidebar"
                >
                  <svg className="w-3.5 h-3.5 text-slate-500 transition-transform group-hover:scale-110" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" viewBox="0 0 24 24">
                    <polyline points="9 18 15 12 9 6" />
                  </svg>
                </button>
              </div>
            )}
            
            {sortedEncounters.length === 0 ? (
              <div className="text-xs text-gray-400 text-center py-6">No Encounters</div>
            ) : (
              <div className={`relative py-1 ${sidebarCollapsed ? "pl-0 border-l-0 ml-0 space-y-3" : "pl-4 border-l border-gray-200 ml-2.5 space-y-3.5"}`}>
                {sortedEncounters.map((enc) => {
                  const isActive = enc.id === encounterId
                  const encDate = new Date(enc.startedAt)
                  
                  // Date formatting
                  const dayStr = encDate.getDate().toString().padStart(2, '0')
                  const monthStr = encDate.toLocaleString('default', { month: 'short' })
                  const yearStr = encDate.getFullYear()
                  const timeStr = encDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                  
                  // Doctor / Dept resolution
                  const doc = consultants.find(c => c.id === enc.primaryProviderId)
                  const docName = doc ? `${doc.salutation ? doc.salutation + ' ' : ''}${doc.firstName} ${doc.lastName}` : (enc.providerName || 'Unknown Provider')
                  const deptName = doc?.specialisation || doc?.qualification || ''

                  // Full display tooltip
                  const tooltipText = `${docName}${deptName ? ' (' + deptName + ')' : ''} — ${dayStr} ${monthStr} ${yearStr} at ${timeStr}`

                  // Clean doctor name for compact collapsed view (e.g. Dr S.Sarada. -> Sarada)
                  const rawName = doc ? `${doc.firstName} ${doc.lastName}` : docName
                  const cleanName = rawName.replace(/Dr\s*/i, '').replace(/[.]/g, '').trim()
                  const nameParts = cleanName.split(/\s+/).filter(Boolean)
                  const compactDocName = nameParts.length > 1
                    ? nameParts.find(p => p.length > 2) || nameParts[nameParts.length - 1]
                    : nameParts[0] || 'Staff'

                  if (sidebarCollapsed) {
                    return (
                      <Link
                        key={enc.id}
                        to={`/op-casesheet/${enc.id}`}
                        className="block group"
                        title={tooltipText}
                      >
                        {/* Premium Calendar Card badge */}
                        <div className={`overflow-hidden rounded-xl border text-center flex flex-col transition-all duration-200 ${
                          isActive
                            ? "border-neutral-500 ring-2 ring-neutral-100 shadow-md transform scale-102 border-l-4 border-l-neutral-600"
                            : "border-slate-200 hover:border-slate-350 hover:shadow-xs border-l-4 border-l-slate-300 hover:border-l-neutral-400"
                        }`}>
                          <div className={`text-[8px] font-extrabold py-0.5 tracking-wider uppercase transition-colors ${
                            isActive ? "bg-neutral-600 text-white" : "bg-slate-100 text-slate-500 group-hover:bg-neutral-200 group-hover:text-white"
                          }`}>
                            {monthStr}
                          </div>
                          <div className="bg-white py-1.5 transition-colors flex flex-col items-center justify-center min-h-[46px]">
                            <span className={`text-[13px] font-extrabold font-mono leading-none ${isActive ? "text-slate-900 font-black" : "text-slate-650 group-hover:text-slate-900"}`}>
                              {dayStr}
                            </span>
                            <span className={`text-[9px] font-bold truncate max-w-full px-0.5 leading-tight mt-1 ${
                              isActive ? "text-slate-700 font-extrabold" : "text-slate-450 group-hover:text-slate-600"
                            }`}>
                              {compactDocName}
                            </span>
                          </div>
                        </div>
                      </Link>
                    )
                  }

                  return (
                    <Link
                      key={enc.id}
                      to={`/op-casesheet/${enc.id}`}
                      className="block group relative"
                    >
                      {/* Timeline Dot */}
                      <span className={`absolute -left-[21px] top-2.5 w-2.5 h-2.5 rounded-full border transition-all duration-200 ${
                        isActive
                          ? "bg-neutral-600 border-white ring-2 ring-neutral-100"
                          : "bg-white border-slate-300 group-hover:border-neutral-500"
                      }`} />
                      
                      {/* Card Content */}
                      <div 
                        className={`rounded-xl border transition-all duration-200 text-left p-3 ${
                          isActive
                            ? "bg-neutral-50 border-neutral-300 shadow-sm"
                            : "bg-white border-slate-150 hover:border-neutral-300 hover:shadow-xs"
                        }`}
                        title={tooltipText}
                      >
                        {/* Date and Time Header */}
                        <div className="flex items-center justify-between gap-1 mb-1.5">
                          <span className={`text-[11px] font-bold font-mono tracking-tight ${isActive ? "text-neutral-900" : "text-slate-600 group-hover:text-neutral-900"}`}>
                            {dayStr} {monthStr} {yearStr}
                          </span>
                          <span className="text-[9px] text-gray-400 font-medium">
                            {timeStr}
                          </span>
                        </div>
                        
                        {/* Doctor Name */}
                        <p className={`text-[11px] font-bold leading-normal truncate ${isActive ? "text-slate-900 font-extrabold" : "text-slate-700 group-hover:text-slate-900"}`}>
                          {docName}
                        </p>
                        
                        {/* Specialisation / Qualification */}
                        {deptName && (
                          <p className={`text-[9px] font-medium mt-0.5 truncate ${isActive ? "text-neutral-600" : "text-slate-400"}`}>
                            {deptName}
                          </p>
                        )}
                      </div>
                    </Link>
                  )
                })}
              </div>
            )}
          </div>
        </div>
      </div>

      {showPrintModal && (
        <div 
          className="fixed inset-0 z-50 flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden animate-in zoom-in-95 duration-200">
            <div className="flex items-between justify-between px-6 py-4 border-b border-gray-100">
              <h3 className="text-base font-bold text-gray-900">Print Options</h3>
              <button
                onClick={() => setShowPrintModal(false)}
                className="text-gray-400 hover:text-gray-600 transition-colors text-lg"
              >
                ✕
              </button>
            </div>
            
            <div className="p-6 space-y-6">
              <div className="space-y-4">
                {/* Case Sheet Option */}
                <div className="flex items-center gap-6">
                  <label className="flex items-center gap-2 cursor-pointer select-none text-sm font-semibold text-gray-700 w-32 shrink-0">
                    <input
                      type="checkbox"
                      checked={printOptions.caseSheet}
                      onChange={e => {
                        const val = e.target.checked
                        setPrintOptions(prev => ({
                          ...prev,
                          caseSheet: val,
                          caseSheetTemplate: val ? prev.caseSheetTemplate : false
                        }))
                      }}
                      className="w-4 h-4 text-neutral-600 border-gray-300 rounded focus:ring-neutral-500 accent-neutral-600"
                    />
                    Case Sheet
                  </label>
                  
                  {(csData?.template?.name || selectedTemplate?.name) && (
                    <label className={cn(
                      "flex items-center gap-2 cursor-pointer select-none text-sm font-medium text-gray-600 transition-opacity",
                      !printOptions.caseSheet && "opacity-50 pointer-events-none"
                    )}>
                      <input
                        type="checkbox"
                        disabled={!printOptions.caseSheet}
                        checked={printOptions.caseSheetTemplate}
                        onChange={e => setPrintOptions(prev => ({ ...prev, caseSheetTemplate: e.target.checked }))}
                        className="w-4 h-4 text-neutral-600 border-gray-300 rounded focus:ring-neutral-500 accent-neutral-600"
                      />
                      {csData?.template?.name || selectedTemplate?.name}
                    </label>
                  )}
                </div>

                {/* Prescription Option */}
                <div className="flex items-center">
                  <label className="flex items-center gap-2 cursor-pointer select-none text-sm font-semibold text-gray-700">
                    <input
                      type="checkbox"
                      checked={printOptions.prescription}
                      onChange={e => setPrintOptions(prev => ({ ...prev, prescription: e.target.checked }))}
                      className="w-4 h-4 text-neutral-600 border-gray-300 rounded focus:ring-neutral-500 accent-neutral-600"
                    />
                    Prescription
                  </label>
                </div>

                {/* Diagnostic Option */}
                <div className="flex items-center">
                  <label className="flex items-center gap-2 cursor-pointer select-none text-sm font-semibold text-gray-700">
                    <input
                      type="checkbox"
                      checked={printOptions.diagnostic}
                      onChange={e => setPrintOptions(prev => ({ ...prev, diagnostic: e.target.checked }))}
                      className="w-4 h-4 text-neutral-600 border-gray-300 rounded focus:ring-neutral-500 accent-neutral-600"
                    />
                    Diagnostic
                  </label>
                </div>
              </div>

              {/* Centered PRINT button */}
              <div className="flex justify-center pt-2">
                <button
                  onClick={() => handlePrint()}
                  className="px-6 py-2 bg-neutral-600 hover:bg-neutral-700 text-white font-bold text-sm rounded-lg shadow-md hover:shadow-lg transition-all flex items-center gap-2"
                >
                  <svg className="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                  </svg>
                  PRINT
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Vitals Display ────────────────────────────────────────────────────────────
function VitalsDisplay({ vitalData }: { vitalData: Record<string, unknown> | null }) {
  const excluded = new Set(['casesheet', 'dischargeNotes', 'vitals_history',
    'prescriptions', 'diagnostic_orders', 'progress_notes', 'nurse_notes', 'other_charges'])
  const entries = Object.entries(vitalData ?? {})
    .filter(([k]) => !excluded.has(k))

  if (entries.length === 0) {
    return <p className="text-sm text-gray-400">No vitals recorded for this visit.</p>
  }
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
      {entries.map(([key, value]) => (
        <div key={key} className="bg-neutral-50 rounded-lg px-3 py-2">
          <p className="text-xs text-neutral-500 capitalize">{key.replace(/([A-Z])/g, ' $1').trim()}</p>
          <p className="text-sm font-bold text-neutral-900">{String(value)}</p>
        </div>
      ))}
    </div>
  )
}

// ─── Attachments Tab ───────────────────────────────────────────────────────────
function AttachmentsTab({ encounterId, readOnly }: { encounterId: string; readOnly?: boolean }) {
  const qc = useQueryClient()
  const { data: attachments = [] } = useQuery({
    queryKey: ['attachments', encounterId],
    queryFn: () => attachmentApi.getByEncounter(encounterId),
  })

  const [uploading, setUploading] = useState(false)
  const [previewAttachment, setPreviewAttachment] = useState<Attachment | null>(null)

  const handleUpload = async (file: File) => {
    setUploading(true)
    try {
      await attachmentApi.upload(file, 'VISIT', encounterId)
      qc.invalidateQueries({ queryKey: ['attachments', encounterId] })
      toast({ title: 'File uploaded', variant: 'success' })
    } catch {
      toast({ title: 'Upload failed', variant: 'destructive' })
    } finally { setUploading(false) }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-gray-800">Attachments</h3>
        {!readOnly && (
          <label className={cn(
            'px-3 py-1.5 text-xs font-semibold bg-neutral-600 text-white rounded-lg cursor-pointer hover:bg-neutral-700 transition-colors',
            uploading && 'opacity-50 pointer-events-none'
          )}>
            {uploading ? 'Uploading…' : '+ Upload File'}
            <input type="file" className="hidden" onChange={e => e.target.files?.[0] && handleUpload(e.target.files[0])} />
          </label>
        )}
      </div>

      {attachments.length === 0 ? (
        <p className="text-sm text-gray-400">No attachments yet.</p>
      ) : (
        <ul className="space-y-2">
          {attachments.map(a => (
            <li key={a.id} className="flex items-center justify-between px-3.5 py-2.5 border border-gray-200 rounded-xl text-xs hover:bg-slate-50/50 transition-colors shadow-sm bg-white">
              <span className="font-medium text-gray-700 truncate flex items-center gap-2">
                <Paperclip className="w-3.5 h-3.5 text-gray-400 shrink-0" />
                {a.fileName}
              </span>
              <div className="flex items-center gap-3 shrink-0 ml-2">
                <button
                  onClick={() => setPreviewAttachment(a)}
                  className="inline-flex items-center gap-1 text-neutral-600 hover:text-neutral-700 font-semibold transition-colors"
                >
                  <Eye className="w-3.5 h-3.5" />
                  View
                </button>
                <span className="text-gray-300">|</span>
                <a href={attachmentApi.getDownloadUrl(a.id)} download={a.fileName}
                  className="inline-flex items-center gap-1 text-neutral-600 hover:text-neutral-700 font-semibold transition-colors">
                  <Download className="w-3.5 h-3.5" />
                  Download
                </a>
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* Attachment View Modal Overlay */}
      {previewAttachment && (
        <div
          className="fixed inset-0 z-[150] flex items-center justify-center p-2 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200"
          style={{ marginTop: 0 }}
        >
          <div className="bg-white rounded-[2rem] shadow-2xl border border-gray-150 p-6 max-w-4xl w-full relative overflow-hidden transform animate-in zoom-in-95 duration-300 flex flex-col max-h-[90vh]">
            <div className="flex items-center justify-between border-b border-gray-200 pb-3 mb-4">
              <span className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-1.5">
                View Attachment: {previewAttachment.fileName}
              </span>
              <button
                onClick={() => setPreviewAttachment(null)}
                className="text-slate-400 hover:text-slate-600 text-lg font-bold p-1 rounded-lg hover:bg-slate-100 transition-all"
              >
                ✕
              </button>
            </div>

            <div className="flex-1 overflow-auto flex items-center justify-center bg-slate-50 rounded-xl min-h-[40vh]">
              {(() => {
                const ext = previewAttachment.fileName.split('.').pop()?.toLowerCase() || ''
                const isImage = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext) || previewAttachment.contentType?.startsWith('image/')
                const isPdf = ext === 'pdf' || previewAttachment.contentType === 'application/pdf'
                const fileUrl = attachmentApi.getDownloadUrl(previewAttachment.id)

                if (isImage) {
                  return (
                    <img src={fileUrl} className="max-w-full max-h-[60vh] object-contain rounded-lg shadow-sm" alt="Attachment Preview" />
                  )
                }

                if (isPdf) {
                  return (
                    <iframe src={fileUrl} className="w-full h-[60vh] rounded-xl border border-gray-200 shadow-inner" title="PDF Preview" />
                  )
                }

                return (
                  <div className="flex flex-col items-center justify-center p-8 text-center">
                    <p className="text-sm font-semibold text-slate-700 mb-1">Preview not available for this file type</p>
                    <p className="text-xs text-slate-400 mb-4">{previewAttachment.fileName}</p>
                    <a
                      href={fileUrl}
                      download
                      className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold text-white bg-neutral-600 hover:bg-neutral-700 rounded-xl shadow-sm transition-all"
                    >
                      Download to View
                    </a>
                  </div>
                )
              })()}
            </div>

            <div className="border-t border-gray-150 pt-4 flex justify-end gap-3 mt-4">
              <button
                onClick={() => setPreviewAttachment(null)}
                className="px-5 py-2 text-sm font-bold text-slate-600 bg-slate-50 hover:bg-slate-100 rounded-xl transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
