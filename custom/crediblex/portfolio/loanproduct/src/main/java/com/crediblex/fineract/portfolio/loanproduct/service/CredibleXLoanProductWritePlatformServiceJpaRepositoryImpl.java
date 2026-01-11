package com.crediblex.fineract.portfolio.loanproduct.service;

import com.google.gson.JsonArray;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.fineract.accounting.producttoaccountmapping.service.ProductToGLAccountMappingWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.entityaccess.domain.FineractEntityAccessType;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.infrastructure.event.business.domain.loan.product.LoanProductCreateBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.delinquency.domain.DelinquencyBucketRepository;
import org.apache.fineract.portfolio.floatingrates.domain.FloatingRate;
import org.apache.fineract.portfolio.floatingrates.domain.FloatingRateRepositoryWrapper;
import org.apache.fineract.portfolio.fund.domain.Fund;
import org.apache.fineract.portfolio.fund.domain.FundRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeOffBehaviour;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleTransactionProcessorFactory;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.AprCalculator;
import org.apache.fineract.portfolio.loanaccount.service.LoanProductAssembler;
import org.apache.fineract.portfolio.loanaccount.service.LoanProductUpdateUtil;
import org.apache.fineract.portfolio.loanproduct.LoanProductConstants;
import org.apache.fineract.portfolio.loanproduct.domain.AdvancedPaymentAllocationsJsonParser;
import org.apache.fineract.portfolio.loanproduct.domain.CreditAllocationsJsonParser;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductCreditAllocationRule;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductPaymentAllocationRule;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.apache.fineract.portfolio.loanproduct.domain.LoanSupportedInterestRefundTypes;
import org.apache.fineract.portfolio.loanproduct.exception.LoanProductCannotBeModifiedDueToNonClosedLoansException;
import org.apache.fineract.portfolio.loanproduct.exception.LoanProductNotFoundException;
import org.apache.fineract.portfolio.loanproduct.serialization.LoanProductDataValidator;
import org.apache.fineract.portfolio.loanproduct.service.LoanProductWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.portfolio.rate.domain.Rate;
import org.apache.fineract.portfolio.rate.domain.RateRepositoryWrapper;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Primary
public class CredibleXLoanProductWritePlatformServiceJpaRepositoryImpl extends LoanProductWritePlatformServiceJpaRepositoryImpl {

    private final ConfigurationDomainService configurationDomainService;

    public CredibleXLoanProductWritePlatformServiceJpaRepositoryImpl(PlatformSecurityContext context,
            LoanProductDataValidator fromApiJsonDeserializer, LoanProductRepository loanProductRepository, AprCalculator aprCalculator,
            FundRepository fundRepository, ChargeRepositoryWrapper chargeRepository, RateRepositoryWrapper rateRepository,
            ProductToGLAccountMappingWritePlatformService accountMappingWritePlatformService,
            FineractEntityAccessUtil fineractEntityAccessUtil, FloatingRateRepositoryWrapper floatingRateRepository,
            LoanRepositoryWrapper loanRepositoryWrapper, BusinessEventNotifierService businessEventNotifierService,
            DelinquencyBucketRepository delinquencyBucketRepository,
            LoanRepaymentScheduleTransactionProcessorFactory loanRepaymentScheduleTransactionProcessorFactory,
            AdvancedPaymentAllocationsJsonParser advancedPaymentJsonParser, CreditAllocationsJsonParser creditAllocationsJsonParser,
            LoanProductAssembler loanProductAssembler, LoanProductUpdateUtil loanProductUpdateUtil,
            ConfigurationDomainService configurationDomainService) {
        super(context, fromApiJsonDeserializer, loanProductRepository, aprCalculator, fundRepository, chargeRepository, rateRepository,
                accountMappingWritePlatformService, fineractEntityAccessUtil, floatingRateRepository, loanRepositoryWrapper,
                businessEventNotifierService, delinquencyBucketRepository, loanRepaymentScheduleTransactionProcessorFactory,
                advancedPaymentJsonParser, creditAllocationsJsonParser, loanProductAssembler, loanProductUpdateUtil);
        this.configurationDomainService = configurationDomainService;
    }

