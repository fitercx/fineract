package com.crediblex.fineract.accounting.productaccountmapping.service;

import static org.apache.fineract.accounting.common.AccountingConstants.AccrualAccountsForLoan.DEFERRED_INCOME;

import com.crediblex.fineract.accounting.productaccountmapping.exception.MissingDeferredIncomeAccountMappingException;
import com.crediblex.fineract.accounting.productaccountmapping.exception.ProductAccountMappingNotSupported;
import com.google.gson.JsonElement;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.common.AccountingConstants.LoanProductAccountingParams;
import org.apache.fineract.accounting.common.AccountingRuleType;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.glaccount.domain.GLAccountType;
import org.apache.fineract.accounting.productaccountmapping.service.LoanProductToGLAccountMappingHelper;
import org.apache.fineract.accounting.productaccountmapping.service.ProductToGLAccountMappingWritePlatformServiceImpl;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMapping;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.accounting.producttoaccountmapping.exception.ProductToGLAccountMappingInvalidException;
import org.apache.fineract.accounting.producttoaccountmapping.serialization.ProductToGLAccountMappingFromApiJsonDeserializer;
import org.apache.fineract.accounting.producttoaccountmapping.service.SavingsProductToGLAccountMappingHelper;
import org.apache.fineract.accounting.producttoaccountmapping.service.ShareProductToGLAccountMappingHelper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.PortfolioProductType;
import org.apache.fineract.portfolio.loanproduct.LoanProductConstants;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom extension to add deferredIncomeAccountId mapping for accrual periodic loan products.
 */
@Slf4j
@Service
@Primary
public class CustomProductToGLAccountMappingWritePlatformServiceImpl extends ProductToGLAccountMappingWritePlatformServiceImpl {

    protected final FromJsonHelper fromApiJsonHelper;
    protected final ProductToGLAccountMappingFromApiJsonDeserializer deserializer;
    protected final LoanProductToGLAccountMappingHelper loanProductToGLAccountMappingHelper;
    protected final SavingsProductToGLAccountMappingHelper savingsProductToGLAccountMappingHelper;
    protected final ShareProductToGLAccountMappingHelper shareProductToGLAccountMappingHelper;
    protected final GLAccountRepository glAccountRepository;
    private final ProductToGLAccountMappingRepository productToGLAccountMappingRepository;

    public CustomProductToGLAccountMappingWritePlatformServiceImpl(FromJsonHelper fromApiJsonHelper,
            ProductToGLAccountMappingFromApiJsonDeserializer deserializer,
            LoanProductToGLAccountMappingHelper loanProductToGLAccountMappingHelper,
            SavingsProductToGLAccountMappingHelper savingsProductToGLAccountMappingHelper,
            ShareProductToGLAccountMappingHelper shareProductToGLAccountMappingHelper, GLAccountRepository glAccountRepository,
            ProductToGLAccountMappingRepository productToGLAccountMappingRepository) {
        super(fromApiJsonHelper, deserializer, loanProductToGLAccountMappingHelper, savingsProductToGLAccountMappingHelper,
                shareProductToGLAccountMappingHelper);

        this.fromApiJsonHelper = fromApiJsonHelper;
        this.deserializer = deserializer;
        this.loanProductToGLAccountMappingHelper = loanProductToGLAccountMappingHelper;
        this.savingsProductToGLAccountMappingHelper = savingsProductToGLAccountMappingHelper;
        this.shareProductToGLAccountMappingHelper = shareProductToGLAccountMappingHelper;
        this.glAccountRepository = glAccountRepository;
        this.productToGLAccountMappingRepository = productToGLAccountMappingRepository;

    }

    @Override
    @Transactional
    public void createLoanProductToGLAccountMapping(Long loanProductId, JsonCommand command) {
        super.createLoanProductToGLAccountMapping(loanProductId, command);
        handleDeferredIncomeMappingOnCreate(loanProductId, command);
    }

    private void handleDeferredIncomeMappingOnCreate(Long loanProductId, JsonCommand command) {
        final JsonElement element = this.fromApiJsonHelper.parse(command.json());
        final Integer accountingRuleTypeId = this.fromApiJsonHelper.extractIntegerNamed("accountingRule", element, Locale.getDefault());
        final AccountingRuleType accountingRuleType = AccountingRuleType.fromInt(accountingRuleTypeId);
        if (accountingRuleType == AccountingRuleType.ACCRUAL_PERIODIC) {
            final Long deferredIncomeAccountId = fromApiJsonHelper
                    .extractLongNamed(LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue(), element);
            final Boolean enableLocReceivable = fromApiJsonHelper.extractBooleanNamed(LoanProductConstants.ENABLE_LOC_RECEIVABLE_PARAM_NAME,
                    element);
            if (Boolean.TRUE.equals(enableLocReceivable) && deferredIncomeAccountId == null) {
                // LOC receivable requires deferred income account mapping
                throw new MissingDeferredIncomeAccountMappingException();
            }
            if (deferredIncomeAccountId != null) {
                validateDeferredIncomeAccountIsLiability(deferredIncomeAccountId);

                // Check if mapping already exists to prevent duplicates
                ProductToGLAccountMapping existing = productToGLAccountMappingRepository.findCoreProductToFinAccountMapping(loanProductId,
                        PortfolioProductType.LOAN.getValue(), DEFERRED_INCOME.getValue());

                if (existing == null) {
                    // Only create mapping if it doesn't exist
                    this.loanProductToGLAccountMappingHelper.saveLoanToLiabilityAccountMapping(element,
                            LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue(), loanProductId, DEFERRED_INCOME.getValue());
                    log.debug("Created deferred income account mapping for loan product: {}", loanProductId);
                } else {
                    log.debug("Deferred income account mapping already exists for loan product: {}, skipping creation", loanProductId);
                }
            }
        } else {
            // If user supplied the param but rule is not periodic accrual -> validation failure
            if (command.parameterExists(LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue())) {
                throw new ProductAccountMappingNotSupported();
            }
        }
    }

