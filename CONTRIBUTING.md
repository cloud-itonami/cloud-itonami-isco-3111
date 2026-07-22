# Contributing

`cloud-itonami-isco-3111` accepts contributions to the OSS actor, policy tests,
documentation, examples and open occupation blueprint.

## Development

```bash
clojure -M:test
```

Keep changes small and include tests for governor, store, advisor or ledger
behavior.

## Rules

- Do not commit real sample data, credentials or lab operating documents.
- Keep production writes and result finalization behind Lab Technician Governor.
- Treat this occupation's workflows as high-risk: add tests for sample
  provenance, protocol verification, calibration and audit logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which policy invariant is affected
- how it was tested
- whether operator or certification docs need updates
