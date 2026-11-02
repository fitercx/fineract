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
package com.crediblex.fineract.accounting.journalentry.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class LOCAccountingHelperNonRecourseTest {

    private static final String NON_RECOURSE = LOCAccountingHelper.RECEIVABLE_NON_RECOURSE_LOC_PRODUCT_TYPE;

    private JdbcTemplate jdbcTemplate;
    private LOCAccountingHelper locAccountingHelper;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        locAccountingHelper = new LOCAccountingHelper(jdbcTemplate, mock(GLAccountRepository.class));
    }

    @Test
    void loanOnNonRecourseLocIsDetected() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(42L), eq(NON_RECOURSE))).thenReturn(1);

        assertTrue(locAccountingHelper.isNonRecourseLocLoan(42L));
    }

    @Test
    void loanOnOtherLocIsNotNonRecourse() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(42L), eq(NON_RECOURSE))).thenReturn(0);

        assertFalse(locAccountingHelper.isNonRecourseLocLoan(42L));
    }

    @Test
    void nullLoanIsNotNonRecourse() {
        assertFalse(locAccountingHelper.isNonRecourseLocLoan(null));
    }

    @Test
    void loanLookupFailurePropagates() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(42L), eq(NON_RECOURSE)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(DataAccessResourceFailureException.class, () -> locAccountingHelper.isNonRecourseLocLoan(42L));
    }

    @Test
    void accountServingOnlyNonRecourseIsDetected() {
        stubSavingsAccountTypes(List.of(NON_RECOURSE));

        assertTrue(locAccountingHelper.isNonRecourseLocSavingsAccount(7L));
    }

    @Test
    void accountSharedWithReceivableIsNotNonRecourse() {
        stubSavingsAccountTypes(List.of(NON_RECOURSE, "RECEIVABLE"));

        assertFalse(locAccountingHelper.isNonRecourseLocSavingsAccount(7L));
    }

    @Test
    void accountSharedWithLoanWithoutLocIsNotNonRecourse() {
        stubSavingsAccountTypes(List.of(NON_RECOURSE, "NONE"));

        assertFalse(locAccountingHelper.isNonRecourseLocSavingsAccount(7L));
    }

    @Test
    void savingsQueryChecksSettlementAndAssociatedLoans() {
        stubSavingsAccountTypes(List.of(NON_RECOURSE));

        locAccountingHelper.isNonRecourseLocSavingsAccount(7L);

        verify(jdbcTemplate).queryForList(
                and(contains("settlement_savings_account_id"), and(contains("m_portfolio_account_associations"), contains("LEFT JOIN"))),
                eq(String.class), eq(7L), eq(7L));
    }

    @Test
    void unlinkedSavingsAccountIsNotNonRecourse() {
        stubSavingsAccountTypes(List.of());

        assertFalse(locAccountingHelper.isNonRecourseLocSavingsAccount(7L));
    }

    @Test
    void savingsLookupFailurePropagates() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(7L), eq(7L)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThrows(DataAccessResourceFailureException.class, () -> locAccountingHelper.isNonRecourseLocSavingsAccount(7L));
    }

    private void stubSavingsAccountTypes(List<String> types) {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(7L), eq(7L))).thenReturn(types);
    }
}
