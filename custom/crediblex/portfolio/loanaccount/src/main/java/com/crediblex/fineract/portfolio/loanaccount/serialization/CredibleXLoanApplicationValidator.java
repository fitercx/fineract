package com.crediblex.fineract.portfolio.loanaccount.serialization;

import com.crediblex.fineract.portfolio.loanaccount.domain.CredibleXLoanRepositoryWrapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepositoryWrapper;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityRelationRepository;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityToEntityMappingRepository;
import org.apache.fineract.organisation.holiday.domain.HolidayRepository;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.accountdetails.domain.AccountType;
import org.apache.fineract.portfolio.calendar.domain.CalendarInstanceRepository;
import org.apache.fineract.portfolio.calendar.service.CalendarUtils;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.collateralmanagement.domain.ClientCollateralManagement;
import org.apache.fineract.portfolio.collateralmanagement.domain.ClientCollateralManagementRepositoryWrapper;
import org.apache.fineract.portfolio.collateralmanagement.service.LoanCollateralAssembler;
import org.apache.fineract.portfolio.common.domain.DaysInYearCustomStrategyType;
import org.apache.fineract.portfolio.common.domain.DaysInYearType;
import org.apache.fineract.portfolio.group.domain.Group;
import org.apache.fineract.portfolio.group.domain.GroupRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.*;
import org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.AdvancedPaymentScheduleTransactionProcessor;
import org.apache.fineract.portfolio.loanaccount.exception.InvalidAmountOfCollateralQuantity;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleProcessingType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.apache.fineract.portfolio.loanaccount.mapper.LoanMapper;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanApplicationValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanChargeApiJsonValidator;
import org.apache.fineract.portfolio.loanaccount.serialization.LoanScheduleValidator;
import org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.LoanUtilService;
import org.apache.fineract.portfolio.loanproduct.LoanProductConstants;
import org.apache.fineract.portfolio.loanproduct.domain.*;
import org.apache.fineract.portfolio.loanproduct.exception.EqualAmortizationUnsupportedFeatureException;
import org.apache.fineract.portfolio.loanproduct.exception.LoanProductNotFoundException;
import org.apache.fineract.portfolio.loanproduct.serialization.LoanProductDataValidator;
import org.apache.fineract.portfolio.loanproduct.service.LoanProductReadPlatformService;
import com.crediblex.fineract.portfolio.loc.service.LocLoanApplicationValidator;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Primary
public class CredibleXLoanApplicationValidator extends LoanApplicationValidator {

    private final CredibleXLoanRepositoryWrapper credibleXLoanRepositoryWrapper;
    private final LocLoanApplicationValidator locLoanApplicationValidator;

    public CredibleXLoanApplicationValidator(FromJsonHelper fromApiJsonHelper, LoanScheduleValidator loanScheduleValidator,
                                             ClientCollateralManagementRepositoryWrapper clientCollateralManagementRepositoryWrapper,
                                             LoanChargeApiJsonValidator loanChargeApiJsonValidator,
                                             LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory,
                                             AdvancedPaymentAllocationsValidator advancedPaymentAllocationsValidator, ConfigurationDomainService configurationDomainService,
                                             LoanProductRepository loanProductRepository, ClientRepositoryWrapper clientRepository, GroupRepositoryWrapper groupRepository,
                                             LoanReadPlatformService loanReadPlatformService, LoanProductDataValidator loanProductDataValidator,
                                             GlobalConfigurationRepositoryWrapper globalConfigurationRepository,
                                             FineractEntityToEntityMappingRepository entityMappingRepository,
                                             FineractEntityRelationRepository fineractEntityRelationRepository, CredibleXLoanRepositoryWrapper credibleXLoanRepositoryWrapper,
                                             LoanProductReadPlatformService loanProductReadPlatformService, LoanCollateralAssembler collateralAssembler,
                                             WorkingDaysRepositoryWrapper workingDaysRepository, HolidayRepository holidayRepository,
                                             SavingsAccountRepositoryWrapper savingsAccountRepository, LoanLifecycleStateMachine defaultLoanLifecycleStateMachine,
                                             CalendarInstanceRepository calendarInstanceRepository, LoanUtilService loanUtilService,
                                             EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService, LoanMapper loanMapper,
                                             LocLoanApplicationValidator locLoanApplicationValidator) {
        super(fromApiJsonHelper, loanScheduleValidator, clientCollateralManagementRepositoryWrapper, loanChargeApiJsonValidator,
                loanRepaymentScheduleTransactionProcessorFactory, advancedPaymentAllocationsValidator, configurationDomainService,
                loanProductRepository, clientRepository, groupRepository, loanReadPlatformService, loanProductDataValidator,
                globalConfigurationRepository, entityMappingRepository, fineractEntityRelationRepository, credibleXLoanRepositoryWrapper,
                loanProductReadPlatformService, collateralAssembler, workingDaysRepository, holidayRepository, savingsAccountRepository,
                defaultLoanLifecycleStateMachine, calendarInstanceRepository, loanUtilService, entityDatatableChecksWritePlatformService,
                loanMapper);

        this.credibleXLoanRepositoryWrapper = credibleXLoanRepositoryWrapper;
        this.locLoanApplicationValidator = locLoanApplicationValidator;
    }

