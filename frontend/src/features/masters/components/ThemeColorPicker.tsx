import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from '../../../hooks/useToast'
import { configApi } from '../../../services/config/configApi'
import { useAuthStore } from '../../../store/authStore'
import {
  THEME_PRESETS,
  applyTheme,
  cacheTheme,
  isValidHex,
  previewHex,
} from '../../../theme/theme'

/**
 * Picks the hospital's theme colour.
 *
 * Only hospital administrators (or superadmins) can change or save the theme.
 * Non-admins see the currently configured theme in read-only mode with an
 * informative message matching the hospital logo permission guard.
 */
export function ThemeColorPicker({ isAdmin: propIsAdmin }: { isAdmin?: boolean } = {}) {
  const qc = useQueryClient()
  const user = useAuthStore(s => s.user)
  const storeIsAdmin = (user?.isHospitalAdmin ?? false) || (user?.isSuperAdmin ?? false)
  const isAdmin = propIsAdmin !== undefined ? propIsAdmin : storeIsAdmin

  const { data: savedColor, isLoading } = useQuery({
    queryKey: ['config', 'theme'],
    queryFn: configApi.getTheme,
  })

  const [draft, setDraft] = useState<string | null>(null)
  const [customHex, setCustomHex] = useState('')

  useEffect(() => {
    if (savedColor !== undefined) {
      setDraft(savedColor)
      setCustomHex(savedColor ?? '')
    }
  }, [savedColor])

  const dirty = (draft ?? '') !== (savedColor ?? '')

  /** Live preview — repaint now, without saving. */
  const preview = (hex: string | null) => {
    if (!isAdmin) return
    setDraft(hex)
    applyTheme(hex)
  }

  const save = useMutation({
    mutationFn: () => configApi.saveTheme(draft),
    onSuccess: (color) => {
      applyTheme(color)
      cacheTheme(color)
      // Immediately update the query cache so every component that reads
      // ['config', 'theme'] sees the new value right away — without this the
      // 2-minute staleTime means the old colour lingers until a refetch,
      // which is why the theme only "stuck" after a full page refresh.
      qc.setQueryData(['config', 'theme'], color)
      // Notify the rest of the app (App.tsx, Sidebar, etc.) so they repaint
      // immediately, mirroring the hospital-logo-changed pattern.
      window.dispatchEvent(new CustomEvent('hospital-theme-changed', { detail: color }))
      qc.invalidateQueries({ queryKey: ['config', 'theme'] })
      toast({
        title: color ? 'Theme colour saved for this hospital' : 'Theme reset to the default',
        variant: 'success',
      })
    },
    onError: (e: any) =>
      toast({
        title: 'Could not save the theme',
        description: e?.response?.data?.message || e.message,
        variant: 'destructive',
      }),
  })

  const cancel = () => {
    setDraft(savedColor ?? null)
    setCustomHex(savedColor ?? '')
    applyTheme(savedColor ?? null)
  }

  if (isLoading) return null

  const activeSwatch = draft ? previewHex(draft) : '#525252'
  const activePreset = THEME_PRESETS.find(
    p => p.color.toLowerCase() === (savedColor ?? '#525252').toLowerCase()
  )

  if (!isAdmin) {
    return (
      <div className="border-t border-gray-150 pt-6 mt-6">
        <div className="flex items-baseline justify-between gap-4 mb-1">
          <h4 className="text-sm font-bold text-gray-800">Theme Colour</h4>
        </div>
        <p className="text-sm text-gray-500 mb-2 max-w-xl">
          Applies to everyone at this hospital and is remembered next time they sign in.
        </p>
        <p className="text-xs text-amber-600 font-semibold mb-4">
          Hospital theme can only be updated centrally by the hospital administrator.
        </p>
        <div className="flex items-center gap-3">
          <div
            className="w-10 h-10 rounded-lg border border-gray-200 shadow-sm shrink-0"
            style={{ backgroundColor: activeSwatch }}
          />
          <div>
            <p className="text-xs font-semibold text-gray-800">
              {activePreset ? activePreset.name : (savedColor ? 'Custom colour' : 'Default (Slate)')}
            </p>
            <p className="text-xs font-mono text-gray-500">
              {savedColor || '#525252'}
            </p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="border-t border-gray-150 pt-6 mt-6">
      <div className="flex items-baseline justify-between gap-4 mb-1">
        <h4 className="text-sm font-bold text-gray-800">Theme Colour</h4>
        {dirty && <span className="text-xs font-semibold text-amber-700">Previewing — not saved</span>}
      </div>
      <p className="text-sm text-gray-500 mb-4 max-w-xl">
        Applies to everyone at this hospital and is remembered next time they sign in.
        Selecting a colour previews it straight away; it is only kept when you save.
      </p>

      <div className="flex flex-wrap gap-2.5 mb-4">
        {THEME_PRESETS.map(p => {
          const selected = (draft ?? '').toLowerCase() === p.color.toLowerCase()
          return (
            <button
              key={p.color}
              type="button"
              onClick={() => { preview(p.color); setCustomHex(p.color) }}
              title={p.name}
              aria-label={`Use ${p.name}`}
              aria-pressed={selected}
              className={
                'w-11 h-11 rounded-lg border-2 transition-transform focus:outline-none ' +
                'focus:ring-2 focus:ring-offset-2 focus:ring-gray-400 ' +
                (selected ? 'border-gray-800 scale-105' : 'border-gray-200 hover:scale-105')
              }
              style={{ backgroundColor: previewHex(p.color) }}
            />
          )
        })}
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <div>
          <label className="block text-xs font-semibold text-gray-600 mb-1">Custom colour</label>
          <div className="flex items-center gap-2">
            <input
              type="color"
              value={activeSwatch}
              onChange={e => { setCustomHex(e.target.value); preview(e.target.value) }}
              className="w-11 h-10 rounded-lg border border-gray-200 bg-white p-1 cursor-pointer"
              aria-label="Pick a custom colour"
            />
            <input
              type="text"
              value={customHex}
              placeholder="#0f6b57"
              maxLength={7}
              onChange={e => {
                const v = e.target.value
                setCustomHex(v)
                if (isValidHex(v)) preview(v.toLowerCase())
              }}
              className="w-32 px-3 py-2 border border-gray-200 rounded-lg text-sm font-mono bg-gray-50 focus:outline-none focus:ring-2 focus:ring-gray-400 focus:bg-white"
            />
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => save.mutate()}
            disabled={!dirty || save.isPending}
            className="px-5 py-2 text-sm font-semibold text-white bg-neutral-600 rounded-lg hover:bg-neutral-700 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {save.isPending ? 'Saving…' : 'Save Theme'}
          </button>
          {dirty && (
            <button
              type="button"
              onClick={cancel}
              className="px-4 py-2 text-sm font-semibold text-gray-600 hover:text-gray-800"
            >
              Cancel
            </button>
          )}
          {!dirty && savedColor && (
            <button
              type="button"
              onClick={() => { preview(null); setCustomHex('') }}
              className="px-4 py-2 text-sm font-semibold text-gray-600 hover:text-gray-800"
            >
              Reset to default
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
