# Phase 3 — Intelligent OFBiz Extension Code (Generated)

This directory contains **non-invasive, convention-safe** code generation outputs for Apache OFBiz.

## What’s included

- `plugins/kaviaorderenrichment/` — a sample OFBiz **plugin component** implementing an “Order Enrichment” capability:
  - XML service definitions (`servicedef/services.xml`)
  - Java service implementation (`src/main/java/.../OrderEnrichmentServices.java`)
  - Service ECA wiring (`servicedef/secas.xml`) to integrate without modifying OFBiz core
  - Configuration properties (`config/kaviaorderenrichment.properties`)
  - Plugin-level `ofbiz-component.xml` and `build.gradle` (following OFBiz plugin conventions)

## What’s *not* included / modified

- No changes are made to the source OFBiz repository under `ofbiz-framework-307435/`.
- This is a **copy-ready** plugin that can be placed into an OFBiz `plugins/` directory.

## Quick install (manual copy)

1. Copy the plugin folder into the OFBiz source repo (or an OFBiz distribution) `plugins/` directory:

   - From: `generated-code/plugins/kaviaorderenrichment`
   - To:   `<OFBIZ_HOME>/plugins/kaviaorderenrichment`

2. Start OFBiz (or install plugin first if needed):

   - `./gradlew ofbiz`
   - (Optional) `./gradlew installPlugin -PpluginId=kaviaorderenrichment`

3. Create or update an order in OFBiz to trigger `resetGrandTotal`.
   The plugin wires an ECA to run automatically after `resetGrandTotal`.

## Developer onboarding

See `generated-code/ONBOARDING.md`.
