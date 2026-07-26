# Agent Rollout Runbook

Operational procedure for taking the AI agent layer live, and for taking it down
again. Written for whoever is on call, not for whoever built it.

## 1. The kill switch

**If patients are being harmed, confused, or given wrong information, stop first
and investigate second.**

```
Settings → Agent → Rollout → Kill switch → ON
```

Effect is immediate and tenant-wide. Every channel stops; in-flight
conversations escalate to the Copilot queue rather than being dropped, so nobody
is left mid-sentence. It does not require a deploy, a restart, or an engineer.

Equivalent if the UI is unavailable:

```sql
UPDATE agent_rollout SET kill_switch = true WHERE tenant_id = :tenant;
```

Turning it back on is a deliberate act with a written reason. If you cannot say
what was fixed, it is not fixed.

## 2. The stages

Promotion is one step at a time, lowest risk first. Each stage must clear its
evidence bar before the next is considered.

| Stage | What is live | Agreement bar | Min scored proposals |
|---|---|---|---|
| `off` | nothing | — | — |
| `shadow` | reads and proposes; **executes nothing** | — | — |
| `whatsapp_scheduling` | WhatsApp booking | 90% | 50 per tool |
| `voice_reception` | inbound voice | 93% | 50 per tool |
| `claims_automation` | NHCX / TPA claims | 97% | 50 per tool |

Claims is strictest because a wrong claim costs the hospital money and the
patient time, and unwinding one is slow.

**Skipping a stage is refused by the system**, not merely discouraged. Skipping
means enabling a channel whose agreement rate was never measured, which defeats
the purpose of a phased rollout.

## 3. Promoting a stage

1. Run the scoring job over the shadow proposals for the target stage.
2. Read the blockers list. Do not read only the headline percentage.
3. Resolve every blocker. In particular:
   - **Missed escalations block promotion outright.** A conversation that should
     have reached a human and did not is a safety failure, and a good average
     does not offset it.
   - **Insufficient evidence is not success.** Three perfect samples is
     unmeasured, not ready.
4. Promote one stage. Watch for a week before considering the next.

## 4. What to watch after go-live

| Signal | Where | Act when |
|---|---|---|
| HITL queue depth | `hms_agent_hitl_pending` | > 20, or oldest > 30 min |
| Escalation timeouts | `hms_agent_hitl_timeouts_total` | any sustained increase |
| Confidence distribution | `hms_agent_confidence` | p50 drops sharply — usually a prompt or model regression |
| Voice latency | `hms_agent_voice_turn_latency_seconds` | p95 > 1.8s |
| Consent refusals | `hms_consent_checks_total{outcome="absent"}` | spike usually means a capture step broke, not that patients changed their minds |
| Tool error ratio | `hms_agent_tool_invocations_total` | > 10% over 15 min |
| NHCX pending | `hms_nhcx_pending_gauge` | rising for 30 min |

## 5. Common incidents

**Queue backing up.** Usually staffing, not software. Check the Copilot is
actually staffed for the hours the channel is open. A 24/7 voice line behind a
9-to-5 escalation desk will fail every night.

**Agent booking the wrong slots.** Roll back to `shadow` — it keeps producing
proposals so you can measure the problem without patients experiencing it.

**Consent refusals spiking.** Check whether a registration flow stopped capturing
consent. The agent is behaving correctly; the capture step is what broke.

**A patient asks to be forgotten.** Settings → Compliance → Erasure requests.
Confirm every target reports a terminal outcome. **A target still `PENDING` means
the erasure is incomplete**, whatever the request status says.

**Agent credential leaked.** Settings → Agent API tokens → Revoke. Immediate.
Issue a replacement and update the agent service configuration.

## 6. Before the first real patient

- [ ] Shadow mode has run long enough to clear the evidence bar for the target stage
- [ ] Copilot queue is staffed for the hours the channel is open
- [ ] Escalation deadline is shorter than the patient's patience, not longer
- [ ] Consent capture is live in registration, in the patient's language
- [ ] An erasure request has been run end to end at least once, in a test tenant
- [ ] Kill switch has been tested by someone who was not told how it works
- [ ] Alert routing reaches a human at 2am, verified by an actual page
- [ ] Someone other than the author has read this runbook
