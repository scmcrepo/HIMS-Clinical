import * as React from 'react'
import { Modal } from './Modal'
import { AlertTriangle, Info, Trash2 } from 'lucide-react'
import { cn } from '../../lib/utils'

export interface ConfirmModalProps {
  isOpen: boolean
  onClose: () => void
  onConfirm: () => void
  title: string
  message: React.ReactNode
  confirmText?: string
  cancelText?: string
  variant?: 'danger' | 'warning' | 'primary'
  isLoading?: boolean
  zIndex?: string
}

export function ConfirmModal({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  variant = 'danger',
  isLoading = false,
  zIndex = 'z-[70]',
}: ConfirmModalProps) {
  const icon = {
    danger: <Trash2 className="w-5 h-5 text-red-600" />,
    warning: <AlertTriangle className="w-5 h-5 text-amber-600" />,
    primary: <Info className="w-5 h-5 text-blue-600" />,
  }[variant]

  const iconBg = {
    danger: 'bg-red-50 text-red-600 border border-red-100',
    warning: 'bg-amber-50 text-amber-600 border border-amber-100',
    primary: 'bg-blue-50 text-blue-600 border border-blue-100',
  }[variant]

  const confirmBtnBg = {
    danger: 'bg-red-600 hover:bg-red-700 text-white focus:ring-red-500',
    warning: 'bg-amber-600 hover:bg-amber-700 text-white focus:ring-amber-500',
    primary: 'bg-neutral-900 hover:bg-neutral-800 text-white focus:ring-neutral-900',
  }[variant]

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      size="md"
      zIndex={zIndex}
      showCloseButton={!isLoading}
    >
      <div className="p-6 space-y-4">
        <div className="flex items-start gap-3.5">
          <div className={cn('w-10 h-10 rounded-xl flex items-center justify-center shrink-0 mt-0.5 shadow-xs', iconBg)}>
            {icon}
          </div>
          <div className="space-y-1.5 flex-1 min-w-0">
            <h3 className="text-base font-bold text-neutral-900">{title}</h3>
            <div className="text-xs text-neutral-600 leading-relaxed">{message}</div>
          </div>
        </div>

        <div className="flex items-center justify-end gap-2.5 pt-4 border-t border-neutral-100">
          <button
            type="button"
            onClick={onClose}
            disabled={isLoading}
            className="px-4 py-2 text-xs font-semibold text-neutral-700 hover:bg-neutral-100 rounded-lg transition-colors cursor-pointer border border-neutral-200 disabled:opacity-50"
          >
            {cancelText}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isLoading}
            className={cn(
              'px-4 py-2 text-xs font-semibold rounded-lg transition-colors cursor-pointer shadow-xs disabled:opacity-50',
              confirmBtnBg
            )}
          >
            {isLoading ? 'Processing...' : confirmText}
          </button>
        </div>
      </div>
    </Modal>
  )
}

export default ConfirmModal
