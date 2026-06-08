import { useState, useEffect, useMemo } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { reportApi, type ReportInfo, type ReportParam } from '../../../services/report/reportApi'
import { consultantApi } from '../../../services/consultant/consultantApi'
import { supplierApi } from '../../../services/masters/masterApi'
import { bedApi } from '../../../services/bed/bedApi'
import { userApi } from '../../../services/user/userApi'
import { ConsultantSearchInput } from '../../../components/shared/ConsultantSearchInput'
import DatePicker from '../../../components/shared/DatePicker'
import { MedicineSearchInput } from '../../../components/shared/MedicineSearchInput'
import { useDepartments } from '../../../hooks/diagnostic/useDiagnostic'

interface ReportDetailViewProps {
  reportName: string
  initialParams: Record<string, string>
  onClose: () => void
  onDrilldown?: ((reportName: string, params: Record<string, string>) => void) | undefined
  onBack?: (() => void) | undefined
}

export function ReportDetailView({ reportName, initialParams, onClose, onDrilldown, onBack }: ReportDetailViewProps) {
  const [params, setParams] = useState<Record<string, string>>(initialParams)
  const [htmlContent, setHtmlContent] = useState<string | null>(null)
  const [isSidebarOpen, setIsSidebarOpen] = useState(true)
  const [currentPage, setCurrentPage] = useState(1)
  const [downloadFormat, setDownloadFormat] = useState<'PDF' | 'XLSX' | null>(null)
  const [appliedReportViewType, setAppliedReportViewType] = useState<string>('summary')

  useEffect(() => {
    setCurrentPage(1)
  }, [htmlContent])

  const pagedData = useMemo(() => {
    if (!htmlContent) return { html: '', totalPages: 0, totalRows: 0 }
    
    const parser = new DOMParser()
    const doc = parser.parseFromString(htmlContent, 'text/html')
    
    const tbodies = Array.from(doc.querySelectorAll('tbody'))
    if (tbodies.length === 0) {
      return { html: htmlContent, totalPages: 0, totalRows: 0 }
    }
    
    let tbody = tbodies[0]
    if (reportName === 'bills_raised_daywise' && tbodies.length > 1) {
      tbody = tbodies[1]
    } else if (tbodies.length > 1) {
      let maxRows = 0
      tbodies.forEach(tb => {
        const rowsCount = tb.querySelectorAll('tr').length
        if (rowsCount > maxRows) {
          maxRows = rowsCount
          tbody = tb
        }
      })
    }
    
    const rows = Array.from(tbody.querySelectorAll('tr'))
    let totalRows = rows.length
    let rowsToPaginate = rows
    let totalsRow: HTMLTableRowElement | null = null

    if ((reportName === 'discount_report' || reportName === 'bills_overdue' || reportName === 'admissions_report') && rows.length > 0) {
      const lastRow = rows[rows.length - 1]
      const text = lastRow.textContent || ''
      if (text.includes('Total : Rs.') || text.trim().startsWith('Total')) {
        totalsRow = lastRow as HTMLTableRowElement
        rowsToPaginate = rows.slice(0, rows.length - 1)
        totalRows = rowsToPaginate.length
      }
    }

    const pageSize = reportName === 'bed_occupancy_period' ? 100 : 15
    const totalPages = Math.ceil(totalRows / pageSize)
    
    if (totalRows > 0) {
      const start = (currentPage - 1) * pageSize
      const end = currentPage * pageSize
      
      rowsToPaginate.forEach((row, idx) => {
        if (idx >= start && idx < end) {
          const currentStyle = row.getAttribute('style') || ''
          const newStyle = currentStyle
            .replace(/display:\s*none\s*!important/gi, '')
            .replace(/display:\s*none/gi, '')
            .trim()
          if (newStyle) {
            row.setAttribute('style', newStyle)
          } else {
            row.removeAttribute('style')
          }
        } else {
          const currentStyle = row.getAttribute('style') || ''
          if (!currentStyle.toLowerCase().includes('display: none')) {
            row.setAttribute('style', `${currentStyle}${currentStyle ? ';' : ''}display: none !important`)
          }
        }
      })

      if (totalsRow) {
        const currentStyle = totalsRow.getAttribute('style') || ''
        const newStyle = currentStyle
          .replace(/display:\s*none\s*!important/gi, '')
          .replace(/display:\s*none/gi, '')
          .trim()
        if (newStyle) {
          totalsRow.setAttribute('style', newStyle)
        } else {
          totalsRow.removeAttribute('style')
        }
      }
    }
    
    return {
      html: doc.head.innerHTML + doc.body.innerHTML,
      totalPages,
      totalRows
    }
  }, [htmlContent, currentPage, reportName])
  const [selectedItemNames, setSelectedItemNames] = useState<Record<string, string>>({})
  
  const { data: departments = [] } = useDepartments()
  const filteredDepartments = useMemo(() => {
    return departments.filter(d => d.departmentType?.toUpperCase() === 'CLINICAL')
  }, [departments])

  const { data: reportInfo, isLoading: isInfoLoading } = useQuery<ReportInfo>({
    queryKey: ['reports', 'info', reportName],
    queryFn: () => reportApi.getReportInfo(reportName),
  })

  const needsConsultants = reportInfo?.parameters.some(p => p.type === 'CONSULTANT')
  const { data: consultants = [] } = useQuery({
    queryKey: ['consultants'],
    queryFn: () => consultantApi.getAll(),
    enabled: !!needsConsultants,
  })

  const needsSuppliers = reportInfo?.parameters.some(p => p.type === 'SUPPLIER')
  const { data: suppliers = [] } = useQuery({
    queryKey: ['suppliers'],
    queryFn: () => supplierApi.getAll(),
    enabled: !!needsSuppliers,
  })

  // Pre-select default department (e.g. STORE or PHARMACY) when departments load
  useEffect(() => {
    const list = filteredDepartments && filteredDepartments.length > 0 ? filteredDepartments : departments
    if (list && list.length > 0) {
      const storeDept = list.find(d => d.name.toUpperCase().includes('STORE')) || 
                        list.find(d => d.name.toUpperCase().includes('PHARMACY')) || 
                        list[0]
      setParams(prev => {
        const updated = { ...prev }
        reportInfo?.parameters.forEach(p => {
          const isDept = p.name === 'dept_id' || p.name === 'department_id' || p.description.toLowerCase().includes('department')
          if (isDept && !updated[p.name]) {
            updated[p.name] = storeDept.id
          }
        })
        return updated
      })
    }
  }, [departments, filteredDepartments, reportInfo])

  const needsBedTypes = reportInfo?.parameters.some(p => p.type === 'BED_TYPE')
  const { data: bedTypes = [] } = useQuery({
    queryKey: ['bedTypes'],
    queryFn: () => bedApi.getBedTypes(),
    enabled: !!needsBedTypes,
  })

  const needsUsers = reportInfo?.parameters.some(p => p.type === 'USER')
  const { data: users = [] } = useQuery({
    queryKey: ['users'],
    queryFn: () => userApi.getAll(),
    enabled: !!needsUsers,
  })

  const executeMutation = useMutation({
    mutationFn: async (execParams?: Record<string, string>) => {
      const p = execParams || params
      const res = await reportApi.executeHtml(reportName, p)
      return {
        htmlContent: res.htmlContent,
        viewType: p.report_view_type || 'summary'
      }
    },
    onSuccess: (data) => {
      setHtmlContent(data.htmlContent)
      setAppliedReportViewType(data.viewType)
    },
  })

  // Automatically execute on mount or when default parameters are populated
  useEffect(() => {
    if (!reportInfo) return
    const today = new Date()
    const y = today.getFullYear()
    const m = String(today.getMonth() + 1).padStart(2, '0')
    const d = String(today.getDate()).padStart(2, '0')
    const todayStr = `${y}-${m}-${d}`

    const updatedParams = { ...params }
    let changed = false
    reportInfo.parameters.forEach(p => {
      if (p.type === 'DATE' && !updatedParams[p.name]) {
        updatedParams[p.name] = todayStr
        changed = true
      } else if (p.type === 'USER' && !updatedParams[p.name]) {
        updatedParams[p.name] = 'ALL'
        changed = true
      } else if (p.type === 'PAYMENT_MODE' && !updatedParams[p.name]) {
        updatedParams[p.name] = 'ALL'
        changed = true
      } else if (p.defaultValue && updatedParams[p.name] === undefined) {
        updatedParams[p.name] = p.defaultValue
        changed = true
      }
    })

    if (changed) {
      setParams(updatedParams)
    }

    // Wait a brief tick to ensure pre-selected dept_id is in params
    const timer = setTimeout(() => {
      executeMutation.mutate(changed ? updatedParams : params)
    }, 100)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reportInfo])

  useEffect(() => {
    const handleDrilldown = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest('.report-drilldown')
      if (target && onDrilldown) {
        e.preventDefault()
        const report = target.getAttribute('data-report')
        const dept = target.getAttribute('data-dept')
        const deptId = target.getAttribute('data-dept-id')
        const consultantId = target.getAttribute('data-consultant-id')
        if (report) {
          const drilldownParams = { ...params }
          if (dept) drilldownParams.department = dept
          if (deptId) drilldownParams.departmentId = deptId
          if (consultantId) drilldownParams.consultantId = consultantId
          onDrilldown(report, drilldownParams)
        }
      }
    }
    
    document.addEventListener('click', handleDrilldown)
    return () => document.removeEventListener('click', handleDrilldown)
  }, [params, onDrilldown])

  useEffect(() => {
    const handleDeptClick = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest('.dept-link')
      if (target) {
        e.preventDefault()
        const dept = target.getAttribute('data-dept')
        if (dept !== null) {
          const updatedParams = {
            ...params,
            report_type: 'DETAIL',
            department_filter: dept
          }
          setParams(updatedParams)
          executeMutation.mutate(updatedParams)
        }
      }
    }

    const handleDeptBackClick = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest('.admissions-back-btn')
      if (target) {
        e.preventDefault()
        const updatedParams = {
          ...params,
          report_type: 'SUMMARY',
          department_filter: ''
        }
        setParams(updatedParams)
        executeMutation.mutate(updatedParams)
      }
    }

    document.addEventListener('click', handleDeptClick)
    document.addEventListener('click', handleDeptBackClick)
    return () => {
      document.removeEventListener('click', handleDeptClick)
      document.removeEventListener('click', handleDeptBackClick)
    }
  }, [params, executeMutation])

  useEffect(() => {
    const handleBedTypeClick = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest('.bed-type-link')
      if (target) {
        e.preventDefault()
        const bedType = target.getAttribute('data-bed-type')
        if (bedType !== null) {
          const updatedParams = {
            ...params,
            bed_type_filter: bedType
          }
          setParams(updatedParams)
          executeMutation.mutate(updatedParams)
        }
      }
    }

    const handleBedBackClick = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest('.bed-occupancy-back-btn')
      if (target) {
        e.preventDefault()
        const updatedParams = {
          ...params,
          bed_type_filter: ''
        }
        setParams(updatedParams)
        executeMutation.mutate(updatedParams)
      }
    }

    document.addEventListener('click', handleBedTypeClick)
    document.addEventListener('click', handleBedBackClick)
    return () => {
      document.removeEventListener('click', handleBedTypeClick)
      document.removeEventListener('click', handleBedBackClick)
    }
  }, [params, executeMutation])

  useEffect(() => {
    const handlePoClick = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest('.po-link')
      if (target) {
        e.preventDefault()
        const poNo = target.getAttribute('data-po-no')
        if (poNo !== null) {
          const updatedParams = {
            ...params,
            report_view_type: 'detail',
            po_no_filter: poNo
          }
          setParams(updatedParams)
          executeMutation.mutate(updatedParams)
        }
      }
    }

    const handlePoBackClick = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest('.po-back-btn')
      if (target) {
        e.preventDefault()
        const updatedParams = {
          ...params,
          report_view_type: 'summary',
          po_no_filter: ''
        }
        setParams(updatedParams)
        executeMutation.mutate(updatedParams)
      }
    }

    document.addEventListener('click', handlePoClick)
    document.addEventListener('click', handlePoBackClick)
    return () => {
      document.removeEventListener('click', handlePoClick)
      document.removeEventListener('click', handlePoBackClick)
    }
  }, [params, executeMutation])

  useEffect(() => {
    const handleGrnClick = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest('.grn-link')
      if (target) {
        e.preventDefault()
        const grnNo = target.getAttribute('data-grn-no')
        if (grnNo !== null) {
          const updatedParams = {
            ...params,
            report_view_type: 'detail',
            grn_no_filter: grnNo
          }
          setParams(updatedParams)
          executeMutation.mutate(updatedParams)
        }
      }
    }

    const handleGrnBackClick = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest('.grn-back-btn')
      if (target) {
        e.preventDefault()
        const updatedParams = {
          ...params,
          report_view_type: 'summary',
          grn_no_filter: ''
        }
        setParams(updatedParams)
        executeMutation.mutate(updatedParams)
      }
    }

    document.addEventListener('click', handleGrnClick)
    document.addEventListener('click', handleGrnBackClick)
    return () => {
      document.removeEventListener('click', handleGrnClick)
      document.removeEventListener('click', handleGrnBackClick)
    }
  }, [params, executeMutation])

  useEffect(() => {
    const handleReturnClick = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest('.return-link')
      if (target) {
        e.preventDefault()
        const returnNo = target.getAttribute('data-return-no')
        if (returnNo !== null) {
          const updatedParams = {
            ...params,
            report_view_type: 'detail',
            return_no_filter: returnNo
          }
          setParams(updatedParams)
          executeMutation.mutate(updatedParams)
        }
      }
    }

    const handleReturnBackClick = (e: MouseEvent) => {
      const target = (e.target as HTMLElement).closest('.return-back-btn')
      if (target) {
        e.preventDefault()
        const updatedParams = {
          ...params,
          report_view_type: 'summary',
          return_no_filter: ''
        }
        setParams(updatedParams)
        executeMutation.mutate(updatedParams)
      }
    }

    document.addEventListener('click', handleReturnClick)
    document.addEventListener('click', handleReturnBackClick)
    return () => {
      document.removeEventListener('click', handleReturnClick)
      document.removeEventListener('click', handleReturnBackClick)
    }
  }, [params, executeMutation])

  const handleDownload = async (format: 'PDF' | 'XLSX') => {
    setDownloadFormat(format)
    try {
      if (format === 'PDF') {
        await reportApi.downloadPdf(reportName, params)
      } else {
        await reportApi.downloadXlsx(reportName, params)
      }
    } catch (err: any) {
      console.error(err)
      alert(`Failed to download report: ${err.message || err}`)
    } finally {
      setDownloadFormat(null)
    }
  }

  const getDisplayTitle = () => {
    if (!reportInfo?.description) return ''
    if (reportName === 'purchase_orders_report') {
      return appliedReportViewType.toLowerCase() === 'detail' 
        ? 'Purchase Order Detail Report' 
        : 'Purchase Order Summary Report'
    }
    if (reportName === 'goods_received_report') {
      return appliedReportViewType.toLowerCase() === 'detail' 
        ? 'Goods Received Detail Report' 
        : 'Goods Received Summary Report'
    }
    if (reportName === 'goods_returned_report') {
      return appliedReportViewType.toLowerCase() === 'detail' 
        ? 'Goods Returned Detail Report' 
        : 'Goods Returned Summary Report'
    }
    return reportInfo.description
  }

  if (isInfoLoading) {
    return <div className="flex items-center justify-center h-full">Loading...</div>
  }

  return (
    <div className="flex h-full bg-white rounded-xl shadow-sm overflow-hidden border border-gray-200" style={{ height: 'calc(100vh - 6rem)' }}>
      {/* Left Options Sidebar */}
      {isSidebarOpen && reportInfo?.parameters && reportInfo.parameters.length > 0 && (
        <aside className="w-64 shrink-0 border-r border-gray-100 flex flex-col bg-gray-50/50">
          <div className="p-4 border-b border-gray-100 bg-white">
          <h2 className="font-bold text-gray-800">Options</h2>
        </div>
        
        <div className="flex-1 p-4 overflow-y-auto space-y-4">
          {reportInfo?.parameters.map((p: ReportParam) => {
            const isDept = p.name === 'dept_id' || p.name === 'department_id' || p.description.toLowerCase().includes('department')
            const isItem = p.name === 'item_id' || p.name === 'product_id' || p.description.toLowerCase().includes('item')

             if (isDept) {
              return (
                <div key={p.name}>
                  <label className="block text-xs font-semibold text-gray-700 mb-1.5">
                    {p.description}
                  </label>
                  <select
                    value={params[p.name] ?? p.defaultValue ?? ''}
                    onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                    required={p.required}
                  >
                    {p.required ? (
                      <option value="">Select Department</option>
                    ) : (
                      <option value="ALL">All Departments</option>
                    )}
                    {filteredDepartments?.map(d => (
                      <option key={d.id} value={d.id}>
                        {d.name}
                      </option>
                    ))}
                  </select>
                </div>
              )
            }

            if (isItem) {
              return (
                <div key={p.name}>
                  <label className="block text-xs font-semibold text-gray-700 mb-1.5">
                    {p.description}
                  </label>
                  <MedicineSearchInput
                    placeholder="Enter Item"
                    value={selectedItemNames[p.name] ?? ''}
                    onSelect={(item) => {
                      setSelectedItemNames(prev => ({ ...prev, [p.name]: item.name }))
                      setParams(prev => ({ ...prev, [p.name]: item.id }))
                    }}
                    onClear={() => {
                      setSelectedItemNames(prev => {
                        const copy = { ...prev }
                        delete copy[p.name]
                        return copy
                      })
                      setParams(prev => {
                        const copy = { ...prev }
                        delete copy[p.name]
                        return copy
                      })
                    }}
                    className="w-full text-sm"
                  />
                </div>
              )
            }

            return (
              <div key={p.name}>
                <label className="block text-xs font-semibold text-gray-700 mb-1.5">
                  {p.description}
                </label>
              {p.type === 'CONSULTANT' ? (
                <ConsultantSearchInput
                  consultants={consultants}
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={id => setParams(prev => ({ ...prev, [p.name]: id }))}
                  placeholder="Select Consultant"
                  size="sm"
                />
              ) : p.type === 'SUPPLIER' ? (
                <select
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                  required={p.required}
                >
                  <option value="">All Suppliers</option>
                  {suppliers.map(s => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              ) : p.type === 'DEPARTMENT' ? (
                <select
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                  required={p.required}
                >
                  {p.required ? (
                    <option value="">Select Department</option>
                  ) : (
                    <option value="ALL">All Departments</option>
                  )}
                  {filteredDepartments.map(d => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
              ) : p.type === 'VISIT' ? (
                <select
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                  required={p.required}
                >
                  <option value="ALL">All</option>
                  <option value="OP">OP</option>
                  <option value="IP">IP</option>
                </select>
              ) : p.type === 'USER' ? (
                <select
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                  required={p.required}
                >
                  <option value="ALL">All Users</option>
                  {users.map(u => (
                    <option key={u.id} value={u.username}>{u.fullName || u.username}</option>
                  ))}
                </select>
              ) : p.type === 'PAYMENT_MODE' ? (
                <select
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                  required={p.required}
                >
                  <option value="ALL">All Modes</option>
                  <option value="CASH">CASH</option>
                  <option value="CARD">CARD</option>
                  <option value="UPI">UPI</option>
                </select>
              ) : p.type === 'REPORT_VIEW_TYPE' ? (
                <select
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                  required={p.required}
                >
                  <option value="summary">Summary Report</option>
                  <option value="detail">Detail Report</option>
                </select>
              ) : p.type === 'BED_TYPE' ? (
                <select
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                  required={p.required}
                >
                  <option value="">Select Bed_Type</option>
                  {bedTypes.map(t => (
                    <option key={t.id} value={t.id}>{t.name}</option>
                  ))}
                </select>
              ) : p.type === 'YEAR' ? (
                <select
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                  required={p.required}
                >
                  <option value="2026">2026</option>
                  <option value="2025">2025</option>
                  <option value="2024">2024</option>
                  <option value="2023">2023</option>
                </select>
              ) : p.type === 'MONTH_INTERVAL' ? (
                <select
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                  required={p.required}
                >
                  <option value="">Select Month_Interval</option>
                  <option value="1">1 month</option>
                  <option value="2">2 month</option>
                  <option value="3">3 month</option>
                  <option value="4">4 month</option>
                  <option value="5">5 month</option>
                  <option value="6">6 month</option>
                </select>
              ) : p.type === 'SCHEDULED_DRUG_TYPE' ? (
                <select
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                  required={p.required}
                >
                  <option value="">Select Scheduled_Drug_Type</option>
                  <option value="H">H</option>
                  <option value="H1">H1</option>
                </select>
              ) : p.type === 'REPORT_TYPE' ? (
                <select
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                  className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                  required={p.required}
                >
                  <option value="SUMMARY">Summary Report</option>
                  <option value="DETAIL">Detail Report</option>
                </select>
              ) : p.type === 'DATE' ? (
                <DatePicker
                  value={params[p.name] ?? p.defaultValue ?? ''}
                  onChange={val => setParams(prev => ({ ...prev, [p.name]: val }))}
                  size="sm"
                  clearable={false}
                />
              ) : (
                  <input
                    type={p.type === 'DATE' ? 'date' : 'text'}
                    value={params[p.name] ?? p.defaultValue ?? ''}
                    onChange={e => setParams(prev => ({ ...prev, [p.name]: e.target.value }))}
                    className="w-full px-3 py-2 border border-gray-200 rounded text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 bg-white"
                    required={p.required}
                  />
              )}
              </div>
            )
          })}
          {(!reportInfo?.parameters || reportInfo.parameters.length === 0) && (
            <div className="text-xs text-gray-400 italic">No options required.</div>
          )}
        </div>

        <div className="p-4 border-t border-gray-100 bg-white flex gap-2">
          <button
            onClick={() => executeMutation.mutate(params)}
            disabled={executeMutation.isPending}
            className="flex-1 bg-neutral-600 hover:bg-neutral-700 text-white text-sm font-semibold py-2 rounded-lg transition-colors disabled:opacity-50"
          >
            {executeMutation.isPending ? 'Filtering...' : 'Filter'}
          </button>
          <button
            onClick={() => {
              setParams(initialParams)
              setSelectedItemNames({})
            }}
            className="flex-1 bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 text-sm font-semibold py-2 rounded-lg transition-colors"
          >
            Reset
          </button>
        </div>
        </aside>
      )}

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col min-w-0 bg-white relative">
        {/* Top bar with generic actions */}
        <div className="h-12 border-b border-gray-100 flex items-center justify-between px-4">
          <div className="flex items-center gap-2">
            {onBack && (
              <button 
                onClick={onBack}
                className="p-1.5 px-3 text-xs font-semibold text-gray-600 hover:bg-gray-100 rounded border"
              >
                &lt; BACK
              </button>
            )}
            {reportInfo?.parameters && reportInfo.parameters.length > 0 && (
              <button 
                onClick={() => setIsSidebarOpen(!isSidebarOpen)}
                className="p-1.5 text-gray-500 hover:bg-gray-100 rounded border"
                title="Toggle filter sidebar"
              >
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 6h16M4 12h16M4 18h16"></path></svg>
              </button>
            )}
          </div>
          
          <div className="flex items-center gap-2">
            {pagedData.totalPages > 1 && (
              <div className="flex items-center gap-1 mr-3 border-r pr-3 border-gray-150">
                <button
                  disabled={currentPage === 1}
                  onClick={() => setCurrentPage(prev => Math.max(prev - 1, 1))}
                  className="px-2 py-1 text-xs font-bold rounded-lg border border-gray-200 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed select-none text-gray-500"
                >
                  &lt;
                </button>
                {pagedData.totalPages <= 6 ? (
                  Array.from({ length: pagedData.totalPages }, (_, i) => i + 1).map(p => (
                    <button
                      key={p}
                      onClick={() => setCurrentPage(p)}
                      className={`px-2.5 py-1 text-xs font-bold rounded-lg transition-colors select-none ${
                        currentPage === p
                          ? 'bg-neutral-600 text-white shadow-sm'
                          : 'bg-white hover:bg-gray-50 text-gray-600 border border-gray-200'
                      }`}
                    >
                      {p}
                    </button>
                  ))
                ) : (
                  <>
                    <button
                      onClick={() => setCurrentPage(1)}
                      className={`px-2.5 py-1 text-xs font-bold rounded-lg transition-colors select-none ${
                        currentPage === 1
                          ? 'bg-neutral-600 text-white shadow-sm'
                          : 'bg-white hover:bg-gray-50 text-gray-600 border border-gray-200'
                      }`}
                    >
                      1
                    </button>
                    {currentPage > 3 && <span className="text-xs text-gray-400 select-none px-1">...</span>}
                    {Array.from({ length: pagedData.totalPages }, (_, i) => i + 1)
                      .filter(p => p !== 1 && p !== pagedData.totalPages && Math.abs(p - currentPage) <= 1)
                      .map(p => (
                        <button
                          key={p}
                          onClick={() => setCurrentPage(p)}
                          className={`px-2.5 py-1 text-xs font-bold rounded-lg transition-colors select-none ${
                            currentPage === p
                              ? 'bg-neutral-600 text-white shadow-sm'
                              : 'bg-white hover:bg-gray-50 text-gray-600 border border-gray-200'
                          }`}
                        >
                          {p}
                        </button>
                      ))}
                    {currentPage < pagedData.totalPages - 2 && <span className="text-xs text-gray-400 select-none px-1">...</span>}
                    <button
                      onClick={() => setCurrentPage(pagedData.totalPages)}
                      className={`px-2.5 py-1 text-xs font-bold rounded-lg transition-colors select-none ${
                        currentPage === pagedData.totalPages
                          ? 'bg-neutral-600 text-white shadow-sm'
                          : 'bg-white hover:bg-gray-50 text-gray-600 border border-gray-200'
                      }`}
                    >
                      {pagedData.totalPages}
                    </button>
                  </>
                )}
                <button
                  disabled={currentPage === pagedData.totalPages}
                  onClick={() => setCurrentPage(prev => Math.min(prev + 1, pagedData.totalPages))}
                  className="px-2 py-1 text-xs font-bold rounded-lg border border-gray-200 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed select-none text-gray-500"
                >
                  &gt;
                </button>
                <span className="text-xs text-gray-500 font-semibold ml-1.5 select-none">of {pagedData.totalPages}</span>
              </div>
            )}
            <div className="relative group inline-block">
              <button 
                disabled={!!downloadFormat}
                className="flex items-center gap-1.5 bg-neutral-600 hover:bg-neutral-700 text-white px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed select-none"
              >
                {downloadFormat ? (
                  <>
                    <svg className="animate-spin -ml-0.5 mr-1 h-3 w-3 text-white" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                    {downloadFormat === 'PDF' ? 'Downloading PDF...' : 'Downloading Excel...'}
                  </>
                ) : (
                  <>
                    Download 
                    <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7"></path></svg>
                  </>
                )}
              </button>
              {/* Dropdown content */}
              {!downloadFormat && (
                <div className="absolute right-0 mt-1 w-32 bg-white rounded-lg shadow-lg border border-gray-100 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-10 py-1">
                  <button onClick={() => handleDownload('PDF')} className="w-full text-left px-4 py-2 text-sm hover:bg-gray-50 text-gray-700">As PDF</button>
                  <button onClick={() => handleDownload('XLSX')} className="w-full text-left px-4 py-2 text-sm hover:bg-gray-50 text-gray-700">As Excel</button>
                </div>
              )}
            </div>
            <button 
              onClick={onClose}
              className="p-1 text-gray-400 hover:text-gray-600"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
            </button>
          </div>
        </div>

        {/* Report Canvas */}
        <div className="flex-1 overflow-auto p-8 bg-white">
          {reportInfo?.description && !['admissions_report', 'discharges_report', 'bed_occupancy_period', 'beds_transferred', 'current_stock', 'expired_items', 'items_expiring_month', 'stock_and_nil_stock', 'zero_stock_items', 'scheduled_drug_sales', 'below_reorder_level', 'stock_adjustments', 'pharmacy_sales_collection', 'slow_moving_items', 'discount_report', 'bills_overdue', 'purchase_orders_report', 'goods_received_report', 'goods_returned_report'].includes(reportName) && (
            <h1 className="text-xl font-bold mb-3 text-gray-800">{getDisplayTitle()}</h1>
          )}
          {executeMutation.isPending && !htmlContent ? (
            <div className="flex items-center justify-center py-20 text-gray-400">Loading report...</div>
          ) : htmlContent ? (
            <div 
              className="report-html-content"
              dangerouslySetInnerHTML={{ __html: pagedData.html }} 
            />
          ) : (
            <div className="flex flex-col items-center justify-center py-24 text-gray-500">
              <svg className="w-12 h-12 mb-4 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
              <h3 className="text-lg font-medium text-gray-900 mb-1">No Record Found</h3>
              <p className="text-sm">Try adjusting your filters to find what you're looking for.</p>
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
