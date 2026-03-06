# Current Task

Status: Completed
Last Updated: 2026-03-06

## Task Goal
Release version `1.3.6.1` to Maven, synchronize `.ai` documents, and commit changes.

## Completed Items
- [x] Reviewed and updated `SqlExecutor` related tests.
- [x] Added risk-validation unit tests for namespace identifier behavior.
- [x] Updated all module `pom.xml` versions to `1.3.6.1`.
- [x] Ran full tests (`mvn test`) successfully.
- [x] Published to Maven (`core` + `starter`) successfully.
- [x] Updated all files under `.ai` to current release context.

## Notes
- Risk validation confirms strict identifier check fails fast on invalid namespace tokens (e.g. `my-db`).
- Existing project convention states actual db names use underscore style, so current behavior is acceptable.