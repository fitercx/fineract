/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.integration.odoo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ReceivableLocLpiJournalMappingTest {

    private final OdooIntegrationReadPlatformServiceImpl service = new OdooIntegrationReadPlatformServiceImpl(null, null);

    @Test
    void invoiceDiscountingRepaymentPostsLpiOn300014() {
        assertEquals("BNK2", service.findReceivableLOCJournalCodeForGlCode("300014", "REPAYMENT", false));
        assertNull(service.findReceivableLOCJournalCodeForGlCode("300017", "REPAYMENT", false));
    }

    @Test
    void invoiceDiscountingClosurePostsLpiOn300014() {
        assertEquals("BNK8", service.findReceivableLOCJournalCodeForGlCode("300014", "EARLY_CLOSURE", false));
    }

    @Test
    void payableFinancingRepaymentKeepsLpiOn300017() {
        assertEquals("BNK2", service.findPayableLOCJournalCodeForGlCode("300017", "REPAYMENT", false));
        assertNull(service.findPayableLOCJournalCodeForGlCode("300014", "REPAYMENT", false));
    }
}
