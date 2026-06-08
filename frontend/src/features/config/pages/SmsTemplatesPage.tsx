import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { smsTemplateApi } from '../../../services/config/smsTemplateApi'
import { toast } from '../../../hooks/useToast'

export default function SmsTemplatesPage() {
  const qc = useQueryClient()
  const { data: templates, isLoading } = useQuery({
    queryKey: ['sms-templates'],
    queryFn: () => smsTemplateApi.getAll(),
  })
  const { data: placeholders } = useQuery({
    queryKey: ['sms-templates', 'placeholders'],
    queryFn: () => smsTemplateApi.getPlaceholders(),
  })

  const [editing, setEditing] = useState<string | null>(null)
  const [body, setBody] = useState('')

  const saveMutation = useMutation({
    mutationFn: () => smsTemplateApi.save(editing!, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sms-templates'] })
      setEditing(null)
      toast({ title: 'SMS template saved', variant: 'success' })
    },
    onError: (e: Error) => toast({ title: 'Save failed', description: e.message, variant: 'destructive' }),
  })

  const handleEdit = (key: string, currentBody: string) => {
    setEditing(key)
    setBody(currentBody)
  }

  const insertPlaceholder = (variable: string) => {
    setBody(prev => prev + variable)
  }

  return (
    <div className="space-y-5 max-w-4xl">
      <div>
        <h2 className="text-xl font-bold text-gray-900">SMS Templates</h2>
        <p className="text-sm text-gray-500 mt-0.5">
          Configure message bodies for automated SMS notifications. Use <code className="bg-gray-100 px-1 rounded text-xs">$variable$</code> placeholders.
        </p>
      </div>

      <div className="grid grid-cols-3 gap-5">
        {/* Template list */}
        <div className="col-span-2 space-y-3">
          {isLoading && <p className="text-sm text-gray-500" aria-live="polite">Loading…</p>}
          {templates?.map(tpl => (
            <div key={tpl.key}
              className="bg-white border border-gray-200 rounded-xl overflow-hidden">
              <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
                <div>
                  <p className="text-sm font-semibold text-gray-900 font-mono">{tpl.key}</p>
                  {tpl.isStandard === 'true' && (
                    <span className="text-xs text-neutral-600 font-medium">Standard template</span>
                  )}
                </div>
                <button
                  onClick={() => handleEdit(tpl.key, tpl.body)}
                  className="text-xs text-neutral-600 hover:text-neutral-800 font-medium px-2 py-1 border border-neutral-200 rounded hover:bg-neutral-50 transition-colors">
                  Edit
                </button>
              </div>
              {editing === tpl.key ? (
                <div className="p-4 space-y-3 bg-neutral-50">
                  <textarea
                    value={body}
                    onChange={e => setBody(e.target.value)}
                    rows={3}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-neutral-500 resize-none bg-white"
                    placeholder="Enter SMS message body…"
                    aria-label={`Template body for ${tpl.key}`}
                  />
                  <div className="flex items-center gap-3">
                    <button
                      onClick={() => saveMutation.mutate()}
                      disabled={saveMutation.isPending}
                      className="px-4 py-1.5 bg-neutral-600 text-white text-xs font-semibold rounded hover:bg-neutral-700 disabled:opacity-50 transition-colors">
                      {saveMutation.isPending ? 'Saving…' : 'Save Template'}
                    </button>
                    <button
                      onClick={() => setEditing(null)}
                      className="px-4 py-1.5 border border-gray-200 text-xs text-gray-600 rounded hover:bg-gray-50 transition-colors">
                      Cancel
                    </button>
                    <span className="text-xs text-gray-400 ml-auto">
                      {body.length} characters
                    </span>
                  </div>
                </div>
              ) : (
                <div className="px-4 py-3">
                  {tpl.body ? (
                    <p className="text-sm text-gray-700 whitespace-pre-wrap">{tpl.body}</p>
                  ) : (
                    <p className="text-sm text-gray-400 italic">No template configured — SMS will not be sent for this event.</p>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>

        {/* Placeholders reference */}
        <div className="space-y-3">
          <div className="bg-white border border-gray-200 rounded-xl overflow-hidden">
            <div className="px-4 py-3 border-b border-gray-100 text-xs font-semibold text-gray-500 uppercase tracking-wide">
              Available Variables
            </div>
            <div className="p-3 space-y-2">
              {placeholders?.map(p => (
                <button
                  key={p.variable}
                  onClick={() => editing && insertPlaceholder(p.variable)}
                  disabled={!editing}
                  title={`Click to insert ${p.variable}`}
                  className="w-full text-left px-2 py-1.5 rounded hover:bg-neutral-50 disabled:cursor-default transition-colors group">
                  <code className="text-xs font-mono text-neutral-700 group-hover:text-neutral-800">
                    {p.variable}
                  </code>
                  <p className="text-xs text-gray-500 mt-0.5">{p.description}</p>
                </button>
              ))}
              {!editing && (
                <p className="text-xs text-gray-400 text-center py-2 italic">
                  Click Edit on a template to insert variables
                </p>
              )}
            </div>
          </div>

          <div className="bg-amber-50 border border-amber-200 rounded-xl p-3">
            <p className="text-xs font-semibold text-amber-700 mb-1">SMS length tip</p>
            <p className="text-xs text-amber-600">
              Standard SMS is 160 characters. Templates over 160 chars are sent as multi-part SMS (billed as 2+ messages).
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
