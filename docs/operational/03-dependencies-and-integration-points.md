# Apache OFBiz — Dependencies and Integration Points

## Purpose

This document summarizes the primary external dependencies and integration points visible in the OFBiz codebase and configuration in this workspace.

It is intended for enterprise stakeholders who need to understand what OFBiz depends on at runtime and where integration can be configured or extended.

## Dependency model: what is “core” vs “optional”

OFBiz is intentionally modular and configuration-driven. Many integrations exist as optional engines or containers that are only active when:

1. A component providing the integration is enabled, and
2. The runtime loader set activates the corresponding container, and
3. The appropriate configuration is present.

Therefore, the presence of integration code in the repository does not imply it is enabled by default.

## Core runtime dependencies (typically required)

### Java runtime

The build and runtime expectations for this repository include a modern JDK (Phase 1 analysis indicates JDK 17 is expected by the project’s README and build setup).

### Embedded servlet container (Tomcat)

When running with the Catalina container enabled, OFBiz embeds Tomcat via `org.apache.ofbiz.catalina.container.CatalinaContainer`. The Catalina component descriptor configures:

HTTP and HTTPS connectors, plus optional AJP.

Optional SSO integration via a Tomcat realm when `security.login.tomcat.sso` is enabled.

Webapp discovery and mounting from component webapp descriptors.

### Database access

OFBiz persists and queries data via the entity engine. Database configuration is driven by `entityengine.xml` (expected on the classpath). The repository includes a Postgres template (`docker/templates/postgres-entityengine.xml`) showing common configuration elements:

Transaction factory selection (for example `GeronimoTransactionFactory`).

Connection factory selection (for example `DBCPConnectionFactory`).

Datasource definitions (including JDBC driver class, JDBC URI, credentials, and pool sizing).

Delegator definitions that map entity groups to datasources.

OFBiz may perform schema checks and missing table/column creation on startup when configured.

## Primary internal integration boundaries (subsystems)

### Web control layer → service engine

The web control layer integrates with the service engine through a `LocalDispatcher` stored in servlet context and request attributes. The `ServiceEventHandler` constructs a service context from the HTTP request and calls `dispatcher.runSync` or `dispatcher.runAsync`.

This boundary is where HTTP request data becomes business-service invocation. It is also where service results become request attributes for view rendering.

### Service engine → entity engine

Services interact with persistence through `Delegator` and the entity engine APIs. The service dispatcher also owns transaction boundaries, which become the default transactional context for entity operations executed inside services.

### Container system → all subsystems

The container loader integrates the entire runtime, ensuring component configuration is loaded first and that containers are started based on the loader profile.

## Optional integration mechanisms

### RMI service engine

The default service engine configuration includes:

An engine named `rmi` (`org.apache.ofbiz.service.rmi.RmiServiceEngine`), and

Service locations such as `rmi://localhost:1099/RMIDispatcher`.

This indicates OFBiz can dispatch services over Java RMI when configured and when the RMI naming service is available.

### JMS service engine and listeners

The service engine includes an engine named `jms` (`org.apache.ofbiz.service.jms.JmsServiceEngine`). The service dispatcher can create a `JmsListenerFactory` when JMS is enabled.

The default `serviceengine.xml` includes a commented `<jms-service>` block that documents the expected configuration shape (jndi server name, factory name, topic/queue, credentials, and listener behavior).

This integration point is therefore present but not enabled by default.

### Docker entrypoint-based database integration

The Docker entrypoint script (`docker/docker-entrypoint.sh`) provides a concrete integration surface for Postgres-based deployments:

It can generate `config/entityengine.xml` from the Postgres template.

It can download the Postgres JDBC driver into `lib-extra/`.

It can load seed/demo data and create admin credentials.

This is not part of the core OFBiz execution pipeline, but it is a common operational integration pattern for container-based deployments.

## Integration point summary table

| Integration point | Purpose | Where configured / implemented |
|---|---|---|
| Embedded Tomcat (HTTP/HTTPS/AJP) | Serves OFBiz webapps and routes requests into OFBiz control servlet | `framework/catalina/ofbiz-component.xml`, `CatalinaContainer` |
| Database (Derby/Postgres/etc.) | Persistence and querying via entity engine | `entityengine.xml` (template: `docker/templates/postgres-entityengine.xml`) |
| Transactions (JTA) | Transaction boundaries for services and persistence | Entity transaction factory selection in `entityengine.xml` and `TransactionUtil` usage in service/web layers |
| RMI service engine | Remote service invocation | `framework/service/config/serviceengine.xml` (`engine name="rmi"` + `<service-location>`) |
| JMS service engine | Messaging-based service invocation and listener integration | `serviceengine.xml` (`engine name="jms"`) plus optional `<jms-service>` configuration |
| Admin control channel | Status/shutdown via admin socket | `start.properties` (`ofbiz.admin.*`) and `AdminClient` usage in `Start` |

## Notes on enterprise integration planning

For enterprise integrations, the cleanest integration boundary is usually the service boundary because services are named, validated, permissioned, and transaction-managed.

When integrating externally, prefer invoking services through supported engines (local, remote, JMS/RMI) rather than bypassing the service layer and operating directly on entities. This preserves OFBiz’s enterprise execution semantics.
