# Apache OFBiz — Low-Level Design (LLD)

## Purpose

This document describes the concrete mechanics of OFBiz runtime behavior as implemented in code. It focuses on:

1. Startup and container loading,
2. Web request routing and event execution,
3. Service execution semantics (transactions, auth, validation, ECAs),
4. Group service orchestration, and
5. Multi-tenancy switching in the web layer.

This document complements Phase 1 analysis in `docs/analysis/` by anchoring key behaviors to specific classes and configuration files.

## Startup and runtime profile selection

### Key classes and responsibilities

`org.apache.ofbiz.base.start.Start` is the JVM entry point. It parses startup commands and dispatches to the control panel for `START`, `STATUS`, or `SHUTDOWN` behaviors.

`org.apache.ofbiz.base.start.StartupControlPanel` initializes global system properties (if configured), constructs the `Config` object, creates the log directory, optionally registers the shutdown hook, and invokes container loading.

`org.apache.ofbiz.base.start.Config` loads one of three properties resources from the classpath depending on command options:

- `start.properties` for normal server runs,
- `load-data.properties` for data loading runs, and
- `test.properties` for test-mode runs.

It also sets important system properties including `ofbiz.home`, `java.awt.headless`, and `derby.system.home`.

### Runtime “loaders” as the activation mechanism

The `ofbiz.start.loaders` property in the startup properties file determines which containers are eligible to start. For example:

- `start.properties` sets `ofbiz.start.loaders=main`,
- `load-data.properties` sets `ofbiz.start.loaders=load-data` and enables auto-shutdown,
- `test.properties` sets `ofbiz.start.loaders=test` and enables auto-shutdown.

This loader list is later intersected against container definitions to decide which containers to load.

## Container loading and container configuration

### ContainerLoader behavior

`org.apache.ofbiz.base.container.ContainerLoader` implements the boot-time container lifecycle. The main logic is:

1. Load the mandatory `ComponentContainer` first (named `component-container`), which populates the component registry used by the rest of the runtime.
2. Load additional containers from `ComponentConfig.getAllConfigurations()` but only when their configured loaders intersect the configured loader list.
3. Start all loaded containers in order.
4. Stop containers in reverse order during shutdown.

### Container configuration model

`org.apache.ofbiz.base.container.ContainerConfig` parses `<container>` blocks (from component descriptors) into `ContainerConfig.Configuration` objects. Each container configuration includes:

- The container’s name and class,
- The configured loaders (a comma-separated list),
- A tree of properties (nested `<property>` elements).

This tree structure is directly consumed by containers (for example Catalina’s Tomcat configuration, and service container dispatcher factory configuration).

## Embedded Tomcat container and webapp loading

### CatalinaContainer

`org.apache.ofbiz.catalina.container.CatalinaContainer` sets up an embedded Tomcat instance. It configures:

1. A single engine (enforced by code),
2. A host (including work directory, deploy flags, and optional SSO realm),
3. Connectors (HTTP, HTTPS, and optionally AJP) from configuration properties,
4. Optional clustering, and
5. Webapp loading from component webapp descriptors.

The Catalina component descriptor (`framework/catalina/ofbiz-component.xml`) defines the container and connectors. For example, the default HTTP connector is configured with port `8080` and “upgrade protocol” enabled for HTTP/2 support.

### Webapps are contributed by components

Webapps are not hard-coded. Each component can declare `<webapp>` entries in its `ofbiz-component.xml`. The Catalina container discovers all webapp definitions using component configuration and mounts them at their declared mount points.

Concrete examples from component descriptors:

The order component mounts the order manager webapp at `/ordermgr` and declares a base permission set `OFBTOOLS,ORDERMGR`.

The product component mounts catalog and facility webapps at `/catalog` and `/facility` respectively, with base permissions such as `OFBTOOLS,CATALOG` and `OFBTOOLS,FACILITY`.

## Web request processing (controller-driven routing)

### ControlServlet and ContextFilter

`org.apache.ofbiz.webapp.control.ContextFilter` runs early in the chain and ensures the servlet context has:

- a `Delegator` (`WebAppUtil.getDelegator`),
- a `Security` instance (`WebAppUtil.getSecurity`),
- a `LocalDispatcher` (`WebAppUtil.getDispatcher`).

It also optionally enables multi-tenant behavior by selecting a tenant ID from:

- the request domain name (via `TenantDomainName` lookup),
- an attribute `userTenantId`, or
- a request parameter `userTenantId`.

If a tenant is found, the delegator name is changed to `<base>#<tenantId>`, which causes subsequent dispatcher creation to use tenant-qualified dispatcher naming.

`org.apache.ofbiz.webapp.control.ControlServlet` is the master servlet that delegates to `RequestHandler.doRequest(...)`. It also sets convenient request attributes (`delegator`, `dispatcher`, and `security`) and performs “sanity checks” to ensure transactions are not left in place after request completion.

### RequestHandler (controller.xml execution)

`org.apache.ofbiz.webapp.control.RequestHandler` is the control-plane implementation that resolves request maps from controller configuration and executes events and views. It also provides lifecycle event execution such as “after-login” and “before-logout” controller events.

While the request handler is a large class, it is structurally centered around:

