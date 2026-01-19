# Apache OFBiz Phase 1 — Module and Domain Breakdown

## How OFBiz defines “modules” (components)

In this repository, the fundamental unit of modularity is the OFBiz component. A component is a directory containing an `ofbiz-component.xml` descriptor and any combination of entity model definitions, service definitions, web applications, and runtime container declarations.

At runtime, components are loaded and sorted by dependency. The component descriptor can declare dependencies and can also declare container configurations, webapps, service resource files, and entity resource files. The framework reads this information into an in-memory component registry that is later used by the container loader and other subsystems.

## How components are discovered and enabled

Component discovery begins with `framework/base/config/component-load.xml`. This file declares the top-level parent directories that are eligible to contain components:

- `framework`
- `themes`
- `applications`
- `plugins`

The build tooling mirrors this logic. In `common.gradle`, the `activeComponents()` function reads `framework/base/config/component-load.xml`, expands each `parent-directory`, and then either reads a nested `component-load.xml` file in that directory or enumerates subdirectories directly. A directory is treated as an enabled component if it contains `ofbiz-component.xml` and the XML’s `enabled` attribute is either absent or set to `true`.

This means component enablement is file-driven and declarative, and it influences both build composition and (indirectly) runtime assembly.

## What a component can contain

The `ComponentConfig` class provides a concrete view of the component descriptor shape and how it is used. When a component is read, OFBiz collects:

A dependency list from `<depends-on component-name="..."/>`, which is later used to sort components with a directed acyclic graph. This is important because it defines which components must be loaded before others.

Classpath contributions from `<classpath type="dir|jar" location="..."/>`, which determines what code and resources are visible once components are assembled.

Entity resources from `<entity-resource .../>` and service resources from `<service-resource .../>`, which identify the entity models and service definitions contributed by the component.

Webapp definitions from `<webapp .../>`, which define mount points, server targets, and base permission requirements for UI and HTTP-facing sub-applications.

Container configurations from `<container .../>`, which define background services or runtime “containers” that can be started as part of the system boot process.

## Framework-level components (core platform)

The `framework/` directory contains the foundational components that most applications build on. From the files examined in this phase, a few key framework capabilities are evident:

The “start” capability is implemented as a distinct component (`framework/start`) and is always included as an active component in the Gradle discovery logic. This component contains the boot entrypoint and startup configuration loader.

The “base” capability provides low-level utilities (properties resolution, scripting facade, XML utilities) and container boot infrastructure (component loading, container configuration). The component system itself is defined here through classes such as `ComponentConfig` and `ComponentLoaderConfig`.

The “entity” capability provides the entity engine’s configuration model and the delegator abstraction over entity models, data sources, caching, and ECA hooks.

The “service” capability provides the service engine, including service containers and the mapping from service definitions to execution engines.

The “webapp” and “widget” capabilities provide the web control layer, visit tracking, theming hooks, and rendering/model constructs. Even in a static analysis, these are visible through entity model definitions (e.g., visit and website entities) and by the presence of webapp-related configuration in components.

## Business application components (domain modules)

The `applications/` directory contains business-domain modules that implement ERP and e-commerce capabilities. In the filesystem snapshot for this repository, examples include:

The product domain, which covers catalog, category, inventory, pricing, promotions, store, and subscription structures. This domain is reflected both by large entity models and by extensive service definitions.

The order domain, which covers order processing, returns, shopping cart/list, requirements, quotes, and order-related notifications and orchestration.

The party domain, which covers parties (people/organizations), contact mechanisms, agreements, and related relationship structures that underpin customers, suppliers, employees, and other roles.

The accounting domain, which covers budgets, financial accounts, invoices, ledgers, payments, and tax. This is typically one of the largest and most relationally dense ERP areas.

The shipment domain, which covers item issuance, shipments, shipment items/packages, picklists, and their linkage to inventory and orders.

This repository also contains additional domains such as content management, marketing, human resources, manufacturing, work effort tracking, and setup/security extensions, but Phase 1 focuses on the dominant domain families visible through the data model and service definitions.

## Cross-cutting dependencies and ordering

Component ordering is not just an organizational choice. The runtime explicitly sorts components based on declared dependencies via `ComponentConfig.sortDependencies()`, which uses a directed graph sort.

This has two practical implications:

First, if a domain module depends on a shared framework module (or on another domain module), that dependency must be declared in the component descriptor so that resources are loaded in the correct order.

Second, component order impacts what service definitions, entity models, and container definitions are available when containers are loaded and started during system boot.

Task completed: Added Phase 1 module and domain breakdown document.
