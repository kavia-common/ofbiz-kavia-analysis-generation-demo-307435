# Apache OFBiz Phase 1 — Service Architecture Analysis

## Service architecture overview

Apache OFBiz expresses a large portion of its business logic as named services. These services form an internal service-oriented architecture (SOA) where the unit of composition is a service name, a schema of input/output parameters, and an implementation strategy selected by a service “engine.”

Services are defined declaratively in XML (for example, `applications/*/servicedef/services.xml`) and are executed by the service engine configured in `framework/service/config/serviceengine.xml`.

At runtime, services are invoked through dispatchers (local or remote). A dispatcher name is typically tied to a delegator name and can be tenant-scoped (for example, by appending `#<tenantId>`), which becomes relevant for multi-tenant behavior.

## Service engine configuration (`serviceengine.xml`)

The service engine configuration is centralized in `framework/service/config/serviceengine.xml`. The configuration defines:

A default service engine (`<service-engine name="default">`) and an authorization service name (`<authorization service-name="userLogin"/>`), indicating that service authentication/authorization is integrated with login/user concepts.

A thread pool configuration used for job polling and background service execution. The configuration includes defaults for max threads, TTL, polling intervals, and retry delays, indicating a built-in job scheduler/poller.

A list of service “engines” mapped from a symbolic engine name to an implementation class. Examples present in this repository include:

- `entity-auto` mapped to `org.apache.ofbiz.service.engine.EntityAutoEngine`
- `java` mapped to `org.apache.ofbiz.service.engine.StandardJavaEngine`
- `groovy` mapped to `org.apache.ofbiz.service.engine.GroovyEngine`
- `script` mapped to `org.apache.ofbiz.service.engine.ScriptEngine`
- `simple` mapped to `org.apache.ofbiz.minilang.SimpleServiceEngine`
- `group` mapped to `org.apache.ofbiz.service.group.ServiceGroupEngine`
- `interface` mapped to `org.apache.ofbiz.service.engine.InterfaceEngine`
- `jms` and `rmi` engines for remote invocation paths

A list of service locations for remote engines (for example, RMI endpoints like `rmi://localhost:1099/RMIDispatcher`). This explicitly documents that OFBiz can dispatch service calls beyond the local JVM when configured.

## Service definition patterns (what “a service” looks like)

### Common fields in service definitions

Service definitions use a repeated structural pattern:

The `<service>` element declares `name`, `engine`, and (for some engines) `location` and `invoke`.

Service parameter schemas are described using `<attribute>` for explicit parameters and `<auto-attributes>` when a service is aligned with an entity model and can auto-expose entity fields as service parameters.

Many services declare `auth="true"`, which indicates authentication requirements are enforced at the service layer.

Some services include transaction directives such as `require-new-transaction="true"`, `use-transaction="false"`, and `transaction-timeout="..."`, reflecting the need for consistent transactional semantics in ERP workflows.

### Interface services and reuse via `implements`

The repository uses “interface” services to define a common signature that other services implement.

For example, in the order domain, `orderNotificationInterface` is an `engine="interface"` service that defines required and optional parameters such as `orderId`, `orderItemSeqId`, and output fields like `emailType`, `body`, and `subject`. Concrete notification services then `implements` this interface.

This pattern is also visible in the product domain where `interfaceProduct` provides a common entity-shaped schema for product operations, and services such as `createProduct` and `updateProduct` implement it.

This approach standardizes service contracts and promotes reuse across implementations.

### Entity-auto CRUD services

A major service pattern in OFBiz is `engine="entity-auto"`, used to generate standard CRUD-style services on top of entity models.

In the product services file, many services use `entity-auto` for create, update, delete, and expire operations against entity names. These services typically use `<auto-attributes include="pk|nonpk" ...>` to expose primary key and non-primary-key fields with appropriate IN/OUT modes.

This reflects a framework-level practice: treat entity models as canonical schemas and auto-provision a consistent service API layer for standard data operations.

### Java and Groovy service implementations

Services implemented via `engine="java"` point to a Java class and method via `location` and `invoke`. For instance, in the order domain, several “sendOrder…” notifications use Java implementations in `org.apache.ofbiz.order.order.OrderServices`.

Services implemented via `engine="groovy"` point to `component://...` locations that resolve to Groovy script files and a function/method name to invoke. For instance, the product domain uses Groovy scripts (e.g., `ProductServicesScript.groovy`) for product creation and updates.

In practice, this means OFBiz supports multiple implementation styles for services while keeping a uniform service interface at the call boundary.

### Group services (service orchestration)

Services using `engine="group"` represent orchestrations rather than direct “one function” implementations. These services typically sequence multiple other service calls and can bind results back into context.

For example, `uploadOrderContentFile` is declared as a group service that invokes `createContentFromUploadedFile` and then `createOrderContent`. This pattern is a key SOA capability: complex workflows can be declared as orchestration graphs without writing a single monolithic implementation.

## Dispatchers and the service container

The service container class `org.apache.ofbiz.service.ServiceContainer` is a runtime container that manages dispatcher creation and lifecycle.

It maintains an internal cache of dispatchers (`DISPATCHER_CACHE`) keyed by dispatcher name. If a delegator has a tenant id, the dispatcher name is tenant-qualified (concatenating `#<tenantId>`), which supports tenant-isolated service invocation paths.

Dispatcher creation is delegated to a `LocalDispatcherFactory` instance, which is configured through container configuration via a `dispatcher-factory` property. This demonstrates a configuration-driven design where service dispatch behavior can be swapped or customized without changing service call sites.

On shutdown, the service container shuts down the job manager and deregisters all dispatchers.

## Remote invocation and distribution patterns

OFBiz supports distributed or remote service execution through multiple engines:

The RMI engine uses configured service locations to send service calls across a Java RMI boundary.

The JMS engine is used in the entity extension component to broadcast cache-clearing operations. For example, `distributedClearAllEntityCaches` uses `engine="jms"` and location `serviceMessenger`, which implies a topic-based distribution mechanism for cross-node cache invalidation.

The HTTP engine is also used in the entity extension services to call remote endpoints for entity sync and cache-clearing when configured.

These options form an explicit foundation for running OFBiz in multi-node environments where service execution and state management must be coordinated.

## Service-related security and permissions

Service definitions use several complementary security constructs:

The `auth="true"` attribute provides a broad authentication requirement gate.

Some services declare explicit `<required-permissions>` blocks, which suggests fine-grained authorization beyond “logged in or not.”

Some services declare `<permission-service .../>`, pointing at a centralized permission check service that is called for CREATE/UPDATE/DELETE operations (for example, the entity sync CRUD services).

This layered approach reflects enterprise needs where access control is enforced consistently at the service boundary, regardless of how service implementations are written.

Task completed: Added Phase 1 service architecture analysis document.
