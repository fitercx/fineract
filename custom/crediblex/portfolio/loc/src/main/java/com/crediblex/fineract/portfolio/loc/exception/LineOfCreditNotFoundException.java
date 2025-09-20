package com.crediblex.fineract.portfolio.loc.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformResourceNotFoundException;

public class LineOfCreditNotFoundException extends AbstractPlatformResourceNotFoundException {

    public LineOfCreditNotFoundException(final Long id) {
        super("error.msg.line.of.credit.id.invalid", "Line of Credit with identifier " + id + " does not exist", id);
    }

}
