import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { smtpConfigApi, type SmtpConfigDto, type SmtpTestDto } from '../../../services/config/smtpConfigApi'
import { toast } from '../../../hooks/useToast'
import { Modal } from '../../../components/ui/Modal'
import {
  Mail, Eye, EyeOff, Plus, Pencil, Trash2, RotateCcw, Send, Loader2,
  Server, Lock, ShieldCheck, CheckCircle2, XCircle,
} from 'lucide-react'

const EMPTY_FORM: SmtpConfigDto = {
  smtpHost: '', smtpPort: 587, username: '', password: '',
  protocol: 'SMTP', tlsEnabled: true, sslEnabled: false,
  fromEmail: '', fromName: '', active: true,
}

type FormErrors = Partial<Record<keyof SmtpConfigDto | 'toEmail', string>>

function validate(form: SmtpConfigDto): FormErrors {
  const e: FormErrors = {}
  if (!form.smtpHost.trim()) e.smtpHost = 'SMTP Host is required'
  if (!form.smtpPort || form.smtpPort < 1 || form.smtpPort > 65535) e.smtpPort = 'Port must be 1–65535'
  if (!form.username.trim()) e.username = 'Username is required'
  if (!form.fromEmail.trim()) e.fromEmail = 'From Email is required'
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.fromEmail)) e.fromEmail = 'Invalid email'
  if (!form.protocol) e.protocol = 'Protocol is required'
  return e
}

