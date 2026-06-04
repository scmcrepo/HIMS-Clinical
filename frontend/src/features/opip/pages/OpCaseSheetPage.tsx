/**
 * OpCaseSheetPage.tsx — full OP case sheet with:
 *  - Left column: Patient visit history timeline
 *  - Right column: Selected visit case sheet details with curved consultant header tab
 *  - 4 tabs: Clinical Notes, Prescription, Diagnostic Order, Attachments, Vitals
 */
import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { encounterApi } from '../../../services/encounter/encounterApi'
import { opQueueApi, recordApi, templateApi } from '../../../services/casesheet/casesheetApi'
import { templateApi as masterTemplateApi, deptCreateApi } from '../../../services/masters/masterApi'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { DynamicCaseSheetForm } from '../components/DynamicCaseSheetForm'
import { PrescriptionTab } from '../components/PrescriptionTab'
import { DiagnosticOrderTab } from '../components/DiagnosticOrderTab'
import { attachmentApi } from '../../../services/attachment/attachmentApi'
import { opPrescriptionApi, opDiagnosticApi } from '../../../services/opip/opipApi'
import { configApi } from '../../../services/config/configApi'
import { formatDateTime } from '../../../lib/dateUtils'
import { cn } from '../../../lib/utils'
import BackButton from '../../../components/shared/BackButton'
import { toast } from '../../../hooks/useToast'
import { usePatient } from '../../../hooks/patient/usePatient'
import type { CaseSheetData } from '../../../types/casesheet'

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

type Tab = 'vitals' | 'clinical' | 'prescription' | 'diagnostic' | 'attachments'

