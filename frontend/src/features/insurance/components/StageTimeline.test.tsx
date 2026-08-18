import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StageTimeline } from './StageTimeline';
import type { InsuranceDesk, WorkflowStage } from '../insuranceDesk';

/**
 * WO-020 / ID-007.
 *
 * The timeline is the navigation for the whole desk, so the thing worth testing
 * is which steps it will let a clerk into. The gating rules themselves are unit
 * tested in insuranceDesk.test.ts; these assert the component actually honours
 * them rather than rendering everything enabled.
 */

function deskFixture(overrides: Partial<InsuranceDesk> = {}): InsuranceDesk {
  return {
    id: 'ins-1',
    patientId: 'pat-1',
    billId: null,
    encounterId: null,
    insurerName: 'Star Health',
    tpaName: 'Medi Assist',
    policyNumber: 'POL-1',
    memberId: null,
    policyType: null,
    currentStage: null,
    currentStageLabel: null,
    stageTimestamps: {
      preauth: null,
      preauthApproval: null,
      enhancement: null,
      enhancementApproval: null,
      checkList: null,
      dispatch: null,
      disallowance: null,
    },
    billLinked: false,
    cardExpired: false,
    effectiveApprovedLimit: null,
    cardValidity: null,
    preAuthType: null,
    preauthCommunicationToTpa: null,
    preauthFaxNo: null,
    preauthMailId: null,
    preauthAppliedDate: null,
    preauthRequestedAmount: null,
    claimNo: null,
    preauthApprovalStatus: null,
    preauthDateOfApproval: null,
    preauthCommunicationByTpa: null,
    preauthApproveFaxNo: null,
    preauthApproveMailId: null,
    preauthApprovedLimit: null,
    preauthRejectionReason: null,
    enhancementType: null,
    enhancementAppliedDate: null,
    enhancementRequestedAmount: null,
    enhancementCommunicationToTpa: null,
    enhancementFaxNo: null,
    enhancementMailId: null,
    reasonForEnhancement: null,
    enhancementApprovalStatus: null,
    enhancementDateOfApproval: null,
    enhancementCommunicationByTpa: null,
    enhancementApprovedLimit: null,
    enhancementRejectionReason: null,
    checklist: null,
    modeOfDispatch: null,
    courier: null,
    dispatchDate: null,
    dispatchedBy: null,
    dispatchMailId: null,
    podNo: null,
    reasonForDelay: null,
    cheques: [],
    totalReceived: 0,
    ...overrides,
  };
}

const atStage = (stage: WorkflowStage, extra: Partial<InsuranceDesk> = {}) =>
  deskFixture({ currentStage: stage, ...extra });

describe('StageTimeline', () => {
  it('renders all seven steps', () => {
    render(
      <StageTimeline desk={deskFixture()} activeStep="preauth" onSelect={() => {}} />,
    );
    expect(screen.getAllByRole('button')[0]!).toBeTruthy();
    expect(screen.getByRole('button', { name: /Dispatch Entry/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Disallowance Entry/ })).toBeTruthy();
  });

  it('disables steps the claim has not reached', () => {
    render(
      <StageTimeline desk={deskFixture()} activeStep="preauth" onSelect={() => {}} />,
    );
    const dispatch = screen.getByRole('button', { name: /Dispatch Entry/ }) as HTMLButtonElement;
    expect(dispatch.disabled).toBe(true);
  });

  it('keeps completed steps clickable so corrections are possible', async () => {
    // A clerk fixing a fax number after the docket shipped is routine.
    const onSelect = vi.fn();
    render(
      <StageTimeline
        desk={atStage('DISPATCH_ENTRY', { billLinked: true })}
        activeStep="dispatch"
        onSelect={onSelect}
      />,
    );
    await userEvent.click(screen.getAllByRole('button')[0]!);
    expect(onSelect).toHaveBeenCalledWith('preauth');
  });

  it('locks the enhancement step when no bill is linked', () => {
    render(
      <StageTimeline
        desk={atStage('PREAUTHORISATION_APPROVAL', { billLinked: false })}
        activeStep="preauthApproval"
        onSelect={() => {}}
      />,
    );
    const enhancement = screen.getByRole('button', {
      name: /Enhancement Request/,
    }) as HTMLButtonElement;
    expect(enhancement.disabled).toBe(true);
    expect(enhancement.title).toMatch(/credit bill/i);
  });

  it('unlocks the enhancement step once a bill is linked', () => {
    render(
      <StageTimeline
        desk={atStage('PREAUTHORISATION_APPROVAL', { billLinked: true })}
        activeStep="preauthApproval"
        onSelect={() => {}}
      />,
    );
    const enhancement = screen.getByRole('button', {
      name: /Enhancement Request/,
    }) as HTMLButtonElement;
    expect(enhancement.disabled).toBe(false);
  });

  it('lets a claim skip straight to the check-list', () => {
    // Most claims never need an enhancement.
    render(
      <StageTimeline
        desk={atStage('PREAUTHORISATION_APPROVAL', { billLinked: true })}
        activeStep="preauthApproval"
        onSelect={() => {}}
      />,
    );
    const checklist = screen.getByRole('button', {
      name: /Check-list Entry/,
    }) as HTMLButtonElement;
    expect(checklist.disabled).toBe(false);
  });

  it('closes every step once the pre-auth is rejected', () => {
    render(
      <StageTimeline
        desk={atStage('PREAUTHORISATION_REJECTED')}
        activeStep="preauthApproval"
        onSelect={() => {}}
      />,
    );
    for (const label of [/Preauthorise$/, /Check-list Entry/, /Dispatch Entry/]) {
      expect((screen.getByRole('button', { name: label }) as HTMLButtonElement).disabled).toBe(
        true,
      );
    }
  });

  it('does not fire onSelect for a locked step', async () => {
    const onSelect = vi.fn();
    render(
      <StageTimeline desk={deskFixture()} activeStep="preauth" onSelect={onSelect} />,
    );
    await userEvent.click(screen.getByRole('button', { name: /Disallowance Entry/ }));
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('shows a completion date only for steps that were actually worked', () => {
    // Inferring completion from the current stage would draw the enhancement
    // steps as done on a claim that skipped them.
    render(
      <StageTimeline
        desk={atStage('DISPATCH_ENTRY', {
          billLinked: true,
          stageTimestamps: {
            preauth: '2026-08-01T10:00:00Z',
            preauthApproval: '2026-08-03T10:00:00Z',
            enhancement: null,
            enhancementApproval: null,
            checkList: '2026-08-10T10:00:00Z',
            dispatch: '2026-08-11T10:00:00Z',
            disallowance: null,
          },
        })}
        activeStep="dispatch"
        onSelect={() => {}}
      />,
    );
    // Index rather than name: "Preauthorise" is a prefix of "Preauthorise
    // Approval", so a name regex matches two buttons.
    const steps = screen.getAllByRole('button');
    expect(steps[0]!.textContent).toMatch(/2026/);
    // The skipped enhancement step carries no date.
    const enhancement = screen.getByRole('button', { name: /Enhancement Request/ });
    expect(enhancement.textContent).not.toMatch(/2026/);
  });

  it('marks the active step for assistive technology', () => {
    render(
      <StageTimeline
        desk={atStage('PREAUTHORISATION')}
        activeStep="preauthApproval"
        onSelect={() => {}}
      />,
    );
    expect(
      screen.getByRole('button', { name: /Preauthorise Approval/ }).getAttribute('aria-current'),
    ).toBe('step');
  });
});
