# GSD Debug Knowledge Base

Resolved debug sessions. Used by `gsd-debugger` to surface known-pattern hypotheses at the start of new investigations.

---

## duplicate-default-clause — duplicate default clause in switch breaks Vite transform
- **Date:** 2026-08-13
- **Error patterns:** PARSE_ERROR, default clause, switch statement, vite:oxc, Transform failed, useSSEStream.ts, SSE stream
- **Root cause:** Commit 4402ee7's hand-edit of useSSEStream.ts duplicated the `default:` clause in the `switch (eventName)` statement (inserted a new `default:` at line 131 above the existing one from the initial commit at line 132) and de-indented `case 'ToolCallEvent':` (line 125) in the same edit. oxc parser rejects a second `default:` → Vite transform fails at parse.
- **Fix:** Removed the duplicate `default:` line in the switch; restored the 10-space indentation on `case 'ToolCallEvent':` (matching sibling cases).
- **Files changed:** frontend/src/composables/useSSEStream.ts
---
