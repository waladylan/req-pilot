# WFM Test Case Quality Report

## What Was Tested

- Requirement text to WFM v1 generation.
- WFM normalization and validation.
- WFM to backward-compatible flowchart mapping.
- WFM path extraction, including retry-loop protection.
- WFM-based test case generation.
- Test case quality checks for required fields, duplicate IDs/titles, generic titles, empty steps, and semantic coverage.
- `generate-testcases` compatibility for WFM-first requests.

## Samples

| Sample | Generated test cases | Covered paths |
| --- | ---: | --- |
| Delete Product | 2 | success, cancel |
| User Login | 6 | success, missing information, invalid credentials, retry, cancel, failure |
| Checkout Order | 6 | success, invalid address, payment failure, retry, cancel |
| Approval Workflow | 4 | approved, rejected, missing documents |
| Password Reset | 4 | reset link sent, email not found, email service failure |

## Known Limitations

- The rule-based parser still uses keyword and line-structure heuristics, so complex natural language can be misclassified.
- Nested decisions are represented as separate branches in this version rather than a fully nested AST.
- Retry loops are preserved as meaningful paths but traversal is capped with a max depth to prevent infinite loops.
- AI integration is intentionally not implemented; future AI output should target WFM v1 JSON directly.

## Remaining Improvements

- Add richer nested-branch parsing once WFM editing patterns stabilize.
- Expand multilingual keyword coverage beyond the current English/Vietnamese heuristics.
- Add UI-level component tests around loading and API error states when a browser test setup is introduced.
