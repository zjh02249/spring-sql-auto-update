# Architecture Decisions

## ADR-009: SqlExecutor Namespace Handling Refresh
Date: 2026-03-06
Status: Accepted

### Context
A refresh was required for `SqlExecutor` to address namespace restore behavior and improve namespace extraction coverage.

### Decision
- Use namespace-centric extraction (`extractNamespace`) instead of old `extractDatabaseName` naming.
- Expand extraction patterns across UPDATE/DELETE/INSERT/REPLACE/CREATE/ALTER/DROP/TRUNCATE.
- Use JDBC `Savepoint` API for namespace switch rollback safety.
- Always attempt restoration to default namespace in statement-finalization path when switched.

### Consequences
- Better resilience for namespace switch failures and transactional safety.
- Stricter identifier validation may reject non-underscore naming style tokens.
- Added dedicated tests to verify strictness behavior and normal-path success.

## ADR-010: Release 1.3.6.1 Process Baseline
Date: 2026-03-06
Status: Accepted

### Decision
Adopt the enforced release sequence:
1. Version sync
2. Full test run
3. Targeted deploy (core + starter)
4. `.ai` synchronization
5. Git commit

### Consequences
- Higher release consistency and traceability.
- Lower risk of stale docs/version drift.