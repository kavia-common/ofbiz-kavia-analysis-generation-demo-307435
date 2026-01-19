/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.kavia.orderenrichment;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * Kavia Order Enrichment Services (generated demo).
 *
 * <p>This service is intended to demonstrate a convention-safe way to enrich orders in OFBiz by:
 * <ul>
 *   <li>Hooking into an existing lifecycle event (SECA on resetGrandTotal)</li>
 *   <li>Calculating derived attributes</li>
 *   <li>Persisting them using an existing entity (OrderAttribute) without requiring schema changes</li>
 * </ul>
 */
public class OrderEnrichmentServices {

    private static final String MODULE = OrderEnrichmentServices.class.getName();

    /**
     * Properties resource name for this plugin (from config/kaviaorderenrichment.properties).
     */
    private static final String PROPS_RESOURCE = "kaviaorderenrichment";

    private static final String DEFAULT_ATTR_PREFIX = "KAVIA_";
    private static final String ATTR_RISK_BUCKET = "RISK_BUCKET";
    private static final String ATTR_ITEM_COUNT = "ITEM_COUNT";
    private static final String ATTR_ENRICHMENT_TS = "ENRICHMENT_TS";

    /**
     * Enriches an order by computing a simple risk bucket and item count and storing them in OrderAttribute.
     *
     * <p>Expected inputs:
     * <ul>
     *   <li>orderId (String, required)</li>
     *   <li>userLogin (GenericValue, required)</li>
     *   <li>dryRun (Boolean, optional)</li>
     * </ul>
     *
     * <p>Outputs:
     * <ul>
     *   <li>enrichmentApplied (Boolean)</li>
     *   <li>riskBucket (String)</li>
     *   <li>orderItemCount (Long)</li>
     * </ul>
     *
     * @param dctx OFBiz dispatch context
     * @param context service context
     * @return service result map
     */
    public static Map<String, Object> enrichOrder(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();

        String orderId = (String) context.get("orderId");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Boolean dryRun = (Boolean) context.get("dryRun");

        if (UtilValidate.isEmpty(orderId)) {
            return ServiceUtil.returnError("Missing required parameter: orderId");
        }
        if (userLogin == null) {
            // In SECA usage with run-as-user="system", OFBiz should provide a userLogin.
            // If not present, fail safely.
            return ServiceUtil.returnError("Missing required parameter: userLogin");
        }

        String enabledStr = UtilProperties.getPropertyValue(PROPS_RESOURCE, "enabled", "Y");
        boolean enabled = "Y".equalsIgnoreCase(enabledStr);

        Map<String, Object> result = new HashMap<>(ServiceUtil.returnSuccess());
        if (!enabled) {
            result.put("enrichmentApplied", Boolean.FALSE);
            Debug.logInfo("kaviaorderenrichment disabled; skipping for orderId=" + orderId, MODULE);
            return result;
        }

        boolean isDryRun = Boolean.TRUE.equals(dryRun);

        try {
            GenericValue orderHeader = EntityQuery.use(delegator)
                    .from("OrderHeader")
                    .where("orderId", orderId)
                    .queryOne();

            if (orderHeader == null) {
                result.put("enrichmentApplied", Boolean.FALSE);
                return ServiceUtil.returnError("OrderHeader not found for orderId=" + orderId);
            }

            BigDecimal grandTotal = orderHeader.getBigDecimal("grandTotal");
            long orderItemCount = EntityQuery.use(delegator)
                    .from("OrderItem")
                    .where("orderId", orderId)
                    .queryCount();

            String attrPrefix = UtilProperties.getPropertyValue(PROPS_RESOURCE, "attribute.prefix", DEFAULT_ATTR_PREFIX);

            // Compute risk bucket based on configurable threshold.
            String thresholdStr = UtilProperties.getPropertyValue(PROPS_RESOURCE, "risk.high.grandTotal", "1000");
            BigDecimal highThreshold;
            try {
                highThreshold = new BigDecimal(thresholdStr);
            } catch (NumberFormatException nfe) {
                // Fail-safe default
                Debug.logWarning("Invalid risk.high.grandTotal=" + thresholdStr + "; defaulting to 1000", MODULE);
                highThreshold = new BigDecimal("1000");
            }

            String riskBucket;
            if (grandTotal == null) {
                riskBucket = "UNKNOWN";
            } else if (grandTotal.compareTo(highThreshold) >= 0) {
                riskBucket = "HIGH";
            } else {
                riskBucket = "NORMAL";
            }

            result.put("riskBucket", riskBucket);
            result.put("orderItemCount", orderItemCount);

            if (isDryRun) {
                result.put("enrichmentApplied", Boolean.FALSE);
                Debug.logInfo("Dry-run enrichment computed for orderId=" + orderId + " riskBucket=" + riskBucket, MODULE);
                return result;
            }

            Timestamp nowTs = UtilDateTime.nowTimestamp();

            // Persist as OrderAttribute records (existing entity; no schema change required).
            upsertOrderAttribute(delegator, orderId, attrPrefix + ATTR_RISK_BUCKET, riskBucket);
            upsertOrderAttribute(delegator, orderId, attrPrefix + ATTR_ITEM_COUNT, String.valueOf(orderItemCount));
            upsertOrderAttribute(delegator, orderId, attrPrefix + ATTR_ENRICHMENT_TS, nowTs.toString());

            result.put("enrichmentApplied", Boolean.TRUE);
            Debug.logInfo("Order enrichment applied for orderId=" + orderId
                    + " riskBucket=" + riskBucket + " itemCount=" + orderItemCount, MODULE);

            return result;
        } catch (GenericEntityException e) {
            Debug.logError(e, "Failed to enrich orderId=" + orderId, MODULE);
            return ServiceUtil.returnError("Failed to enrich orderId=" + orderId + ": " + e.getMessage());
        }
    }

    private static void upsertOrderAttribute(Delegator delegator, String orderId, String attrName, String attrValue)
            throws GenericEntityException {
        GenericValue existing = EntityQuery.use(delegator)
                .from("OrderAttribute")
                .where("orderId", orderId, "attrName", attrName)
                .queryOne();

        if (existing == null) {
            GenericValue created = delegator.makeValue("OrderAttribute");
            created.set("orderId", orderId);
            created.set("attrName", attrName);
            created.set("attrValue", attrValue);
            delegator.create(created);
        } else {
            existing.set("attrValue", attrValue);
            delegator.store(existing);
        }
    }
}
