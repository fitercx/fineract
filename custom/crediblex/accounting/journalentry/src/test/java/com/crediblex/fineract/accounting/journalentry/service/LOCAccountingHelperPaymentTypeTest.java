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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class LOCAccountingHelperPaymentTypeTest {

    private static final Long PAYMENT_TYPE_ID = 7L;

    private JdbcTemplate jdbcTemplate;
    private LOCAccountingHelper locAccountingHelper;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        locAccountingHelper = new LOCAccountingHelper(jdbcTemplate, mock(GLAccountRepository.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "DISBURSEMENT_OF_INVOICE", "PAYABLES_DISBURSEMENT" })
    void payableLOCDisbursementPaymentTypesAreDetected(String codeName) {
        stubCodeName(codeName);

        assertTrue(locAccountingHelper.isPayableLOCDisbursementPaymentType(PAYMENT_TYPE_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = { "RBF_LOAN_DISBURSEMENT", "PROCESSING_FEE", "" })
    void otherPaymentTypesAreNotPayableLOCDisbursement(String codeName) {
        stubCodeName(codeName);

        assertFalse(locAccountingHelper.isPayableLOCDisbursementPaymentType(PAYMENT_TYPE_ID));
    }

    @Test
    void payablesDisbursementIsNotAReceivableLOCDisbursement() {
        stubCodeName("PAYABLES_DISBURSEMENT");

        assertFalse(locAccountingHelper.isDisbursementOfInvoicePaymentType(PAYMENT_TYPE_ID));
    }

    @Test
    void unknownPaymentTypeIsNotPayableLOCDisbursement() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(PAYMENT_TYPE_ID)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertFalse(locAccountingHelper.isPayableLOCDisbursementPaymentType(PAYMENT_TYPE_ID));
    }

    private void stubCodeName(String codeName) {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(PAYMENT_TYPE_ID))).thenReturn(codeName);
    }
}
