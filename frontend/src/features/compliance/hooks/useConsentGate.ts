import { useCallback, useState } from 'react'

import { asConsentRequired } from '../../../services/compliance/complianceApi'
import type {
  ConsentAttestation,
  ConsentRequiredPayload,
} from '../../../types/compliance'

/**
 * Wraps any call that can be refused for want of consent.
 *
 * <p>The cycle is: call, catch a 409, show the notice, capture the attestation,
 * call again with it attached. Every screen that touches ABHA enrolment, policy
 * discovery or pre-auth needs the same cycle, and three hand-rolled copies would
 * drift — one would forget to clear the payload on success and leave the dialog
 * stuck open, another would retry without the attestation and loop.
 *
 * <p>Usage:
 *
 * <pre>
 *   const gate = useConsentGate&lt;AbhaLinkage&gt;()
 *
 *   const start = () =&gt;
 *     gate.run(consent =&gt; abhaApi.start({ ...cmd, consent }))
 *
 *   {gate.payload &amp;&amp; (
 *     &lt;ConsentGateModal
 *       payload={gate.payload}
 *       submitting={gate.retrying}
 *       onAttest={gate.attest}
 *       onClose={gate.cancel}
 *     /&gt;
 *   )}
 * </pre>
 *
 * <p>The callback takes the attestation as its argument rather than the hook
 * holding the request: the caller keeps its own command object, so a retry sends
 * exactly what the first attempt sent plus the consent. A hook that captured and
 * replayed the request would silently resend stale form state.
 */
export function useConsentGate<T>() {
  const [payload, setPayload] = useState<ConsentRequiredPayload | null>(null)
  const [retrying, setRetrying] = useState(false)
  const [pending, setPending] = useState<
    ((consent?: ConsentAttestation) => Promise<T>) | null
  >(null)

  /**
   * Run an action that may be refused.
   *
   * <p>Rethrows anything that is not a consent refusal, so ordinary failures
   * still reach the caller's error handling instead of vanishing into this hook.
   */
  const run = useCallback(
    async (action: (consent?: ConsentAttestation) => Promise<T>): Promise<T | null> => {
      try {
        return await action(undefined)
      } catch (error) {
        const refusal = asConsentRequired(error)
        if (!refusal) throw error

        // Hold the action so `attest` can replay it with the consent attached.
        setPending(() => action)
        setPayload(refusal)
        return null
      }
    },
    [],
  )

  /** Replay the held action with the desk's attestation attached. */
  const attest = useCallback(
    async (attestation: ConsentAttestation): Promise<T | null> => {
      if (!pending) return null
      setRetrying(true)
      try {
        const result = await pending(attestation)
        setPayload(null)
        setPending(null)
        return result
      } finally {
        // Cleared even on failure, or a server-side rejection of the
        // attestation would leave the button spinning with no way back.
        setRetrying(false)
      }
    },
    [pending],
  )

  /** The patient declined. Drops the held action without retrying it. */
  const cancel = useCallback(() => {
    setPayload(null)
    setPending(null)
    setRetrying(false)
  }, [])

  return { run, attest, cancel, payload, retrying }
}
