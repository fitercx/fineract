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
package com.crediblex.fineract.portfolio.loc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditData;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditReadPlatformService;
import jakarta.ws.rs.core.UriInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.exception.ResourceNotFoundException;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientCreditLinesApiResourceTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private LineOfCreditReadPlatformService readPlatformService;

    @Mock
    private DefaultToApiJsonSerializer<LineOfCreditData> toApiJsonSerializer;

    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;

    @Mock
    private AppUser appUser;

    @Mock
    private UriInfo uriInfo;

    @Mock
    private ApiRequestJsonSerializationSettings settings;

    @InjectMocks
    private ClientCreditLinesApiResource underTest;

    private static final Long CLIENT_ID = 34L;
    private static final Long LINE_OF_CREDIT_ID = 1L;

    @BeforeEach
    void setUp() {
        given(context.authenticatedUser()).willReturn(appUser);
        given(apiRequestParameterHelper.process(uriInfo.getQueryParameters())).willReturn(settings);
    }

    @Test
    void retrieveCreditLines_WithValidPermission_ShouldReturnCreditLines() {
        // given
        Collection<LineOfCreditData> expectedCreditLines = Arrays.asList(
                createLineOfCreditData(1L, CLIENT_ID, "Credit Line 1"),
                createLineOfCreditData(2L, CLIENT_ID, "Credit Line 2")
        );

        doNothing().when(appUser).validateHasReadPermission("LINE_OF_CREDIT");
        given(readPlatformService.retrieveAllLineOfCreditsForClient(CLIENT_ID)).willReturn(expectedCreditLines);
        given(toApiJsonSerializer.serialize(settings, expectedCreditLines, Collections.singleton("lineOfCredit")))
                .willReturn("{\"creditLines\": [...]}");

        // when
        String result = underTest.retrieveCreditLines(CLIENT_ID, uriInfo);

        // then
        assertThat(result).isEqualTo("{\"creditLines\": [...]}");
        verify(readPlatformService).retrieveAllLineOfCreditsForClient(CLIENT_ID);
        verify(toApiJsonSerializer).serialize(settings, expectedCreditLines, Collections.singleton("lineOfCredit"));
    }

    @Test
    void retrieveCreditLines_WithNoPermission_ShouldThrowException() {
        // given
        doThrow(new NoAuthorizationException("No permission")).when(appUser).validateHasReadPermission("LINE_OF_CREDIT");

        // when & then
        assertThatThrownBy(() -> underTest.retrieveCreditLines(CLIENT_ID, uriInfo))
                .isInstanceOf(NoAuthorizationException.class)
                .hasMessage("No permission");

        verifyNoInteractions(readPlatformService);
        verifyNoInteractions(toApiJsonSerializer);
    }

    @Test
    void retrieveCreditLines_WithEmptyResult_ShouldReturnEmptyCollection() {
        // given
        Collection<LineOfCreditData> emptyCreditLines = Collections.emptyList();

        doNothing().when(appUser).validateHasReadPermission("LINE_OF_CREDIT");
        given(readPlatformService.retrieveAllLineOfCreditsForClient(CLIENT_ID)).willReturn(emptyCreditLines);
        given(toApiJsonSerializer.serialize(settings, emptyCreditLines, Collections.singleton("lineOfCredit")))
                .willReturn("[]");

        // when
        String result = underTest.retrieveCreditLines(CLIENT_ID, uriInfo);

        // then
        assertThat(result).isEqualTo("[]");
        verify(readPlatformService).retrieveAllLineOfCreditsForClient(CLIENT_ID);
        verify(toApiJsonSerializer).serialize(settings, emptyCreditLines, Collections.singleton("lineOfCredit"));
    }

    @Test
    void retrieveOne_WithValidPermissionAndMatchingClient_ShouldReturnCreditLine() {
        // given
        LineOfCreditData expectedCreditLine = createLineOfCreditData(LINE_OF_CREDIT_ID, CLIENT_ID, "Credit Line 1");

        doNothing().when(appUser).validateHasReadPermission("LINE_OF_CREDIT");
        given(readPlatformService.retrieveOne(LINE_OF_CREDIT_ID)).willReturn(expectedCreditLine);
        given(toApiJsonSerializer.serialize(settings, expectedCreditLine, Collections.singleton("lineOfCredit")))
                .willReturn("{\"id\": 1, \"clientId\": 34}");

        // when
        String result = underTest.retrieveOne(CLIENT_ID, LINE_OF_CREDIT_ID, uriInfo);

        // then
        assertThat(result).isEqualTo("{\"id\": 1, \"clientId\": 34}");
        verify(readPlatformService).retrieveOne(LINE_OF_CREDIT_ID);
        verify(toApiJsonSerializer).serialize(settings, expectedCreditLine, Collections.singleton("lineOfCredit"));
    }

    @Test
    void retrieveOne_WithNoPermission_ShouldThrowException() {
        // given
        doThrow(new NoAuthorizationException("No permission")).when(appUser).validateHasReadPermission("LINE_OF_CREDIT");

        // when & then
        assertThatThrownBy(() -> underTest.retrieveOne(CLIENT_ID, LINE_OF_CREDIT_ID, uriInfo))
                .isInstanceOf(NoAuthorizationException.class)
                .hasMessage("No permission");

        verifyNoInteractions(readPlatformService);
        verifyNoInteractions(toApiJsonSerializer);
    }

    @Test
    void retrieveOne_WithNonMatchingClient_ShouldThrowException() {
        // given
        LineOfCreditData creditLine = createLineOfCreditData(LINE_OF_CREDIT_ID, 999L, "Credit Line 1"); // Different client ID

        doNothing().when(appUser).validateHasReadPermission("LINE_OF_CREDIT");
        given(readPlatformService.retrieveOne(LINE_OF_CREDIT_ID)).willReturn(creditLine);

        // when & then
        assertThatThrownBy(() -> underTest.retrieveOne(CLIENT_ID, LINE_OF_CREDIT_ID, uriInfo))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Line of credit not found for the specified client");

        verify(readPlatformService).retrieveOne(LINE_OF_CREDIT_ID);
        verifyNoInteractions(toApiJsonSerializer);
    }

    @Test
    void retrieveOne_WithNullCreditLine_ShouldThrowException() {
        // given
        doNothing().when(appUser).validateHasReadPermission("LINE_OF_CREDIT");
        given(readPlatformService.retrieveOne(LINE_OF_CREDIT_ID)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> underTest.retrieveOne(CLIENT_ID, LINE_OF_CREDIT_ID, uriInfo))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Line of credit not found for the specified client");

        verify(readPlatformService).retrieveOne(LINE_OF_CREDIT_ID);
        verifyNoInteractions(toApiJsonSerializer);
    }

    @Test
    void retrieveOne_WithNullClientId_ShouldThrowException() {
        // given
        LineOfCreditData creditLine = createLineOfCreditData(LINE_OF_CREDIT_ID, null, "Credit Line 1");

        doNothing().when(appUser).validateHasReadPermission("LINE_OF_CREDIT");
        given(readPlatformService.retrieveOne(LINE_OF_CREDIT_ID)).willReturn(creditLine);

        // when & then
        assertThatThrownBy(() -> underTest.retrieveOne(CLIENT_ID, LINE_OF_CREDIT_ID, uriInfo))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Line of credit not found for the specified client");

        verify(readPlatformService).retrieveOne(LINE_OF_CREDIT_ID);
        verifyNoInteractions(toApiJsonSerializer);
    }

    @Test
    void retrieveCreditLines_WithNullClientId_ShouldHandleGracefully() {
        // given
        Collection<LineOfCreditData> emptyCreditLines = Collections.emptyList();

        doNothing().when(appUser).validateHasReadPermission("LINE_OF_CREDIT");
        given(readPlatformService.retrieveAllLineOfCreditsForClient(null)).willReturn(emptyCreditLines);
        given(toApiJsonSerializer.serialize(settings, emptyCreditLines, Collections.singleton("lineOfCredit")))
                .willReturn("[]");

        // when
        String result = underTest.retrieveCreditLines(null, uriInfo);

        // then
        assertThat(result).isEqualTo("[]");
        verify(readPlatformService).retrieveAllLineOfCreditsForClient(null);
        verify(toApiJsonSerializer).serialize(settings, emptyCreditLines, Collections.singleton("lineOfCredit"));
    }

    @Test
    void retrieveOne_WithNullLineOfCreditId_ShouldHandleGracefully() {
        // given
        doNothing().when(appUser).validateHasReadPermission("LINE_OF_CREDIT");
        given(readPlatformService.retrieveOne(null)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> underTest.retrieveOne(CLIENT_ID, null, uriInfo))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Line of credit not found for the specified client");

        verify(readPlatformService).retrieveOne(null);
        verifyNoInteractions(toApiJsonSerializer);
    }

    @Test
    void retrieveCreditLines_WithLargeClientId_ShouldWorkCorrectly() {
        // given
        Long largeClientId = Long.MAX_VALUE;
        Collection<LineOfCreditData> expectedCreditLines = Arrays.asList(
                createLineOfCreditData(1L, largeClientId, "Credit Line 1")
        );

        doNothing().when(appUser).validateHasReadPermission("LINE_OF_CREDIT");
        given(readPlatformService.retrieveAllLineOfCreditsForClient(largeClientId)).willReturn(expectedCreditLines);
        given(toApiJsonSerializer.serialize(settings, expectedCreditLines, Collections.singleton("lineOfCredit")))
                .willReturn("{\"creditLines\": [...]}");

        // when
        String result = underTest.retrieveCreditLines(largeClientId, uriInfo);

        // then
        assertThat(result).isEqualTo("{\"creditLines\": [...]}");
        verify(readPlatformService).retrieveAllLineOfCreditsForClient(largeClientId);
        verify(toApiJsonSerializer).serialize(settings, expectedCreditLines, Collections.singleton("lineOfCredit"));
    }

    @Test
    void retrieveOne_WithLargeIds_ShouldWorkCorrectly() {
        // given
        Long largeClientId = Long.MAX_VALUE;
        Long largeCreditLineId = Long.MAX_VALUE - 1;
        LineOfCreditData expectedCreditLine = createLineOfCreditData(largeCreditLineId, largeClientId, "Credit Line 1");

        doNothing().when(appUser).validateHasReadPermission("LINE_OF_CREDIT");
        given(readPlatformService.retrieveOne(largeCreditLineId)).willReturn(expectedCreditLine);
        given(toApiJsonSerializer.serialize(settings, expectedCreditLine, Collections.singleton("lineOfCredit")))
                .willReturn("{\"id\": " + largeCreditLineId + ", \"clientId\": " + largeClientId + "}");

        // when
        String result = underTest.retrieveOne(largeClientId, largeCreditLineId, uriInfo);

        // then
        assertThat(result).isEqualTo("{\"id\": " + largeCreditLineId + ", \"clientId\": " + largeClientId + "}");
        verify(readPlatformService).retrieveOne(largeCreditLineId);
        verify(toApiJsonSerializer).serialize(settings, expectedCreditLine, Collections.singleton("lineOfCredit"));
    }

    private LineOfCreditData createLineOfCreditData(Long id, Long clientId, String name) {
        LineOfCreditData data = mock(LineOfCreditData.class);
        when(data.getId()).thenReturn(id);
        when(data.getClientId()).thenReturn(clientId);
        when(data.getName()).thenReturn(name);
        when(data.getProductType()).thenReturn("Payable");
        when(data.getMaximumAmount()).thenReturn(new BigDecimal("5000000.00"));
        when(data.getAvailableBalance()).thenReturn(new BigDecimal("5000000.00"));
        when(data.getConsumedAmount()).thenReturn(BigDecimal.ZERO);
        when(data.getActivationStatus()).thenReturn(new EnumOptionData(0L, "INACTIVE", "INACTIVE"));
        when(data.getStartDate()).thenReturn(LocalDate.of(2025, 1, 1));
        when(data.getEndDate()).thenReturn(LocalDate.of(2025, 12, 31));
        return data;
    }
}