1. Resolving the request URI to a request-map entry from controller configuration,
2. Checking request security and request constraints (including CSRF logic and allowed hosts),
3. Executing event handlers (service, java, groovy/script, simple/minilang, and others),
4. Selecting the view handler for the resolved view type, and
5. Rendering or redirecting based on the controller’s response mapping.

## Event execution (web events)

### ServiceEventHandler

`org.apache.ofbiz.webapp.event.ServiceEventHandler` turns a controller event into a service call. It constructs the service context by reading the service model’s input parameters and sourcing values from:

- request parameters and the combined request map (`UtilHttp.getCombinedMap`),
- multipart upload maps (if present),
- request attributes and session attributes (as a fallback).

It adds `userLogin`, `locale`, `timeZone`, and `visualTheme` to the service context and then invokes either:

- `dispatcher.runSync(serviceName, context)` for sync events, or
- `dispatcher.runAsync(serviceName, context)` for async events.

It maps service result messages into request attributes (`_ERROR_MESSAGE_`, `_EVENT_MESSAGE_`, and list/map variants), and it propagates result keys as request attributes for subsequent view rendering.

### JavaEventHandler

`org.apache.ofbiz.webapp.event.JavaEventHandler` invokes static Java methods declared in controller configuration. It:

1. Loads the handler class using the thread context classloader,
2. Begins a transaction (`TransactionUtil.begin(timeout)`),
3. Invokes a static method `(HttpServletRequest, HttpServletResponse) -> String`,
4. Commits the transaction in a `finally` block.

This is a concrete example of how OFBiz may impose transaction boundaries outside the service layer in the web layer.

## Service execution semantics (ServiceDispatcher)

### Dispatcher hierarchy

`org.apache.ofbiz.service.ServiceContainer` creates and caches `LocalDispatcher` instances. The cache key is the dispatcher name, and when the delegator has a tenant id, the dispatcher name is transformed into `<dispatcherName>#<tenantId>`.

`org.apache.ofbiz.service.GenericDispatcherFactory` is the default `LocalDispatcherFactory` used to create a `LocalDispatcher` backed by the global `ServiceDispatcher`.

`org.apache.ofbiz.service.ServiceDispatcher` is the “engine” that enforces the service contract and orchestration semantics. It owns:

- Engine selection (`GenericEngineFactory`),
- ECA configuration loading (`ServiceEcaUtil.readConfig()`),
- Service group config loading (`ServiceGroupReader.readConfig()`),
- Security integration (`SecurityFactory.getInstance(delegator)`),
- Job manager integration (`JobManager.getInstance(...)`),
- JMS listener factory integration when enabled.

### Sync service execution

`ServiceDispatcher.runSync(...)` implements the full service execution contract including:

1. Semaphore acquisition when configured by the service model,
2. Running-service logging (bounded in-memory log),
3. ECA rule evaluation around key phases (auth, in-validate, invoke, commit, return, and global commit/rollback hooks),
4. Authentication and permission enforcement,
5. Input validation (`ModelService.validate(...)`) and default value propagation,
6. Transaction begin/suspend/resume behavior with optional “require new transaction” semantics,
7. Engine invocation (`engine.runSync(localName, modelService, context)`),
8. Callback dispatch (`engine.sendCallbacks(...)`),
9. Deadlock retry logic when the service owns the transaction,
10. Commit/rollback control based on error conditions,
11. Notification evaluation (`modelService.evalNotifications(...)`),
12. Timing logs for slow services.

The service execution path is therefore the primary enterprise “business logic boundary” in OFBiz.

### Async service execution

`ServiceDispatcher.runAsync(...)` supports asynchronous dispatch and can optionally persist job execution depending on the event handler and engine configuration. It uses similar auth/validate steps, begins transactions if configured, and delegates to the engine for async execution. The JobManager and configured thread pool settings influence how background work is executed and scheduled.

## Group service orchestration

### Definition loading

`org.apache.ofbiz.service.group.ServiceGroupReader` loads group definitions from:

1. `serviceengine.xml` `<service-groups>` locations, and
2. Component service resource infos of type `group` (declared in `ofbiz-component.xml`).

Definitions are cached in-memory by group name.

### Execution model

`org.apache.ofbiz.service.group.ServiceGroupEngine` is the engine used when a service has engine name `group`. It retrieves a `GroupModel` and calls `groupModel.run(dispatcher, localName, context)`.

`org.apache.ofbiz.service.group.GroupModel` supports multiple orchestration strategies via the `send-mode` attribute:

- `all` executes all invokes sequentially and merges results,
- `round-robin` executes one invoke based on a rotating index,
- `random` selects a random invoke,
- `first-available` tries each invoke, tolerating failures,
- `none` returns an empty result map.

When `result-to-context="true"` is enabled on an invoke, the invoke’s result is merged back into the context for subsequent invokes, enabling declarative composition without custom Java/Groovy code.

## Multi-tenancy design notes (web and services)

The multi-tenancy mechanism is visible in the web layer (`ContextFilter`) and in dispatcher naming (`ServiceContainer.getLocalDispatcher`). When tenant mode is enabled, requests are bound to a tenant-specific delegator and services are executed via a tenant-qualified dispatcher name, providing a clean partitioning mechanism for tenant-specific datastore and configuration.

## References

For additional architectural context, see `docs/analysis/03-service-architecture-analysis.md` and `docs/analysis/05-execution-and-boot-flow-analysis.md`.
