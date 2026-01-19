# kaviaorderenrichment — Sample OFBiz Plugin (Order Enrichment)

This is a **sample OFBiz plugin component** generated for Phase 3 to demonstrate convention-safe, extension-ready code generation.

## What it does

- Defines a Java service: `kaviaEnrichOrder`
- Wires it via SECA so it runs automatically after OFBiz recalculates the order grand total:
  - Trigger: `resetGrandTotal` (event: `return`)
- Persists enrichment results to the existing OFBiz entity `OrderAttribute`, avoiding any new entity definitions.

## Why this is “convention-safe”

- No modifications to OFBiz core code
- Uses standard OFBiz plugin/component layout
- Uses OFBiz Service Engine XML descriptors + Java service implementation
- Uses SECA (service-eca) to integrate with existing lifecycle events

## Files

- `ofbiz-component.xml` — registers service + SECA resources and puts `config/` on the classpath
- `servicedef/services.xml` — service model definition
- `servicedef/secas.xml` — SECA rules wiring the service into OFBiz flow
- `src/main/java/.../OrderEnrichmentServices.java` — Java implementation
- `config/kaviaorderenrichment.properties` — configuration toggles

## Configuration

Edit `config/kaviaorderenrichment.properties`:

- `enabled=Y|N`
- `attribute.prefix=KAVIA_`
- `risk.high.grandTotal=1000`

## Installation into OFBiz

1. Copy this folder to `<OFBIZ_HOME>/plugins/kaviaorderenrichment`
2. Start OFBiz:
   - `./gradlew ofbiz`
3. Create/update an order so that `resetGrandTotal` runs; the enrichment service will be invoked automatically.

## Notes / Extension ideas

- Add more attributes (e.g., fulfillment channel, customer segment) using existing entities.
- Add more SECA rules (e.g., on `storeOrder`, `changeOrderStatus`) with conditions.
- Add permissions/required-permissions if you expose the service for manual invocation.