export default function SmtpConfigPage() {
  const qc = useQueryClient()
  const [form, setForm] = useState<SmtpConfigDto>({ ...EMPTY_FORM })
  const [editingId, setEditingId] = useState<string | null>(null)
  const [errors, setErrors] = useState<FormErrors>({})
  const [showPassword, setShowPassword] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<SmtpConfigDto | null>(null)
  const [testEmailOpen, setTestEmailOpen] = useState(false)
  const [testToEmail, setTestToEmail] = useState('')

  // ── Queries ────────────────────────────────────────────────────────────────

  const { data: configs = [], isLoading } = useQuery({
    queryKey: ['smtp-configs'],
    queryFn: () => smtpConfigApi.getAll(),
  })

  // ── Mutations ──────────────────────────────────────────────────────────────

  const saveMutation = useMutation({
    mutationFn: () => {
      if (editingId) return smtpConfigApi.update(editingId, form)
      return smtpConfigApi.create(form)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['smtp-configs'] })
      toast({ title: editingId ? 'Configuration updated' : 'Configuration created', variant: 'success' })
      resetForm()
    },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => smtpConfigApi.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['smtp-configs'] })
      toast({ title: 'Configuration deleted', variant: 'success' })
      setDeleteTarget(null)
      if (editingId === deleteTarget?.id) resetForm()
    },
    onError: (e: Error) => toast({ title: 'Delete failed', description: e.message, variant: 'destructive' }),
  })

  const testMutation = useMutation({
    mutationFn: (data: SmtpTestDto) => smtpConfigApi.testConnection(data),
    onSuccess: () => {
      toast({ title: 'Test email sent successfully!', variant: 'success' })
      setTestEmailOpen(false)
      setTestToEmail('')
    },
    onError: (e: Error) => toast({ title: 'Test failed', description: e.message, variant: 'destructive' }),
  })

  // ── Handlers ───────────────────────────────────────────────────────────────

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const validationErrors = validate(form)
    if (!editingId && !form.password?.trim()) validationErrors.password = 'Password is required'
    setErrors(validationErrors)
    if (Object.keys(validationErrors).length > 0) return
    saveMutation.mutate()
  }

  const handleEdit = (config: SmtpConfigDto) => {
    setEditingId(config.id!)
    setForm({ ...config, password: '' })
    setErrors({})
    setShowPassword(false)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const resetForm = () => {
    setForm({ ...EMPTY_FORM })
    setEditingId(null)
    setErrors({})
    setShowPassword(false)
  }

  const handleTestConnection = () => {
    const validationErrors = validate(form)
    if (!form.password?.trim() && !editingId) validationErrors.password = 'Password is required for test'
    if (!testToEmail.trim()) { validationErrors.toEmail = 'Recipient email is required'; }
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(testToEmail)) { validationErrors.toEmail = 'Invalid email'; }
    setErrors(validationErrors)
    if (Object.keys(validationErrors).length > 0) return

    testMutation.mutate({
      smtpHost: form.smtpHost, smtpPort: form.smtpPort, username: form.username,
      password: form.password || '', protocol: form.protocol,
      tlsEnabled: form.tlsEnabled, sslEnabled: form.sslEnabled,
      fromEmail: form.fromEmail, fromName: form.fromName, toEmail: testToEmail,
    })
  }

  const setField = <K extends keyof SmtpConfigDto>(key: K, value: SmtpConfigDto[K]) => {
    setForm(prev => ({ ...prev, [key]: value }))
    if (errors[key]) setErrors(prev => { const n = { ...prev }; delete n[key]; return n })
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="space-y-5 max-w-6xl">
      {/* Header */}
      <div>
        <h2 className="text-xl font-bold text-gray-900">SMTP Configuration</h2>
        <p className="text-xs text-gray-500 mt-0.5">Manage outbound email server settings</p>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-5 gap-6 items-start">
        {/* ── Form Card ─────────────────────────────────────────────────────── */}
        <form onSubmit={handleSubmit} className="xl:col-span-2 bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
          <div className="px-5 py-3.5 border-b border-gray-100 bg-gray-50/70 flex items-center justify-between">
            <h3 className="text-sm font-bold text-gray-800 flex items-center gap-2">
              <Server size={15} className="text-gray-500" />
              {editingId ? 'Update Configuration' : 'New Configuration'}
            </h3>
            {editingId && (
              <span className="text-[10px] font-bold uppercase tracking-wider text-slate-700 bg-slate-100 border border-slate-200 px-2 py-0.5 rounded">Editing</span>
            )}
          </div>

          <div className="p-5 space-y-4">
            {/* SMTP Host */}
            <div>
              <label htmlFor="smtpHost" className="block text-xs font-semibold text-gray-700 mb-1">SMTP Host <span className="text-red-500">*</span></label>
              <input id="smtpHost" type="text" value={form.smtpHost} onChange={e => setField('smtpHost', e.target.value)}
                placeholder="smtp.gmail.com"
                className={`w-full px-3 py-2 border rounded-lg text-sm bg-gray-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500 transition-all ${errors.smtpHost ? 'border-red-300 bg-red-50/50' : 'border-gray-200'}`} />
              {errors.smtpHost && <p className="text-xs text-red-500 mt-1">{errors.smtpHost}</p>}
            </div>

            {/* Port + Protocol */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label htmlFor="smtpPort" className="block text-xs font-semibold text-gray-700 mb-1">Port <span className="text-red-500">*</span></label>
                <input id="smtpPort" type="number" value={form.smtpPort} onChange={e => setField('smtpPort', Number(e.target.value))}
                  min={1} max={65535}
                  className={`w-full px-3 py-2 border rounded-lg text-sm bg-gray-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500 transition-all ${errors.smtpPort ? 'border-red-300 bg-red-50/50' : 'border-gray-200'}`} />
                {errors.smtpPort && <p className="text-xs text-red-500 mt-1">{errors.smtpPort}</p>}
              </div>
              <div>
                <label htmlFor="protocol" className="block text-xs font-semibold text-gray-700 mb-1">Protocol <span className="text-red-500">*</span></label>
                <select id="protocol" value={form.protocol} onChange={e => setField('protocol', e.target.value)}
                  className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500 transition-all">
                  <option value="SMTP">SMTP</option>
                  <option value="SMTPS">SMTPS</option>
                </select>
              </div>
            </div>

            {/* Username */}
            <div>
              <label htmlFor="username" className="block text-xs font-semibold text-gray-700 mb-1">Username <span className="text-red-500">*</span></label>
              <input id="username" type="text" value={form.username} onChange={e => setField('username', e.target.value)}
                placeholder="user@example.com"
                className={`w-full px-3 py-2 border rounded-lg text-sm bg-gray-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500 transition-all ${errors.username ? 'border-red-300 bg-red-50/50' : 'border-gray-200'}`} />
              {errors.username && <p className="text-xs text-red-500 mt-1">{errors.username}</p>}
            </div>

            {/* Password */}
            <div>
              <label htmlFor="password" className="block text-xs font-semibold text-gray-700 mb-1">
                Password {!editingId && <span className="text-red-500">*</span>}
                {editingId && <span className="text-gray-400 font-normal ml-1">(leave blank to keep current)</span>}
              </label>
              <div className="relative">
                <input id="password" type={showPassword ? 'text' : 'password'} value={form.password || ''} onChange={e => setField('password', e.target.value)}
                  placeholder={editingId ? '••••••••' : 'Enter password'}
                  className={`w-full px-3 py-2 pr-10 border rounded-lg text-sm bg-gray-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500 transition-all ${errors.password ? 'border-red-300 bg-red-50/50' : 'border-gray-200'}`} />
                <button type="button" onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-gray-400 hover:text-gray-600 transition-colors" tabIndex={-1}>
                  {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              </div>
              {errors.password && <p className="text-xs text-red-500 mt-1">{errors.password}</p>}
            </div>

            {/* From Email + From Name */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label htmlFor="fromEmail" className="block text-xs font-semibold text-gray-700 mb-1">From Email <span className="text-red-500">*</span></label>
                <input id="fromEmail" type="email" value={form.fromEmail} onChange={e => setField('fromEmail', e.target.value)}
                  placeholder="noreply@hospital.com"
                  className={`w-full px-3 py-2 border rounded-lg text-sm bg-gray-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500 transition-all ${errors.fromEmail ? 'border-red-300 bg-red-50/50' : 'border-gray-200'}`} />
                {errors.fromEmail && <p className="text-xs text-red-500 mt-1">{errors.fromEmail}</p>}
              </div>
              <div>
                <label htmlFor="fromName" className="block text-xs font-semibold text-gray-700 mb-1">From Name</label>
                <input id="fromName" type="text" value={form.fromName} onChange={e => setField('fromName', e.target.value)}
                  placeholder="HMS System"
                  className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm bg-gray-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500 transition-all" />
              </div>
            </div>

            {/* Toggles */}
            <div className="flex flex-wrap items-center gap-x-6 gap-y-2 pt-1">
              <label className="flex items-center gap-2 cursor-pointer select-none group">
                <input type="checkbox" checked={form.tlsEnabled} onChange={e => setField('tlsEnabled', e.target.checked)}
                  className="w-4 h-4 rounded border-gray-300 text-neutral-800 focus:ring-neutral-500 cursor-pointer" />
                <span className="text-xs font-medium text-gray-700 group-hover:text-gray-900 flex items-center gap-1">
                  <Lock size={12} /> TLS
                </span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer select-none group">
                <input type="checkbox" checked={form.sslEnabled} onChange={e => setField('sslEnabled', e.target.checked)}
                  className="w-4 h-4 rounded border-gray-300 text-neutral-800 focus:ring-neutral-500 cursor-pointer" />
                <span className="text-xs font-medium text-gray-700 group-hover:text-gray-900 flex items-center gap-1">
                  <ShieldCheck size={12} /> SSL
                </span>
              </label>
              <label className="flex items-center gap-2 cursor-pointer select-none group">
                <input type="checkbox" checked={form.active} onChange={e => setField('active', e.target.checked)}
                  className="w-4 h-4 rounded border-gray-300 text-emerald-600 focus:ring-emerald-500 cursor-pointer" />
                <span className="text-xs font-medium text-gray-700 group-hover:text-gray-900">Active</span>
              </label>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="px-5 py-3.5 border-t border-gray-100 bg-gray-50/50 flex flex-wrap items-center gap-2">
            <button type="submit" disabled={saveMutation.isPending}
              className="px-4 py-2 bg-neutral-800 text-white text-xs font-semibold rounded-lg hover:bg-neutral-900 disabled:opacity-50 transition-all shadow-sm flex items-center gap-1.5">
              {saveMutation.isPending ? <Loader2 size={13} className="animate-spin" /> : editingId ? <Pencil size={13} /> : <Plus size={13} />}
              {saveMutation.isPending ? 'Saving…' : editingId ? 'Update' : 'Save'}
            </button>
            <button type="button" onClick={resetForm}
              className="px-4 py-2 border border-gray-200 bg-white text-gray-700 text-xs font-medium rounded-lg hover:bg-gray-50 transition-colors flex items-center gap-1.5">
              <RotateCcw size={13} /> Reset
            </button>
            <button type="button" onClick={() => {
              const validationErrors = validate(form)
              if (Object.keys(validationErrors).length > 0) { setErrors(validationErrors); return }
              setTestEmailOpen(true)
            }}
              className="px-4 py-2 border border-gray-200 bg-white text-gray-700 text-xs font-medium rounded-lg hover:bg-gray-50 hover:border-gray-300 transition-all flex items-center gap-1.5 ml-auto">
              <Send size={13} /> Test Connection
            </button>
          </div>
        </form>

        {/* ── Table ──────────────────────────────────────────────────────────── */}
        <div className="xl:col-span-3 bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden flex flex-col">
          <div className="px-5 py-3.5 border-b border-gray-100 bg-gray-50/70 flex items-center justify-between">
            <h3 className="text-sm font-bold text-gray-800 flex items-center gap-2">
              <Mail size={15} className="text-gray-500" /> Configurations
            </h3>
            <span className="text-xs text-gray-500 font-medium">{configs.length} total</span>
          </div>

          {isLoading ? (
            <div className="flex items-center justify-center py-16" aria-live="polite">
              <Loader2 size={22} className="animate-spin text-neutral-500" />
            </div>
          ) : configs.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-gray-400">
              <Mail size={36} strokeWidth={1.5} className="mb-2 text-gray-300" />
              <p className="text-sm font-medium text-gray-500">No SMTP configurations</p>
              <p className="text-xs text-gray-400 mt-0.5">Create one using the form</p>
            </div>
          ) : (
            <div className="overflow-x-auto flex-1">
              <table className="w-full text-sm" id="smtp-config-table">
                <thead>
                  <tr className="bg-gray-50/60 border-b border-gray-100 text-[11px] font-bold text-gray-500 uppercase tracking-wider">
                    <th className="text-left px-4 py-3">Host</th>
                    <th className="text-left px-4 py-3">Port</th>
                    <th className="text-left px-4 py-3">Username</th>
                    <th className="text-left px-4 py-3">From Email</th>
                    <th className="text-left px-4 py-3">Protocol</th>
                    <th className="text-center px-4 py-3">Status</th>
                    <th className="text-center px-4 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {configs.map(cfg => (
                    <tr key={cfg.id} className="hover:bg-gray-50/80 transition-colors group">
                      <td className="px-4 py-3 font-medium text-gray-800">{cfg.smtpHost}</td>
                      <td className="px-4 py-3 text-gray-600 font-mono text-xs">{cfg.smtpPort}</td>
                      <td className="px-4 py-3 text-gray-600 max-w-[150px] truncate text-xs" title={cfg.username}>{cfg.username}</td>
                      <td className="px-4 py-3 text-gray-600 max-w-[180px] truncate text-xs" title={cfg.fromEmail}>{cfg.fromEmail}</td>
                      <td className="px-4 py-3">
                        <span className="text-[10px] font-bold uppercase tracking-wider text-slate-700 bg-slate-100 border border-slate-200 px-2 py-0.5 rounded">{cfg.protocol}</span>
                      </td>
                      <td className="px-4 py-3 text-center">
                        {cfg.active ? (
                          <span className="inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded">
                            <CheckCircle2 size={11} /> Active
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-gray-600 bg-gray-100 border border-gray-200 px-2 py-0.5 rounded">
                            <XCircle size={11} /> Inactive
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-center">
                        <div className="flex items-center justify-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button onClick={() => handleEdit(cfg)} title="Edit"
                            className="p-1.5 rounded-lg text-gray-400 hover:text-neutral-800 hover:bg-gray-100 transition-colors">
                            <Pencil size={13} />
                          </button>
                          <button onClick={() => setDeleteTarget(cfg)} title="Delete"
                            className="p-1.5 rounded-lg text-gray-400 hover:text-red-600 hover:bg-red-50 transition-colors">
                            <Trash2 size={13} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {/* ── Delete Confirmation Modal ─────────────────────────────────────── */}
      <Modal isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} title="Delete SMTP Configuration" size="sm">
        <div className="p-6 space-y-4">
          <div className="flex items-start gap-3">
            <div className="w-9 h-9 rounded-full bg-red-50 border border-red-100 flex items-center justify-center shrink-0 mt-0.5">
              <Trash2 size={16} className="text-red-600" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-gray-900">Delete Configuration?</h3>
              <p className="text-xs text-gray-500 mt-1 leading-relaxed">
                Are you sure you want to delete the SMTP configuration for <strong className="text-gray-700">{deleteTarget?.smtpHost}</strong>? This action cannot be undone.
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 justify-end pt-2">
            <button onClick={() => setDeleteTarget(null)}
              className="px-4 py-2 border border-gray-200 text-gray-700 text-xs font-medium rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
            <button onClick={() => deleteTarget?.id && deleteMutation.mutate(deleteTarget.id)}
              disabled={deleteMutation.isPending}
              className="px-4 py-2 bg-red-600 text-white text-xs font-bold rounded-lg hover:bg-red-700 disabled:opacity-50 transition-all flex items-center gap-1.5 shadow-sm">
              {deleteMutation.isPending ? <Loader2 size={13} className="animate-spin" /> : <Trash2 size={13} />}
              {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
            </button>
          </div>
        </div>
      </Modal>

      {/* ── Test Connection Modal ─────────────────────────────────────────── */}
      <Modal isOpen={testEmailOpen} onClose={() => { setTestEmailOpen(false); setTestToEmail(''); }} title="Test SMTP Connection" size="sm">
        <div className="p-6 space-y-4">
          <div className="flex items-start gap-3">
            <div className="w-9 h-9 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center shrink-0 mt-0.5">
              <Send size={15} className="text-slate-700" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-gray-900">Send Test Email</h3>
              <p className="text-xs text-gray-500 mt-1 leading-relaxed">
                A test email will be sent using the current form values to verify the SMTP configuration.
              </p>
            </div>
          </div>
          <div>
            <label htmlFor="testToEmail" className="block text-xs font-semibold text-gray-700 mb-1">Recipient Email <span className="text-red-500">*</span></label>
            <input id="testToEmail" type="email" value={testToEmail} onChange={e => {
              setTestToEmail(e.target.value)
              if (errors.toEmail) setErrors(prev => { const n = { ...prev }; delete n.toEmail; return n })
            }}
              placeholder="test@example.com"
              className={`w-full px-3 py-2 border rounded-lg text-sm bg-gray-50/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-neutral-500 transition-all ${errors.toEmail ? 'border-red-300 bg-red-50/50' : 'border-gray-200'}`} />
            {errors.toEmail && <p className="text-xs text-red-500 mt-1">{errors.toEmail}</p>}
          </div>
          <div className="flex items-center gap-2 justify-end pt-2">
            <button onClick={() => { setTestEmailOpen(false); setTestToEmail('') }}
              className="px-4 py-2 border border-gray-200 text-gray-700 text-xs font-medium rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
            <button onClick={handleTestConnection}
              disabled={testMutation.isPending}
              className="px-4 py-2 bg-neutral-800 text-white text-xs font-semibold rounded-lg hover:bg-neutral-900 disabled:opacity-50 transition-all flex items-center gap-1.5 shadow-sm">
              {testMutation.isPending ? <Loader2 size={13} className="animate-spin" /> : <Send size={13} />}
              {testMutation.isPending ? 'Sending…' : 'Send Test Email'}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
