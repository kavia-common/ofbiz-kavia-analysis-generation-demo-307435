# Apache OFBiz — Enterprise Documentation (Phase 2)

## Purpose and scope

This folder contains Phase 2, enterprise-quality documentation generated from a static analysis of the Apache OFBiz codebase under `ofbiz-framework-307435/`. The OFBiz repository is treated as read-only; no OFBiz source files were modified.

Phase 2 documentation is intentionally derived from:

1. The Phase 1 analysis outputs in `docs/analysis/`, and
2. Direct inspection of key OFBiz runtime, configuration, and orchestration sources (startup, web control, service engine, and container wiring).

## How to use these docs

If you are new to OFBiz in this workspace, start with the High-Level Design (HLD) to understand the major subsystems and runtime boundaries, then proceed to the Low-Level Design (LLD) and the operational guides.

For deeper “evidence” and code-level corroboration, refer to the Phase 1 analysis set, which contains additional file-by-file references and repository structure context.

## Documents

### Architecture

The High-Level Design explains OFBiz as a component-driven platform composed of containers that host the web layer, service engine, and entity engine.

See: `architecture/01-high-level-design-hld.md`.

The Low-Level Design dives into the concrete control flows and major classes used for startup, HTTP routing, service execution, and orchestration.

See: `architecture/02-low-level-design-lld.md`.

Component interaction flows document the runtime sequences that connect HTTP requests, controller configuration, event handlers, services, and persistence.

See: `architecture/03-component-interaction-flows.md`.

Service orchestration overview focuses on how OFBiz composes services, applies ECA hooks, manages transactions, schedules background jobs, and runs “group” services.

See: `architecture/04-service-orchestration-overview.md`.

### Operational

Configuration and environment setup explains how runtime behavior is configured via `start.properties` (and variants), container/component descriptors, `serviceengine.xml`, and (optionally) Docker entrypoint automation.

See: `operational/01-configuration-and-environment-setup.md`.

Runtime execution guide explains how OFBiz starts, which containers load for a given runtime “loader” set, how shutdown works, and what to validate when boot issues occur.

See: `operational/02-runtime-execution-guide.md`.

Dependencies and integration points describe where OFBiz integrates with external systems (database, embedded Tomcat, RMI/JMS) and how those integration points are configured.

See: `operational/03-dependencies-and-integration-points.md`.

### Business mapping

Business capability to module mapping connects common ERP/e-commerce capabilities to OFBiz components in this repository, with concrete evidence from `ofbiz-component.xml` descriptors.

See: `business/01-business-capability-to-module-mapping.md`.

E-commerce and ERP workflows provide a cross-domain explanation of how OFBiz typically executes core business processes using the web control layer, services, and entities.

See: `business/02-ecommerce-and-erp-workflows.md`.

## Phase 1 analysis (inputs)

Phase 1 documents are located in `docs/analysis/` and are referenced throughout Phase 2 for additional detail:

- `docs/analysis/01-codebase-overview-and-statistics.md`
- `docs/analysis/02-module-and-domain-breakdown.md`
- `docs/analysis/03-service-architecture-analysis.md`
- `docs/analysis/04-data-model-and-entity-relationships.md`
- `docs/analysis/05-execution-and-boot-flow-analysis.md`
- `docs/analysis/README.md`
