package com.crediblex.fineract.portfolio.loanaccount.api;

import com.crediblex.fineract.portfolio.loanaccount.data.BackdatedRepaymentPenaltyDTO;
import com.crediblex.fineract.portfolio.loanaccount.data.CredXLoanSearchResultData;
import com.crediblex.fineract.portfolio.loanaccount.data.CredXOverdueClientData;
import com.crediblex.fineract.portfolio.loanaccount.data.CredXOverdueLoansSummaryData;
import com.crediblex.fineract.portfolio.loanaccount.data.FutureLPIChargesData;
import com.crediblex.fineract.portfolio.loanaccount.service.CredXLoanReadPlatformServiceImpl;
import io.micrometer.common.util.StringUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.api.DateParam;
import org.apache.fineract.infrastructure.core.data.DateFormat;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.api.LoanTransactionsApiResource;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargePaidByReadService;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@Path("/v1/loans/crediblex")
public class CredibleXLoanTransactionsApiResource extends LoanTransactionsApiResource {

    private final CredXLoanReadPlatformServiceImpl credibleXLoanReadPlatformService;
    private final DefaultToApiJsonSerializer<BackdatedRepaymentPenaltyDTO> penaltyJsonSerializer;
    private final DefaultToApiJsonSerializer<FutureLPIChargesData> futureLPIJsonSerializer;
    private final DefaultToApiJsonSerializer<CredXOverdueClientData> overdueLoansJsonSerializer;
    private final DefaultToApiJsonSerializer<CredXOverdueLoansSummaryData> overdueLoansSummaryJsonSerializer;

    public CredibleXLoanTransactionsApiResource(PlatformSecurityContext context, LoanReadPlatformService loanReadPlatformService,
            ApiRequestParameterHelper apiRequestParameterHelper, DefaultToApiJsonSerializer<LoanTransactionData> toApiJsonSerializer,
            PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService,
            PaymentTypeReadPlatformService paymentTypeReadPlatformService, LoanChargePaidByReadService loanChargePaidByReadService,
            CredXLoanReadPlatformServiceImpl credibleXLoanReadPlatformService,
            DefaultToApiJsonSerializer<BackdatedRepaymentPenaltyDTO> penaltyJsonSerializer,
            DefaultToApiJsonSerializer<FutureLPIChargesData> futureLPIJsonSerializer,
            DefaultToApiJsonSerializer<CredXOverdueClientData> overdueLoansJsonSerializer,
            DefaultToApiJsonSerializer<CredXOverdueLoansSummaryData> overdueLoansSummaryJsonSerializer) {
        super(context, loanReadPlatformService, apiRequestParameterHelper, toApiJsonSerializer, commandsSourceWritePlatformService,
                paymentTypeReadPlatformService, loanChargePaidByReadService);
        this.credibleXLoanReadPlatformService = credibleXLoanReadPlatformService;
        this.penaltyJsonSerializer = penaltyJsonSerializer;
        this.futureLPIJsonSerializer = futureLPIJsonSerializer;
        this.overdueLoansJsonSerializer = overdueLoansJsonSerializer;
        this.overdueLoansSummaryJsonSerializer = overdueLoansSummaryJsonSerializer;
    }

    @GET
    @Path("{loanId}/transactions/template/penalties")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve Penalties Accrued up to a Given Date", description = "Returns only the penalties accrued on a loan up to the provided transaction date. "
            + "Useful when a repayment is backdated and you want penalties only till the actual transaction date, "
            + "instead of till the posting date.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BackdatedRepaymentPenaltyDTO.class))) })
    public String retrievePenaltiesTemplate(@PathParam("loanId") @Parameter(description = "loanId", required = true) final Long loanId,
            @QueryParam("transactionDate") @Parameter(description = "Transaction Date") final DateParam transactionDateDateParam,
            @QueryParam("dateFormat") @Parameter(description = "dateFormat") final String rawDateFormat,
            @QueryParam("locale") @Parameter(description = "locale") final String locale, @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        final DateFormat dateFormat = StringUtils.isBlank(rawDateFormat) ? null : new DateFormat(rawDateFormat);

        // Default to current date if no transactionDate is provided
        LocalDate transactionDate;
        if (transactionDateDateParam == null) {
            transactionDate = DateUtils.getLocalDateOfTenant();
        } else {
            transactionDate = transactionDateDateParam.getDate("transactionDate", dateFormat, locale);
        }

        // Call custom service method to compute penalties till transactionDate
        BackdatedRepaymentPenaltyDTO penaltiesData = this.credibleXLoanReadPlatformService.retrieveLoanPenaltiesTemplate(loanId,
                transactionDate);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        return this.penaltyJsonSerializer.serialize(settings, penaltiesData, this.responseDataParameters);
    }

