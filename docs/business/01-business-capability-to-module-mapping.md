# Apache OFBiz — Business Capability to Module Mapping

## Purpose

This document maps common ERP and e-commerce business capabilities to OFBiz components as represented in this repository. The mapping is based on:

1. The OFBiz component model described in Phase 1 analysis, and
2. Concrete evidence from `ofbiz-component.xml` descriptors for major application components.

The goal is to help stakeholders and new engineers quickly understand “where to look” in a large enterprise codebase.

## How to interpret this mapping

In OFBiz, a “module” is effectively an enabled component. Components contribute capabilities through:

Service definitions (`<service-resource ...>`), which are the primary business logic surface,

Entity resources (`<entity-resource ...>`), which define data models, rules, and seed/demo datasets,

Webapps (`<webapp ...>`), which provide UI and HTTP entry points, and

Test suites (`<test-suite ...>`), which indicate functional areas with existing automated coverage.

This means the best signal for capability ownership is not just the presence of code packages but the component descriptor’s resource declarations.

## Capability mapping (representative components)

### Party / customer master data

Capability: party master data, contacts, agreements, roles, and communication mechanisms.

Primary component: `applications/party`.

Evidence from `applications/party/ofbiz-component.xml`:

The component contributes multiple service definition files including `servicedef/services_party.xml`, `servicedef/services_contact.xml`, and `servicedef/services_communication.xml`.

It exposes a webapp named `party` mounted at `/partymgr` with base permissions `OFBTOOLS,PARTYMGR`.

It includes test suites such as `testdef/PartyTests.xml` and contact mechanism tests, indicating a defined testing surface for party-related behavior.

### Catalog, product, and facilities (inventory-related foundations)

Capability: product catalog management, category management, pricing and promotions, facility and inventory operations, and shipment integrations.

Primary component: `applications/product`.

Evidence from `applications/product/ofbiz-component.xml`:

The component contributes service definitions spanning pricing (`services_price.xml`, `services_pricepromo.xml`), inventory (`services_inventory.xml`), facility (`services_facility.xml`), and store/catalog configuration.

It provides webapps mounted at `/catalog` and `/facility`, indicating interactive UI surfaces for those capabilities.

It declares multiple test suites including `ProductPromoTests.xml`, `ProductConfigTests.xml`, and others, reflecting the complexity and breadth of the domain.

### Order management and shopping workflow foundations

Capability: cart, checkout, orders, returns, quotes, and related scheduled operations.

Primary component: `applications/order`.

Evidence from `applications/order/ofbiz-component.xml`:

The component contributes a large set of service definitions including `services_cart.xml`, `services_checkout.xml`, `services_order.xml`, and `services_return.xml`.

It provides the order manager webapp mounted at `/ordermgr`.

It declares test suites such as `ShoppingCartTests.xml`, `OrderTest.xml`, and quote/request tests, reflecting a mature functional domain.

### Accounting and finance

Capability: invoicing, payment, ledger, budgets, financial accounts, and tax integrations.

Primary component: `applications/accounting`.

Evidence from `applications/accounting/ofbiz-component.xml`:

The component contributes service definitions across invoices, payments, ledger, and tax (`services_invoice.xml`, `services_payment.xml`, `services_ledger.xml`, `services_tax.xml`, and more).

It exposes multiple webapps: accounting (`/accounting`), accounts receivable (`/ar`), and accounts payable (`/ap`).

It declares test suites for accounting, payments, invoices, fixed assets, and rates, indicating explicit validation points for key financial workflows.

## Cross-cutting “platform” capabilities (framework components)

Some capabilities are provided by framework components rather than business application components:

The web control layer and controller-driven routing are provided by `framework/webapp`.

The service engine and orchestration behavior are provided by `framework/service`.

The persistence model, datasource routing, and delegator abstractions are provided by `framework/entity` and related entity extension components.

The embedded Tomcat runtime is provided by `framework/catalina` when enabled.

These platform components should be treated as shared infrastructure rather than business-domain ownership.

## Summary

In OFBiz, business capabilities map most reliably to components, not packages. When planning enhancements or integrations, start by identifying:

1. The owning application component(s),
2. The service definition files declared by those components,
3. The webapps (if there is an interactive/UI entry point), and
4. Existing test suites that can be extended to validate changes.
