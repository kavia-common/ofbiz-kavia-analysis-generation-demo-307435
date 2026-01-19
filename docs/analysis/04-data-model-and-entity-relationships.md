# Apache OFBiz Phase 1 — Data Model and Entity Relationships

## Entity Engine overview

OFBiz’s persistence layer is implemented through the Entity Engine. The Entity Engine is built around three core concepts:

First, entity models are declared in XML and define entities, fields, primary keys, indexes, and relationships. Second, a “delegator” is the runtime facade used by application code to interact with entities, and it is configured to map entity groups to specific data sources. Third, data sources define how the system connects to databases and what SQL/field-type conventions it uses.

The code supports additional cross-cutting behavior around persistence operations, such as caching and ECA processing.

## Configuration model: `EntityConfig` and the `entityengine.xml` shape

The `org.apache.ofbiz.entity.config.model.EntityConfig` class models the `entityengine.xml` configuration file and loads it from the runtime classpath (`UtilURL.fromResource("entityengine.xml")`). This implies `entityengine.xml` is expected to exist as a classpath resource at runtime.

In this Phase 1 analysis environment, the canonical `entityengine.xml` file is not present as a plain file under the source tree, but the repository contains representative templates under `docker/templates/`. The template `docker/templates/postgres-entityengine.xml` demonstrates the expected structure and is used here to document the configuration model.

The entity engine configuration includes:

Resource loaders, such as a file-based loader used to load field type files.

Transaction and connection factory classes, indicating that OFBiz delegates transaction management and connection pooling to pluggable factories.

Delegator definitions (`<delegator ...>`), which bind a named delegator to an entity model reader and group reader, and map entity groups to data sources via `<group-map group-name="..." datasource-name="..."/>`.

Entity model readers (`<entity-model-reader name="..."/>`) and related reader elements (group, ECA, data readers).

Field types (`<field-type .../>`), which describe database-specific column/field type mappings.

Data sources (`<datasource ...>`), which define connection parameters, schema behavior, and data loading behavior.

## Delegators, entity groups, and datasources

The Postgres template shows a canonical “default” delegator that maps multiple entity groups to different datasources, including separate datasources for OLAP and tenant data:

- `org.apache.ofbiz` → a primary datasource
- `org.apache.ofbiz.olap` → an OLAP datasource
- `org.apache.ofbiz.tenant` → a tenant datasource

This pattern is typical of OFBiz: a delegator name selects a configuration profile, and entity group names segment the entity namespace into groups that can be routed to different backends.

The datasource configuration in the template also highlights schema and boot-time behavior:

The `check-on-start="true"` and `add-missing-on-start="true"` properties indicate that, on startup, OFBiz may validate or even alter schema state based on the entity model definitions. This is a powerful enterprise convenience, but it also becomes a key operational risk point because schema checks can fail due to permissions, incompatible schema changes, or database availability.

## Multi-tenancy behavior in the delegator implementation

The `org.apache.ofbiz.entity.GenericDelegator` implementation contains explicit multi-tenant behavior.

When a delegator name includes `#<tenantId>`, the delegator treats this as a tenant-scoped delegator. During construction, it verifies that the tenant exists in the `Tenant` entity, and it can load tenant-specific datasource overrides via `TenantDataSource`. When tenant overrides exist, the delegator uses them to override JDBC URI, username, and password at runtime.

This design indicates a tenant model where the same entity schema is reused but the actual backing database connection can be varied per tenant, and where tenant metadata is stored in a shared “base” delegator’s datastore.

## Entity model patterns used throughout OFBiz

OFBiz entity models are expressed in XML using a few primary constructs:

The `<entity>` element defines a base entity (table-like), including `<field>`, `<prim-key>`, `<index>`, and `<relation>` constructs.

The `<view-entity>` element defines a virtual entity built from joins across member entities. These are used for reporting, query convenience, or de-normalized read patterns.

The `<extend-entity>` element adds fields to entities defined elsewhere, allowing cross-component extension without direct modification of the original component’s entity file.

Relationship types include `one`, `many`, and `one-nofk`, where `one-nofk` indicates a relationship that is not enforced by a database foreign key constraint but is still modeled at the entity layer.

## Core framework data domains (examples)

### Common domain primitives

The framework common entity model (`framework/common/entitydef/entitymodel.xml`) defines foundational entities that are widely reused across domains:

Enumerations (`Enumeration`, `EnumerationType`) provide typed code tables.

Statuses (`StatusItem`, `StatusType`, and `StatusValidChange`) provide workflow state modeling and transition constraints.

Geography (`Geo`, `GeoAssoc`, `GeoType`) provides hierarchical geospatial boundaries and is often reused for addresses, tax geo determination, and reporting.

