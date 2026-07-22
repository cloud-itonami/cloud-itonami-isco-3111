# Operator Guide

## First Deployment

1. Register the lab's samples, test protocols and equipment before enabling
   the actor for that lab.
2. Define consent and chain-of-custody categories for sample provenance.
3. Run synthetic operating cases (`clojure -M:test`).
4. Enable human-reviewed sign-off for out-of-spec flags and low-confidence
   proposals.
5. Measure operating outcomes and audit coverage.

## Minimum Production Controls

- sample/protocol/equipment registration log
- out-of-spec escalation path (always human sign-off, never auto-dismissed)
- provenance for all committed analytical records
- human review for low-confidence advisor proposals
- audit export for all gated actions

## Certification

Certified operators must prove that the Lab Technician Governor gates every
test-protocol run, result log and calibration proposal, and that out-of-spec
results always escalate to humans.
