# cloud-itonami-isco-3111

Open Occupation Blueprint for **ISCO-08 3111**: Chemical and Physical Science Technicians.

This repository designs a forkable OSS lab support operation for analytical chemistry and materials science: an autonomous lab advisor proposes routine test protocols, sample analyses, equipment calibration, and result logging under a governor-gated actor, ensuring test protocol integrity, sample verification, and human-in-the-loop escalation for out-of-specification results and novel findings.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here an autonomous lab advisor proposes test protocol execution, sample analysis, result logging, and equipment calibration under an actor that gates all proposals and an independent **Lab Technician Governor** that enforces analytical integrity. The governor never
dispatches lab automation itself; `:high`/`:safety-critical` actions (such as
flagging out-of-specification results, or proposing calibration procedures) require human sign-off.

## Core Contract

```text
sample + test protocol + equipment + analytical timeline
        |
        v
Lab Advisor -> Lab Technician Governor -> run-test-protocol/log-result/flag/calibrate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or finalize a result without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `3111`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

## Reference implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors
section, alongside `cloud-itonami-isco-2111`, and other occupation actors): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph`, with the Lab Advisor and Lab Technician Governor as distinct graph nodes and
human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/chemistry/store.cljc` — `Store` protocol + `MemStore`:
  registered samples, test protocols, equipment, committed records, an append-only audit ledger.
- `src/chemistry/advisor.cljc` — `Advisor` protocol; `mock-advisor`
  (deterministic, default) proposes a lab operation from a
  request; `llm-advisor` wraps a `langchain.model/ChatModel` — either
  way the advisor only ever produces a `:propose`-effect proposal,
  never a committed record, and LLM parse failures always yield
  `:confidence 0.0` (forces escalation, never fabricated confidence).
- `src/chemistry/governor.cljc` — `LabTechnicianGovernor/check`: a pure
  function, wired as its own `:govern` node. Hard invariants
  (unregistered sample, missing test protocol, a proposal whose
  `:effect` isn't `:propose`, finalized claims in draft results)
  always route to `:hold`. Escalation invariants (`:flag-out-of-spec-result`,
  or low advisor confidence) always route to
  `:request-approval` — an `interrupt-before` node that the graph
  checkpoints and only resumes on explicit human approval
  (`actor/approve!`), matching the README's robotics-premise statement
  that out-of-spec flagging always requires
  human sign-off.
- `src/chemistry/actor.cljc` — `build-graph`, `run-request!`,
  `approve!`: the `langgraph.graph/state-graph` wiring itself.

Proposal operations (advisor-only, all `:effect :propose`):
- `:run-test-protocol` — execute a pre-approved, registered test protocol on a sample.
- `:log-result` — log a test result to the record.
- `:flag-out-of-spec-result` — surface a result outside expected tolerance (escalates).
- `:calibrate-equipment` — propose a calibration procedure run.

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation).

## License

AGPL-3.0-or-later.
