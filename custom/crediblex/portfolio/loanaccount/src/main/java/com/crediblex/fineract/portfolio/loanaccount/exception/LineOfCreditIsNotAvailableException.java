package com.crediblex.fineract.portfolio.loanaccount.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

public class LineOfCreditIsNotAvailableException extends AbstractPlatformDomainRuleException {

    public LineOfCreditIsNotAvailableException(Exception e, String locName) {
        super("error.msg.line.of.credit.disabled", "Line of credit " + locName + " is not found or it is disabled", e);
    }

    public LineOfCreditIsNotAvailableException(String locName) {
        super("error.msg.line.of.credit.disabled", "Line of credit " + locName + " is disabled or is not active by the drawdown date");
    }

}