    private void validateDeferredIncomeAccountIsLiability(Long glAccountId) {
        GLAccount glAccount = glAccountRepository.findById(glAccountId).orElse(null);
        if (glAccount == null) {
            throw new ProductAccountMappingNotSupported(glAccountId);
        }
        if (!Objects.equals(glAccount.getType(), GLAccountType.LIABILITY.getValue())) {
            throw new ProductToGLAccountMappingInvalidException(LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue(),
                    glAccount.getName(), glAccountId, GLAccountType.fromInt(glAccount.getType()).toString(),
                    GLAccountType.LIABILITY.toString());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> updateLoanProductToGLAccountMapping(Long loanProductId, JsonCommand command, boolean accountingRuleChanged,
            AccountingRuleType accountingRuleType) {

        Map<String, Object> changes = super.updateLoanProductToGLAccountMapping(loanProductId, command, accountingRuleChanged,
                accountingRuleType);

        final JsonElement element = this.fromApiJsonHelper.parse(command.json());

        if (accountingRuleChanged) {
            // After super call mappings were recreated if needed; re-add our mapping for periodic accrual
            if (accountingRuleType == AccountingRuleType.ACCRUAL_PERIODIC) {
                Long deferredIncomeAccountId = fromApiJsonHelper
                        .extractLongNamed(LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue(), element);
                final Boolean enableLocReceivable = fromApiJsonHelper
                        .extractBooleanNamed(LoanProductConstants.ENABLE_LOC_RECEIVABLE_PARAM_NAME, element);
                if (Boolean.TRUE.equals(enableLocReceivable) && deferredIncomeAccountId == null) {
                    throw new MissingDeferredIncomeAccountMappingException();
                }
                if (deferredIncomeAccountId != null) {
                    validateDeferredIncomeAccountIsLiability(deferredIncomeAccountId);
                    // ensure mapping exists after recreation
                    this.loanProductToGLAccountMappingHelper.saveLoanToLiabilityAccountMapping(element,
                            LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue(), loanProductId, DEFERRED_INCOME.getValue());
                    changes.put(LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue(), deferredIncomeAccountId);
                }
            } else {
                // accounting changed away from periodic accrual, nothing to re-add
            }
        } else {
            // selective update scenario
            if (accountingRuleType == AccountingRuleType.ACCRUAL_PERIODIC) {
                Long paramValue = this.fromApiJsonHelper.extractLongNamed(LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue(),
                        element);
                final Boolean enableLocReceivable = fromApiJsonHelper
                        .extractBooleanNamed(LoanProductConstants.ENABLE_LOC_RECEIVABLE_PARAM_NAME, element);
                if (Boolean.TRUE.equals(enableLocReceivable) && paramValue == null
                        && command.parameterExists(LoanProductConstants.ENABLE_LOC_RECEIVABLE_PARAM_NAME)) {
                    // If explicitly enabling LOC receivable without providing deferred mapping
                    throw new MissingDeferredIncomeAccountMappingException();
                }
                ProductToGLAccountMapping existing = productToGLAccountMappingRepository.findCoreProductToFinAccountMapping(loanProductId,
                        PortfolioProductType.LOAN.getValue(), DEFERRED_INCOME.getValue());
                if (paramValue != null) {
                    validateDeferredIncomeAccountIsLiability(paramValue);
                    if (existing == null || (existing.getGlAccount() != null && !existing.getGlAccount().getId().equals(paramValue))) {
                        // remove old mapping if different
                        if (existing != null) {
                            productToGLAccountMappingRepository.delete(existing);
                        }
                        this.loanProductToGLAccountMappingHelper.saveLoanToLiabilityAccountMapping(element,
                                LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue(), loanProductId,
                                DEFERRED_INCOME.getValue());
                        changes.put(LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue(), paramValue);
                    }
                } else {
                    // param omitted -> remove existing mapping if present
                    if (existing != null) {
                        productToGLAccountMappingRepository.delete(existing);
                        changes.put(LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue(), null);
                    }
                }
            } else {
                // Non periodic accrual: if mapping exists remove it
                ProductToGLAccountMapping existing = productToGLAccountMappingRepository.findCoreProductToFinAccountMapping(loanProductId,
                        PortfolioProductType.LOAN.getValue(), DEFERRED_INCOME.getValue());
                if (existing != null) {
                    productToGLAccountMappingRepository.delete(existing);
                    changes.put(LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue(), null);
                }
                if (command.parameterExists(LoanProductAccountingParams.DEFERRED_INCOME_ACCOUNT_ID.getValue())) {
                    throw new ProductAccountMappingNotSupported();
                }
            }
        }

        return changes;
    }

}
