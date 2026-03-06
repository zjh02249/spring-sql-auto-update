# .ai Usage

## Goal
Keep long-running project context stable across sessions and collaborators.

## Standard Workflow
1. Read `context.md`, `constraints.md`, `summary.md` before development.
2. Update `current-task.md` while implementing.
3. If behavior or architecture changes, append ADR in `decisions.md`.
4. Before release: update versions, run tests, deploy, refresh `.ai` files.
5. Commit code + docs together.

## Release Checklist
1. Update all `pom.xml` versions.
2. Run `mvn test`.
3. Deploy publishable modules:
   `mvn clean deploy -DskipTests -pl flyway-digital-core,flyway-digital-spring-boot-starter -am`
4. Update `.ai` docs to final released version.
5. Commit all changes.

## Current Baseline
- Released version: `1.3.6.1`
- Release date: `2026-03-06`