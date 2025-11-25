package com.crediblex.fineract.portfolio.loc.exception;

import java.math.BigDecimal;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

public class ActivationInsufficientBalanceException extends AbstractPlatformDomainRuleException {

    public ActivationInsufficientBalanceException(final BigDecimal transactionAmount) {
        super("error.msg.loc.insufficient.settlement.savings.balance",
                "Complete the LOC Activation Fee payment to enable your credit facility.", "charge", transactionAmount);
    }
}
