import { cn } from '../../../lib/utils'
import {
  TIMELINE_STEPS,
  lockReason,
  unlockedSteps,
  type InsuranceDesk,
  type TimelineStepKey,
} from '../insuranceDesk'

/**
 * The seven-stage progress rail (WO-020 / ID-007).
 *
 * Shows where the claim is, what has been done and when, and what the clerk may
 * open next. Completed steps stay clickable — correcting a fax number after
 * dispatch is routine desk work, and a rail that locks history forces people
 * back to a spreadsheet.
 *
 * The gating logic lives in `insuranceDesk.ts` and is unit-tested; this
 * component only renders its output.
 */
export function StageTimeline({
  desk,
  activeStep,
  onSelect,
}: {
  desk: InsuranceDesk
  activeStep: TimelineStepKey
  onSelect: (step: TimelineStepKey) => void
}) {
  const unlocked = unlockedSteps(desk)

  return (
    <nav aria-label="Claim stages" className="w-56 shrink-0 border-r border-gray-100 pr-4">
      <ol className="space-y-1">
        {TIMELINE_STEPS.map((step, idx) => {
          const done = Boolean(desk.stageTimestamps[step.key])
          const open = unlocked[step.key]
          const active = activeStep === step.key
          const reason = lockReason(step.key, desk)

          return (
            <li key={step.key}>
              <button
                type="button"
                onClick={() => open && onSelect(step.key)}
                disabled={!open}
                title={reason ?? undefined}
                aria-current={active ? 'step' : undefined}
                className={cn(
                  'w-full text-left px-3 py-2 rounded-lg transition-colors flex items-start gap-2.5',
                  active && 'bg-neutral-100',
                  !active && open && 'hover:bg-gray-50',
                  !open && 'opacity-40 cursor-not-allowed',
                )}
              >
                {/* The marker is a real sequence indicator: these stages happen
                    in order and the number is the order. */}
                <span
                  className={cn(
                    'mt-0.5 w-5 h-5 shrink-0 rounded-full border text-[10px] font-semibold flex items-center justify-center',
                    done
                      ? 'bg-green-600 border-green-600 text-white'
                      : active
                        ? 'border-neutral-500 text-neutral-700'
                        : 'border-gray-300 text-gray-400',
                  )}
                  aria-hidden="true"
                >
                  {done ? '✓' : idx + 1}
                </span>
                <span className="min-w-0">
                  <span
                    className={cn(
                      'block text-xs font-medium truncate',
                      active ? 'text-neutral-900' : done ? 'text-gray-700' : 'text-gray-500',
                    )}
                  >
                    {step.label}
                  </span>
                  {done && (
                    <span className="block text-[10px] text-gray-400 mt-0.5">
                      {new Date(desk.stageTimestamps[step.key]!).toLocaleDateString('en-IN', {
                        day: '2-digit',
                        month: 'short',
                        year: 'numeric',
                      })}
                    </span>
                  )}
                </span>
              </button>
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
