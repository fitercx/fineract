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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class CustomLoanScheduleAssembler extends LoanScheduleAssembler {

    private static final Logger log = LoggerFactory.getLogger(CustomLoanScheduleAssembler.class);

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

            BigDecimal amountAfterAdvance = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed("amountAfterAdvance", element);
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
            terms.setAmountAfterAdvance(element.getAsJsonObject().get("amountAfterAdvance").getAsBigDecimal());
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
        BigDecimal savedPrincipalForRestore = null;

        // For LOC Receivable loans, ensure charges are calculated using proposed principal (amountAfterAdvance)
        // instead of approved principal during approval. This ensures consistent fee calculation:
        // 10% of loan amount (81,000), not 10% of disbursed amount (66,194.31)
        // Note: We check LoanLineOfCreditParams directly instead of loan.isReceivableLocLoan() because
        // the flag might not be set yet at this point (it's set during constructLoanApplicationTerms).
        Optional<LoanLineOfCreditParams> llocParams = loanLineOfCreditParamsRepository.findByLoanId(loan.getId());
        boolean isLocReceivable = llocParams.isPresent() && llocParams.get().getLineOfCredit() != null
                && llocParams.get().getLineOfCredit().getProductType() != null
                && llocParams.get().getLineOfCredit().getProductType().isReceivable();

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

        if (isLocReceivable && llocParams.get().getAmountAfterAdvance() != null) {
            BigDecimal proposedPrincipal = llocParams.get().getAmountAfterAdvance();
            // Save current principal to restore later
            savedPrincipalForRestore = loan.getLoanRepaymentScheduleDetail().getPrincipal().getAmount();
            log.info(
                    "LOC Receivable loan {}: Setting principal to proposed principal {} (from approved principal {}) for charge recalculation",
                    loan.getId(), proposedPrincipal, savedPrincipalForRestore);
            // Temporarily set principal to proposed principal for charge recalculation
            loan.getLoanRepaymentScheduleDetail().setPrincipal(proposedPrincipal);
        }

        loanChargeService.recalculateAllCharges(loan);

        // Note: Do NOT restore principal yet - keep it as proposed principal so that schedule regeneration
        // uses the correct principal. The CustomLoanScheduleService.regenerateRepaymentSchedule() will
        // handle setting it correctly during charge recalculation, and we'll restore it after schedule regeneration.

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
                // Schedule regeneration is called with principal still set to proposed principal (81,000)
                // This ensures the schedule is generated with correct charge amounts
                loanScheduleService.regenerateRepaymentSchedule(loan, loanUtilService.buildScheduleGeneratorDTO(loan, null));
                loanAccrualsProcessingService.reprocessExistingAccruals(loan);
            }
        }

        // Restore principal to approved amount for LOC Receivable loans after schedule regeneration
        // This ensures the loan state is correct after approval
        // Use the same isLocReceivable check we used earlier (via LoanLineOfCreditParams)
        if (isLocReceivable && savedPrincipalForRestore != null) {
            log.info("LOC Receivable loan {}: Restoring principal to approved principal {} after schedule regeneration", loan.getId(),
                    savedPrincipalForRestore);
            loan.getLoanRepaymentScheduleDetail().setPrincipal(savedPrincipalForRestore);
            // Update derived fields after principal restoration to ensure schedule status is correctly calculated
            loan.updateLoanScheduleDependentDerivedFields();
            loan.updateLoanSummaryDerivedFields();
        }

        return Pair.of(loan, actualChanges);
    }

    private BigDecimal getProposedPrincipal(JsonElement element, BigDecimal amountAfterAdvance, MathContext mc) {
        BigDecimal invoiceAmount = element.getAsJsonObject().get("invoiceAmount").getAsBigDecimal();
        BigDecimal disapprovedAmount = element.getAsJsonObject().get("disapprovedAmount").getAsBigDecimal();
        BigDecimal advancePercentage = element.getAsJsonObject().get("advancePercentage").getAsBigDecimal();
        BigDecimal amountForCalculation = invoiceAmount.subtract(disapprovedAmount);

        BigDecimal proposedPrincipal = (advancePercentage.multiply(amountForCalculation)).divide(BigDecimal.valueOf(100), mc.getPrecision(),
                RoundingMode.HALF_UP);

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
