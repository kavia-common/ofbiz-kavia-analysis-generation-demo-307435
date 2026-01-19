# Apache OFBiz Phase 1 — Codebase Overview and Statistics

## Scope and constraints

This document summarizes a static, code-driven analysis of the Apache OFBiz codebase in the read-only source workspace at `ofbiz-framework-307435/`. All findings are derived from source files and configuration artifacts; no source files were modified.

## What Apache OFBiz is (in this repository)

Apache OFBiz is an ERP and e-commerce framework implemented primarily in Java, with significant configuration and extension surfaces in XML and Groovy. The top-level README describes OFBiz as an ERP system providing “libraries, entities, services and features to run all aspects of your business,” and provides a Gradle-based “clean/load/start” workflow for a runnable demo instance.

From an architectural standpoint, the codebase centers around three major pillars:

First, a component system that discovers enabled components from the filesystem and their `ofbiz-component.xml` descriptors. Second, a service-oriented execution layer (“Service Engine”) where business logic is expressed as named services implemented by Java classes, Groovy scripts, entity CRUD automation, or grouped orchestrations. Third, an “Entity Engine” that provides a declarative entity model, a delegator abstraction over data sources, caching, and event-condition-action (ECA) hooks around persistence operations.

## Repository structure at a glance

OFBiz is organized around “components,” and most production logic lives under these top-level component groupings:

The `framework/` directory contains the core runtime and cross-cutting components. The `applications/` directory contains business-domain components such as order management, product/catalog, party/customer, accounting, and more. The `themes/` directory contains UI themes and assets. The `plugins/` directory provides optional components. Supporting runtime scaffolding exists under `runtime/`, and container-oriented deployment templates are present under `docker/`.

Component discovery is rooted in `framework/base/config/component-load.xml`, which declares the top-level component parent directories that are eligible for loading:

- `framework`
- `themes`
- `applications`
- `plugins`

This design is reinforced by Gradle’s component discovery logic (described below), which uses this same component-load configuration as the basis for building the multi-project build.

## Languages and artifact types

The repository uses multiple languages and structured formats that work together:

Java is the primary implementation language for the framework runtime and many business services.

Groovy is used for many service implementations and scripting needs, as evidenced by service definitions that reference Groovy scripts for the implementation `location`.

XML is heavily used for declarative configuration: entity models, service definitions, component descriptors, and other runtime wiring.

Gradle build logic is written in Groovy (for example, `settings.gradle` and `common.gradle`).

Properties files and XML-based properties are used for configuration and localization; OFBiz also supports component-relative resource addressing via `component://...` style URIs.

Shell scripts exist for helper tooling and container entrypoints (for example, Gradle wrapper initialization scripts), though the analysis in this phase focuses on core boot, service, and entity configuration.

## Build and tooling

### Gradle and build layout

The build is a Gradle multi-project build. In `settings.gradle`, the root project is named `ofbiz` and uses Gradle Develocity build scan tooling:

- `com.gradle.develocity` version `3.18.2`
- `com.gradle.common-custom-user-data-gradle-plugin` version `2.0.2`

The Gradle wrapper points to Gradle 8.8:

- `distributionUrl=https://services.gradle.org/distributions/gradle-8.8-bin.zip`

### Component-driven subproject inclusion

The repository includes subprojects dynamically based on enabled components. The `settings.gradle` applies `common.gradle`, and then includes each “active component” as a Gradle subproject.

In `common.gradle`, an “active component” is defined as any directory containing an `ofbiz-component.xml` whose `enabled` attribute is absent or set to `true`. Active components are discovered under the parent directories listed by `framework/base/config/component-load.xml`. The `framework/start` component is always included, even if not found by this discovery.

This design means the build graph and the runtime component set are aligned: Gradle only builds what is enabled as components.

### Runtime prerequisites (per README)

The top-level README states that OFBiz expects JDK 17 and uses Gradle tasks to prepare and start the system. It documents a typical quickstart:

- Initialize wrapper scripts (platform-specific)
- Prepare data (`cleanAll loadAll`)
- Start (`gradlew ofbiz`)

These steps imply the runtime depends on preloaded demo/seed datasets and will download dependencies during Gradle execution on first run.

## Configuration-driven behavior (high-level)

OFBiz is highly configuration-driven. At a high level:

Component presence and enablement are driven by `ofbiz-component.xml` and `component-load.xml`, which are used both for build inclusion and runtime component loading.

Many business behaviors are exposed as named services defined in `servicedef/services*.xml` files. Services specify their execution engine and their implementation location and method to invoke.

Entity schemas are defined in `entitydef/*entitymodel*.xml` files and are wired to datasources via delegator and datasource definitions (often through an `entityengine.xml` file provided at runtime).

## Key “entry points” relevant for further analysis

Even without running the system, the codebase reveals a typical enterprise runtime lifecycle:

Build-time component discovery and inclusion happens via `settings.gradle` and `common.gradle`.

Runtime component discovery happens via a boot-time component loader and component descriptors (covered in more detail in the execution analysis).

The service engine and entity engine are configured via dedicated XML configuration files and are selected at runtime based on loader configuration and component configuration.

Task completed: Added Phase 1 codebase overview and statistics document.
