import * as React from 'react';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { cn } from '../../lib/utils';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  description?: string;
  children: React.ReactNode;
  className?: string;
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '3xl' | '4xl' | 'max';
  showCloseButton?: boolean;
}

const sizeClasses = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
  '2xl': 'max-w-2xl',
  '3xl': 'max-w-3xl',
  '4xl': 'max-w-4xl',
  max: 'max-w-7xl',
};

export function Modal({
  isOpen,
  onClose,
  title,
  description,
  children,
  className,
  size = 'md',
  showCloseButton = true,
}: ModalProps) {
  return (
    <DialogPrimitive.Root open={isOpen} onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-neutral-950/60 backdrop-blur-sm transition-all duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=open]:fade-in-0 data-[state=closed]:fade-out-0" />
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 overflow-y-auto">
          <DialogPrimitive.Content
            className={cn(
              "relative w-full bg-white rounded-2xl shadow-2xl border border-neutral-100 flex flex-col max-h-[90vh] overflow-hidden focus:outline-none transition-all duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=open]:fade-in-0 data-[state=closed]:fade-out-0 data-[state=open]:zoom-in-95 data-[state=closed]:zoom-out-95",
              sizeClasses[size],
              className
            )}
          >
            {/* Visually hidden Title and Description for screen readers */}
            <div className="sr-only">
              <DialogPrimitive.Title>{title || 'Modal Dialog'}</DialogPrimitive.Title>
              <DialogPrimitive.Description>
                {description || 'Interactive overlay container'}
              </DialogPrimitive.Description>
            </div>

            {showCloseButton && (
              <DialogPrimitive.Close className="absolute top-4 right-4 text-neutral-400 hover:text-neutral-600 p-1.5 hover:bg-neutral-50 rounded-lg transition-colors focus:outline-none z-10 cursor-pointer">
                <X size={18} />
              </DialogPrimitive.Close>
            )}

            {children}
          </DialogPrimitive.Content>
        </div>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
