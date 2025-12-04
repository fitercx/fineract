package com.crediblex.fineract.accounting.productaccountmapping.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

public class ProductAccountMappingNotSupported extends AbstractPlatformDomainRuleException {

    public ProductAccountMappingNotSupported() {
        super("error.msg.invalid.account.type",
                "Passed in GLAccount (Deffered Income Account) is not supported for this accoutning type type.");
    }

    public ProductAccountMappingNotSupported(Long id) {
        super("error.msg.invalid.account.type",
                "Passed in GLAccount (Deffered Income Account) with Id " + id + " is not a valid gl account");
    }
}
