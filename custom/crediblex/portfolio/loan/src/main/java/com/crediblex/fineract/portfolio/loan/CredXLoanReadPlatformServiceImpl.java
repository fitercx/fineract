package com.crediblex.fineract.portfolio.loan;

import com.crediblex.fineract.portfolio.loan.repository.CredXLoanTransactionRepository;
import com.crediblex.fineract.portfolio.loan.queries.LoanQueries.RapaymentStatusQuery;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.portfolio.accountdetails.service.AccountDetailsReadPlatformService;
import org.apache.fineract.portfolio.calendar.service.CalendarReadPlatformService;
import org.apache.fineract.portfolio.charge.service.ChargeReadPlatformService;
import org.apache.fineract.portfolio.client.service.ClientReadPlatformService;
import org.apache.fineract.portfolio.delinquency.service.DelinquencyReadPlatformService;
import org.apache.fineract.portfolio.floatingrates.service.FloatingRatesReadPlatformService;
import org.apache.fineract.portfolio.fund.service.FundReadPlatformService;
import org.apache.fineract.portfolio.group.service.GroupReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanMapper;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanTransactionMapper;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanForeclosureValidator;
import org.apache.fineract.portfolio.loanaccount.service.*;
import org.apache.fineract.portfolio.loanproduct.service.LoanDropdownReadPlatformService;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.apache.fineract.portfolio.loanproduct.service.LoanProductReadPlatformService;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CredXLoanReadPlatformServiceImpl extends LoanReadPlatformServiceImpl {

    private final CredXLoanTransactionRepository credXLoanTransactionRepository;

    public CredXLoanReadPlatformServiceImpl(JdbcTemplate jdbcTemplate, PlatformSecurityContext context,
            LoanRepositoryWrapper loanRepositoryWrapper, ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository,
            LoanProductReadPlatformService loanProductReadPlatformService, ClientReadPlatformService clientReadPlatformService,
            GroupReadPlatformService groupReadPlatformService, LoanDropdownReadPlatformService loanDropdownReadPlatformService,
            FundReadPlatformService fundReadPlatformService, ChargeReadPlatformService chargeReadPlatformService,
            CodeValueReadPlatformService codeValueReadPlatformService, CalendarReadPlatformService calendarReadPlatformService,
            StaffReadPlatformService staffReadPlatformService, PaginationHelper paginationHelper,
            PaymentTypeReadPlatformService paymentTypeReadPlatformService,
            LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory,
            FloatingRatesReadPlatformService floatingRatesReadPlatformService, LoanUtilService loanUtilService,
            ConfigurationDomainService configurationDomainService, AccountDetailsReadPlatformService accountDetailsReadPlatformService,
            ColumnValidator columnValidator, DatabaseSpecificSQLGenerator sqlGenerator,
            DelinquencyReadPlatformService delinquencyReadPlatformService, LoanTransactionRepository loanTransactionRepository,
            LoanChargePaidByReadService loanChargePaidByReadService, LoanTransactionRelationReadService loanTransactionRelationReadService,
            LoanForeclosureValidator loanForeclosureValidator, LoanTransactionMapper loanTransactionMapper, LoanMapper loanMapper,
            LoanTransactionProcessingService loadTransactionProcessingService,
                                            CredXLoanTransactionRepository credXLoanTransactionRepository) {
        super(jdbcTemplate, context, loanRepositoryWrapper, applicationCurrencyRepository, loanProductReadPlatformService,
                clientReadPlatformService, groupReadPlatformService, loanDropdownReadPlatformService, fundReadPlatformService,
                chargeReadPlatformService, codeValueReadPlatformService, calendarReadPlatformService, staffReadPlatformService,
                paginationHelper, paymentTypeReadPlatformService, loanRepaymentScheduleTransactionProcessorFactory,
                floatingRatesReadPlatformService, loanUtilService, configurationDomainService, accountDetailsReadPlatformService,
                columnValidator, sqlGenerator, delinquencyReadPlatformService, loanTransactionRepository, loanChargePaidByReadService,
                loanTransactionRelationReadService, loanForeclosureValidator, loanTransactionMapper, loanMapper,
                loadTransactionProcessingService);
        this.credXLoanTransactionRepository = credXLoanTransactionRepository;
    }

    @Override
    public LoanTransactionData retrieveLoanTransactionTemplate(Long loanId) {
        RapaymentStatusQuery.Result result = credXLoanTransactionRepository.retrieveLoanRepaymentTemplate(loanId);

        CurrencyData currencyData = new CurrencyData(result.getCurrencyCode(), result.getCurrencyName(), result.getCurrencyDigits(),
                result.getInMultiplesOf(), result.getCurrencyDisplaySymbol(), result.getCurrencyNameCode());

        final LoanTransactionEnumData transactionType = LoanEnumerations.transactionType(LoanTransactionType.REPAYMENT);
        final LocalDate date = ((java.sql.Date) result.getTransactionDate()).toLocalDate();
        final BigDecimal principalPortion = result.getPrincipalDue();
        final BigDecimal interestDue = result.getInterestDue();
        final BigDecimal feeDue = result.getFeeDue();
        final BigDecimal penaltyDue = result.getPenaltyDue();
        final BigDecimal totalDue = principalPortion.add(interestDue).add(feeDue).add(penaltyDue);
        final BigDecimal netDisbursalAmount = result.getNetDisbursalAmount();
        boolean manuallyReversed = false;
        return new LoanTransactionData(null, null, null, transactionType, null, currencyData, date, totalDue, netDisbursalAmount,
                principalPortion, interestDue, feeDue, penaltyDue, null, ExternalId.empty(), null, null, null, null, manuallyReversed,
                loanId, ExternalId.empty());
    }

}
