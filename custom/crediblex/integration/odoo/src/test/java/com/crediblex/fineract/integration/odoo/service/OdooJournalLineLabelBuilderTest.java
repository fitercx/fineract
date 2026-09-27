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

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OdooJournalLineLabelBuilderTest {

    @Test
    void buildsFullLabelForDisbursement() {
        String label = OdooJournalLineLabelBuilder.build("Acme LLC", "DISBURSEMENT", LocalDate.of(2026, 7, 15), "RF", 3064L);

        assertEquals("Acme LLC - Disbursement 2026-07-15 - Invoice discounting - Loan ID 3064", label);
    }

    @Test
    void buildsLabelForRepaymentPayableFinancing() {
        String label = OdooJournalLineLabelBuilder.build("Acme LLC", "REPAYMENT", LocalDate.of(2026, 8, 1), "PF", 100L);

        assertEquals("Acme LLC - Repayment 2026-08-01 - Payable Financing - Loan ID 100", label);
    }

    @Test
    void buildsLabelForAccrualRevenueBasedFinancing() {
        String label = OdooJournalLineLabelBuilder.build("Acme LLC", "ACCRUAL", LocalDate.of(2026, 7, 31), "RBF", 55L);

        assertEquals("Acme LLC - Accrual 2026-07-31 - Revenue Based Financing - Loan ID 55", label);
    }

    @Test
    void buildsLabelForEarlyClosure() {
        String label = OdooJournalLineLabelBuilder.build("Acme LLC", "EARLY_CLOSURE", LocalDate.of(2026, 9, 10), "RF", 3064L);

        assertEquals("Acme LLC - Early Closure 2026-09-10 - Invoice discounting - Loan ID 3064", label);
    }

    @Test
    void omitsClientNameWhenMissing() {
        String label = OdooJournalLineLabelBuilder.build(null, "DISBURSEMENT", LocalDate.of(2026, 7, 15), "RF", 3064L);

        assertEquals("Disbursement 2026-07-15 - Invoice discounting - Loan ID 3064", label);
    }

    @Test
    void omitsProductWhenUnknownShortName() {
        String label = OdooJournalLineLabelBuilder.build("Acme LLC", "DISBURSEMENT", LocalDate.of(2026, 7, 15), "XYZ", 3064L);

        assertEquals("Acme LLC - Disbursement 2026-07-15 - Loan ID 3064", label);
    }

    @Test
    void omitsLoanIdWhenMissing() {
        String label = OdooJournalLineLabelBuilder.build("Acme LLC", "DISBURSEMENT", LocalDate.of(2026, 7, 15), "RF", null);

        assertEquals("Acme LLC - Disbursement 2026-07-15 - Invoice discounting", label);
    }

    @Test
    void returnsNullForUnsupportedEvent() {
        assertNull(OdooJournalLineLabelBuilder.build("Acme LLC", "SAVINGS_DEPOSIT", LocalDate.of(2026, 7, 15), "RF", 3064L));
        assertNull(OdooJournalLineLabelBuilder.build("Acme LLC", null, LocalDate.of(2026, 7, 15), "RF", 3064L));
    }

    @Test
    void omitsDateWhenMissingButKeepsEvent() {
        String label = OdooJournalLineLabelBuilder.build("Acme LLC", "DISBURSEMENT", null, "RF", 3064L);

        assertEquals("Acme LLC - Disbursement - Invoice discounting - Loan ID 3064", label);
    }
}
