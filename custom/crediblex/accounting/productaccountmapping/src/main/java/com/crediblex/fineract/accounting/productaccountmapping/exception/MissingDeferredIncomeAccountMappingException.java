package com.crediblex.fineract.accounting.productaccountmapping.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

/**
 * Thrown when a loan product has line of credit receivable enabled under periodic accrual accounting
 * but the deferred income GL account mapping was not provided.
 */
public class MissingDeferredIncomeAccountMappingException extends AbstractPlatformDomainRuleException {

    public MissingDeferredIncomeAccountMappingException() {
        super("error.msg.loanproduct.deferred.income.account.required.for.loc.receivable",
                "Deferred income GL Account mapping is mandatory when line of credit receivable is enabled for periodic accrual accounting.");
    }
}

