# .ai Directory Guide

Project: Flyway Digital
Current Version: 1.3.6.1
Last Updated: 2026-03-06

This directory stores persistent AI collaboration context for this repository.

## Files
- `context.md`: project background, architecture, version baseline
- `constraints.md`: hard constraints and release rules
- `current-task.md`: current execution state and checklist
- `decisions.md`: architecture decisions (ADR style)
- `roadmap.md`: milestone plan
- `summary.md`: latest stage summary and handover context
- `prompt-template.md`: reusable startup prompt template
- `USAGE.md`: how to use and maintain this directory

## Maintenance Rules
- Keep versions consistent with root and module `pom.xml`.
- Update `summary.md` and `current-task.md` after every release.
- Record non-trivial behavior changes in `decisions.md`.
- Do not leave stale target version references.