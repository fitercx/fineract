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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ForeclosureRefundOdooGroupingTest {

    @Test
    void refundSuffixOnForeclosureEntryIsRefundEvent() {
        assertEquals("EARLY_CLOSURE_REFUND", JournalEntryOdooTrackingService.resolveForeclosureRefundEvent("EARLY_CLOSURE", "L117416-R"));
    }

    @Test
    void settlementEntryStaysEarlyClosure() {
        assertEquals("EARLY_CLOSURE", JournalEntryOdooTrackingService.resolveForeclosureRefundEvent("EARLY_CLOSURE", "L117416"));
    }

    @Test
    void otherEventsAreUnchanged() {
        assertEquals("REPAYMENT", JournalEntryOdooTrackingService.resolveForeclosureRefundEvent("REPAYMENT", "L117416-R"));
        assertNull(JournalEntryOdooTrackingService.resolveForeclosureRefundEvent(null, "L117416-R"));
        assertEquals("EARLY_CLOSURE", JournalEntryOdooTrackingService.resolveForeclosureRefundEvent("EARLY_CLOSURE", null));
    }

    @Test
    void refundEntriesGetTheirOwnMoveOnTheSameJournal() {
        OdooJournalEntryService.MoveGroupKey settlement = OdooJournalEntryService.MoveGroupKey.of(8, "EARLY_CLOSURE");
        OdooJournalEntryService.MoveGroupKey refund = OdooJournalEntryService.MoveGroupKey.of(8, "EARLY_CLOSURE_REFUND");

        assertNotEquals(settlement, refund);
        assertEquals(settlement.journalId(), refund.journalId());
    }

    @Test
    void nonRefundEventsOnSameJournalShareOneMove() {
        assertEquals(OdooJournalEntryService.MoveGroupKey.of(2, "REPAYMENT"), OdooJournalEntryService.MoveGroupKey.of(2, "EARLY_CLOSURE"));
        assertEquals(OdooJournalEntryService.MoveGroupKey.of(2, null), OdooJournalEntryService.MoveGroupKey.of(2, "REPAYMENT"));
    }
}
