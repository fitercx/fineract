package com.crediblex.fineract.portfolio.loanaccount.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

public class LinkAccountExpiredException extends AbstractPlatformDomainRuleException {

    public LinkAccountExpiredException(final String entity, final String defaultUserMessage, final Object... defaultUserMessageArgs) {
        super("error.msg." + entity + ".expired", defaultUserMessage, defaultUserMessageArgs);
    }

}
