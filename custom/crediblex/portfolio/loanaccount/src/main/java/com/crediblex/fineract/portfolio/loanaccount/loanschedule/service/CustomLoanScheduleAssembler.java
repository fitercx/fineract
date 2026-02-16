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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
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
@Slf4j
public class CustomLoanScheduleAssembler extends LoanScheduleAssembler {

    private final PaymentPeriodsInOneYearCalculator paymentPeriodsInOneYearCalculator;
    private final LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository;

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
            LoanProductRelatedDetailUpdateUtil relatedDetailUpdateUtil, PaymentPeriodsInOneYearCalculator paymentPeriodsInOneYearCalculator,
            LoanLineOfCreditParamsRepository loanLineOfCreditParamsRepository) {
        super(fromApiJsonHelper, loanProductRepository, applicationCurrencyRepository, loanChargeAssembler, loanScheduleFactory,
                aprCalculator, calendarRepository, holidayRepository, configurationDomainService, clientRepository, groupRepository,
                workingDaysRepository, floatingRatesReadPlatformService, variableLoanScheduleFromApiJsonValidator,
                calendarInstanceRepository, loanUtilService, loanDisbursementDetailsAssembler, loanRepositoryWrapper,
                defaultLoanLifecycleStateMachine, loanAccrualsProcessingService, loanDisbursementService, loanChargeService,
                loanScheduleService, relatedDetailUpdateUtil);
        this.paymentPeriodsInOneYearCalculator = paymentPeriodsInOneYearCalculator;
        this.loanLineOfCreditParamsRepository = loanLineOfCreditParamsRepository;
    }

    @Override
    protected LoanApplicationTerms assembleLoanApplicationTermsFrom(JsonElement element, LoanProduct loanProduct) {

        boolean isReceivableLOC = loanProduct.isEnableLocReceivable();

        if (isReceivableLOC) {
            LoanApplicationTerms term = super.assembleLoanApplicationTermsFrom(element, loanProduct);
            term.setIsReceivableLineOfCredit(true);

            // We'll override amountAfterAdvance with amountInFacilityCurrency to calculate schedule for receivable only as it comes from Funded Amount field in Frontend / SDK API
            // Frontend / SDK API calculates: min(amountAfterAdvanceInAED, requestedAmountInAED, availableLimit)
            BigDecimal amountAfterAdvance = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("amountInFacilityCurrency", element);
            BigDecimal proposedPrincipal = getProposedPrincipal(element, amountAfterAdvance, term.getPrincipal().getMc());

            term.setPrincipal(Money.of(term.getCurrency(), proposedPrincipal));
            term.setDisbursedPrincipal(Money.of(term.getCurrency(), proposedPrincipal));
            term.setAmountAfterAdvance(proposedPrincipal);

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
                if (loanCharge.isDueAtDisbursement() || loanCharge.isInstalmentFee()) {
                    chargesDueAtTimeOfDisbursement = chargesDueAtTimeOfDisbursement.add(loanCharge.amount());

                    if (loanCharge.hasTax()) {
                        chargesDueAtTimeOfDisbursement = chargesDueAtTimeOfDisbursement.add(loanCharge.getTaxAmount());
                    }
                }
            }

            BigDecimal netDisbursalAmount = amountAfterAdvance.subtract(totalInterestChargable.getAmount())
                    .subtract(chargesDueAtTimeOfDisbursement);
            element.getAsJsonObject().addProperty("principal", netDisbursalAmount);

        }

        LoanApplicationTerms terms = super.assembleLoanApplicationTermsFrom(element, loanProduct);

        terms.setIsReceivableLineOfCredit(isReceivableLOC);
        if (isReceivableLOC) {
            terms.setIsReceivableLineOfCredit(true);
            terms.setAmountAfterAdvance(element.getAsJsonObject().get("amountInFacilityCurrency").getAsBigDecimal());
        }

        return terms;
    }

    @Override
    public Pair<Loan, Map<String, Object>> assembleLoanApproval(AppUser currentUser, JsonCommand command, Long loanId) {
        final JsonArray disbursementDataArray = command.arrayOfParameterNamed(LoanApiConstants.disbursementDataParameterName);
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId, true);

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
            // Check if this is a LOC Receivable loan by checking LOC params directly
            // The isReceivableLocLoan field might not be set yet during approval
            Optional<LoanLineOfCreditParams> locParams = loanLineOfCreditParamsRepository.findByLoanId(loanId);
            boolean isReceivableLocLoan = locParams.isPresent() && locParams.get().getLineOfCredit().getProductType().isReceivable();

            /*
             * All the calculations are done based on the principal amount, so it is necessary to set principal amount
             * to approved amount
             *
             * For LOC Receivable loans, we should NOT change the principal in LoanRepaymentScheduleDetail during
             * approval because charges need to be calculated based on the proposed principal (loan amount before
             * interest deduction), not the approved/disbursed principal. The principal in LoanRepaymentScheduleDetail
             * is used in charge calculations, so changing it would cause charges to be calculated incorrectly.
             */
            loan.setApprovedPrincipal(approvedLoanAmount);

            // For LOC Receivable loans, set the principal to proposed amount for charge calculations
            // The principal might have been set to approved amount earlier, so we need to restore it to proposed amount
            // The principal will be updated during disbursal when the schedule is regenerated
            // Use the LOC params check instead of loan.isReceivableLocLoan() which might not be set yet
            if (!isReceivableLocLoan) {
                loan.getLoanRepaymentScheduleDetail().setPrincipal(approvedLoanAmount);
            } else {
                // For LOC Receivable loans, ensure principal is set to proposed amount (not approved amount)
                // This is critical because charges must be calculated based on proposed principal (81,000), not
                // approved (66,089.30)
                BigDecimal proposedPrincipal = loan.getProposedPrincipal();
                loan.getLoanRepaymentScheduleDetail().setPrincipal(proposedPrincipal);
                // Also set the field on the loan object so CustomLoanChargeService can use it
                loan.setReceivableLocLoan(true);
            }
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
        return Pair.of(loan, actualChanges);
    }

    private BigDecimal getProposedPrincipal(JsonElement element, BigDecimal amountAfterAdvance, MathContext mc) {

        BigDecimal proposedPrincipal = element.getAsJsonObject().get("amountInFacilityCurrency").getAsBigDecimal();

        if (proposedPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new GeneralPlatformDomainRuleException("loan.proposed.principal.cannot.be.less.than.or.equal.to.zero",
                    "Proposed principal amount must be greater than zero for receivable line of credit.", List.of());
        }

        if (proposedPrincipal.compareTo(amountAfterAdvance) != 0) {
            throw new GeneralPlatformDomainRuleException("loan.proposed.principal.calculated.must.be.equal.to.amount.after.advance",
                    "Proposed principal amount must be equal to amount after advance for receivable line of credit.", List.of());
        }

        return proposedPrincipal;
    }
}
