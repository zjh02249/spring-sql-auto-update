# Technical Constraints

Project: Flyway Digital
Last Updated: 2026-03-06
Status: Mandatory

## Language and Runtime
- Java 8+ compatibility is mandatory.
- Do not use Java 9+ only language features.

## Dependency Rules
- `flyway-digital-core` must remain lightweight (JDBC + SLF4J oriented).
- No heavy ORM/framework dependency in core.
- Spring dependencies belong to starter module only.

## SQL Execution Rules
- Framework manages transaction per SQL script.
- SQL scripts should not include manual transaction control statements.
- Keep Flyway-compatible history table behavior.

## Module Publish Rules
- Publish: `flyway-digital-core`, `flyway-digital-spring-boot-starter`
- Do not publish sample modules.
- Version must be synchronized across all module `pom.xml` files.

## Release Rules (AI mandatory)
1. Run `mvn test` before release.
2. Deploy with explicit module list.
3. If deploy fails, do not finalize commit as release-complete state.
4. Update `.ai` files after successful release.
5. Commit all release artifacts and docs together.