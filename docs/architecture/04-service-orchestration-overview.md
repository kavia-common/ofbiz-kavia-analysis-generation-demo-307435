# Apache OFBiz — Service Orchestration Overview

## Purpose

This document explains how OFBiz orchestrates business logic through named services. It focuses on:

1. How services are configured and discovered,
2. How dispatchers route service calls,
3. The “outer pipeline” applied to every service call (auth, validation, transactions, ECAs),
4. How group services provide declarative orchestration, and
5. Where background execution and integration mechanisms (JobManager, JMS, RMI) fit in.

## Service engine configuration

The service engine is configured by `framework/service/config/serviceengine.xml`. This file defines a single service engine configuration named `default` and includes:

A service name used for authorization (`userLogin`), which is invoked by the service dispatcher when credential-style inputs such as `login.username` and `login.password` are present.

Thread-pool settings (such as `poll-db-millis`, `max-threads`, and `ttl`) used for job polling and background execution.

A registry of engine names to engine implementation classes. The default configuration includes engines such as:

- `java` for Java service implementations,
- `groovy` and `script` for script-based service implementations,
- `entity-auto` for auto-generated CRUD-style services,
- `group` for service orchestration via group definitions,
- `rmi` and `jms` for remote invocation patterns.

The configuration also includes service-location entries for RMI endpoints, establishing that OFBiz can be deployed with remote service dispatch when desired.

## Dispatcher architecture

### LocalDispatcher as the call boundary

Most application code interacts with services through a `LocalDispatcher`. In web contexts, `LocalDispatcher` instances are created per webapp based on a dispatcher name from web.xml and a delegator selected for that webapp (and potentially tenant-qualified).

`ServiceContainer.getLocalDispatcher(dispatcherName, delegator)` caches dispatchers by name. When a delegator has a tenant id, the dispatcher name is transformed into `<dispatcherName>#<tenantId>`, creating tenant-scoped dispatcher namespaces.

### ServiceDispatcher as the orchestration engine

`ServiceDispatcher` is the global coordinator responsible for applying the service execution contract. When a `LocalDispatcher` calls `runSync(...)` or `runAsync(...)`, the work is delegated to `ServiceDispatcher` which:

1. Selects the appropriate engine implementation (Java, Groovy, entity-auto, group, etc.),
2. Loads and evaluates Service ECAs around well-defined phases,
3. Applies transaction boundaries and handles retries for deadlock scenarios,
4. Performs authentication and permission checks,
5. Validates input and output parameters (if enabled),
6. Triggers notifications and callbacks.

The important enterprise takeaway is that OFBiz applies a common execution pipeline regardless of the implementation style of a service.

## Service execution pipeline (sync)

The service dispatcher’s sync pipeline includes:

First, it normalizes and prepares the execution context (including locale normalization). It can also acquire a semaphore lock for the service when configured, enabling controlled concurrency for services that must be serialized or guarded.

Second, it performs authentication and permissions evaluation. If the service is configured with `auth="true"` and there is no `userLogin`, the dispatcher throws an authorization exception (with a few service-specific exceptions such as `SetTimeZoneFromBrowser`).

Third, it validates inputs using the service model’s parameter definitions. OFBiz performs both structural validation and type conversion via the service model.

Fourth, if the service is configured to use transactions, the dispatcher begins a transaction or suspends/resumes transactions when “require new transaction” semantics are configured. It also provides explicit retry logic for deadlocks when the dispatcher owns the transaction.

Fifth, it invokes the selected engine to run the service implementation. The engine returns a result map that is merged into the service result.

Finally, the dispatcher optionally validates output parameters, commits or rolls back the transaction based on error status, runs post-execution ECAs, and triggers notifications.

## Service ECAs (Event-Condition-Action)

Service ECAs provide cross-cutting orchestration hooks that run at named phases in the service lifecycle. The service dispatcher retrieves an event map for the service and evaluates rules at phases including:

- `auth` (pre-auth),
- `in-validate`,
- `invoke` (pre-invoke),
- `commit` and global commit hooks,
- `return`,
- and global rollback/commit hooks.

These rules can call other services, mutate context, or perform side effects, and they are a major part of OFBiz’s configuration-driven orchestration model.

## Group services as declarative orchestration

### What a group definition is

A “group service” is a service whose engine is `group`. The group engine retrieves a `GroupModel` and executes its configured invokes.

Group definitions are loaded via `ServiceGroupReader`. In addition to service-engine configuration, group definitions can be contributed by components. For example, the work effort component has a group definition file at `applications/workeffort/servicedef/service_groups.xml`:

It defines a group `updateWorkEffortAndAssoc` with `send-mode="all"` and two synchronous invokes: `updateWorkEffort` and `updateWorkEffortAssoc`.

It also defines a group `createWorkEffortRequestItemAndRequestItem` with an invoke that sets `result-to-context="true"`, which causes results from the first invoke to be merged into the context for subsequent invokes.

### How group execution works

`GroupModel.run(...)` selects behavior based on the `send-mode` attribute. In `send-mode="all"`, group execution is sequential, and the group merges results from each invoke into the final result map.

If `result-to-context="true"` is enabled for an invoke, the invoke’s result is merged into the “running context” and becomes available to later invokes. This provides a configuration-only orchestration mechanism that can approximate a simple workflow without writing custom code.

## Background execution and scheduled services

The service dispatcher creates a `JobManager` instance associated with the delegator. The job manager supports scheduling work (including startup services defined in `serviceengine.xml`).

The default `serviceengine.xml` includes commented examples of `<startup-service>` entries, and the dispatcher contains code that retrieves startup services from the service engine configuration and schedules them for execution after startup.

## Integration-oriented orchestration mechanisms

OFBiz includes service engines intended for integration with distributed or remote deployments:

The RMI engine supports remote dispatch to RMI endpoints defined as service locations in `serviceengine.xml`.

The JMS engine is available and supported by `JmsListenerFactory` in the dispatcher when enabled. The default `serviceengine.xml` includes a commented example JMS service configuration, indicating the integration surface exists but is not enabled by default.

These engines provide a foundation for multi-node deployments and integration patterns, but their activation depends on configuration.

## Summary

OFBiz’s orchestration model centers on named services with a strong, consistent execution pipeline. The framework’s orchestration engine (ServiceDispatcher) enforces enterprise requirements such as transactional integrity, authorization, validation, and cross-cutting rule execution, while group services provide a declarative mechanism for composing workflows from existing services.
