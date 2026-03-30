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

package com.crediblex.fineract.portfolio.loc.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loc.data.BulkLoanDisbursementRequest;
import com.crediblex.fineract.portfolio.loc.data.BulkLoanDisbursementRequest.SingleLoanDisbursementRequest;
import com.crediblex.fineract.portfolio.loc.data.LocStatus;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCredit;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepositoryWrapper;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class LineOfCreditBulkDisbursementServiceImplTest {

    @Mock
    private PlatformSecurityContext securityContext;

    @Mock
    private LineOfCreditRepositoryWrapper lineOfCreditRepositoryWrapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    private LineOfCreditBulkDisbursementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LineOfCreditBulkDisbursementServiceImpl(securityContext, lineOfCreditRepositoryWrapper, jdbcTemplate,
                commandsSourceWritePlatformService);
    }

    @Test
    @DisplayName("Should throw exception when request is null")
    void shouldThrowExceptionWhenRequestIsNull() {
        Long lineOfCreditId = 1L;
        Long clientId = 1L;

        assertThrows(PlatformApiDataValidationException.class,
                () -> service.validateBulkDisbursementRequest(lineOfCreditId, clientId, null));
    }

    @Test
    @DisplayName("Should throw exception when loans list is empty")
    void shouldThrowExceptionWhenLoansListIsEmpty() {
        Long lineOfCreditId = 1L;
        Long clientId = 1L;
        BulkLoanDisbursementRequest request = BulkLoanDisbursementRequest.builder().loans(new ArrayList<>()).build();

        assertThrows(PlatformApiDataValidationException.class,
                () -> service.validateBulkDisbursementRequest(lineOfCreditId, clientId, request));
    }

    @Test
    @DisplayName("Should throw exception when LOC is not active")
    void shouldThrowExceptionWhenLocIsNotActive() {
        Long lineOfCreditId = 1L;
        Long clientId = 1L;

        LineOfCredit lineOfCredit = mock(LineOfCredit.class);
        Client client = mock(Client.class);

        when(client.getId()).thenReturn(clientId);
        when(lineOfCredit.getStatus()).thenReturn(LocStatus.SUBMITTED);
        when(lineOfCredit.getClient()).thenReturn(client);
        when(lineOfCreditRepositoryWrapper.findOneWithNotFoundDetection(lineOfCreditId)).thenReturn(lineOfCredit);

        SingleLoanDisbursementRequest loanRequest = SingleLoanDisbursementRequest.builder().loanId(100L).build();

        BulkLoanDisbursementRequest request = BulkLoanDisbursementRequest.builder().loans(List.of(loanRequest)).build();

        assertThrows(PlatformApiDataValidationException.class,
                () -> service.validateBulkDisbursementRequest(lineOfCreditId, clientId, request));
    }

    @Test
    @DisplayName("Should throw exception when loan does not belong to LOC")
    void shouldThrowExceptionWhenLoanNotUnderLoc() {
        Long lineOfCreditId = 1L;
        Long clientId = 1L;
        Long loanId = 100L;

        LineOfCredit lineOfCredit = mock(LineOfCredit.class);
        Client client = mock(Client.class);

        when(client.getId()).thenReturn(clientId);
        when(lineOfCredit.getStatus()).thenReturn(LocStatus.ACTIVE);
        when(lineOfCredit.getClient()).thenReturn(client);
        when(lineOfCreditRepositoryWrapper.findOneWithNotFoundDetection(lineOfCreditId)).thenReturn(lineOfCredit);

        // Return empty list - no loans under this LOC
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(lineOfCreditId))).thenReturn(new ArrayList<>());

        SingleLoanDisbursementRequest loanRequest = SingleLoanDisbursementRequest.builder().loanId(loanId).build();

        BulkLoanDisbursementRequest request = BulkLoanDisbursementRequest.builder().loans(List.of(loanRequest)).build();

        assertThrows(PlatformApiDataValidationException.class,
                () -> service.validateBulkDisbursementRequest(lineOfCreditId, clientId, request));
    }

    @Test
    @DisplayName("Should throw exception for duplicate loan IDs in request")
    void shouldThrowExceptionForDuplicateLoanIds() {
        Long lineOfCreditId = 1L;
        Long clientId = 1L;
        Long loanId = 100L;

        LineOfCredit lineOfCredit = mock(LineOfCredit.class);
        Client client = mock(Client.class);

        when(client.getId()).thenReturn(clientId);
        when(lineOfCredit.getStatus()).thenReturn(LocStatus.ACTIVE);
        when(lineOfCredit.getClient()).thenReturn(client);
        when(lineOfCreditRepositoryWrapper.findOneWithNotFoundDetection(lineOfCreditId)).thenReturn(lineOfCredit);

        // Same loan ID appears twice
        SingleLoanDisbursementRequest loanRequest1 = SingleLoanDisbursementRequest.builder().loanId(loanId).build();
        SingleLoanDisbursementRequest loanRequest2 = SingleLoanDisbursementRequest.builder().loanId(loanId).build();

        BulkLoanDisbursementRequest request = BulkLoanDisbursementRequest.builder().loans(List.of(loanRequest1, loanRequest2)).build();

        assertThrows(PlatformApiDataValidationException.class,
                () -> service.validateBulkDisbursementRequest(lineOfCreditId, clientId, request));
    }
}
