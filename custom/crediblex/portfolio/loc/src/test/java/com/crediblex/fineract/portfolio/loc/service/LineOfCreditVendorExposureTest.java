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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loc.charge.service.LineOfCreditChargeReadService;
import com.crediblex.fineract.portfolio.loc.data.VendorExposureResponse;
import com.crediblex.fineract.portfolio.loc.domain.LineOfCreditRepository;
import com.crediblex.fineract.portfolio.loc.exception.VendorNotFoundException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class LineOfCreditVendorExposureTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ClientReadPlatformService clientReadPlatformService;
    @Mock
    private LineOfCreditChargeReadService chargeReadService;
    @Mock
    private StaffReadPlatformService staffReadPlatformService;
    @Mock
    private LineOfCreditRepository lineOfCreditRepository;

    private LineOfCreditReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LineOfCreditReadPlatformServiceImpl(context, jdbcTemplate, clientReadPlatformService, chargeReadService,
                staffReadPlatformService, lineOfCreditRepository);
    }

    @Test
    @DisplayName("parseAndValidateVendorIds rejects null/blank/empty")
    void parseRejectsEmptyIds() {
        assertThrows(PlatformApiDataValidationException.class, () -> LineOfCreditReadPlatformServiceImpl.parseAndValidateVendorIds(null));
        assertThrows(PlatformApiDataValidationException.class, () -> LineOfCreditReadPlatformServiceImpl.parseAndValidateVendorIds(""));
        assertThrows(PlatformApiDataValidationException.class,
                () -> LineOfCreditReadPlatformServiceImpl.parseAndValidateVendorIds("  , , "));
    }

    @Test
    @DisplayName("parseAndValidateVendorIds rejects non-numeric tokens")
    void parseRejectsNonNumeric() {
        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> LineOfCreditReadPlatformServiceImpl.parseAndValidateVendorIds("1,abc,3"));
        assertEquals("abc", ex.getErrors().get(0).getArgs().get(0).getValue());
    }

    @Test
    @DisplayName("parseAndValidateVendorIds dedupes while preserving order")
    void parseDedupesPreservingOrder() {
        assertEquals(List.of(10L, 20L, 30L), LineOfCreditReadPlatformServiceImpl.parseAndValidateVendorIds("10,20,10,30,20"));
    }

    @Test
    @DisplayName("retrieveVendorsExposure returns ordered utilization rows")
    @SuppressWarnings({ "unchecked", "deprecation" })
    void retrieveVendorsExposureSuccess() {
        final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sqlCaptor.capture(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(List.of(new VendorExposureResponse(20L, "Beta LLC", new BigDecimal("15000")),
                        new VendorExposureResponse(10L, "Alpha LLC", new BigDecimal("90000"))));

        final Collection<VendorExposureResponse> result = service.retrieveVendorsExposure("10,20");
        final List<VendorExposureResponse> list = result.stream().toList();

        assertEquals(2, list.size());
        assertEquals(10L, list.get(0).getId());
        assertEquals("Alpha LLC", list.get(0).getName());
        assertEquals(new BigDecimal("90000"), list.get(0).getUtilization());
        assertEquals(20L, list.get(1).getId());

        final String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("principal_outstanding_derived"));
        assertTrue(sql.contains("loan_status_id IN (100, 200)"));
        assertTrue(sql.contains("m_loan_approver_buyers_suppliers"));
    }

    @Test
    @DisplayName("retrieveVendorsExposure 404 lists missing vendor ids")
    @SuppressWarnings({ "unchecked", "deprecation" })
    void retrieveVendorsExposureMissingIds() {
        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(List.of(new VendorExposureResponse(10L, "Alpha LLC", BigDecimal.ZERO)));

        final VendorNotFoundException ex = assertThrows(VendorNotFoundException.class,
                () -> service.retrieveVendorsExposure("10,99,11"));
        assertTrue(ex.getDefaultUserMessage().contains("99"));
        assertTrue(ex.getDefaultUserMessage().contains("11"));
    }
}