Units of measure (`Uom`, `UomType`, conversions) provide measurement and currency conversion primitives.

System properties (`SystemProperty`) support configuration that can be stored in the database rather than only in properties files.

These foundational entities explain how higher-level modules can standardize on common types like “status,” “geo,” and “enumeration” without duplicating schemas.

### Webapp and visit tracking

The framework webapp entity model (`framework/webapp/entitydef/entitymodel.xml`) defines visit and request tracking entities:

`Visit` and `ServerHit` provide a built-in session/visit logging model. Both are marked as `never-cache="true"` and `no-auto-stamp="true"` in some cases, suggesting performance considerations and an explicit desire to avoid caching “high volume” telemetry-like data.

`WebSite` and `WebPage` provide basic website configuration, including hosts/ports, HTTPS enablement, and visual theme set binding.

### Entity extension and synchronization

The entity extension entity model (`framework/entityext/entitydef/entitymodel.xml`) includes synchronization entities such as `EntitySync`, `EntitySyncHistory`, and inclusion lists.

This implies that OFBiz supports explicit, schema-driven data synchronization mechanisms that can track history, inclusion sets, and removal information, which aligns with the service-level support for remote sync and cache clearing.

## Business domain entity models (high-level)

The `applications/datamodel/entitydef/` directory contains consolidated entity models for major business domains. These are large schema files that reflect OFBiz’s ERP breadth.

### Product domain

The product entity model (`product-entitymodel.xml`) covers multiple subdomains including catalog, category, cost, facility, feature, inventory, price, product definitions, promotions, store configuration, subscription, and supplier functions.

Even in the sampled sections, the model shows:

Catalog structures (`ProdCatalog`, `ProdCatalogCategory`) that link catalogs to categories with dates and sequence ordering.

Product categorization (`ProductCategory` and related membership) supporting hierarchies and association metadata.

Promotion integration via relations that link product or category state to promo entities and content.

Store-level configuration entities that connect product stores to telecom configuration and related enumerations, indicating that store settings are modeled as relational configuration rather than hard-coded settings.

### Order domain

The order entity model (`order-entitymodel.xml`) captures order processing and its cross-domain relationships. Representative entities include:

`OrderAdjustment`, which models taxes, shipping adjustments, promotion actions, and other financial adjustments at the order/order-item level. It references promo identifiers, tax authority identifiers, and GL accounts, demonstrating how order execution is tied into tax and accounting primitives.

Allocation plan entities and view entities that join allocation headers/items to orders, order items, products, statuses, and enumerations. This indicates that inventory and fulfillment planning is a first-class modeled domain.

### Party domain

The party entity model (`party-entitymodel.xml`) provides the person/organization and relationship backbone typical of ERP systems.

In the sampled portions:

Agreements and addenda (e.g., `Agreement`, `Addendum`) link parties and roles to products and effective dates.

View entities consolidate party classification and contact mechanism information, indicating that “party” is a central hub entity used by many business processes.

Person-specific subdomains such as marital status are modeled as dated associations to parties, reinforcing the “party as hub + dated associations” pattern.

### Accounting domain

The accounting entity model (`accounting-entitymodel.xml`) includes modules such as budgets, financial accounts, fixed assets, invoices, ledger, payments, rates, and tax.

The sampled portion shows budget entities (`Budget`, `BudgetItem`, `BudgetRevision`) and ledger categorization (`GlAccountCategoryMember`, `GlAccountCategoryType`) plus view entities joining payments and financial account transactions. This indicates the accounting model is highly relational and is built to support both transactional processing and reporting views.

### Shipment domain

The shipment entity model (`shipment-entitymodel.xml`) ties together order fulfillment and inventory movement.

Entities such as `ItemIssuance` link:

- orders and order items,
- inventory items,
- shipments and shipment items,
- and (optionally) fixed asset maintenance history.

This reflects an enterprise-grade fulfillment model where issuance is a traceable event connecting demand (orders) to supply (inventory) and execution (shipments).

## Caching and event hooks around persistence

`GenericDelegator` demonstrates that entity operations include:

Cache interaction (including a primary key cache, query caches, and methods to clear cache lines and all caches). Some entities explicitly mark themselves as “never cache” to opt out.

ECA rule evaluation around key lifecycle events for create/store/remove/find operations. Even when ECA rules are disabled or absent, the delegator structure still supports invoking validation, run, cache-check/put/clear, and return events.

This implies that both functional behavior (via rules) and non-functional behavior (via caching) are part of the entity engine’s standard execution path, not optional add-ons.

Task completed: Added Phase 1 data model and entity relationships document.
