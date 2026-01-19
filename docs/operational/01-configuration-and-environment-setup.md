# Apache OFBiz — Configuration and Environment Setup Guide

## Scope and constraints

This guide documents the runtime configuration surfaces present in the OFBiz repository in this workspace. It focuses on configuration and environment setup, not on modifying OFBiz source code.

In this demo workspace, the OFBiz repository is treated as read-only. If you need to change behavior, prefer configuration overlays or runtime-specific configuration files rather than modifying framework code.

## Startup configuration (properties-driven)

### Startup properties files

OFBiz loads startup configuration from one of three classpath resources under `org/apache/ofbiz/base/start/`:

1. `start.properties` for normal execution,
2. `load-data.properties` for data loading mode, and
3. `test.properties` for test-mode runs.

The selection logic is implemented in `org.apache.ofbiz.base.start.Config` and depends on whether the parsed command list includes `--load-data` or `--test`.

### Key properties and what they control

The following properties are central to execution and environment setup.

`ofbiz.start.loaders` determines which containers can start. For example, `start.properties` sets `ofbiz.start.loaders=main`. Loader names are later matched against container definitions in component descriptors.

`ofbiz.home` determines the base directory. The `Config` class sets the system property `ofbiz.home` based on this value (defaulting to the current directory). Many subsystems assume `ofbiz.home` is set.

`ofbiz.log.dir` determines where logs are written. If not set, `Config` defaults to `runtime/logs` relative to `ofbiz.home`.

`derby.system.home` defaults to `runtime/data/derby` and influences the default embedded Derby location.

`ofbiz.enable.hook` controls whether the JVM shutdown hook is registered.

`ofbiz.auto.shutdown` controls whether the system shuts down automatically after containers load (used by `load-data.properties` and `test.properties`).

`ofbiz.admin.host`, `ofbiz.admin.port`, and `ofbiz.admin.key` configure the admin socket used by `AdminClient` for status/shutdown requests. The provided `start.properties` includes explicit defaults for `ofbiz.admin.port` and `ofbiz.admin.key`.

### Port offsets

The `Config` class also supports a port offset parameter (via the `--portoffset` startup option). Port offset affects admin port, and it is also used by the Tomcat container when binding connector ports.

## Runtime assembly configuration (components and containers)

### Component discovery and enablement

Components are discovered from the parent directories listed in `framework/base/config/component-load.xml`:

- `framework`
- `themes`
- `applications`
- `plugins`

Each component is defined by the presence of an `ofbiz-component.xml` file. Enablement is file-driven (the `enabled` attribute is either absent or set to `true`).

### Container definitions inside components

Components can define containers using `<container>` blocks in their `ofbiz-component.xml` descriptor. Containers are activated by the runtime loader set: a container’s `loaders` attribute is intersected with `ofbiz.start.loaders` to decide whether it is loaded.

The configuration model supporting this is `org.apache.ofbiz.base.container.ContainerConfig`, which parses nested `<property>` definitions into a property tree that container classes consume.

## Web server configuration (embedded Tomcat)

When running under the `main` loader, the Catalina component defines an embedded Tomcat container (`org.apache.ofbiz.catalina.container.CatalinaContainer`) in `framework/catalina/ofbiz-component.xml`.

This container configuration defines:

The engine and default host configuration.

Connectors, including:

- an HTTP connector (default port `8080`),
- an HTTPS connector (default port `8443` with JSSE configuration),
- an AJP connector (default port `8009`, disabled or restricted depending on security configuration).

Connector ports are adjusted by the global `portOffset` value at runtime.

Webapps are discovered from component webapp definitions and mounted at their configured mount points.

## Service engine configuration

The service engine is configured by `framework/service/config/serviceengine.xml`. This file defines:

The authorization service name (`userLogin`).

Thread pool settings relevant to job polling and background work.

Engine implementations for `java`, `groovy`, `entity-auto`, `group`, `jms`, `rmi`, and others.

Service locations for RMI endpoints.

Startup services and JMS service definitions (included as examples and commented by default).

## Database and entity engine configuration

### entityengine.xml as a classpath resource

OFBiz expects an `entityengine.xml` resource on the classpath at runtime (loaded by the entity configuration model). The repository includes templates for common configurations under `docker/templates/`.

A representative template is `docker/templates/postgres-entityengine.xml`, which illustrates:

A transaction factory (`GeronimoTransactionFactory`),
A connection factory (`DBCPConnectionFactory`),
Delegator definitions (such as `default` and `default-no-eca`),
Datasource definitions for Postgres-backed entity groups.

### Schema behavior

Datasources can be configured with schema checks and schema evolution behavior. In the Postgres template, `check-on-start="true"` and `add-missing-on-start="true"` indicate that OFBiz may verify and attempt to create missing schema elements at startup.

This can be convenient in demo environments, but in enterprise deployments it requires:

1. Database availability at boot,
2. Sufficient schema permissions, and
3. A controlled approach to schema migrations.

## Docker-based configuration automation (optional)

The repository includes a Docker entrypoint script at `docker/docker-entrypoint.sh`. This script is an operational convenience layer that can:

Generate a runtime `config/entityengine.xml` from the Postgres template when Postgres environment variables are set.

Optionally download the Postgres JDBC driver into `lib-extra/`.

Apply configuration overlays by writing into `config/` so those resources appear earlier in the classpath.

Disable specific components by rewriting their `ofbiz-component.xml` descriptors (based on the `OFBIZ_DISABLE_COMPONENTS` environment variable).

Load seed/demo data using `/ofbiz/bin/ofbiz --load-data ...` and optionally create an admin user record with a computed password hash.

### Environment variables supported by docker-entrypoint.sh

The entrypoint script documents its environment variable contract. Key variables include:

`OFBIZ_SKIP_INIT` to skip initialization steps.

`OFBIZ_DATA_LOAD` to select data load behavior (`none`, `seed`, or `demo`).

`OFBIZ_ADMIN_USER` and `OFBIZ_ADMIN_PASSWORD` to configure admin user creation.

`OFBIZ_POSTGRES_HOST` plus related `OFBIZ_POSTGRES_*` variables to configure Postgres datasources and generate `config/entityengine.xml`.

`OFBIZ_HOST` and `OFBIZ_CONTENT_URL_PREFIX` to apply configuration overrides for security host header allowlists and content URL prefix configuration.

`OFBIZ_ENABLE_AJP_PORT` to modify Catalina configuration and enable AJP binding on all interfaces.

## Summary

OFBiz runtime configuration is primarily properties- and XML-driven. Startup properties select runtime loaders, which select containers, which then load components and their contributions (webapps, services, entities). Most enterprise deployments control behavior by:

1. Selecting loader profiles,
2. Providing environment-appropriate entityengine and serviceengine configuration,
3. Enabling/disabling components through component descriptors, and
4. Using configuration overlays to avoid modifying framework internals.
