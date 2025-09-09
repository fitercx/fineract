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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crediblex.fineract.portfolio.loc.data.LineOfCreditActionRequest;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditData;
import com.crediblex.fineract.portfolio.loc.data.LineOfCreditRequest;
import com.crediblex.fineract.portfolio.loc.service.LineOfCreditReadPlatformService;
import jakarta.ws.rs.core.UriInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
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
class LineOfCreditApiResourceTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private LineOfCreditReadPlatformService readPlatformService;

    @Mock
    private DefaultToApiJsonSerializer<LineOfCreditData> toApiJsonSerializer;

    @Mock
    private ApiRequestParameterHelper apiRequestParameterHelper;

    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Mock
    private AppUser appUser;

    @Mock
    private UriInfo uriInfo;

    @Mock
    private ApiRequestJsonSerializationSettings settings;

    @Mock
    private CommandWrapper commandWrapper;

    @Mock
    private CommandProcessingResult commandProcessingResult;

    @InjectMocks
    private LineOfCreditApiResource underTest;

    private static final Long CLIENT_ID = 34L;
    private static final Long LINE_OF_CREDIT_ID = 1L;
    private static final LineOfCreditRequest LINE_OF_CREDIT_REQUEST = new LineOfCreditRequest();
    private static final LineOfCreditActionRequest LINE_OF_CREDIT_ACTION_REQUEST = new LineOfCreditActionRequest("yyyy-MM-dd", "en");

    @BeforeEach
    void setUp() {
        given(context.authenticatedUser()).willReturn(appUser);
        given(apiRequestParameterHelper.process(uriInfo.getQueryParameters())).willReturn(settings);
        
        // Set up the test request
        LINE_OF_CREDIT_REQUEST.setClientId(34L);
        LINE_OF_CREDIT_REQUEST.setName("Test Credit Line");
        LINE_OF_CREDIT_REQUEST.setProductType("PAYABLE");
        LINE_OF_CREDIT_REQUEST.setMaximumAmount("5000000");
        LINE_OF_CREDIT_REQUEST.setStartDate("29 August 2025");
        LINE_OF_CREDIT_REQUEST.setEndDate("29 October 2025");
        LINE_OF_CREDIT_REQUEST.setDateFormat("dd MMMM yyyy");
        LINE_OF_CREDIT_REQUEST.setLocale("en");
    }

    @Test
    void retrieveTemplate_WithValidPermission_ShouldReturnTemplate() {
        // given
        LineOfCreditData template = createLineOfCreditData(null, null, "Template");

        doNothing().when(appUser).validateHasReadPermission("LINE_OF_CREDIT");
        given(readPlatformService.retrieveTemplate()).willReturn(template);
        given(toApiJsonSerializer.serialize(settings, Collections.singleton(template), Collections.singleton("lineOfCredit")))
                .willReturn("{\"template\": {...}}");

        // when
        String result = underTest.retrieveTemplate(CLIENT_ID, uriInfo);

        // then
        assertThat(result).isEqualTo("{\"template\": {...}}");
        verify(readPlatformService).retrieveTemplate();
        verify(toApiJsonSerializer).serialize(settings, Collections.singleton(template), Collections.singleton("lineOfCredit"));
    }

    @Test
    void retrieveTemplate_WithNoPermission_ShouldThrowException() {
        // given
        doThrow(new NoAuthorizationException("No permission")).when(appUser).validateHasReadPermission("LINE_OF_CREDIT");

        // when & then
        assertThatThrownBy(() -> underTest.retrieveTemplate(CLIENT_ID, uriInfo))
                .isInstanceOf(NoAuthorizationException.class)
                .hasMessage("No permission");

        verifyNoInteractions(readPlatformService);
        verifyNoInteractions(toApiJsonSerializer);
    }

    @Test
    void retrieveOne_WithValidPermission_ShouldReturnCreditLine() {
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
    void create_WithValidPermission_ShouldCreateCreditLine() {
        // given
        doNothing().when(appUser).validateHasCreatePermission("LINE_OF_CREDIT");
        given(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class))).willReturn(commandProcessingResult);
        given(toApiJsonSerializer.serialize(commandProcessingResult)).willReturn("{\"resourceId\": 1}");

        // when
        String result = underTest.create(CLIENT_ID, LINE_OF_CREDIT_REQUEST);
        // then
        assertThat(result).isEqualTo("{\"resourceId\": 1}");
        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verify(toApiJsonSerializer).serialize(commandProcessingResult);
    }

    @Test
    void create_WithNoPermission_ShouldThrowException() {
        // given
        doThrow(new NoAuthorizationException("No permission")).when(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));

        // when & then
        assertThatThrownBy(() -> underTest.create(CLIENT_ID, LINE_OF_CREDIT_REQUEST))
                .isInstanceOf(NoAuthorizationException.class)
                .hasMessage("No permission");

        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verifyNoInteractions(toApiJsonSerializer);
    }


    @Test
    void update_WithValidPermission_ShouldUpdateCreditLine() {
        // given
        doNothing().when(appUser).validateHasUpdatePermission("LINE_OF_CREDIT");
        given(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class))).willReturn(commandProcessingResult);
        given(toApiJsonSerializer.serialize(commandProcessingResult)).willReturn("{\"resourceId\": 1}");

        // when
        String result = underTest.update(CLIENT_ID, LINE_OF_CREDIT_ID, LINE_OF_CREDIT_REQUEST);
        // then
        assertThat(result).isEqualTo("{\"resourceId\": 1}");
        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verify(toApiJsonSerializer).serialize(commandProcessingResult);
    }

    @Test
    void update_WithNoPermission_ShouldThrowException() {
        // given
        doThrow(new NoAuthorizationException("No permission")).when(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));

        // when & then
        assertThatThrownBy(() -> underTest.update(CLIENT_ID, LINE_OF_CREDIT_ID, LINE_OF_CREDIT_REQUEST))
                .isInstanceOf(NoAuthorizationException.class)
                .hasMessage("No permission");

        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verifyNoInteractions(toApiJsonSerializer);
    }

    @Test
    void activate_WithValidPermission_ShouldActivateCreditLine() {
        // given
        doNothing().when(appUser).validateHasUpdatePermission("LINE_OF_CREDIT");
        given(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class))).willReturn(commandProcessingResult);
        given(toApiJsonSerializer.serialize(commandProcessingResult)).willReturn("{\"resourceId\": 1}");

        // when
        String result = underTest.activate(CLIENT_ID, LINE_OF_CREDIT_ID, LINE_OF_CREDIT_ACTION_REQUEST);

        // then
        assertThat(result).isEqualTo("{\"resourceId\": 1}");
        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verify(toApiJsonSerializer).serialize(commandProcessingResult);
    }

    @Test
    void activate_WithNoPermission_ShouldThrowException() {
        // given
        doThrow(new NoAuthorizationException("No permission")).when(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));

        // when & then
        assertThatThrownBy(() -> underTest.activate(CLIENT_ID, LINE_OF_CREDIT_ID, LINE_OF_CREDIT_ACTION_REQUEST))
                .isInstanceOf(NoAuthorizationException.class)
                .hasMessage("No permission");

        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verifyNoInteractions(toApiJsonSerializer);
    }

    @Test
    void activate_WithNullRequest_ShouldActivateCreditLineWithDefaultRequest() {
        // given
        doNothing().when(appUser).validateHasUpdatePermission("LINE_OF_CREDIT");
        given(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class))).willReturn(commandProcessingResult);
        given(toApiJsonSerializer.serialize(commandProcessingResult)).willReturn("{\"resourceId\": 1}");

        // when
        String result = underTest.activate(CLIENT_ID, LINE_OF_CREDIT_ID, null);
        // then
        assertThat(result).isEqualTo("{\"resourceId\": 1}");
        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verify(toApiJsonSerializer).serialize(commandProcessingResult);
    }

    @Test
    void deactivate_WithValidPermission_ShouldDeactivateCreditLine() {
        // given
        doNothing().when(appUser).validateHasUpdatePermission("LINE_OF_CREDIT");
        given(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class))).willReturn(commandProcessingResult);
        given(toApiJsonSerializer.serialize(commandProcessingResult)).willReturn("{\"resourceId\": 1}");

        // when
        String result = underTest.deactivate(CLIENT_ID, LINE_OF_CREDIT_ID, LINE_OF_CREDIT_ACTION_REQUEST);
        // then
        assertThat(result).isEqualTo("{\"resourceId\": 1}");
        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verify(toApiJsonSerializer).serialize(commandProcessingResult);
    }

    @Test
    void deactivate_WithNoPermission_ShouldThrowException() {
        // given
        doThrow(new NoAuthorizationException("No permission")).when(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));

        // when & then
        assertThatThrownBy(() -> underTest.deactivate(CLIENT_ID, LINE_OF_CREDIT_ID, LINE_OF_CREDIT_ACTION_REQUEST))
                .isInstanceOf(NoAuthorizationException.class)
                .hasMessage("No permission");

        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verifyNoInteractions(toApiJsonSerializer);
    }

    @Test
    void deactivate_WithNullRequest_ShouldDeactivateCreditLineWithDefaultRequest() {
        // given
        doNothing().when(appUser).validateHasUpdatePermission("LINE_OF_CREDIT");
        given(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class))).willReturn(commandProcessingResult);
        given(toApiJsonSerializer.serialize(commandProcessingResult)).willReturn("{\"resourceId\": 1}");

        // when
        String result = underTest.deactivate(CLIENT_ID, LINE_OF_CREDIT_ID, null);

        // then
        assertThat(result).isEqualTo("{\"resourceId\": 1}");
        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verify(toApiJsonSerializer).serialize(commandProcessingResult);
    }

    @Test
    void delete_WithValidPermission_ShouldDeleteCreditLine() {
        // given
        doNothing().when(appUser).validateHasUpdatePermission("LINE_OF_CREDIT");
        given(commandsSourceWritePlatformService.logCommandSource(any(CommandWrapper.class))).willReturn(commandProcessingResult);
        given(toApiJsonSerializer.serialize(commandProcessingResult)).willReturn("{\"resourceId\": 1}");

        // when
        String result = underTest.delete(CLIENT_ID, LINE_OF_CREDIT_ID);
        // then
        assertThat(result).isEqualTo("{\"resourceId\": 1}");
        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verify(toApiJsonSerializer).serialize(commandProcessingResult);
    }

    @Test
    void delete_WithNoPermission_ShouldThrowException() {
        // given
        doThrow(new NoAuthorizationException("No permission")).when(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));

        // when & then
        assertThatThrownBy(() -> underTest.delete(CLIENT_ID, LINE_OF_CREDIT_ID))
                .isInstanceOf(NoAuthorizationException.class)
                .hasMessage("No permission");

        verify(commandsSourceWritePlatformService).logCommandSource(any(CommandWrapper.class));
        verifyNoInteractions(toApiJsonSerializer);
    }

    @Test
    void retrieveAllForClient_WithValidPermission_ShouldReturnClientCreditLines() {
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
        String result = underTest.retrieveAllForClient(CLIENT_ID, uriInfo);

        // then
        assertThat(result).isEqualTo("{\"creditLines\": [...]}");
        verify(readPlatformService).retrieveAllLineOfCreditsForClient(CLIENT_ID);
        verify(toApiJsonSerializer).serialize(settings, expectedCreditLines, Collections.singleton("lineOfCredit"));
    }

    @Test
    void retrieveAllForClient_WithNoPermission_ShouldThrowException() {
        // given
        doThrow(new NoAuthorizationException("No permission")).when(appUser).validateHasReadPermission("LINE_OF_CREDIT");

        // when & then
        assertThatThrownBy(() -> underTest.retrieveAllForClient(CLIENT_ID, uriInfo))
                .isInstanceOf(NoAuthorizationException.class)
                .hasMessage("No permission");

        verifyNoInteractions(readPlatformService);
        verifyNoInteractions(toApiJsonSerializer);
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
