import { useQuery } from '@tanstack/react-query'
import { BadgeCheck } from 'lucide-react'

import { abhaApi } from '../../../services/abha/abhaApi'
import { isVerified } from '../types'

interface Props {
  patientId: string
  /** Render nothing at all when the patient has no ABHA, rather than a placeholder. */
  hideWhenAbsent?: boolean
}

/**
 * The verified-ABHA badge on the patient master.
 *
 * <p>Shows the masked number only. A badge is a recognition aid, and putting a
 * full national health ID into a screen that sits open at a reception desk all
 * day is how it ends up in a photograph.
 *
 * <p>Silent while loading and silent on error: a patient record must render
 * whether or not ABDM is reachable, and an error banner here would imply
 * something is wrong with the patient rather than with a gateway.
 */
export default function AbhaVerifiedBadge({ patientId, hideWhenAbsent = true }: Props) {
  const { data: linkage } = useQuery({
    queryKey: ['abha', 'linked', patientId],
    queryFn: () => abhaApi.linkedFor(patientId),
    retry: false,
  })

  if (!isVerified(linkage)) {
    return hideWhenAbsent ? null : (
      <span className="inline-flex items-center rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-neutral-500">
        No ABHA
      </span>
    )
  }

  return (
    <span
      className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700"
      title={linkage!.abhaAddress ?? 'ABHA verified'}
    >
      <BadgeCheck size={13} className="shrink-0" />
      {linkage!.abhaNumberMasked}
    </span>
  )
}
