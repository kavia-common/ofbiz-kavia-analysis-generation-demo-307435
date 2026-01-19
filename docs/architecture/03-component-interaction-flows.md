# Apache OFBiz — Component Interaction Flows

## Purpose

This document captures the most important runtime interaction flows in OFBiz, expressed as:

1. A narrative describing what happens and why it matters, and
2. Mermaid sequence/flow diagrams that are suitable for enterprise architecture review.

The flows here are derived from Phase 1 analysis and corroborated by the OFBiz startup, container, web control, and service engine sources.

## Flow 1: Startup and container activation

OFBiz boots by converting startup commands into a runtime configuration (`Config`) and then loading containers. Containers are started based on the loader list from startup properties, which acts as a runtime profile mechanism.

```mermaid
sequenceDiagram
  participant CLI as "CLI args"
  participant Start as "Start"
  participant SCP as "StartupControlPanel"
  participant Cfg as "Config"
  participant CL as "ContainerLoader"
  participant CC as "ComponentContainer"
  participant Other as "Other containers"

  CLI->>Start: "Start.main(args)"
  Start->>SCP: "init(commands)"
  SCP->>Cfg: "new Config(commands)"
  SCP->>CL: "load(config, commands)"
  CL->>CC: "init(component-container)"
  CC-->>CL: "ComponentConfig registry loaded"
  CL->>Other: "load containers whose loaders intersect config.loaders"
  CL->>Other: "start() each container"
  Other-->>Start: "runtime ready"
```

## Flow 2: Webapp initialization and request entry

In a web deployment using the embedded Tomcat container (`CatalinaContainer`), a request is processed through filters and the main OFBiz control servlet.

Two concepts are important for understanding OFBiz request handling:

1. The web layer is responsible for ensuring `delegator`, `dispatcher`, and `security` are available in request/session context.
2. The core routing is controller-driven, meaning `controller.xml` request maps define which events run and which views are rendered.

```mermaid
sequenceDiagram
  participant Tomcat as "Tomcat"
  participant CF as "ContextFilter"
  participant WAU as "WebAppUtil"
  participant CS as "ControlServlet"
  participant RH as "RequestHandler"

  Tomcat->>CF: "doFilter(request)"
  CF->>WAU: "getDelegator(servletContext)"
  CF->>WAU: "getSecurity(servletContext)"
  CF->>WAU: "getDispatcher(servletContext)"
  CF-->>Tomcat: "chain.doFilter()"
  Tomcat->>CS: "service() / doGet/doPost"
  CS->>RH: "doRequest(request, response, ...)"
```

## Flow 3: Multi-tenant selection at the HTTP boundary

When multi-tenant mode is enabled, the web layer may switch the delegator and dispatcher based on domain name or explicit tenant identifiers. This is significant because it changes both the persistence context and service dispatcher namespace.

```mermaid
flowchart TD
  A["Incoming HTTP request"] --> B["ContextFilter.doFilter"]
  B --> C{"Multi-tenant enabled?"}
  C -->|No| D["Use default delegator + dispatcher"]
  C -->|Yes| E["Resolve tenantId (domainName, attribute, or parameter)"]
  E --> F{"tenantId found?"}
  F -->|No| D
  F -->|Yes| G["Set delegatorName = base#tenantId in session"]
  G --> H["DelegatorFactory.getDelegator(base#tenantId)"]
  H --> I["ServiceContainer.getLocalDispatcher(dispatcherName, tenantDelegator)"]
  I --> J["Request continues with tenant context"]
```

## Flow 4: Controller event execution → service invocation

Controller events can call services directly. In this path, the service boundary becomes the functional boundary for the business operation, and the controller event is primarily responsible for mapping request values into the service’s expected parameter schema.

```mermaid
sequenceDiagram
  participant RH as "RequestHandler"
  participant EF as "EventFactory"
  participant SEH as "ServiceEventHandler"
  participant LD as "LocalDispatcher"
  participant SD as "ServiceDispatcher"
  participant Eng as "Service engine (java/groovy/entity-auto/group)"
  participant EE as "Entity engine (Delegator)"

  RH->>EF: "Resolve event handler by type"
  EF->>SEH: "invoke(event)"
  SEH->>LD: "runSync(serviceName, context)"
  LD->>SD: "runSync(localName, modelService, context)"
  SD->>SD: "auth + permission + validate + tx begin"
  SD->>Eng: "engine.runSync(...)"
  Eng->>EE: "CRUD / query via delegator (if needed)"
  EE-->>Eng: "entity results"
  Eng-->>SD: "service result"
  SD->>SD: "ECA hooks + notifications + tx commit/rollback"
  SD-->>SEH: "result map"
  SEH-->>RH: "set messages + attributes"
```

## Flow 5: Service engine sync execution (transaction + ECA semantics)

The service engine enforces consistent enterprise behavior: it can acquire semaphores, manage transactions, execute ECA hooks at multiple phases, invoke the actual engine implementation, and validate outputs.

The key insight is that even when business logic is implemented in different ways (Java, Groovy, entity-auto, group), the same outer execution pipeline is applied by `ServiceDispatcher`.

```mermaid
flowchart TD
  A["ServiceDispatcher.runSync(...)"] --> B["Acquire semaphore (optional)"]
  B --> C["Build context + locale normalization"]
  C --> D["RunningService log entry"]
  D --> E["Load ECA event map for service"]
  E --> F["Auth check (incl. login.username/password mapping)"]
  F --> G["Permissions evaluation"]
  G --> H["Input validation + default values"]
  H --> I{"use-transaction?"}
  I -->|No| J["Invoke engine.runSync(...)"]
  I -->|Yes| K["Begin/suspend/resume transaction"]
  K --> J
  J --> L["Callbacks + result merge"]
  L --> M["Output validation (optional)"]
  M --> N["Commit or rollback"]
  N --> O["Notifications + return ECA"]
  O --> P["Return result map"]
```

## Notes for architects and implementers

The flows above show why OFBiz is often described as configuration-driven and service-oriented. Most enterprise behaviors (routing, authorization, validation, transactional integrity, orchestration composition) are applied by the framework’s control plane and execution pipeline rather than being duplicated in each business module.

For customizations, the safest strategy is to extend through component-contributed services, entities, and webapps so the same pipelines continue to apply consistently.
