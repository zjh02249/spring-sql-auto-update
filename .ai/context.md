# Project Context

## Overview
- Name: Flyway Digital
- Positioning: Lightweight Flyway-compatible SQL migration tool
- Current released version: `1.3.6.1`
- Last release date: `2026-03-06`

## Core Capabilities
- Semantic migration version ordering
- CRC32 checksum validation
- Transactional script execution
- Baseline-on-migrate support
- Spring Boot starter auto-configuration (2.x/3.x compatible)
- Dynamic datasource integration

## Module Structure
- `flyway-digital-core`: migration engine and runtime logic
- `flyway-digital-spring-boot-starter`: Spring Boot integration
- `flyway-digital-samples`: usage demos, non-publishable modules

## Current Release Notes (1.3.6.1)
- Replaced `SqlExecutor` implementation to strengthen namespace handling.
- Added savepoint-based namespace switch safety path.
- Added risk validation tests for namespace identifier strictness.
- Full test suite passed before release.
- Core and starter successfully published to Maven repository.