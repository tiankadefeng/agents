---
slug: duplicate-default-clause
status: resolved
trigger: |
  [plugin:vite:oxc] Transform failed with 1 error:
  [PARSE_ERROR] A 'default' clause cannot appear more than once in a 'switch' statement.
  src/composables/useSSEStream.ts:131:11 — first default at 131, another at 132.
  frontend/src/composables/useSSEStream.ts
created: 2026-08-13
updated: 2026-08-13
---

# Debug Session: duplicate-default-clause

## Trigger (verbatim)
```
[plugin:vite:oxc] Transform failed with 1 error:
[PARSE_ERROR] A 'default' clause cannot appear more than once in a 'switch' statement.
     ╭─[ src/composables/useSSEStream.ts:131:11 ]
 131 │               default:
 132 │           default:
─────╯
/Volumes/D/dev/workspace/AI/agents/frontend/src/composables/useSSEStream.ts
```

## Symptoms
- **Expected**: Vite dev server transforms `frontend/src/composables/useSSEStream.ts` without error; SSE stream routes events by event name.
- **Actual**: Vite/oxc transform fails at parse — duplicate `default:` clause in the `switch (eventName)` statement (lines 131-132).
- **Error message**: `[PARSE_ERROR] A 'default' clause cannot appear more than once in a 'switch' statement.` (provided verbatim in trigger).
- **Timeline**: Regression introduced recently (file is mid-refactor for Phase 5 ReAct tool-event routing); likely a bad merge/conflict resolution.
- **Reproduction**: Run the frontend dev server (Vite) — transform of `useSSEStream.ts` fails immediately.

## Current Focus
hypothesis: CONFIRMED — commit 4402ee7 hand-edit duplicated `default:` and de-indented `case 'ToolCallEvent':` in useSSEStream.ts.
test: Vite transform of useSSEStream.ts (original reproduction path)
expecting: PASS
next_action: DONE — human verification confirmed fixed in real workflow; session archived.

## Evidence
- timestamp: 2026-08-13
  checked: frontend/src/composables/useSSEStream.ts lines 114-136
  found: switch (eventName) has `case 'ToolCallEvent':` at line 125 with zero indentation (all sibling cases have 10 spaces), and two consecutive `default:` lines at 131-132.
  implication: duplicate default is the exact parse error; unindented case is same-commit damage.

- timestamp: 2026-08-13
  checked: git blame -L 124,133 frontend/src/composables/useSSEStream.ts
  found: line 125 + line 131 from commit 4402ee7 (2026-08-13 17:42:44); line 132 + 133 from e958f56 (2026-08-07, init); lines 126-130 from 4ed7776 (2026-08-13 16:32).
  implication: NOT a merge artifact — a single bad hand-edit in 4402ee7. 4402ee7 added the duplicate default and stripped the case indent in the same edit.

- timestamp: 2026-08-13
  checked: git show 4402ee7 -- frontend/src/composables/useSSEStream.ts
  found: diff shows `-          case 'ToolCallEvent':` → `+case 'ToolCallEvent':`, and `+          default:` added directly above the existing `default:` (which came from e958f56). The commit also deleted the `// Phase 5: ToolCallEvent / ToolResultEvent routing` comment.
  implication: root cause confirmed — malformed hand-edit in 4402ee7, not a merge conflict artifact.

- timestamp: 2026-08-13
  checked: .planning/debug/knowledge-base.md
  found: file does not exist (first debug session, no prior known-pattern matches).
  implication: no KB candidates to test.

- timestamp: 2026-08-13
  checked: FIX APPLIED — removed duplicate `default:` (line 131), restored 10-space indent on `case 'ToolCallEvent':` (line 125) in frontend/src/composables/useSSEStream.ts
  found: switch statement now has single `default:`; all case clauses correctly indented.
  implication: fix addresses both artifacts of the same botched commit.

- timestamp: 2026-08-13
  checked: `npx oxlint src/composables/useSSEStream.ts` (oxc parser — same parser family as Vite 8's transform)
  found: exit code 0, zero diagnostics. File parses cleanly.
  implication: parse error eliminated at the parser level.

- timestamp: 2026-08-13
  checked: `npx vite build` (full production build, original reproduction path — Vite transform of all modules incl. useSSEStream.ts)
  found: "1598 modules transformed... ✓ built in 567ms", exit code 0. Only pre-existing chunk-size warning (unrelated).
  implication: original reproduction (Vite transform failure) is fixed end-to-end.

- timestamp: 2026-08-13
  checked: human verification in real workflow (user ran `npm run dev` in frontend; Vite dev server starts without the oxc parse error, SSE streaming + ReAct tool events confirmed working)
  found: user confirmed "fix works in real workflow" via coordinator.
  implication: fix verified beyond the automated checks — dev server and streaming behavior confirmed by user.

## Eliminated
- hypothesis: "duplicate default is a git merge/conflict-resolution artifact"
  evidence: git blame + git show 4402ee7 show a single hand-edit introduced both the duplicate `default:` and the de-indented case. No merge commit is involved; the change is attributed entirely to a non-merge commit.
  timestamp: 2026-08-13

## Resolution
- root_cause: Commit 4402ee7's hand-edit of useSSEStream.ts duplicated the `default:` clause in the `switch (eventName)` statement (inserted a new `default:` at line 131 above the existing one from the initial commit at line 132) and de-indented `case 'ToolCallEvent':` (line 125) in the same edit. oxc parser rejects a second `default:` → Vite transform fails at parse.
- fix: Removed the duplicate `default:` line in the switch; restored the 10-space indentation on `case 'ToolCallEvent':` (matching sibling cases).
- verification: `npx oxlint src/composables/useSSEStream.ts` exits 0 with no diagnostics (oxc parse clean); `npx vite build` succeeds — 1598 modules transformed, built in 567ms, exit 0 (original Vite transform failure no longer occurs); user confirmed dev server + SSE streaming + ReAct tool events work in real workflow.
- files_changed: [frontend/src/composables/useSSEStream.ts]
