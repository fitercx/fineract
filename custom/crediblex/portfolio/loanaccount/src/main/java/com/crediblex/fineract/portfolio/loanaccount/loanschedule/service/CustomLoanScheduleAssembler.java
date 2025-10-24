package com.crediblex.fineract.portfolio.loanaccount.loanschedule.service;

import static org.apache.fineract.portfolio.loanaccount.domain.Loan.APPROVED_ON_DATE;
import static org.apache.fineract.portfolio.loanaccount.domain.Loan.DATE_FORMAT;
import static org.apache.fineract.portfolio.loanaccount.domain.Loan.EVENT_DATE;
import static org.apache.fineract.portfolio.loanaccount.domain.Loan.EXPECTED_DISBURSEMENT_DATE;
import static org.apache.fineract.portfolio.loanaccount.domain.Loan.LOCALE;
import static org.apache.fineract.portfolio.loanaccount.domain.Loan.PARAM_STATUS;

import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParams;
import com.crediblex.fineract.portfolio.loanaccount.domain.LoanLineOfCreditParamsRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.holiday.domain.HolidayRepository;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.calendar.domain.CalendarRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.floatingrates.service.FloatingRatesReadPlatformService;
import org.apache.fineract.portfolio.group.domain.GroupRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.LoanTermVariationsData;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanEvent;
import org.apache.fineract.portfolio.loanaccount.domain.LoanLifecycleStateMachine;
import org.apache.fineract.portfolio.loanaccount.domain.LoanOfficerAssignmentHistory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.AprCalculator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.DefaultScheduledDateGenerator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanApplicationTerms;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleGeneratorFactory;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.PaymentPeriodsInOneYearCalculator;
import org.apache.fineract.portfolio.loanaccount.loanschedule.service.LoanScheduleAssembler;
import org.apache.fineract.portfolio.loanaccount.serialization.VariableLoanScheduleFromApiJsonValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanAccrualsProcessingService;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanChargeService;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementDetailsAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanDisbursementService;
import org.apache.fineract.portfolio.loanaccount.service.LoanProductRelatedDetailUpdateUtil;
import org.apache.fineract.portfolio.loanaccount.service.LoanScheduleService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanScheduleAssembler extends LoanScheduleAssembler {

    private LoanLineOfCreditParamsRepository lineOfCreditParamsRepository;
    private PaymentPeriodsInOneYearCalculator paymentPeriodsInOneYearCalculator;

    public CustomLoanScheduleAssembler(FromJsonHelper fromApiJsonHelper, LoanProductRepository loanProductRepository,
            ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository, LoanChargeAssembler loanChargeAssembler,
            LoanScheduleGeneratorFactory loanScheduleFactory, AprCalculator aprCalculator, CalendarRepository calendarRepository,
            HolidayRepository holidayRepository, ConfigurationDomainService configurationDomainService,
            ClientRepositoryWrapper clientRepository, GroupRepositoryWrapper groupRepository,
            WorkingDaysRepositoryWrapper workingDaysRepository, FloatingRatesReadPlatformService floatingRatesReadPlatformService,
            VariableLoanScheduleFromApiJsonValidator variableLoanScheduleFromApiJsonValidator,
            CalendarInstanceRepository calendarInstanceRepository, LoanUtilService loanUtilService,
            LoanDisbursementDetailsAssembler loanDisbursementDetailsAssembler, LoanRepositoryWrapper loanRepositoryWrapper,
            LoanLifecycleStateMachine defaultLoanLifecycleStateMachine, LoanAccrualsProcessingService loanAccrualsProcessingService,
            LoanDisbursementService loanDisbursementService, LoanChargeService loanChargeService, LoanScheduleService loanScheduleService,
            LoanProductRelatedDetailUpdateUtil relatedDetailUpdateUtil, LoanLineOfCreditParamsRepository lineOfCreditParamsRepository,
            PaymentPeriodsInOneYearCalculator paymentPeriodsInOneYearCalculator) {
        super(fromApiJsonHelper, loanProductRepository, applicationCurrencyRepository, loanChargeAssembler, loanScheduleFactory,
                aprCalculator, calendarRepository, holidayRepository, configurationDomainService, clientRepository, groupRepository,
                workingDaysRepository, floatingRatesReadPlatformService, variableLoanScheduleFromApiJsonValidator,
                calendarInstanceRepository, loanUtilService, loanDisbursementDetailsAssembler, loanRepositoryWrapper,
                defaultLoanLifecycleStateMachine, loanAccrualsProcessingService, loanDisbursementService, loanChargeService,
                loanScheduleService, relatedDetailUpdateUtil);
        this.lineOfCreditParamsRepository = lineOfCreditParamsRepository;
        this.paymentPeriodsInOneYearCalculator = paymentPeriodsInOneYearCalculator;
    }

    @Override
    protected LoanApplicationTerms assembleLoanApplicationTermsFrom(JsonElement element, LoanProduct loanProduct) {

        boolean isLineOfCredit = loanProduct.isLocEnabled() && element.getAsJsonObject().has("lineOfCreditId");
        boolean isReceivableLOC = isLineOfCredit && loanProduct.isEnableLocReceivable();

        if (isReceivableLOC) {
            LoanApplicationTerms term = super.assembleLoanApplicationTermsFrom(element, loanProduct);

            final DefaultScheduledDateGenerator scheduledDateGenerator = new DefaultScheduledDateGenerator();
            LocalDate loanEndDate = scheduledDateGenerator.getLastRepaymentDate(term, term.getHolidayDetailDTO());
            LoanTermVariationsData lastDueDateVariation = term.getLoanTermVariations().fetchLoanTermDueDateVariationsData(loanEndDate);
            if (lastDueDateVariation != null) {
                loanEndDate = lastDueDateVariation.getDateValue();
            }
            term.updateLoanEndDate(loanEndDate);
            MathContext mc = MoneyHelper.getMathContext();
            Money totalInterestChargable = term.calculateTotalInterestCharged(paymentPeriodsInOneYearCalculator, mc);

            BigDecimal chargesDueAtTimeOfDisbursement = BigDecimal.ZERO;

            // There is no multidisursal here so multidisbursal detail will be empty list
            final Set<LoanCharge> loanCharges = loanChargeAssembler.fromParsedJson(element, new ArrayList<>());
            for (final LoanCharge loanCharge : loanCharges) {
                if (loanCharge.isDueAtDisbursement()) {
                    chargesDueAtTimeOfDisbursement = chargesDueAtTimeOfDisbursement.add(loanCharge.amount());

                    if (loanCharge.hasTax()) {
                        chargesDueAtTimeOfDisbursement = chargesDueAtTimeOfDisbursement.add(loanCharge.getTaxAmount());
                    }
                }
            }

            BigDecimal approvedReceivableAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("approvedReceivableAmount",
                    element);

            BigDecimal netDisbursalAmount = approvedReceivableAmount.subtract(totalInterestChargable.getAmount())
                    .subtract(chargesDueAtTimeOfDisbursement);
            element.getAsJsonObject().addProperty("principal", netDisbursalAmount);

        }

        LoanApplicationTerms terms = super.assembleLoanApplicationTermsFrom(element, loanProduct);

        terms.setIsLineOfCredit(true);
        if (isReceivableLOC) {
            terms.setIsReceivableLineOfCredit(true);
            terms.setApprovedReceivableLineAmount(element.getAsJsonObject().get("approvedReceivableAmount").getAsBigDecimal());
        }

        return terms;
    }

    @Override
    public Pair<Loan, Map<String, Object>> assembleLoanApproval(AppUser currentUser, JsonCommand command, Long loanId) {
        final JsonArray disbursementDataArray = command.arrayOfParameterNamed(LoanApiConstants.disbursementDataParameterName);
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);

        Optional<LoanLineOfCreditParams> lineOfCreditParams = lineOfCreditParamsRepository.findByLoanId(loanId);
        boolean isReceivableLOC = lineOfCreditParams.isPresent()
                && lineOfCreditParams.get().getLineOfCredit().getProductType().isReceivable();

        final Map<String, Object> actualChanges = new HashMap<>();
        defaultLoanLifecycleStateMachine.transition(LoanEvent.LOAN_APPROVED, loan);
        actualChanges.put(PARAM_STATUS, LoanEnumerations.status(loan.getStatus()));

        LocalDate approvedOn = command.localDateValueOfParameterNamed(APPROVED_ON_DATE);
        String approvedOnDateChange = command.stringValueOfParameterNamed(APPROVED_ON_DATE);
        if (approvedOn == null) {
            approvedOn = command.localDateValueOfParameterNamed(EVENT_DATE);
            approvedOnDateChange = command.stringValueOfParameterNamed(EVENT_DATE);
        }

        LocalDate expectedDisbursementDate = command.localDateValueOfParameterNamed(EXPECTED_DISBURSEMENT_DATE);

        BigDecimal approvedLoanAmount = command.bigDecimalValueOfParameterNamed(LoanApiConstants.approvedLoanAmountParameterName);
        if (approvedLoanAmount != null) {
            /*
             * All the calculations are done based on the principal amount, so it is necessary to set principal amount
             * to approved amount
             */
            loan.setApprovedPrincipal(approvedLoanAmount);
            loan.getLoanRepaymentScheduleDetail().setPrincipal(approvedLoanAmount);
            actualChanges.put(LoanApiConstants.approvedLoanAmountParameterName, approvedLoanAmount);
            actualChanges.put(LoanApiConstants.disbursementPrincipalParameterName, approvedLoanAmount);
            actualChanges.put(LoanApiConstants.disbursementNetDisbursalAmountParameterName, loan.getNetDisbursalAmount());

            if (disbursementDataArray != null) {
                loanDisbursementService.updateDisbursementDetails(loan, command, actualChanges);
            }
        }

        loanChargeService.recalculateAllCharges(loan);

        loan.setApprovedOnDate(approvedOn);
        loan.setApprovedBy(currentUser);

        actualChanges.put(LOCALE, command.locale());
        actualChanges.put(DATE_FORMAT, command.dateFormat());
        actualChanges.put(APPROVED_ON_DATE, approvedOnDateChange);

        if (expectedDisbursementDate != null) {
            loan.setExpectedDisbursementDate(expectedDisbursementDate);
            actualChanges.put(EXPECTED_DISBURSEMENT_DATE, expectedDisbursementDate);
        }

        if (loan.getLoanOfficer() != null) {
            final LoanOfficerAssignmentHistory loanOfficerAssignmentHistory = LoanOfficerAssignmentHistory.createNew(loan,
                    loan.getLoanOfficer(), approvedOn);
            loan.getLoanOfficerHistory().add(loanOfficerAssignmentHistory);
        }

        if (!actualChanges.isEmpty()) {
            if (actualChanges.containsKey(LoanApiConstants.approvedLoanAmountParameterName)
                    || actualChanges.containsKey("recalculateLoanSchedule") || actualChanges.containsKey("expectedDisbursementDate")) {
                loanScheduleService.regenerateRepaymentSchedule(loan, loanUtilService.buildScheduleGeneratorDTO(loan, null));
                loanAccrualsProcessingService.reprocessExistingAccruals(loan);
            }
        }

        // For LOC, dont reset the amount with approved principal, use approved principal less the interest which will
        // be paid early
        loan.adjustNetDisbursalAmount(
                isReceivableLOC
                        ? loan.getApprovedPrincipal()
                                .subtract(loan.getRepaymentScheduleInstallments().stream()
                                        .map(LoanRepaymentScheduleInstallment::getInterestCharged).reduce(BigDecimal.ZERO, BigDecimal::add))
                        : loan.getApprovedPrincipal());

        return Pair.of(loan, actualChanges);
    }
}
