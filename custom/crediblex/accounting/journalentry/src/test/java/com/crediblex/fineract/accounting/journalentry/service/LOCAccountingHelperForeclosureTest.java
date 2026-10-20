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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class LOCAccountingHelperForeclosureTest {

    private JdbcTemplate jdbcTemplate;
    private LOCAccountingHelper locAccountingHelper;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        locAccountingHelper = new LOCAccountingHelper(jdbcTemplate, mock(GLAccountRepository.class));
    }

    @Test
    void loanForeclosureTransferIsDetected() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(928018L))).thenReturn(6);

        assertTrue(locAccountingHelper.isForeclosureAccountTransfer("L928018"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 4, 5 })
    void otherTransferTypesAreNotForeclosure(int transferType) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(928018L))).thenReturn(transferType);

        assertFalse(locAccountingHelper.isForeclosureAccountTransfer("L928018"));
    }

    @Test
    void repaymentWithoutTransferIsNotForeclosure() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(928018L))).thenThrow(new EmptyResultDataAccessException(1));

        assertFalse(locAccountingHelper.isForeclosureAccountTransfer("L928018"));
    }

    @Test
    void creditBalanceTreatsNullSumAsZero() {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(JournalEntryType.CREDIT.getValue()), eq(42L), eq(84L)))
                .thenReturn(null);

        assertEquals(BigDecimal.ZERO, locAccountingHelper.getLoanCreditBalanceForGLAccount(42L, 84L));
    }

    @Test
    void creditBalanceIsReturnedFromLedger() {
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(JournalEntryType.CREDIT.getValue()), eq(42L), eq(84L)))
                .thenReturn(new BigDecimal("60.410"));

        assertEquals(new BigDecimal("60.410"), locAccountingHelper.getLoanCreditBalanceForGLAccount(42L, 84L));
    }

    @Test
    void invoiceAmountIsReadFromLineOfCreditParams() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBigDecimal("invoice_amount")).thenReturn(new BigDecimal("100000.00"));
        stubInvoiceQuery(resultSet);

        assertEquals(new BigDecimal("100000.00"), locAccountingHelper.getInvoiceAmount(42L));
    }

    @Test
    void invoiceAmountIsNullWhenLoanHasNoParams() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        stubInvoiceQuery(resultSet);

        assertNull(locAccountingHelper.getInvoiceAmount(42L));
    }

    @SuppressWarnings("unchecked")
    private void stubInvoiceQuery(ResultSet resultSet) {
        when(jdbcTemplate.query(contains("m_loan_line_of_credit_params"), any(ResultSetExtractor.class), eq(42L)))
                .thenAnswer(invocation -> ((ResultSetExtractor<BigDecimal>) invocation.getArgument(1)).extractData(resultSet));
    }
}