    /**
     * Override the base validation to add support for lineOfCreditId parameter.
     * This allows the lineOfCreditId to pass through the base validation before
     * our custom Line of Credit validation runs.
     */
    @Override
    public void validateForCreate(JsonCommand command) {
        String json = command.json();
        
        // Validate request body
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        
        // Custom validation for lineOfCreditId before base validation
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        final Set<String> extendedSupportedParameters = new HashSet<>(Arrays.asList(
            // Base supported parameters
            "locale", "dateFormat", "id", "clientId", "groupId", "loanType", "productId", "principal", 
            "totalLoan", "parentAccount", "loanTermFrequency", "loanTermFrequencyType", "numberOfRepayments", 
            "repaymentEvery", "repaymentFrequencyType", "repaymentFrequencyNthDayType", "repaymentFrequencyDayOfWeekType",
            "interestRatePerPeriod", "amortizationType", "amortizationTypeOptions", "interestType", 
            "isFloatingInterestRate", "interestRateDifferential", "interestCalculationPeriodType",
            "allowPartialPeriodInterestCalculation", "interestRateFrequencyType", "expectedDisbursementDate",
            "repaymentsStartingFromDate", "graceOnPrincipalPayment", "graceOnInterestPayment", "graceOnInterestCharged",
            "interestChargedFromDate", "submittedOnDate", "submittedOnNote", "accountNo", "externalId", "fundId",
            "loanOfficerId", "loanPurposeId", "inArrearsTolerance", "charges", "collateral", 
            "transactionProcessingStrategyCode", "calendarId", "syncDisbursementWithMeeting", "linkAccountId",
            "disbursementData", "fixedEmiAmount", "maxOutstandingBalance", "graceOnArrearsAgeing",
            "createStandingInstructionAtDisbursement", "isTopup", "loanIdToClose", "datatables", 
            "isEqualAmortization", "rates", "applicationId", "lastApplication", "daysInYearType",
            "fixedPrincipalPercentagePerInstallment", "disallowExpectedDisbursements", "fraudAttributeName",
            "loanScheduleProcessingType", "fixedLength", "enableInstallmentLevelDelinquency", "enableDownPayment",
            "enableAutoRepaymentDownPayment", "disbursedAmountPercentageDownPayment", 
            "interestRecognitionOnDisbursementDate", "daysInYearCustomStrategy",
            "allowPartialPeriodInterestCalcualtion", "graceOnArrearsAgeing", "repaymentsStartingFromDate",
            "interestChargedFromDate", "repaymentFrequencyNthDayType", "repaymentFrequencyDayOfWeekType",
            "interestRateFrequencyType", "enableInstallmentLevelDelinquency",
            "lineOfCreditId"
        ));
        
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, extendedSupportedParameters);
        
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        validateForCreate(element);
    }



    protected void validateForCreate(final JsonElement element) {
        boolean isMeetingMandatoryForJLGLoans = configurationDomainService.isMeetingMandatoryForJLGLoans();

        final Long productId = this.fromApiJsonHelper.extractLongNamed(LoanApiConstants.productIdParameterName, element);
        if (productId == null) {
            throwMandatoryParameterError(LoanApiConstants.productIdParameterName);
        }
        final LoanProduct loanProduct = this.loanProductRepository.findById(productId)
                .orElseThrow(() -> new LoanProductNotFoundException(productId));

        final Long clientId = this.fromApiJsonHelper.extractLongNamed(LoanApiConstants.clientIdParameterName, element);
        final Long groupId = this.fromApiJsonHelper.extractLongNamed(LoanApiConstants.groupIdParameterName, element);
        final Client client = clientId != null ? this.clientRepository.findOneWithNotFoundDetection(clientId) : null;
        final Group group = groupId != null ? this.groupRepository.findOneWithNotFoundDetection(groupId) : null;

        validateClientOrGroup(client, group, productId);
        validateOrThrow("loan", baseDataValidator -> {
            final String loanTypeStr = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.loanTypeParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.loanTypeParameterName).value(loanTypeStr).notNull();

            if (!StringUtils.isBlank(loanTypeStr)) {
                final AccountType loanType = AccountType.fromName(loanTypeStr);
                baseDataValidator.reset().parameter(LoanApiConstants.loanTypeParameterName).value(loanType.getValue()).inMinMaxRange(1, 4);

                if (loanType.isIndividualAccount()) {
                    baseDataValidator.reset().parameter(LoanApiConstants.clientIdParameterName).value(clientId).notNull()
                            .longGreaterThanZero();
                    baseDataValidator.reset().parameter(LoanApiConstants.groupIdParameterName).value(groupId)
                            .mustBeBlankWhenParameterProvided(LoanApiConstants.clientIdParameterName, clientId);
                }

                if (loanType.isGroupAccount()) {
                    baseDataValidator.reset().parameter(LoanApiConstants.groupIdParameterName).value(groupId).notNull()
                            .longGreaterThanZero();
                    baseDataValidator.reset().parameter(LoanApiConstants.clientIdParameterName).value(clientId)
                            .mustBeBlankWhenParameterProvided(LoanApiConstants.groupIdParameterName, groupId);
                }

                if (loanType.isJLGAccount()) {
                    baseDataValidator.reset().parameter(LoanApiConstants.clientIdParameterName).value(clientId).notNull()
                            .integerGreaterThanZero();
                    baseDataValidator.reset().parameter(LoanApiConstants.groupIdParameterName).value(groupId).notNull()
                            .longGreaterThanZero();

                    // if it is JLG loan that must have meeting details
                    if (isMeetingMandatoryForJLGLoans) {

                        final Long calendarId = this.fromApiJsonHelper.extractLongNamed(LoanApiConstants.calendarIdParameterName, element);
                        baseDataValidator.reset().parameter(LoanApiConstants.calendarIdParameterName).value(calendarId).notNull()
                                .integerGreaterThanZero();

                        // if it is JLG loan then must have a value for
                        // syncDisbursement passed in
                        final Boolean syncDisbursement = this.fromApiJsonHelper
                                .extractBooleanNamed(LoanApiConstants.syncDisbursementWithMeetingParameterName, element);

                        if (syncDisbursement == null) {
                            baseDataValidator.reset().parameter(LoanApiConstants.syncDisbursementWithMeetingParameterName)
                                    .value(syncDisbursement).trueOrFalseRequired(false);
                        }
                    }

                }
            }

            boolean isEqualAmortization = false;
            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.isEqualAmortizationParam, element)) {
                isEqualAmortization = this.fromApiJsonHelper.extractBooleanNamed(LoanApiConstants.isEqualAmortizationParam, element);
                baseDataValidator.reset().parameter(LoanApiConstants.isEqualAmortizationParam).value(isEqualAmortization).ignoreIfNull()
                        .validateForBooleanValue();
                if (isEqualAmortization && loanProduct.isInterestRecalculationEnabled()) {
                    throw new EqualAmortizationUnsupportedFeatureException("interest.recalculation", "interest recalculation");
                }
            }

            BigDecimal fixedPrincipalPercentagePerInstallment = this.fromApiJsonHelper
                    .extractBigDecimalWithLocaleNamed(LoanApiConstants.fixedPrincipalPercentagePerInstallmentParamName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.fixedPrincipalPercentagePerInstallmentParamName)
                    .value(fixedPrincipalPercentagePerInstallment).notLessThanMin(BigDecimal.ONE)
                    .notGreaterThanMax(BigDecimal.valueOf(100));

            baseDataValidator.reset().parameter(LoanApiConstants.productIdParameterName).value(productId).notNull()
                    .integerGreaterThanZero();

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.accountNoParameterName, element)) {
                final String accountNo = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.accountNoParameterName, element);
                baseDataValidator.reset().parameter(LoanApiConstants.accountNoParameterName).value(accountNo).ignoreIfNull()
                        .notExceedingLengthOf(20);
            }

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.externalIdParameterName, element)) {
                final String externalId = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.externalIdParameterName, element);
                baseDataValidator.reset().parameter(LoanApiConstants.externalIdParameterName).value(externalId).ignoreIfNull()
                        .notExceedingLengthOf(100);
            }

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.fundIdParameterName, element)) {
                final Long fundId = this.fromApiJsonHelper.extractLongNamed(LoanApiConstants.fundIdParameterName, element);
                baseDataValidator.reset().parameter(LoanApiConstants.fundIdParameterName).value(fundId).ignoreIfNull()
                        .integerGreaterThanZero();
            }

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.loanOfficerIdParameterName, element)) {
                final Long loanOfficerId = this.fromApiJsonHelper.extractLongNamed(LoanApiConstants.loanOfficerIdParameterName, element);
                baseDataValidator.reset().parameter(LoanApiConstants.loanOfficerIdParameterName).value(loanOfficerId).ignoreIfNull()
                        .integerGreaterThanZero();
            }

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.loanPurposeIdParameterName, element)) {
                final Long loanPurposeId = this.fromApiJsonHelper.extractLongNamed(LoanApiConstants.loanPurposeIdParameterName, element);
                baseDataValidator.reset().parameter(LoanApiConstants.loanPurposeIdParameterName).value(loanPurposeId).ignoreIfNull()
                        .integerGreaterThanZero();
            }

            final Integer loanTermFrequency = this.fromApiJsonHelper
                    .extractIntegerWithLocaleNamed(LoanApiConstants.loanTermFrequencyParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.loanTermFrequencyParameterName).value(loanTermFrequency).notNull()
                    .integerGreaterThanZero();

            final Integer loanTermFrequencyType = this.fromApiJsonHelper
                    .extractIntegerSansLocaleNamed(LoanApiConstants.loanTermFrequencyTypeParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.loanTermFrequencyTypeParameterName).value(loanTermFrequencyType).notNull()
                    .inMinMaxRange(0, 3);

            final Integer numberOfRepayments = this.fromApiJsonHelper
                    .extractIntegerWithLocaleNamed(LoanApiConstants.numberOfRepaymentsParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.numberOfRepaymentsParameterName).value(numberOfRepayments).notNull()
                    .integerGreaterThanZero();

            final Integer repaymentEvery = this.fromApiJsonHelper
                    .extractIntegerWithLocaleNamed(LoanApiConstants.repaymentEveryParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.repaymentEveryParameterName).value(repaymentEvery).notNull()
                    .integerGreaterThanZero();

            final Integer repaymentEveryType = this.fromApiJsonHelper
                    .extractIntegerSansLocaleNamed(LoanApiConstants.repaymentFrequencyTypeParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.repaymentFrequencyTypeParameterName).value(repaymentEveryType).notNull()
                    .inMinMaxRange(0, 3);

            CalendarUtils.validateNthDayOfMonthFrequency(baseDataValidator, LoanApiConstants.repaymentFrequencyNthDayTypeParameterName,
                    LoanApiConstants.repaymentFrequencyDayOfWeekTypeParameterName, element, this.fromApiJsonHelper);

            final Integer interestType = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(LoanApiConstants.interestTypeParameterName,
                    element);
            baseDataValidator.reset().parameter(LoanApiConstants.interestTypeParameterName).value(interestType).notNull().inMinMaxRange(0,
                    1);

            final Integer interestCalculationPeriodType = this.fromApiJsonHelper
                    .extractIntegerSansLocaleNamed(LoanApiConstants.interestCalculationPeriodTypeParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.interestCalculationPeriodTypeParameterName)
                    .value(interestCalculationPeriodType).notNull().inMinMaxRange(0, 1);

            if (loanProduct.isLinkedToFloatingInterestRate()) {
                if (isEqualAmortization) {
                    throw new EqualAmortizationUnsupportedFeatureException("floating.interest.rate", "floating interest rate");
                }
                if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.interestRatePerPeriodParameterName, element)) {
                    baseDataValidator.reset().parameter(LoanApiConstants.interestRatePerPeriodParameterName).failWithCode(
                            "not.supported.loanproduct.linked.to.floating.rate",
                            "interestRatePerPeriod param is not supported, selected Loan Product is linked with floating interest rate.");
                }

                if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.isFloatingInterestRate, element)) {
                    final Boolean isFloatingInterestRate = this.fromApiJsonHelper
                            .extractBooleanNamed(LoanApiConstants.isFloatingInterestRate, element);
                    if (isFloatingInterestRate != null && isFloatingInterestRate
                            && !loanProduct.getFloatingRates().isFloatingInterestRateCalculationAllowed()) {
                        baseDataValidator.reset().parameter(LoanApiConstants.isFloatingInterestRate).failWithCode(
                                "true.not.supported.for.selected.loanproduct",
                                "isFloatingInterestRate value of true not supported for selected Loan Product.");
                    }
                } else {
                    baseDataValidator.reset().parameter(LoanApiConstants.isFloatingInterestRate).trueOrFalseRequired(false);
                }

                if (InterestMethod.FLAT.getValue().equals(interestType)) {
                    baseDataValidator.reset().parameter(LoanApiConstants.interestTypeParameterName).failWithCode(
                            "should.be.0.for.selected.loan.product",
                            "interestType should be DECLINING_BALANCE for selected Loan Product as it is linked to floating rates.");
                }

                final String interestRateDifferentialParameterName = LoanApiConstants.interestRateDifferential;
                final BigDecimal interestRateDifferential = this.fromApiJsonHelper
                        .extractBigDecimalWithLocaleNamed(interestRateDifferentialParameterName, element);
                baseDataValidator.reset().parameter(interestRateDifferentialParameterName).value(interestRateDifferential).notNull()
                        .zeroOrPositiveAmount().inMinAndMaxAmountRange(loanProduct.getFloatingRates().getMinDifferentialLendingRate(),
                                loanProduct.getFloatingRates().getMaxDifferentialLendingRate());
            } else {

                if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.isFloatingInterestRate, element)) {
                    baseDataValidator.reset().parameter(LoanApiConstants.isFloatingInterestRate).failWithCode(
                            "not.supported.loanproduct.not.linked.to.floating.rate",
                            "isFloatingInterestRate param is not supported, selected Loan Product is not linked with floating interest rate.");
                }
                if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.interestRateDifferential, element)) {
                    baseDataValidator.reset().parameter(LoanApiConstants.interestRateDifferential).failWithCode(
                            "not.supported.loanproduct.not.linked.to.floating.rate",
                            "interestRateDifferential param is not supported, selected Loan Product is not linked with floating interest rate.");
                }

                final BigDecimal interestRatePerPeriod = this.fromApiJsonHelper
                        .extractBigDecimalWithLocaleNamed(LoanApiConstants.interestRatePerPeriodParameterName, element);
                baseDataValidator.reset().parameter(LoanApiConstants.interestRatePerPeriodParameterName).value(interestRatePerPeriod)
                        .notNull().zeroOrPositiveAmount();
            }

            final Integer amortizationType = this.fromApiJsonHelper
                    .extractIntegerSansLocaleNamed(LoanApiConstants.amortizationTypeParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.amortizationTypeParameterName).value(amortizationType).notNull()
                    .inMinMaxRange(0, 1);

            if (!AmortizationMethod.EQUAL_PRINCIPAL.getValue().equals(amortizationType) && fixedPrincipalPercentagePerInstallment != null) {
                baseDataValidator.reset().parameter(LoanApiConstants.fixedPrincipalPercentagePerInstallmentParamName).failWithCode(
                        "not.supported.principal.fixing.not.allowed.with.equal.installments",
                        "Principal fixing cannot be done with equal installment amortization");
            }

            final LocalDate expectedDisbursementDate = this.fromApiJsonHelper
                    .extractLocalDateNamed(LoanApiConstants.expectedDisbursementDateParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.expectedDisbursementDateParameterName).value(expectedDisbursementDate)
                    .notNull();

            // grace validation
            final Integer graceOnPrincipalPayment = this.fromApiJsonHelper
                    .extractIntegerWithLocaleNamed(LoanApiConstants.graceOnPrincipalPaymentParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.graceOnPrincipalPaymentParameterName).value(graceOnPrincipalPayment)
                    .zeroOrPositiveAmount();

            final Integer graceOnInterestPayment = this.fromApiJsonHelper
                    .extractIntegerWithLocaleNamed(LoanApiConstants.graceOnInterestPaymentParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.graceOnInterestPaymentParameterName).value(graceOnInterestPayment)
                    .zeroOrPositiveAmount();

            final Integer graceOnInterestCharged = this.fromApiJsonHelper
                    .extractIntegerWithLocaleNamed(LoanApiConstants.graceOnInterestChargedParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.graceOnInterestChargedParameterName).value(graceOnInterestCharged)
                    .zeroOrPositiveAmount();

            final Integer graceOnArrearsAgeing = this.fromApiJsonHelper
                    .extractIntegerWithLocaleNamed(LoanProductConstants.GRACE_ON_ARREARS_AGEING_PARAMETER_NAME, element);
            baseDataValidator.reset().parameter(LoanProductConstants.GRACE_ON_ARREARS_AGEING_PARAMETER_NAME).value(graceOnArrearsAgeing)
                    .zeroOrPositiveAmount();

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.interestChargedFromDateParameterName, element)) {
                final LocalDate interestChargedFromDate = this.fromApiJsonHelper
                        .extractLocalDateNamed(LoanApiConstants.interestChargedFromDateParameterName, element);
                baseDataValidator.reset().parameter(LoanApiConstants.interestChargedFromDateParameterName).value(interestChargedFromDate)
                        .ignoreIfNull().notNull();
            }

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.repaymentsStartingFromDateParameterName, element)) {
                final LocalDate repaymentsStartingFromDate = this.fromApiJsonHelper
                        .extractLocalDateNamed(LoanApiConstants.repaymentsStartingFromDateParameterName, element);
                baseDataValidator.reset().parameter(LoanApiConstants.repaymentsStartingFromDateParameterName)
                        .value(repaymentsStartingFromDate).ignoreIfNull().notNull();
            }

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.inArrearsToleranceParameterName, element)) {
                final BigDecimal inArrearsTolerance = this.fromApiJsonHelper
                        .extractBigDecimalWithLocaleNamed(LoanApiConstants.inArrearsToleranceParameterName, element);
                baseDataValidator.reset().parameter(LoanApiConstants.inArrearsToleranceParameterName).value(inArrearsTolerance)
                        .ignoreIfNull().zeroOrPositiveAmount();
            }

            final LocalDate submittedOnDate = this.fromApiJsonHelper.extractLocalDateNamed(LoanApiConstants.submittedOnDateParameterName,
                    element);
            baseDataValidator.reset().parameter(LoanApiConstants.submittedOnDateParameterName).value(submittedOnDate).notNull();

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.submittedOnNoteParameterName, element)) {
                final String submittedOnNote = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.submittedOnNoteParameterName,
                        element);
                baseDataValidator.reset().parameter(LoanApiConstants.submittedOnNoteParameterName).value(submittedOnNote).ignoreIfNull()
                        .notExceedingLengthOf(500);
            }

            final String transactionProcessingStrategy = this.fromApiJsonHelper
                    .extractStringNamed(LoanApiConstants.transactionProcessingStrategyCodeParameterName, element);
            baseDataValidator.reset().parameter(LoanApiConstants.transactionProcessingStrategyCodeParameterName)
                    .value(transactionProcessingStrategy).notNull();

            validateLinkedSavingsAccount(element, baseDataValidator);

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.createStandingInstructionAtDisbursementParameterName, element)) {
                final Boolean createStandingInstructionAtDisbursement = this.fromApiJsonHelper
                        .extractBooleanNamed(LoanApiConstants.createStandingInstructionAtDisbursementParameterName, element);
                final Long linkAccountId = this.fromApiJsonHelper.extractLongNamed(LoanApiConstants.linkAccountIdParameterName, element);

                if (createStandingInstructionAtDisbursement) {
                    baseDataValidator.reset().parameter(LoanApiConstants.linkAccountIdParameterName).value(linkAccountId).notNull()
                            .longGreaterThanZero();
                }
            }

            // charges
            loanChargeApiJsonValidator.validateLoanCharges(element, loanProduct, baseDataValidator);

            /*
             * TODO: Add collaterals for other loan accounts if needed. For now it's only applicable for individual
             * accounts. (loanType.isJLG() || loanType.isGLIM())
             */

            if (!StringUtils.isBlank(loanTypeStr)) {
                final AccountType loanType = AccountType.fromName(loanTypeStr);

                // collateral
                if (loanType.isIndividualAccount() && element.isJsonObject()
                        && this.fromApiJsonHelper.parameterExists(LoanApiConstants.collateralParameterName, element)) {
                    final JsonObject topLevelJsonElement = element.getAsJsonObject();
                    final Locale locale = this.fromApiJsonHelper.extractLocaleParameter(topLevelJsonElement);
                    if (topLevelJsonElement.get(LoanApiConstants.collateralParameterName).isJsonArray()) {

                        final Type collateralParameterTypeOfMap = new TypeToken<Map<String, Object>>() {

                        }.getType();
                        final Set<String> supportedParameters = new HashSet<>(
                                Arrays.asList(LoanApiConstants.clientCollateralIdParameterName, LoanApiConstants.quantityParameterName));
                        final JsonArray array = topLevelJsonElement.get(LoanApiConstants.collateralParameterName).getAsJsonArray();
                        for (int i = 1; i <= array.size(); i++) {
                            final JsonObject collateralItemElement = array.get(i - 1).getAsJsonObject();

                            final String collateralJson = this.fromApiJsonHelper.toJson(collateralItemElement);
                            this.fromApiJsonHelper.checkForUnsupportedParameters(collateralParameterTypeOfMap, collateralJson,
                                    supportedParameters);

                            final Long clientCollateralId = this.fromApiJsonHelper
                                    .extractLongNamed(LoanApiConstants.clientCollateralIdParameterName, collateralItemElement);
                            baseDataValidator.reset().parameter(LoanApiConstants.collateralParameterName)
                                    .parameterAtIndexArray(LoanApiConstants.clientCollateralIdParameterName, i).value(clientCollateralId)
                                    .notNull().integerGreaterThanZero();

                            final BigDecimal quantity = this.fromApiJsonHelper
                                    .extractBigDecimalNamed(LoanApiConstants.quantityParameterName, collateralItemElement, locale);
                            baseDataValidator.reset().parameter(LoanApiConstants.collateralParameterName)
                                    .parameterAtIndexArray(LoanApiConstants.quantityParameterName, i).value(quantity).notNull()
                                    .positiveAmount();

                            final ClientCollateralManagement clientCollateralManagement = this.clientCollateralManagementRepositoryWrapper
                                    .getCollateral(clientCollateralId);

                            if (clientCollateralId != null
                                    && BigDecimal.valueOf(0).compareTo(clientCollateralManagement.getQuantity()) >= 0) {
                                throw new InvalidAmountOfCollateralQuantity(clientCollateralManagement.getQuantity());
                            }

                        }
                    } else {
                        baseDataValidator.reset().parameter(LoanApiConstants.collateralParameterName).expectedArrayButIsNot();
                    }
                }
            }

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.fixedEmiAmountParameterName, element)) {
                if (!(loanProduct.isCanDefineInstallmentAmount() || loanProduct.isMultiDisburseLoan())) {
                    List<String> unsupportedParameterList = new ArrayList<>();
                    unsupportedParameterList.add(LoanApiConstants.fixedEmiAmountParameterName);
                    throw new UnsupportedParameterException(unsupportedParameterList);
                }
                if (isEqualAmortization) {
                    throw new EqualAmortizationUnsupportedFeatureException("fixed.emi", "fixed emi");
                }
                final BigDecimal emiAmount = this.fromApiJsonHelper
                        .extractBigDecimalWithLocaleNamed(LoanApiConstants.fixedEmiAmountParameterName, element);
                baseDataValidator.reset().parameter(LoanApiConstants.fixedEmiAmountParameterName).value(emiAmount).ignoreIfNull()
                        .positiveAmount();
            }
            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.maxOutstandingBalanceParameterName, element)) {
                final BigDecimal maxOutstandingBalance = this.fromApiJsonHelper
                        .extractBigDecimalWithLocaleNamed(LoanApiConstants.maxOutstandingBalanceParameterName, element);
                baseDataValidator.reset().parameter(LoanApiConstants.maxOutstandingBalanceParameterName).value(maxOutstandingBalance)
                        .ignoreIfNull().positiveAmount();
            }

            final BigDecimal principal = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(LoanApiConstants.principalParamName,
                    element);

            if (loanProduct.isCanUseForTopup() && this.fromApiJsonHelper.parameterExists(LoanApiConstants.isTopup, element)) {
                final Boolean isTopup = this.fromApiJsonHelper.extractBooleanNamed(LoanApiConstants.isTopup, element);
                baseDataValidator.reset().parameter(LoanApiConstants.isTopup).value(isTopup).validateForBooleanValue();

                if (isTopup != null && isTopup) {
                    final Long loanId = this.fromApiJsonHelper.extractLongNamed(LoanApiConstants.loanIdToClose, element);
                    baseDataValidator.reset().parameter(LoanApiConstants.loanIdToClose).value(loanId).notNull().longGreaterThanZero();

                    if (clientId != null) {
                        final Long loanIdToClose = this.fromApiJsonHelper.extractLongNamed(LoanApiConstants.loanIdToClose, element);
                        final Loan loanToClose = this.loanRepositoryWrapper.findNonClosedLoanThatBelongsToClient(loanIdToClose, clientId);
                        if (loanToClose == null) {
                            throw new GeneralPlatformDomainRuleException(
                                    "error.msg.loan.loanIdToClose.no.active.loan.associated.to.client.found",
                                    "loanIdToClose is invalid, No Active Loan associated with the given Client ID found.");
                        }
                        if (loanToClose.isMultiDisburmentLoan()
                                && !loanToClose.getLoanProductRelatedDetail().isInterestRecalculationEnabled()) {
                            throw new GeneralPlatformDomainRuleException(
                                    "error.msg.loan.topup.on.multi.tranche.loan.without.interest.recalculation.not.supported",
                                    "Topup on loan with multi-tranche disbursal and without interest recalculation is not supported.");
                        }
                        final LocalDate disbursalDateOfLoanToClose = loanToClose.getDisbursementDate();
                        if (!DateUtils.isAfter(submittedOnDate, disbursalDateOfLoanToClose)) {
                            throw new GeneralPlatformDomainRuleException(
                                    "error.msg.loan.submitted.date.should.be.after.topup.loan.disbursal.date",
                                    "Submitted date of this loan application " + submittedOnDate
                                            + " should be after the disbursed date of loan to be closed " + disbursalDateOfLoanToClose);
                        }
                        if (!loanToClose.getCurrencyCode().equals(loanProduct.getCurrency().getCode())) {
                            throw new GeneralPlatformDomainRuleException("error.msg.loan.to.be.closed.has.different.currency",
                                    "loanIdToClose is invalid, Currency code is different.");
                        }
                        final LocalDate lastUserTransactionOnLoanToClose = loanToClose.getLastUserTransactionDate();
                        if (DateUtils.isBefore(expectedDisbursementDate, lastUserTransactionOnLoanToClose)) {
                            throw new GeneralPlatformDomainRuleException(
                                    "error.msg.loan.disbursal.date.should.be.after.last.transaction.date.of.loan.to.be.closed",
                                    "Disbursal date of this loan application " + expectedDisbursementDate
                                            + " should be after last transaction date of loan to be closed "
                                            + lastUserTransactionOnLoanToClose);
                        }
                        BigDecimal loanOutstanding = this.loanReadPlatformService
                                .retrieveLoanPrePaymentTemplate(LoanTransactionType.REPAYMENT, loanIdToClose, expectedDisbursementDate)
                                .getAmount();
                        if (loanOutstanding.compareTo(principal) > 0) {
                            throw new GeneralPlatformDomainRuleException("error.msg.loan.amount.less.than.outstanding.of.loan.to.be.closed",
                                    "Topup loan amount should be greater than outstanding amount of loan to be closed.");
                        }
                    }
                }
            }
            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.datatables, element)) {
                final JsonArray datatables = this.fromApiJsonHelper.extractJsonArrayNamed(LoanApiConstants.datatables, element);
                baseDataValidator.reset().parameter(LoanApiConstants.datatables).value(datatables).notNull().jsonArrayNotEmpty();
            }

            if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.daysInYearTypeParameterName, element)) {
                final Integer daysInYearType = this.fromApiJsonHelper.extractIntegerNamed(LoanApiConstants.daysInYearTypeParameterName,
                        element, Locale.getDefault());
                baseDataValidator.reset().parameter(LoanApiConstants.daysInYearTypeParameterName).value(daysInYearType).notNull()
                        .isOneOfTheseValues(1, 360, 364, 365);
            }

            validateLoanMultiDisbursementDate(element, baseDataValidator, expectedDisbursementDate, principal);

            String loanScheduleProcessingType = loanProduct.getLoanProductRelatedDetail().getLoanScheduleProcessingType().name();
            if (this.fromApiJsonHelper.parameterExists(LoanProductConstants.LOAN_SCHEDULE_PROCESSING_TYPE, element)) {
                loanScheduleProcessingType = this.fromApiJsonHelper.extractStringNamed(LoanProductConstants.LOAN_SCHEDULE_PROCESSING_TYPE,
                        element);
                baseDataValidator.reset().parameter(LoanProductConstants.LOAN_SCHEDULE_PROCESSING_TYPE).value(loanScheduleProcessingType)
                        .isOneOfEnumValues(LoanScheduleProcessingType.class);
            }
            if (LoanScheduleProcessingType.VERTICAL.equals(LoanScheduleProcessingType.valueOf(loanScheduleProcessingType))
                    && !AdvancedPaymentScheduleTransactionProcessor.ADVANCED_PAYMENT_ALLOCATION_STRATEGY
                            .equals(transactionProcessingStrategy)) {
                baseDataValidator.reset().parameter(LoanProductConstants.LOAN_SCHEDULE_PROCESSING_TYPE).failWithCode(
                        "supported.only.with.advanced.payment.allocation.strategy",
                        "Vertical repayment schedule processing is only available with `Advanced payment allocation` strategy");
            }

            List<LoanProductPaymentAllocationRule> allocationRules = loanProduct.getPaymentAllocationRules();

            if (LoanScheduleProcessingType.HORIZONTAL.name().equals(loanScheduleProcessingType)
                    && AdvancedPaymentScheduleTransactionProcessor.ADVANCED_PAYMENT_ALLOCATION_STRATEGY
                            .equals(transactionProcessingStrategy)) {
                advancedPaymentAllocationsValidator.checkGroupingOfAllocationRules(allocationRules);
            }

            validatePartialPeriodSupport(interestCalculationPeriodType, baseDataValidator, element, loanProduct);

            // validate enable installment level delinquency
            if (this.fromApiJsonHelper.parameterExists(LoanProductConstants.ENABLE_INSTALLMENT_LEVEL_DELINQUENCY, element)) {
                final Boolean isEnableInstallmentLevelDelinquency = this.fromApiJsonHelper
                        .extractBooleanNamed(LoanProductConstants.ENABLE_INSTALLMENT_LEVEL_DELINQUENCY, element);
                baseDataValidator.reset().parameter(LoanProductConstants.ENABLE_INSTALLMENT_LEVEL_DELINQUENCY)
                        .value(isEnableInstallmentLevelDelinquency).validateForBooleanValue();
                if (loanProduct.getDelinquencyBucket() == null) {
                    if (isEnableInstallmentLevelDelinquency) {
                        baseDataValidator.reset().parameter(LoanProductConstants.ENABLE_INSTALLMENT_LEVEL_DELINQUENCY).failWithCode(
                                "can.be.enabled.for.loan.with.loan.product.having.valid.delinquency.bucket",
                                "Installment level delinquency cannot be enabled for a loan if Delinquency bucket is not configured for loan product");
                    }
                }
            }

            validateBorrowerCycle(element, loanProduct, clientId, groupId, baseDataValidator);

            String accountNo = this.fromApiJsonHelper.extractStringNamed(LoanApiConstants.accountNoParameterName, element);
            final String externalIdStr = this.fromApiJsonHelper.extractStringNamed("externalId", element);
            ExternalId externalId = ExternalIdFactory.produce(externalIdStr);

            if (!externalId.isEmpty()) {
                final Loan existingLoanWithSameExternalId = this.credibleXLoanRepositoryWrapper.findOneWithoutNotFoundDetection(externalId);
                // Only throw error if the externalId belongs to a different loan
                if (existingLoanWithSameExternalId != null && !existingLoanWithSameExternalId.getAccountNumber().equals(accountNo)) {
                    throw new GeneralPlatformDomainRuleException("error.msg.loan.with.externalId.already.used",
                            "Loan with externalId is already registered.");
                }
            }

            loanScheduleValidator.validateDownPaymentAttribute(loanProduct.getLoanProductRelatedDetail().isEnableDownPayment(), element);

            checkForProductMixRestrictions(element);
            validateDisbursementDetails(loanProduct, element);
            validateCollateral(element);
            // validate if disbursement date is a holiday or a non-working day
            validateDisbursementDateIsOnNonWorkingDay(expectedDisbursementDate);
            Long officeId = resolveOfficeId(client, group);
            validateDisbursementDateIsOnHoliday(expectedDisbursementDate, officeId);
            final Integer recurringMoratoriumOnPrincipalPeriods = this.fromApiJsonHelper
                    .extractIntegerWithLocaleNamed("recurringMoratoriumOnPrincipalPeriods", element);

            if (numberOfRepayments != null) {
                loanProductDataValidator.validateRepaymentPeriodWithGraceSettings(numberOfRepayments, graceOnPrincipalPayment,
                        graceOnInterestPayment, graceOnInterestCharged, recurringMoratoriumOnPrincipalPeriods, baseDataValidator);
            }

            if (fromApiJsonHelper.parameterExists(LoanApiConstants.daysInYearCustomStrategyParameterName, element)) {
                DaysInYearCustomStrategyType daysInYearCustomStrategy = fromApiJsonHelper
                        .enumValueOfParameterNamed("daysInYearCustomStrategyParameterName", element, DaysInYearCustomStrategyType.class);
                if (daysInYearCustomStrategy != null) {
                    if (!LoanScheduleType.PROGRESSIVE.equals(loanProduct.getLoanProductRelatedDetail().getLoanScheduleType())) {
                        baseDataValidator.reset().parameter(LoanApiConstants.daysInYearCustomStrategyParameterName).failWithCode(
                                "days.in.year.custom.strategy.is.only.supported.for.progressive.loan.schedule.type",
                                "daysInYearCustomStrategy is only supported for progressive loan schedule type");
                    }

                    if (!DaysInYearType.ACTUAL.getValue().equals(loanProduct.getLoanProductRelatedDetail().getDaysInYearType())) {
                        baseDataValidator.reset().parameter(LoanApiConstants.daysInYearCustomStrategyParameterName).failWithCode(
                                "days.in.year.custom.strategy.is.only.applicable.for.actual.days.in.year.type",
                                "daysInYearCustomStrategy is only applicable for ACTUAL days in year type");
                    }

                }
            }

        });

        validateSubmittedOnDate(element, null, null, loanProduct);

        final String transactionProcessingStrategy = this.fromApiJsonHelper
                .extractStringNamed(LoanApiConstants.transactionProcessingStrategyCodeParameterName, element);
        validateTransactionProcessingStrategy(transactionProcessingStrategy, loanProduct);

        fixedLengthValidations(element);

        if (this.fromApiJsonHelper.parameterExists(LoanApiConstants.INTEREST_RECOGNITION_ON_DISBURSEMENT_DATE, element)) {
            if (!LoanScheduleType.PROGRESSIVE.equals(loanProduct.getLoanProductRelatedDetail().getLoanScheduleType())) {
                List<String> unsupportedParameterList = new ArrayList<>();
                unsupportedParameterList.add(LoanApiConstants.INTEREST_RECOGNITION_ON_DISBURSEMENT_DATE);
                throw new UnsupportedParameterException(unsupportedParameterList);
            }
        }

        this.locLoanApplicationValidator.validateLineOfCredit(element);
    }
}