    @Transactional
    @Override
    public CommandProcessingResult createLoanProduct(JsonCommand command) {
        try {
            this.context.authenticatedUser();
            this.fromApiJsonDeserializer.validateForCreate(command);
            validateInputDates(command);
            final Fund fund = findFundByIdIfProvided(command.longValueOfParameterNamed("fundId"));
            final String loanTransactionProcessingStrategyCode = command.stringValueOfParameterNamed("transactionProcessingStrategyCode");
            final String currencyCode = command.stringValueOfParameterNamed("currencyCode");
            final List<Charge> charges = assembleListOfProductCharges(command, currencyCode);
            final List<Rate> rates = assembleListOfProductRates(command);
            final List<LoanProductPaymentAllocationRule> loanProductPaymentAllocationRules = advancedPaymentJsonParser
                    .assembleLoanProductPaymentAllocationRules(command, loanTransactionProcessingStrategyCode);
            final List<LoanProductCreditAllocationRule> loanProductCreditAllocationRules = creditAllocationsJsonParser
                    .assembleLoanProductCreditAllocationRules(command, loanTransactionProcessingStrategyCode);
            FloatingRate floatingRate = null;
            if (command.parameterExists("floatingRatesId")) {
                floatingRate = this.floatingRateRepository
                        .findOneWithNotFoundDetection(command.longValueOfParameterNamed("floatingRatesId"));
            }
            final LoanProduct loanProduct = loanProductAssembler.assembleFromJson(fund, loanTransactionProcessingStrategyCode, charges,
                    command, this.aprCalculator, floatingRate, rates, loanProductPaymentAllocationRules, loanProductCreditAllocationRules);
            final boolean factorRateProductEnabled = command.booleanPrimitiveValueOfParameterNamed("factorRateProductEnabled");
            final BigDecimal factorRate = command.bigDecimalValueOfParameterNamed("factorRate");
            loanProduct.setFactorRateProductEnabled(factorRateProductEnabled);
            loanProduct.setFactorRate(factorRate);
            if (factorRateProductEnabled) {
                final Long maximumProductFactorRate = this.configurationDomainService.retrieveMaximumProductFactorRate();
                if (factorRate == null || factorRate.compareTo(BigDecimal.ONE) <= 0) {
                    throw new GeneralPlatformDomainRuleException("error.msg.loan.product.factor.rate.amount.must.be.greater.than.one",
                            "Factor rate product amount must be greater than one");
                }
                if (factorRate.compareTo(BigDecimal.valueOf(maximumProductFactorRate)) > 0) {
                    throw new GeneralPlatformDomainRuleException("error.msg.loan.product.factor.rate.exceeds.maximum.limit",
                            "Factor rate of " + factorRate + " exceeds the maximum limit of " + maximumProductFactorRate,
                            maximumProductFactorRate);
                }
            }
            final Integer penaltyGracePeriod = command.integerValueOfParameterNamed(LoanProductConstants.PENALTY_GRACE_PERIOD_PARAM_NAME);
            loanProduct.setPenaltyGracePeriod(
                    penaltyGracePeriod != null ? penaltyGracePeriod : LoanProductConstants.DEFAULT_PENALTY_GRACE_PERIOD);
            loanProduct.updateLoanProductInRelatedClasses();
            loanProduct.setTransactionProcessingStrategyName(
                    loanRepaymentScheduleTransactionProcessorFactory.determineProcessor(loanTransactionProcessingStrategyCode).getName());
            if (command.parameterExists("delinquencyBucketId")) {
                loanProduct
                        .setDelinquencyBucket(findDelinquencyBucketIdIfProvided(command.longValueOfParameterNamed("delinquencyBucketId")));
            }

            this.loanProductRepository.saveAndFlush(loanProduct);

            // save accounting mappings
            this.accountMappingWritePlatformService.createLoanProductToGLAccountMapping(loanProduct.getId(), command);
            // check if the office specific products are enabled. If yes, then
            // save this savings product against a specific office
            // i.e. this savings product is specific for this office.
            fineractEntityAccessUtil.checkConfigurationAndAddProductResrictionsForUserOffice(
                    FineractEntityAccessType.OFFICE_ACCESS_TO_LOAN_PRODUCTS, loanProduct.getId());

            businessEventNotifierService.notifyPostBusinessEvent(new LoanProductCreateBusinessEvent(loanProduct));

            return new CommandProcessingResultBuilder() //
                    .withCommandId(command.commandId()) //
                    .withEntityId(loanProduct.getId()) //
                    .build();

        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        } catch (final PersistenceException dve) {
            Throwable throwable = ExceptionUtils.getRootCause(dve.getCause());
            handleDataIntegrityIssues(command, throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult updateLoanProduct(Long loanProductId, JsonCommand command) {
        try {
            this.context.authenticatedUser();
            final LoanProduct product = this.loanProductRepository.findById(loanProductId)
                    .orElseThrow(() -> new LoanProductNotFoundException(loanProductId));
            this.fromApiJsonDeserializer.validateForUpdate(command, product);
            validateInputDates(command);
            if (anyChangeInCriticalFloatingRateLinkedParams(command, product)
                    && this.loanRepositoryWrapper.doNonClosedLoanAccountsExistForProduct(product.getId())) {
                throw new LoanProductCannotBeModifiedDueToNonClosedLoansException(product.getId());
            }

            // Validate: Prevent changing from single-disbursal to multi-disbursal when active loans exist
            // This prevents data inconsistency where single-disbursal loans would be treated as multi-disbursal
            // for validation purposes, causing repayment validation errors
            if (command.isChangeInBooleanParameterNamed(LoanProductConstants.MULTI_DISBURSE_LOAN_PARAMETER_NAME,
                    product.isMultiDisburseLoan())) {
                final boolean currentValue = product.isMultiDisburseLoan();
                final boolean newValue = command.booleanPrimitiveValueOfParameterNamed(LoanProductConstants.MULTI_DISBURSE_LOAN_PARAMETER_NAME);
                
                // If changing from single-disbursal (false) to multi-disbursal (true) and active loans exist
                if (!currentValue && newValue && this.loanRepositoryWrapper.doNonClosedLoanAccountsExistForProduct(product.getId())) {
                    log.warn("Attempt to change loan product {} from single-disbursal to multi-disbursal when active loans exist", product.getId());
                    throw new GeneralPlatformDomainRuleException(
                            "error.msg.loanproduct.cannot.change.single.to.multi.disbursal.with.active.loans",
                            "Loan product with identifier " + product.getId()
                                    + " cannot be changed from single-disbursal to multi-disbursal because there are active (non-closed) loans associated with it. "
                                    + "This change would cause data inconsistency and validation errors for existing single-disbursal loans.",
                            product.getId());
                }
            }

            FloatingRate floatingRate = null;
            if (command.parameterExists("floatingRatesId")) {
                floatingRate = this.floatingRateRepository
                        .findOneWithNotFoundDetection(command.longValueOfParameterNamed("floatingRatesId"));
            }
            final Map<String, Object> changes = this.loanProductUpdateUtil.update(product, command, this.aprCalculator, floatingRate);

            final boolean factorRateProductEnabled = command.booleanPrimitiveValueOfParameterNamed("factorRateProductEnabled");
            final BigDecimal factorRate = command.bigDecimalValueOfParameterNamed("factorRate");
            product.setFactorRateProductEnabled(factorRateProductEnabled);
            product.setFactorRate(factorRate);
            if (factorRateProductEnabled) {
                final Long maximumProductFactorRate = this.configurationDomainService.retrieveMaximumProductFactorRate();
                if (factorRate == null || factorRate.compareTo(BigDecimal.ONE) <= 0) {
                    throw new GeneralPlatformDomainRuleException("error.msg.loan.product.factor.rate.amount.must.be.greater.than.one",
                            "Factor rate product amount must be greater than one");
                }
                if (factorRate.compareTo(BigDecimal.valueOf(maximumProductFactorRate)) > 0) {
                    throw new GeneralPlatformDomainRuleException("error.msg.loan.product.factor.rate.exceeds.maximum.limit",
                            "Factor rate of " + factorRate + " exceeds the maximum limit of " + maximumProductFactorRate,
                            maximumProductFactorRate);
                }
            }
            final Integer penaltyGracePeriod = command.integerValueOfParameterNamed(LoanProductConstants.PENALTY_GRACE_PERIOD_PARAM_NAME);
            product.setPenaltyGracePeriod(
                    penaltyGracePeriod != null ? penaltyGracePeriod : LoanProductConstants.DEFAULT_PENALTY_GRACE_PERIOD);

            if (changes.containsKey("fundId")) {
                final Long fundId = (Long) changes.get("fundId");
                final Fund fund = findFundByIdIfProvided(fundId);
                product.setFund(fund);
            }
            if (changes.containsKey("delinquencyBucketId")) {
                product.setDelinquencyBucket(findDelinquencyBucketIdIfProvided((Long) changes.get("delinquencyBucketId")));
            }

            if (changes.containsKey("transactionProcessingStrategyCode")) {
                final String transactionProcessingStrategyCode = (String) changes.get("transactionProcessingStrategyCode");
                final String transactionProcessingStrategyName = loanRepaymentScheduleTransactionProcessorFactory
                        .determineProcessor(transactionProcessingStrategyCode).getName();
                product.setTransactionProcessingStrategyCode(transactionProcessingStrategyCode);
                product.setTransactionProcessingStrategyName(transactionProcessingStrategyName);
            }

            if (changes.containsKey("charges")) {
                final List<Charge> productCharges = assembleListOfProductCharges(command, product.getCurrency().getCode());
                final boolean updated = product.update(productCharges);
                if (!updated) {
                    changes.remove("charges");
                }
            }

            if (changes.containsKey("paymentAllocation")) {
                final List<LoanProductPaymentAllocationRule> loanProductPaymentAllocationRules = advancedPaymentJsonParser
                        .assembleLoanProductPaymentAllocationRules(command, product.getTransactionProcessingStrategyCode());
                loanProductPaymentAllocationRules.forEach(lppar -> lppar.setLoanProduct(product));
                final boolean updated = this.loanProductPaymentAllocationRuleMerger.updateProductPaymentAllocationRules(product,
                        loanProductPaymentAllocationRules);
                if (!updated) {
                    changes.remove("paymentAllocation");
                }
            }
            if (changes.containsKey("creditAllocation")) {
                final List<LoanProductCreditAllocationRule> loanProductCreditAllocationRules = creditAllocationsJsonParser
                        .assembleLoanProductCreditAllocationRules(command, product.getTransactionProcessingStrategyCode());
                loanProductCreditAllocationRules.forEach(lpcar -> lpcar.setLoanProduct(product));
                final boolean updated = this.loanProductCreditAllocationRuleMerger.updateCreditAllocationRules(product,
                        loanProductCreditAllocationRules);
                if (!updated) {
                    changes.remove("creditAllocation");
                }
            }

            if (command.isChangeInBooleanParameterNamed(LoanProductConstants.ENABLE_LOC_PAYABLE_PARAM_NAME, product.isEnableLocPayable())) {
                final boolean newValue = command.booleanPrimitiveValueOfParameterNamed(LoanProductConstants.ENABLE_LOC_PAYABLE_PARAM_NAME);
                changes.put(LoanProductConstants.ENABLE_LOC_PAYABLE_PARAM_NAME, newValue);
                product.setEnableLocPayable(newValue);
            }

            if (command.isChangeInBooleanParameterNamed(LoanProductConstants.ENABLE_LOC_RECEIVABLE_PARAM_NAME,
                    product.isEnableLocReceivable())) {
                final boolean newValue = command
                        .booleanPrimitiveValueOfParameterNamed(LoanProductConstants.ENABLE_LOC_RECEIVABLE_PARAM_NAME);
                changes.put(LoanProductConstants.ENABLE_LOC_RECEIVABLE_PARAM_NAME, newValue);
                product.setEnableLocReceivable(newValue);
            }

            // accounting related changes
            final boolean accountingTypeChanged = changes.containsKey("accountingRule");
            final Map<String, Object> accountingMappingChanges = this.accountMappingWritePlatformService
                    .updateLoanProductToGLAccountMapping(product.getId(), command, accountingTypeChanged, product.getAccountingRule());
            changes.putAll(accountingMappingChanges);

            if (changes.containsKey(LoanProductConstants.RATES_PARAM_NAME)) {
                final List<Rate> productRates = assembleListOfProductRates(command);
                final boolean updated = product.updateRates(productRates);
                if (!updated) {
                    changes.remove(LoanProductConstants.RATES_PARAM_NAME);
                }
            }
            if (command.parameterExists(LoanProductConstants.SUPPORTED_INTEREST_REFUND_TYPES)) {
                JsonArray supportedTransactionsForInterestRefund = command
                        .arrayOfParameterNamed(LoanProductConstants.SUPPORTED_INTEREST_REFUND_TYPES);
                List<LoanSupportedInterestRefundTypes> supportedInterestRefundTypes = new ArrayList<>();
                supportedTransactionsForInterestRefund.iterator().forEachRemaining(value -> {
                    supportedInterestRefundTypes.add(LoanSupportedInterestRefundTypes.valueOf(value.getAsString()));
                });
                product.getLoanProductRelatedDetail().setSupportedInterestRefundTypes(supportedInterestRefundTypes);
            }
            if (command.parameterExists(LoanProductConstants.CHARGE_OFF_BEHAVIOUR)) {
                product.getLoanProductRelatedDetail().setChargeOffBehaviour(
                        command.enumValueOfParameterNamed(LoanProductConstants.CHARGE_OFF_BEHAVIOUR, LoanChargeOffBehaviour.class));
            }
            if (!changes.isEmpty()) {
                product.validateLoanProductPreSave();
                this.loanProductRepository.saveAndFlush(product);
            }
            return new CommandProcessingResultBuilder() //
                    .withCommandId(command.commandId()) //
                    .withEntityId(loanProductId) //
                    .with(changes) //
                    .build();

        } catch (final DataIntegrityViolationException | JpaSystemException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.resourceResult(-1L);
        } catch (final PersistenceException dve) {
            Throwable throwable = ExceptionUtils.getRootCause(dve.getCause());
            handleDataIntegrityIssues(command, throwable, dve);
            return CommandProcessingResult.empty();
        }
    }
}
