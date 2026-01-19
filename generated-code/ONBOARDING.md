# Developer Onboarding — Extending Apache OFBiz Safely (Plugin + Service Engine)

This onboarding guide is tailored to Apache OFBiz “extension-safe” development where:
- **core OFBiz source code is not modified**
- functionality is added through **plugins/components**, **service definitions**, and **configuration-driven wiring**.

## 1) OFBiz extension mechanism (current convention)

OFBiz uses a **plugin system**:
- Plugins are standard OFBiz components placed under: `<OFBIZ_HOME>/plugins/<pluginId>/`
- The legacy `hot-deploy/` mechanism is removed in recent OFBiz and replaced by `plugins/`.

Useful built-in tasks (run at `<OFBIZ_HOME>`):
- Create plugin scaffold: `./gradlew createPlugin -PpluginId=myplugin`
- Install plugin: `./gradlew installPlugin -PpluginId=myplugin`
- Uninstall plugin: `./gradlew uninstallPlugin -PpluginId=myplugin`

## 2) Minimal plugin folder structure (service-only plugin)

A service-focused plugin usually includes:

```
plugins/<pluginId>/
  ofbiz-component.xml
  build.gradle
  config/
    <resource>.properties
  servicedef/
    services.xml
    secas.xml
  src/main/java/
    ... Java service classes ...
```

Notes:
- `ofbiz-component.xml` is the “wiring root” (service resources, ECA resources, classpath entries).
- `config/` is typically added to the classpath via `<classpath type="dir" location="config"/>`.

## 3) Services: XML + Java implementation

### 3.1 Service definition (XML)
Service definitions live in `servicedef/services.xml`:

- `engine="java"` for Java implementations
- `location="<full.class.Name>"` and `invoke="<methodName>"`
- Define attributes with:
  - `name`
  - `type` (e.g., `String`, `Boolean`, `BigDecimal`, or fully-qualified Java types)
  - `mode` (`IN`, `OUT`, `INOUT`)
  - `optional`

### 3.2 Java service method signature (convention)
A typical OFBiz Java service method:

```java
public static Map<String, Object> myService(DispatchContext dctx, Map<String, ? extends Object> context)
```

- Use:
  - `Delegator delegator = dctx.getDelegator();`
  - `LocalDispatcher dispatcher = dctx.getDispatcher();`
  - `GenericValue userLogin = (GenericValue) context.get("userLogin");`
- Return:
  - `ServiceUtil.returnSuccess()` / `ServiceUtil.returnError(...)`

## 4) Configuration-driven behavior

Prefer `config/<resource>.properties` and read via:

- `UtilProperties.getPropertyValue("<resource>", "<key>", "<default>")`

Where `<resource>` is the properties filename without `.properties`.

## 5) Wiring integration safely (SECA / Service ECA)

To integrate without modifying existing services:
- Create `servicedef/secas.xml` in your plugin
- Wire to an existing OFBiz service event (e.g., `resetGrandTotal`, `storeOrder`, `changeOrderStatus`)

This approach is:
- Non-invasive
- Fully configuration-driven
- Easy to enable/disable and evolve

## 6) Example included in this workspace

This workspace includes a sample plugin:
- `generated-code/plugins/kaviaorderenrichment`

It demonstrates:
- A Java service (`kaviaEnrichOrder`) that enriches an order by persisting data in existing OFBiz entity `OrderAttribute`
- A SECA rule that calls the service after `resetGrandTotal` completes

## 7) Best practices checklist

- Prefer existing entities (e.g., `OrderAttribute`) before adding new ones.
- Make integrations configurable (e.g., `enabled=Y/N`).
- Avoid service call loops (do not call the service you are wired from).
- Use `Debug.logInfo/logWarning/logError` with a stable `MODULE` constant.
- Keep services small and deterministic; move complex logic into helpers.
- When you must persist, use OFBiz `Delegator` and `EntityQuery` consistently.

## 8) How to copy into a real OFBiz runtime

1. Copy plugin into `<OFBIZ_HOME>/plugins/`
2. Optionally run `./gradlew installPlugin -PpluginId=<pluginId>`
3. Start OFBiz and validate behavior via normal UI/service flows
