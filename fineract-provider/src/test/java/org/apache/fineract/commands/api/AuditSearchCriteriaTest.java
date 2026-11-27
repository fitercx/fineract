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
package org.apache.fineract.commands.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDate;
import org.apache.fineract.commands.data.request.AuditRequest;
import org.apache.fineract.infrastructure.security.utils.SQLBuilder;
import org.junit.jupiter.api.Test;

public class AuditSearchCriteriaTest {

    @Test
    public void makerRangeUsesDisplayedUtcDateAndIncludesBothDays() {
        AuditRequest request = new AuditRequest();
        request.setMakerDateTimeFrom("15 October 2026");
        request.setMakerDateTimeTo("18 November 2026");
        request.setDateFormat("dd MMMM yyyy");
        request.setLocale("en");

        SQLBuilder criteria = AuditSearchCriteria.build(request);

        assertEquals(" WHERE  " + AuditSearchCriteria.MADE_ON_DAY + " >= ?  AND  " + AuditSearchCriteria.MADE_ON_DAY + " <= ?",
                criteria.getSQLTemplate());
        assertArrayEquals(new Object[] { LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 18) }, criteria.getArguments());
        assertFalse(criteria.getSQLTemplate().contains("aud.made_on_date >="));
    }

    @Test
    public void checkerRangeIsSeparateFromMakerRange() {
        AuditRequest request = new AuditRequest();
        request.setMakerDateTimeFrom("2026-11-11");
        request.setMakerDateTimeTo("2026-11-11");
        request.setCheckerDateTimeFrom("2026-11-01");
        request.setCheckerDateTimeTo("2026-11-18");

        SQLBuilder criteria = AuditSearchCriteria.build(request);

        String sql = criteria.getSQLTemplate();
        assertEquals(" WHERE  " + AuditSearchCriteria.MADE_ON_DAY + " >= ?  AND  " + AuditSearchCriteria.MADE_ON_DAY + " <= ?  AND  "
                + AuditSearchCriteria.CHECKED_ON_DAY + " >= ?  AND  " + AuditSearchCriteria.CHECKED_ON_DAY + " <= ?", sql);
        assertArrayEquals(new Object[] { LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 18) }, criteria.getArguments());
    }

    @Test
    public void blankDatesAreNotAdded() {
        AuditRequest request = new AuditRequest();
        request.setMakerDateTimeFrom("  ");
        request.setActionName("CREATE");

        SQLBuilder criteria = AuditSearchCriteria.build(request);

        assertEquals(" WHERE  aud.action_name = ?", criteria.getSQLTemplate());
        assertArrayEquals(new Object[] { "CREATE" }, criteria.getArguments());
    }
}