export default function OpCaseSheetPage() {
  const { encounterId } = useParams<{ encounterId: string }>()
  const [activeTab, setActiveTab] = useState<Tab>('vitals')
  const qc = useQueryClient()

  // 1. Fetch current encounter
  const { data: encounter, isLoading: encLoading } = useQuery({
    queryKey: ['encounter', encounterId],
    queryFn: () => encounterApi.getById(encounterId!),
    enabled: !!encounterId,
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

  // 4b. Fetch departments to look up names for matching specialization fallback
  const { data: departments = [] } = useQuery({
    queryKey: ['departments'],
    queryFn:  () => deptCreateApi.getAll(),
  })

  const selectedConsultant = consultants.find(c => c.id === encounter?.primaryProviderId)

  // 5. Fetch templates list for the select dropdown
  const { data: templates = [] } = useQuery({
    queryKey: ['case-sheet-templates', 'OP', selectedConsultant?.id, selectedConsultant?.departmentId, departments.length],
    queryFn:  async () => {
      // 1. Try to get templates mapped via the department-template mapping table
      let deptTemplates: any[] = []
      if (selectedConsultant?.departmentId) {
        try {
          deptTemplates = await masterTemplateApi.getDepartmentTemplates(selectedConsultant.departmentId)
        } catch (e) {
          console.error("Failed to fetch department templates", e)
        }
      }

      if (deptTemplates && deptTemplates.length > 0) {
        return deptTemplates.filter((t: any) => t.visitType === 'OP')
      }

      // 2. Fallback to specialization-based matching
      // First, resolve the specialization name
      let resolvedSpec = 'GENERAL'
      if (selectedConsultant) {
        if (selectedConsultant.specialisation && selectedConsultant.specialisation.trim()) {
          resolvedSpec = selectedConsultant.specialisation.toUpperCase()
        } else if (selectedConsultant.departmentId) {
          const dept = departments.find((d: any) => d.id === selectedConsultant.departmentId)
          if (dept) {
            resolvedSpec = dept.name.toUpperCase()
          }
        }
      }

      // Fetch all templates and filter by resolvedSpec
      const allTemplates = await templateApi.list(undefined, 'OP')
      return allTemplates.filter((t: any) => t.specialization?.toUpperCase() === resolvedSpec)
    },
    enabled: !!encounter && consultants.length > 0,
  })

  // 6. Handle selection of template when creating a new case sheet
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('')

  const { data: selectedTemplate, isLoading: templateDetailLoading } = useQuery({
    queryKey: ['case-sheet-template-detail', selectedTemplateId],
    queryFn: () => templateApi.getById(selectedTemplateId),
    enabled: !!selectedTemplateId,
  })

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
      const tid = csData?.template?.id || selectedTemplateId
      if (tid) {
        payload.templateId = tid
      }
      return recordApi.save(encounterId!, payload)
    },
    onSuccess: () => { invalidate(); toast({ title: 'Case sheet saved', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  /* const markConsultedMut = useMutation({
    mutationFn: () => opQueueApi.markConsulted(encounterId!),
    onSuccess: () => { invalidate(); toast({ title: 'Encounter marked as consulted', variant: 'success' }) },
    onError: (e: Error) => toast({ title: 'Error', description: e.message, variant: 'destructive' }),
  }) */

  const handlePrint = (customOptions?: { caseSheet: boolean; caseSheetTemplate: boolean; prescription: boolean; diagnostic: boolean }) => {
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
                secHtml += `<h4 style="font-size: 11px; font-weight: 700; color: #1e3a8a; border-bottom: 1px solid #e5e7eb; padding-bottom: 3px; margin-top: 14px; margin-bottom: 8px; text-transform: uppercase;">${sec.title}</h4>`
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
              <th>Ordered At</th>
            </tr>
          </thead>
          <tbody>
            ${diagnosticOrders.flatMap(ord => ord.items.map(item => ({ ...item, orderedAt: ord.orderedAt }))).map(item => `
              <tr>
                <td><strong>${item.testName}</strong></td>
                <td>${item.category || '—'}</td>
                <td>${item.status || '—'}</td>
                <td>${formatDateTime(item.orderedAt)}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      `
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
            color: #1e3a8a;
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
            color: #1e3a8a;
            border-bottom: 1.5px solid #dbeafe;
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
            background-color: #eff6ff;
            border: 1px solid #dbeafe;
            border-radius: 4px;
            padding: 6px 10px;
            text-align: center;
          }
          .vital-label {
            font-size: 9px;
            color: #2563eb;
            text-transform: capitalize;
            font-weight: 600;
          }
          .vital-val {
            font-size: 12px;
            font-weight: 700;
            color: #1e3a8a;
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
            <img src="/api/hospitalProfile/logo" alt="Hospital Logo" onerror="this.style.display='none'">
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
  const isReadOnly = encounter.status === 'BILLING_DONE' || !isToday

  const TABS: { key: Tab; label: string }[] = [
    { key: 'vitals', label: '🩺 Vitals' },
    { key: 'clinical', label: '📋 Case Sheet' },
    { key: 'prescription', label: '💊 Prescription' },
    { key: 'diagnostic', label: '🧪 Diagnostic Order' },
    { key: 'attachments', label: '📎 Attachments' },
  ]

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
            {encounter.patientNumber} : {patient?.salutation ? patient.salutation + ' ' : ''}{encounter.patientName}{' '}
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
            <div className="w-1.5 h-1.5 rounded-full bg-gray-300" />
            <div className="flex items-center gap-1.5">
              <span className="text-gray-400">Primary Consultant :</span>
              <span className="text-gray-900 font-bold">
                {consultantName} {qualification && <span className="text-gray-500 font-medium text-[10px] bg-gray-100 px-1.5 py-0.5 rounded ml-1">{qualification}</span>}
              </span>
            </div>
            <div className="w-1.5 h-1.5 rounded-full bg-gray-300" />
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
        {/* Left Column: Visit History Sidebar (Vertical Tabs) */}
        <div className="w-full lg:w-16 shrink-0 bg-white border border-gray-200 rounded-xl overflow-hidden self-stretch flex flex-col divide-y divide-gray-100 max-h-[600px] overflow-y-auto shadow-sm">
          {sortedEncounters.length === 0 ? (
            <div className="text-[10px] text-gray-400 text-center py-4 px-1">No Encounters</div>
          ) : (
            sortedEncounters.map((enc, idx) => {
              const isActive = enc.id === encounterId
              const encDate = new Date(enc.startedAt)
              const dayStr = encDate.getDate().toString().padStart(2, '0')
              const monthStr = encDate.toLocaleString('default', { month: 'short' }).toUpperCase()
              const timeStr = encDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
              const doc = consultants.find(c => c.id === enc.primaryProviderId)
              const docName = doc ? `${doc.salutation ? doc.salutation + ' ' : ''}${doc.firstName} ${doc.lastName}` : enc.providerName
              const deptName = doc?.specialisation || doc?.qualification || ''

              const isFirst = idx === 0
              const isLast = idx === sortedEncounters.length - 1

              return (
                <Link
                  key={enc.id}
                  to={`/op-casesheet/${enc.id}`}
                  className={cn(
                    "flex flex-col items-center justify-center w-full py-4 px-2 text-center transition-all cursor-pointer relative",
                    isFirst && "rounded-t-xl",
                    isLast && "rounded-b-xl",
                    isActive
                      ? "bg-blue-600 text-white font-bold"
                      : "bg-white text-gray-600 hover:bg-gray-50 hover:text-gray-900"
                  )}
                  title={`${docName} (${deptName ? deptName + ' · ' : ''}${timeStr})`}
                >
                  <span className="text-2xl font-extrabold leading-none">{dayStr}</span>
                  <span className="text-xs uppercase font-extrabold tracking-wider mt-1">{monthStr}</span>
                </Link>
              )
            })
          )}
        </div>

        {/* Right Column: Case Sheet Content Area */}
        <div className="flex-1 w-full space-y-4">
          {/* Tabs Navigation */}
          <div className="flex gap-1 bg-gray-100 p-1 rounded-lg w-fit flex-wrap" role="tablist">
            {TABS.map(t => (
              <button key={t.key} role="tab" aria-selected={activeTab === t.key}
                onClick={() => setActiveTab(t.key)}
                className={cn(
                  'px-3 py-1.5 text-xs font-medium rounded-md transition-colors whitespace-nowrap',
                  activeTab === t.key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
                )}>
                {t.label}
              </button>
            ))}
          </div>

          {/* Read-only banner */}
          {isReadOnly && (
            <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-2.5 text-xs text-amber-700 shadow-sm flex items-center gap-1.5">
              <span>⚠️</span>
              <span>
                {encounter.status === 'BILLING_DONE'
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
                    🔒 READ-ONLY PAST ENCOUNTER
                  </span>
                )}
              </div>
            </div>

            {/* Tab content inside */}
            <div className="bg-white p-5">
              {activeTab === 'clinical' && (
                csLoading ? (
                  <div className="text-sm text-gray-500 py-8 text-center">Loading case sheet…</div>
                ) : csData?.template ? (
                  <div className="space-y-4">
                    {/* Active template select dropdown (disabled) */}
                    <div className="flex items-center gap-4 border-b border-gray-100 pb-4 mb-4">
                      <label className="text-sm font-semibold text-gray-700 w-32 shrink-0">Case Sheet Form</label>
                      <select
                        disabled
                        value={csData.template.id}
                        className="px-3 py-2 border border-gray-300 rounded-lg text-sm bg-gray-50 text-gray-500 cursor-not-allowed max-w-md w-full"
                      >
                        <option value={csData.template.id}>{csData.template.name}</option>
                      </select>
                    </div>

                     <DynamicCaseSheetForm
                      template={csData.template}
                      initialData={csData.records[0]?.data}
                      onSave={data => saveMut.mutate(data)}
                      isSaving={saveMut.isPending}
                      readOnly={isReadOnly}
                      saveButtonText={csData.records?.[0] ? 'Update Case Sheet' : 'Save Case Sheet'}
                    />
                  </div>
                ) : (
                  <div className="space-y-6">
                    {/* Template select dropdown */}
                    <div className="flex items-center gap-4 border-b border-gray-100 pb-4 mb-4">
                      <label className="text-sm font-semibold text-gray-700 w-32 shrink-0">Case Sheet Form</label>
                      <select
                        value={selectedTemplateId}
                        onChange={e => setSelectedTemplateId(e.target.value)}
                        disabled={isReadOnly}
                        className="px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 max-w-md w-full"
                      >
                        <option value="">Select Template</option>
                        {templates.map(t => (
                          <option key={t.id} value={t.id}>
                            {t.name}
                          </option>
                        ))}
                      </select>
                    </div>

                    {selectedTemplateId ? (
                      templateDetailLoading ? (
                        <div className="text-sm text-gray-500 py-8 text-center">Loading template details…</div>
                      ) : selectedTemplate ? (
                        <DynamicCaseSheetForm
                          template={selectedTemplate}
                          onSave={data => saveMut.mutate(data)}
                          isSaving={saveMut.isPending}
                          readOnly={isReadOnly}
                          saveButtonText={csData?.records?.[0] ? 'Update Case Sheet' : 'Save Case Sheet'}
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
            </div>
          </div>
        </div>
      </div>

      {showPrintModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-gray-900/40 backdrop-blur-sm animate-in fade-in duration-200">
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
                      className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
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
                        className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
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
                      className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
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
                      className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                    />
                    Diagnostic
                  </label>
                </div>
              </div>

              {/* Centered PRINT button */}
              <div className="flex justify-center pt-2">
                <button
                  onClick={() => handlePrint()}
                  className="px-6 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold text-sm rounded-lg shadow-md hover:shadow-lg transition-all flex items-center gap-2"
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
        <div key={key} className="bg-blue-50 rounded-lg px-3 py-2">
          <p className="text-xs text-blue-500 capitalize">{key.replace(/([A-Z])/g, ' $1').trim()}</p>
          <p className="text-sm font-bold text-blue-900">{String(value)}</p>
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
            'px-3 py-1.5 text-xs font-semibold bg-blue-600 text-white rounded-lg cursor-pointer hover:bg-blue-700 transition-colors',
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
            <li key={a.id} className="flex items-center justify-between px-3 py-2 border border-gray-200 rounded-lg text-xs">
              <span className="font-medium text-gray-800 truncate">{a.fileName}</span>
              <a href={attachmentApi.getDownloadUrl(a.id)} target="_blank" rel="noreferrer"
                className="text-blue-600 hover:underline shrink-0 ml-2">Download</a>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
