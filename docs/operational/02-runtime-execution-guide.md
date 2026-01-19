# Apache OFBiz — Runtime Execution Guide

## Purpose

This guide describes how OFBiz executes at runtime in this repository. It focuses on:

1. Boot sequence,
2. Runtime modes (start, load-data, test),
3. Container startup and shutdown behavior,
4. Where to validate runtime readiness, and
5. The most common failure points implied by the boot and container design.

## Boot sequence summary

OFBiz boots through a small number of entry-point classes:

`org.apache.ofbiz.base.start.Start` is the main entry point. It parses command-line arguments into startup commands and selects a command type: help, status, shutdown, or start.

`org.apache.ofbiz.base.start.StartupControlPanel` initializes configuration and then starts containers.

`org.apache.ofbiz.base.start.Config` loads one of the startup property files (`start.properties`, `load-data.properties`, or `test.properties`) and sets system properties such as `ofbiz.home`.

`org.apache.ofbiz.base.container.ContainerLoader` loads containers:

1. It always loads `ComponentContainer` first.
2. It then loads all other containers contributed by components whose configured `loaders` intersect the loader list from `Config`.
3. It starts containers in load order and stops containers in reverse order.

## Runtime modes and what they mean

### Normal server mode (default)

Normal mode is driven by `start.properties`. In this repository’s default configuration:

`start.properties` sets `ofbiz.start.loaders=main`, meaning containers whose `loaders` include `main` will start.

The shutdown hook is enabled by default unless explicitly disabled (`ofbiz.enable.hook=false`).

The process does not auto-shutdown unless `ofbiz.auto.shutdown=true` is set.

### Load-data mode

Load-data mode is driven by `load-data.properties`. In this repository:

It sets `ofbiz.start.loaders=load-data`.
It disables the shutdown hook (`ofbiz.enable.hook=false`).
It enables `ofbiz.auto.shutdown=true`, meaning the process loads containers and then terminates.

This mode is typically used to prepare databases and seed/demo datasets.

### Test mode

Test mode is driven by `test.properties` and similarly:

Sets `ofbiz.start.loaders=test`,
Disables the shutdown hook,
Auto-shuts down after load.

Test mode is designed for executing tests in controlled environments.

## Web server and ports

When the Catalina container is enabled (for example in `main` loader), it starts an embedded Tomcat server configured by `framework/catalina/ofbiz-component.xml`.

Default connector ports include:

- HTTP: `8080`
- HTTPS: `8443`
- AJP: `8009` (subject to security configuration)

These ports are adjusted at runtime using the port offset value from `Config` (if set via startup command options).

## Admin status and shutdown

OFBiz provides an admin socket used for status and shutdown operations. This is configured in `start.properties` using:

- `ofbiz.admin.host`
- `ofbiz.admin.port`
- `ofbiz.admin.key`

The `Start` command supports:

Status requests via `AdminClient.requestStatus(config)`.
Shutdown requests via `AdminClient.requestShutdown(config)`.

Operationally, this means status/shutdown is not necessarily tied to HTTP; it uses a separate control channel.

## Shutdown semantics

Shutdown occurs in one of two ways:

A JVM shutdown hook triggers `StartupControlPanel.shutdownServer(...)`, or

An explicit stop command is executed, which calls shutdown and then exits.

Shutdown is implemented by unloading containers in reverse order:

`ContainerLoader.unload()` iterates the loaded container list in reverse and calls `stop()` on each container.

Some containers also perform subsystem shutdown logic. For example, the service container shuts down the job manager and deregisters service dispatchers.

## Runtime readiness validation checklist

A practical readiness checklist based on the code-driven boot sequence is:

First, validate that `ofbiz.home` is set to the expected directory (it is printed by `Config` at startup).

Second, validate that the component container loaded successfully. If component loading fails, no other containers should be expected to work because most configuration is component-driven.

Third, validate that the expected container set started. Since container activation is loader-driven, a misconfigured loader list can produce an apparently “running” process that is missing key containers (for example the web server).

Fourth, validate that the embedded Tomcat connectors are bound and started. The Catalina container logs connector protocol and port information on startup.

Fifth, validate that the delegator and dispatcher are available in the webapp context. The web context filter initializes these objects using `WebAppUtil` and makes them available to request processing.

Sixth, validate database connectivity. When the entity engine is configured to check schema on start, database errors will often surface during container start and can prevent boot.

## Typical failure points (from static analysis)

### Misconfigured loaders leading to missing containers

Because loader names drive which containers start, a wrong `ofbiz.start.loaders` value can:

Start too little, producing a process with missing capabilities, or

Start too much, producing port conflicts or failing containers.

### Component configuration issues

If component discovery fails (missing `ofbiz-component.xml`, invalid dependency cycles, invalid classpath configuration), the component container will fail and prevent boot from progressing.

### Database availability and schema behavior

Entity engine configuration (via `entityengine.xml`) can cause startup-time schema checks and schema update attempts. If the database is unavailable or credentials do not allow schema operations, startup may fail.

### Transaction leakage in web requests

The control servlet includes defensive checks to rollback transactions left in place after request processing. This indicates that transactional leakage is a recognized operational risk when custom events or services are written incorrectly.

## Summary

OFBiz runtime behavior is best understood as “loader-driven container assembly.” Most operational failures can be diagnosed by:

1. Confirming which properties file was loaded,
2. Confirming which loaders are active,
3. Confirming which containers were loaded/started,
4. Confirming database configuration and availability, and
5. Confirming webapp context initialization of delegator/dispatcher/security.
