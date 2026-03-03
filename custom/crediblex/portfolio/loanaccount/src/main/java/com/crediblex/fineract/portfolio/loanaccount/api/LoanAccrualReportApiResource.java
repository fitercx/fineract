/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.crediblex.fineract.portfolio.loanaccount.api;

import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportRowData;
import com.crediblex.fineract.portfolio.loanaccount.service.LoanMonthlyAccrualReportReadPlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

/**
 * API resource for loan accrual report. Reuses the same calculation logic as "Generate Loan Monthly Accrual Summations"
 * job (monthly interest from accrual transactions); report uses present business date and does not perform any posting.
 */
@Component
@Path("/v1/loans")
@Tag(name = "Loan Accrual Report", description = "Monthly accrual report for a loan (aligned with Generate Loan Monthly Accrual Summations job)")
@RequiredArgsConstructor
public class LoanAccrualReportApiResource {

    private final PlatformSecurityContext context;
    private final LoanMonthlyAccrualReportReadPlatformService accrualReportReadService;
    private final ToApiJsonSerializer<?> toApiJsonSerializer;

    @GET
    @Path("{loanId}/accrual-report")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve accrual report for a loan", description = "Returns month-wise accrual data using the same calculation logic as Generate Loan Monthly Accrual Summations job (no posting). Uses present business date; current month shows interest accrued only up to that date.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = AccrualReportRowSchema.class)))) })
    public String retrieveAccrualReport(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId) {
        context.authenticatedUser().validateHasReadPermission("LOAN");
        LocalDate businessDate = DateUtils.getBusinessLocalDate();
        List<AccrualReportRowData> rows = accrualReportReadService.retrieveAccrualReportForLoan(loanId, businessDate);

        List<Map<String, Object>> rowMaps = rows.stream().map(this::toMap).toList();
        Map<String, Object> response = new HashMap<>();
        response.put("periods", rowMaps);
        return toApiJsonSerializer.serialize(response);
    }

    private Map<String, Object> toMap(AccrualReportRowData row) {
        Map<String, Object> m = new HashMap<>();
        m.put("index", row.getIndex());
        m.put("endOfMonth", row.getEndOfMonth() != null ? row.getEndOfMonth().toString() : null);
        m.put("openingPrincipal", row.getOpeningPrincipal());
        m.put("closingPrincipal", row.getClosingPrincipal());
        m.put("interestAccrued", row.getInterestAccrued());
        m.put("actualInterestAccrued", row.getActualInterestAccrued());
        return m;
    }

    @Schema(description = "Accrual report row")
    private static final class AccrualReportRowSchema {

        @Schema(example = "1")
        public int index;
        @Schema(example = "2025-11-30")
        public String endOfMonth;
        @Schema(example = "350000.00")
        public java.math.BigDecimal openingPrincipal;
        @Schema(example = "325000.00")
        public java.math.BigDecimal closingPrincipal;
        @Schema(example = "3073.29")
        public java.math.BigDecimal interestAccrued;
        @Schema(example = "3073.29")
        public java.math.BigDecimal actualInterestAccrued;
    }
}
