# Prompt Template

Use this template to start a new engineering session:

---
You are continuing work on the Flyway Digital project.

Read these files first:
1. `.ai/context.md`
2. `.ai/constraints.md`
3. `.ai/summary.md`
4. `.ai/current-task.md`
5. `.ai/decisions.md`

Execution requirements:
- Keep Java 8+ compatibility.
- Do not introduce unnecessary dependencies.
- Respect module release boundaries.
- If changes affect architecture/behavior, update `.ai/decisions.md`.
- If release is involved, sync versions, run tests, deploy target modules, update `.ai`, then commit.

Current baseline:
- Released version: `1.3.6.1`
- Latest focus: SqlExecutor namespace handling, risk validation tests, and release consistency.
---