    @GET
    @Path("search")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Search CREDX loan records", description = "Exact-match lookup by loan ID/account number or invoice number.")
    public List<CredXLoanSearchResultData> searchLoanRecords(
            @QueryParam("type") @Parameter(description = "loanId or invoiceNo", required = true) final String type,
            @QueryParam("value") @Parameter(description = "Exact search value", required = true) final String value) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);
        return this.credibleXLoanReadPlatformService.searchLoanRecords(type, value);
    }

    @GET
    @Path("overdue")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve CREDX overdue clients", description = "Returns a page of CLIENTS that each have at least one overdue loan (active loan with a past-due installment carrying a positive principal+interest+LPI balance). "
            + "Every one of the client's active loans is nested under it (overdue and non-overdue), each with a whole-loan 'outstanding' breakdown, a past-due 'overdue' breakdown, and its overdue installments. "
            + "Each client also carries a summary (totalOutstanding, totalOverdue - both split into principal/interest/fees/lpi - and totalLpiOutstanding). "
            + "Pagination and counting are by client. Optional case-insensitive search matches client display name/account no or any of the client's loans by loanId, loan accountNo or invoiceNumber.")
    public String retrieveOverdueLoans(@QueryParam("offset") @Parameter(description = "offset, in clients") final Integer offset,
            @QueryParam("limit") @Parameter(description = "limit (clients), max 200") final Integer limit,
            @QueryParam("search") @Parameter(description = "Optional search matched against client displayName/accountNo and the client's loans (loanId, loan accountNo, invoiceNumber)") final String search,
            @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        final Page<CredXOverdueClientData> overdueClients = this.credibleXLoanReadPlatformService.retrieveCrediblexOverdueLoans(offset,
                limit, search);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        return this.overdueLoansJsonSerializer.serialize(settings, overdueClients, this.responseDataParameters);
    }

    @GET
    @Path("overdue/summary")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve CREDX overdue portfolio summary", description = "Returns portfolio-level aggregates for the ENTIRE overdue population in a single call - every active loan of a client that has at least one overdue loan, "
            + "the same population GET /loans/crediblex/overdue covers with no search. Returns totalClients, totalLoans, a whole-loan totalOutstanding breakdown, a past-due totalOverdue breakdown (both split into principal/interest/fees/lpi) and totalLpiOutstanding. "
            + "It always covers the whole portfolio and is never affected by search or list filters, so it takes no query parameters. "
            + "Invariants: totalOutstanding.total = principal + interest + fees + lpi (same for totalOverdue), and totalLpiOutstanding = totalOverdue.lpi.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CredXOverdueLoansSummaryData.class))) })
    public String retrieveOverdueLoansSummary(@Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        final CredXOverdueLoansSummaryData summary = this.credibleXLoanReadPlatformService.retrieveCrediblexOverdueLoansSummary();
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        return this.overdueLoansSummaryJsonSerializer.serialize(settings, summary, this.responseDataParameters);
    }

    @GET
    @Path("{loanId}/transactions/future-charges")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Calculate Future LPI Charges", description = "Calculates the Late Payment Interest (LPI) charges that would be applied if payment is made on a future date. "
            + "This helps users understand the penalty amount they would incur if they delay payment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = FutureLPIChargesData.class))) })
    public String calculateFutureLPICharges(@PathParam("loanId") @Parameter(description = "loanId", required = true) final Long loanId,
            @QueryParam("transactionDate") @Parameter(description = "Future Transaction Date", required = true) final DateParam transactionDateParam,
            @QueryParam("dateFormat") @Parameter(description = "dateFormat") final String rawDateFormat,
            @QueryParam("locale") @Parameter(description = "locale") final String locale, @Context final UriInfo uriInfo) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);

        final DateFormat dateFormat = StringUtils.isBlank(rawDateFormat) ? new DateFormat("yyyy-MM-dd") : new DateFormat(rawDateFormat);

        if (transactionDateParam == null) {
            throw new IllegalArgumentException("transactionDate parameter is required");
        }

        final LocalDate futureDate = transactionDateParam.getDate("transactionDate", dateFormat, locale);

        // Validate that the date is not in the past
        final LocalDate currentDate = DateUtils.getLocalDateOfTenant();
        if (futureDate.isBefore(currentDate)) {
            throw new IllegalArgumentException("Transaction date cannot be in the past");
        }

        // Call custom service method to compute future LPI charges
        FutureLPIChargesData futureLPIData = this.credibleXLoanReadPlatformService.calculateFutureLPICharges(loanId, futureDate);

        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());

        return this.futureLPIJsonSerializer.serialize(settings, futureLPIData, this.responseDataParameters);
    }
}
