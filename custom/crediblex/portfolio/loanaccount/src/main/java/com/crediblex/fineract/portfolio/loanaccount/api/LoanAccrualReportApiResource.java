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

import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportData;
import com.crediblex.fineract.portfolio.loanaccount.data.AccrualReportRowData;
import com.crediblex.fineract.portfolio.loanaccount.service.AccrualReportCalculator;
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
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

/**
 * Loan Accrual Report: the same figures as the frontend Accrual Report download and the nightly S3 export (Interest
 * Accrued (Actual) from posted accruals, Interest Forecast from the schedule interest still to accrue). Read-only.
 */
@Component
@Path("/v1/loans")
@Tag(name = "Loan Accrual Report", description = "Monthly accrual report for a loan: actual accrued interest and forecast interest")
@RequiredArgsConstructor
public class LoanAccrualReportApiResource {

    private final PlatformSecurityContext context;
    private final LoanMonthlyAccrualReportReadPlatformService accrualReportReadService;
    private final ToApiJsonSerializer<?> toApiJsonSerializer;

    @GET
    @Path("{loanId}/accrual-report")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve accrual report for a loan", description = "Month-wise principal, actual accrued interest (posted accrual transactions by value-date month) and forecast interest (scheduled interest still to accrue), as of the business date. Matches the Accrual Report download.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AccrualReportSchema.class))) })
    public String retrieveAccrualReport(@PathParam("loanId") @Parameter(description = "loanId") final Long loanId) {
        context.authenticatedUser().validateHasReadPermission("LOAN");
        final AccrualReportData report = accrualReportReadService.retrieveAccrualReport(loanId, DateUtils.getBusinessLocalDate());

        final Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("interestAccruedActual", toAmount(report.interestAccruedActualTotal()));
        totals.put("interestForecast", toAmount(report.interestForecastTotal()));
        totals.put("lifetimeInterest", toAmount(report.lifetimeInterestTotal()));

        final Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessDate", report.businessDate().toString());
        response.put("forecastEnabled", report.forecastEnabled());
        response.put("periods", report.rows().stream().map(LoanAccrualReportApiResource::toMap).toList());
        response.put("totals", totals);
        response.put("forecastNote", report.forecastEnabled() ? AccrualReportCalculator.FORECAST_NOTE : null);
        return toApiJsonSerializer.serialize(response);
    }

    private static Map<String, Object> toMap(final AccrualReportRowData row) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("index", row.index());
        m.put("endOfMonth", row.endOfMonth().toString());
        m.put("openingPrincipal", toAmount(row.openingPrincipal()));
        m.put("closingPrincipal", toAmount(row.closingPrincipal()));
        m.put("interestAccruedActual", toAmount(row.interestAccruedActual()));
        m.put("interestForecast", toAmount(row.interestForecast()));
        return m;
    }

    private static BigDecimal toAmount(final String formatted) {
        return formatted == null || formatted.isEmpty() ? null : new BigDecimal(formatted);
    }

    @Schema(description = "Loan accrual report")
    private static final class AccrualReportSchema {

        @Schema(example = "2026-11-24")
        public String businessDate;
        @Schema(example = "true")
        public boolean forecastEnabled;
        @ArraySchema(schema = @Schema(implementation = AccrualReportRowSchema.class))
        public List<AccrualReportRowSchema> periods;
        public AccrualReportTotalsSchema totals;
        @Schema(description = "Present when forecastEnabled is true")
        public String forecastNote;
    }

    @Schema(description = "Accrual report month row; blank amounts are null")
    private static final class AccrualReportRowSchema {

        @Schema(example = "1")
        public int index;
        @Schema(example = "2026-11-30")
        public String endOfMonth;
        @Schema(example = "350000.00")
        public BigDecimal openingPrincipal;
        @Schema(example = "325000.00")
        public BigDecimal closingPrincipal;
        @Schema(example = "3073.29", description = "Null for months starting after the business date")
        public BigDecimal interestAccruedActual;
        @Schema(example = "1210.55", description = "Null when nothing is forecast for the month")
        public BigDecimal interestForecast;
    }

    @Schema(description = "Accrual report totals")
    private static final class AccrualReportTotalsSchema {

        @Schema(example = "6146.58")
        public BigDecimal interestAccruedActual;
        @Schema(example = "3631.65", description = "Null when forecastEnabled is false")
        public BigDecimal interestForecast;
        @Schema(example = "9778.23")
        public BigDecimal lifetimeInterest;
    }
}
