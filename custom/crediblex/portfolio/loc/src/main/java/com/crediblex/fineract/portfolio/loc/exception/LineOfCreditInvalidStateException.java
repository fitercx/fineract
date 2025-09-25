package com.crediblex.fineract.portfolio.loc.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformResourceNotFoundException;

public class LineOfCreditInvalidStateException extends AbstractPlatformResourceNotFoundException {

    public LineOfCreditInvalidStateException(final String id, String state) {
        super("error.msg.line.of.credit.in.invalid.state",
                "Line of Credit with identifier " + id + " is in state " + state + " that does not support operation", id);
    }

    public LineOfCreditInvalidStateException(String message) {
        super("error.msg.line.of.credit.in.invalid.state", message);
    }
}
